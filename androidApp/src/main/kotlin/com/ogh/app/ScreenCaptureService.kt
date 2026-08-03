package org.aossie.ogh

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.annotation.SuppressLint
import android.content.Intent
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.InternalAudioSource
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.audio.MixAudioSource
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.encoder.input.sources.video.NoVideoSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.sources.video.VideoSource as EncoderVideoSource
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.encoder.utils.gl.TranslateTo
import com.pedro.library.multiple.MultiStream
import com.pedro.library.multiple.MultiType
import com.ogh.shared.domain.AudioConfig
import com.ogh.shared.domain.AudioSettings
import com.ogh.shared.domain.VideoConfig
import com.ogh.shared.domain.VideoSettings
import com.ogh.shared.domain.VideoSource

/**
 * Foreground service that manages screen capture and RTMP streaming.
 *
 * Android requires MediaProjection to run inside a foreground service
 * (mandatory since Android 10, enforced with foregroundServiceType since Android 14).
 *
 * This service owns the complete MultiStream lifecycle. A bound, non-foreground
 * instance can prepare and render the idle camera preview. The same pipeline is
 * promoted to a foreground session, publishes RTMP, and is released here.
 *
 * Audio source selection (based on settings):
 * - mic ON + system OFF → MicrophoneSource
 * - mic OFF + system ON → InternalAudioSource (requires MediaProjection)
 * - mic ON + system ON → MixAudioSource (requires MediaProjection)
 * - mic OFF + system OFF → NoAudioSource (silent stream)
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "ogh_streaming_channel"
        private const val NOTIFICATION_ID = 10001
        private const val EXTRA_USES_MEDIA_PROJECTION = "uses_media_projection"
        private const val EXTRA_USES_CAMERA = "uses_camera"
        private const val EXTRA_USES_MICROPHONE = "uses_microphone"

        /** Singleton reference — allows the Activity/ViewModel to communicate with the service. */
        @Volatile
        var instance: ScreenCaptureService? = null
            private set

        fun startIntent(
            context: Context,
            usesMediaProjection: Boolean,
            usesCamera: Boolean,
            usesMicrophone: Boolean,
        ): Intent = Intent(context, ScreenCaptureService::class.java).apply {
            putExtra(EXTRA_USES_MEDIA_PROJECTION, usesMediaProjection)
            putExtra(EXTRA_USES_CAMERA, usesCamera)
            putExtra(EXTRA_USES_MICROPHONE, usesMicrophone)
        }
    }

    private lateinit var multiStream: MultiStream
    private val binder = LocalBinder()
    private var destinationCount = 1
    private var connectedDestinations = BooleanArray(destinationCount)
    private var terminalDestinations = BooleanArray(destinationCount)
    private var notificationManager: NotificationManager? = null
    private var mediaProjection: MediaProjection? = null
    private val projectionCallbackHandler = Handler(Looper.getMainLooper())
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    private var screenSource: ReusableScreenSource? = null
    private val mediaProjectionManager: MediaProjectionManager by lazy {
        applicationContext.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    private lateinit var physicalOrientationTracker: PhysicalOrientationTracker

    /** External callback to forward connection events to the UI layer. */
    var connectionCallback: ConnectChecker? = null

    private var videoConfig = VideoConfig()
    private var audioConfig = AudioConfig()
    private var micEnabled = true
    private var systemAudioEnabled = false
    private var selectedVideoSource = VideoSource.SCREEN
    private var pauseImageUri: String? = null
    private var videoPaused = false
    private var sessionPrepared = false
    private var previewSurface: Surface? = null
    private var preparedPreview: PreviewConfiguration? = null

    inner class LocalBinder : Binder()

    private data class PreviewConfiguration(
        val source: VideoSource,
        val width: Int,
        val height: Int,
        val fps: Int,
        val bitrate: Int,
    )

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        initializeStream(destinationCount)
        physicalOrientationTracker = PhysicalOrientationTracker(
            applicationContext,
            displayFallbackOrientation(CameraHelper.getCameraOrientation(applicationContext)),
            ::onPhysicalOrientationChanged,
        )
        physicalOrientationTracker.start()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service started")
        startForegroundNotification(
            usesMediaProjection = intent?.getBooleanExtra(EXTRA_USES_MEDIA_PROJECTION, false) == true,
            usesCamera = intent?.getBooleanExtra(EXTRA_USES_CAMERA, false) == true,
            usesMicrophone = intent?.getBooleanExtra(EXTRA_USES_MICROPHONE, false) == true,
        )
        // A MediaProjection grant cannot be recreated after process death.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        if (!isStreaming()) clearPreview()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service destroyed — releasing resources")
        instance = null
        if (::physicalOrientationTracker.isInitialized) physicalOrientationTracker.stop()
        releasePipeline()
    }

    private fun releasePipeline() {
        stopStream()
        if (::multiStream.isInitialized && multiStream.isOnPreview) {
            runCatching { multiStream.stopPreview() }
        }
        if (::multiStream.isInitialized) multiStream.release()
        closeScreenSource()
        clearMediaProjection()
        previewSurface = null
        preparedPreview = null
        sessionPrepared = false
        connectionCallback = null
    }

    // --- Public API for ViewModel/Activity ---

    /** Rebuilds the publisher for the exact number of enabled RTMP destinations. */
    @Synchronized
    fun configureDestinations(count: Int) {
        require(count > 0) { "At least one destination is required" }
        if (count == destinationCount) return

        clearPreview()
        stopStream()
        multiStream.release()
        destinationCount = count
        initializeStream(count)
        preparedPreview = null
        sessionPrepared = false
    }

    /**
     * Attaches the selected live source, or prepares an idle camera, on the
     * same pipeline used for publishing. No second capture owner is created.
     */
    @Synchronized
    fun showPreview(
        surface: Surface,
        surfaceWidth: Int,
        surfaceHeight: Int,
        settings: VideoSettings,
        outputCount: Int,
    ): Boolean {
        if (!surface.isValid) return false
        val safeWidth = surfaceWidth.coerceAtLeast(1)
        val safeHeight = surfaceHeight.coerceAtLeast(1)

        if (multiStream.isStreaming) {
            if (selectedVideoSource != settings.source || videoPaused) return false
            attachPreview(surface, safeWidth, safeHeight)
            return true
        }

        if (sessionPrepared) {
            if (selectedVideoSource != settings.source || videoPaused) return false
            attachPreview(surface, safeWidth, safeHeight)
            return true
        }

        if (!settings.source.usesCamera) return false

        val requiredCount = outputCount.coerceAtLeast(1)
        val requested = settings.previewConfiguration()
        if (destinationCount != requiredCount || preparedPreview != requested) {
            clearPreview()
            multiStream.release()
            destinationCount = requiredCount
            initializeStream(requiredCount)
            applyVideoSettings(settings)
            multiStream.changeVideoSource(createVideoSource(settings.source, null))
            multiStream.getGlInterface().setAspectRatioMode(AspectRatioMode.Fill)
            if (!prepareVideoEncoder()) return false
            preparedPreview = requested
        }

        attachPreview(surface, safeWidth, safeHeight)
        return true
    }

    @Synchronized
    fun detachPreview(surface: Surface) {
        if (previewSurface != surface) return
        if (::multiStream.isInitialized && multiStream.isOnPreview) {
            runCatching { multiStream.stopPreview() }
        }
        previewSurface = null
    }

    @Synchronized
    fun clearPreview() {
        if (::multiStream.isInitialized && multiStream.isOnPreview) {
            runCatching { multiStream.stopPreview() }
        }
        previewSurface = null
    }

    private fun attachPreview(surface: Surface, width: Int, height: Int) {
        multiStream.getGlInterface().apply {
            setAspectRatioMode(
                if (selectedVideoSource.usesCamera) AspectRatioMode.Fill else AspectRatioMode.Adjust,
            )
            setPreviewIsPortrait(isPortraitSurface(width, height))
        }
        if (multiStream.isOnPreview && previewSurface != surface) {
            multiStream.stopPreview()
        }
        if (!multiStream.isOnPreview) {
            multiStream.startPreview(surface, width, height)
        } else {
            multiStream.getGlInterface().setPreviewResolution(width, height)
        }
        previewSurface = surface
        applyCameraOrientation()
    }

    private fun VideoSettings.previewConfiguration() = PreviewConfiguration(
        source = source,
        width = resolution.width,
        height = resolution.height,
        fps = fps,
        bitrate = bitrate,
    )

    private fun applyVideoSettings(videoSettings: VideoSettings) {
        videoConfig = VideoConfig(
            width = videoSettings.resolution.width,
            height = videoSettings.resolution.height,
            fps = videoSettings.fps,
            bitrate = videoSettings.bitrate,
        )
        selectedVideoSource = videoSettings.source
        pauseImageUri = videoSettings.pauseImageUri
    }

    private fun applySettings(videoSettings: VideoSettings, audioSettings: AudioSettings) {
        applyVideoSettings(videoSettings)
        this.audioConfig = AudioConfig(
            sampleRate = audioSettings.sampleRate,
            isStereo = audioSettings.stereo,
            bitrate = audioSettings.bitrate,
        )
        this.micEnabled = audioSettings.enableMicrophone
        this.systemAudioEnabled = audioSettings.enableSystemAudio
        Log.i(TAG, "Settings applied: ${videoSettings.resolution.label} ${videoSettings.fps}fps, mic=$micEnabled, sysAudio=$systemAudioEnabled")
    }

    /**
     * Configures the stream with the granted MediaProjection.
     *
     * Sets up the correct audio source based on settings:
     * - mic + system → MixAudioSource (both combined)
     * - mic only → MicrophoneSource
     * - system only → InternalAudioSource
     * - neither → NoAudioSource
     *
     * @param resultCode The result code from the permission activity.
     * @param data The intent data from the permission activity.
     * @return true if the stream was successfully prepared.
     */
    @Synchronized
    fun prepareStream(
        resultCode: Int?,
        data: Intent?,
        videoSettings: VideoSettings,
        audioSettings: AudioSettings,
    ): Boolean {
        sessionPrepared = false
        val canReusePreview = multiStream.isOnPreview &&
            preparedPreview == videoSettings.previewConfiguration()
        applySettings(videoSettings, audioSettings)
        applyCameraOrientation()
        val needsProjection = selectedVideoSource == VideoSource.SCREEN ||
            (systemAudioEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        if (needsProjection && (resultCode == null || data == null)) {
            Log.e(TAG, "MediaProjection permission is required for the selected sources")
            return false
        }

        startForegroundNotification(
            usesMediaProjection = needsProjection,
            usesCamera = selectedVideoSource.usesCamera,
            usesMicrophone = micEnabled,
        )
        if (multiStream.isStreaming) stopStream()
        closeScreenSource()
        clearMediaProjection()
        val projection = if (needsProjection) {
            mediaProjectionManager.getMediaProjection(resultCode!!, data!!)
                ?: run {
                    Log.e(TAG, "Failed to obtain MediaProjection")
                    return false
                }
        } else null
        installMediaProjection(projection)

        return try {
            if (!canReusePreview) {
                clearPreview()
                multiStream.changeVideoSource(createVideoSource(selectedVideoSource, projection))
            }
            configureAudioSource(projection)
            videoPaused = false
            val videoPrepared = canReusePreview || prepareVideoEncoder()
            val prepared = videoPrepared && multiStream.prepareAudio(
                audioConfig.sampleRate,
                audioConfig.isStereo,
                audioConfig.bitrate,
            )
            sessionPrepared = prepared
            prepared
        } catch (error: Exception) {
            Log.e(TAG, "Failed to prepare stream", error)
            false
        }
    }

    private fun createVideoSource(
        source: VideoSource,
        projection: MediaProjection?,
    ): EncoderVideoSource =
        when (source) {
            VideoSource.SCREEN -> reusableScreenSource(
                requireNotNull(projection) { "Screen capture permission is missing" },
            )
            VideoSource.BACK_CAMERA -> Camera2Source(applicationContext)
            VideoSource.FRONT_CAMERA -> Camera2Source(applicationContext).apply { switchCamera() }
        }

    private fun reusableScreenSource(projection: MediaProjection): ReusableScreenSource {
        screenSource?.takeIf { it.belongsTo(projection) }?.let { return it }
        closeScreenSource()
        return ReusableScreenSource(applicationContext, projection) {
            onMediaProjectionStopped(projection)
        }.also { screenSource = it }
    }

    @Synchronized
    private fun onMediaProjectionStopped(projection: MediaProjection) {
        if (mediaProjection !== projection) return

        val sessionRequiresProjection =
            selectedVideoSource == VideoSource.SCREEN ||
                systemAudioEnabled

        mediaProjection = null
        mediaProjectionCallback = null
        screenSource = null

        if (!sessionRequiresProjection) return

        val callback = connectionCallback

        finishSession()

        callback?.onConnectionFailed(
            "Screen capture permission ended",
        )
    }

    private fun installMediaProjection(projection: MediaProjection?) {
        mediaProjection = projection

        if (projection == null) return

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                onMediaProjectionStopped(projection)
            }
        }

        mediaProjectionCallback = callback
        projection.registerCallback(
            callback,
            projectionCallbackHandler,
        )
    }

    private fun clearMediaProjection() {
        val projection = mediaProjection
        val callback = mediaProjectionCallback

        // Clear references before stop() so an asynchronously delivered callback
        // is treated as intentional cleanup.
        mediaProjection = null
        mediaProjectionCallback = null

        if (projection != null && callback != null) {
            runCatching {
                projection.unregisterCallback(callback)
            }
        }

        if (projection != null) {
            runCatching {
                projection.stop()
            }
        }
    }

    private fun closeScreenSource() {
        screenSource?.close()
        screenSource = null
    }

    /**
     * Selects and applies the correct audio source based on mic/system audio settings.
     *
     * This is where the magic happens:
     * - MixAudioSource captures both mic + system audio simultaneously
     * - InternalAudioSource captures only system/app audio
     * - MicrophoneSource captures only mic
     * - NoAudioSource for silent streams
     *
     * Both InternalAudioSource and MixAudioSource require a MediaProjection reference.
     */
    private fun configureAudioSource(projection: MediaProjection?) {
        if (systemAudioEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "System audio capture requires Android 10; continuing without it")
            multiStream.changeAudioSource(
                if (micEnabled) MicrophoneSource() else NoAudioSource(),
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            configureModernAudioSource(projection)
            return
        }

        multiStream.changeAudioSource(if (micEnabled) MicrophoneSource() else NoAudioSource())
    }

    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun configureModernAudioSource(projection: MediaProjection?) {
        val audioSource = when {
            micEnabled && systemAudioEnabled -> {
                Log.i(TAG, "Audio source: MixAudioSource (mic + system)")
                MixAudioSource(requireNotNull(projection), null)
            }
            systemAudioEnabled -> {
                Log.i(TAG, "Audio source: InternalAudioSource (system only)")
                InternalAudioSource(requireNotNull(projection), null)
            }
            micEnabled -> {
                Log.i(TAG, "Audio source: MicrophoneSource (mic only)")
                MicrophoneSource()
            }
            else -> {
                Log.i(TAG, "Audio source: NoAudioSource (silent)")
                NoAudioSource()
            }
        }

        multiStream.changeAudioSource(audioSource)
    }

    /** Replaces live video with a local image while audio and RTMP stay connected. */
    @Synchronized
    fun pauseVideo(): Boolean {
        if (!multiStream.isStreaming || videoPaused) return false
        var filterApplied = false
        return runCatching {
            val (canvasWidth, canvasHeight) = encodedVideoDimensions()
            val bitmap = PauseSlateFactory(applicationContext).create(
                pauseImageUri?.let(Uri::parse),
                canvasWidth,
                canvasHeight,
            )
            val pauseFilter = ImageObjectFilterRender().apply {
                setImage(bitmap)
                setScale(100f, 100f)
                setPosition(TranslateTo.CENTER)
            }
            multiStream.getGlInterface().setFilter(pauseFilter)
            filterApplied = true
            multiStream.requestKeyframe()
            videoPaused = true
            updateNotification("Video hidden — stream remains live")
            true
        }.getOrElse { error ->
            Log.e(TAG, "Failed to display pause image", error)
            if (filterApplied) multiStream.getGlInterface().clearFilters()
            false
        }
    }

    /** Restores the selected screen or camera source without reconnecting RTMP. */
    @Synchronized
    fun resumeVideo(): Boolean {
        if (!multiStream.isStreaming || !videoPaused) return false
        return runCatching {
            multiStream.getGlInterface().clearFilters()
            applyCameraOrientation()
            multiStream.requestKeyframe()
            videoPaused = false
            updateNotification("Streaming in progress…")
            true
        }.getOrElse { error ->
            Log.e(TAG, "Failed to restore video source", error)
            false
        }
    }

    /**
     * Begins RTMP streaming to every configured endpoint using one encoder.
     *
     * Endpoint order maps to RootEncoder's indexed RTMP clients.
     */
    @Synchronized
    fun startStreams(endpoints: List<String>): Boolean {
        require(endpoints.size == destinationCount) {
            "Expected $destinationCount destinations, got ${endpoints.size}"
        }
        val startedIndices = mutableListOf<Int>()
        return runCatching {
            endpoints.forEachIndexed { index, endpoint ->
                Log.i(TAG, "Starting RTMP destination ${index + 1}/$destinationCount (key redacted)")
                multiStream.startStream(MultiType.RTMP, index, endpoint)
                startedIndices.add(index)
            }
            true
        }.getOrElse { error ->
            Log.e(TAG, "Failed to start streams, rolling back", error)
            startedIndices.forEach { index ->
                runCatching { multiStream.stopStream(MultiType.RTMP, index) }
            }
            false
        }
    }

    /** Stops the active RTMP stream. */
    @Synchronized
    fun stopStream() {
        if (::multiStream.isInitialized && multiStream.isStreaming) {
            Log.i(TAG, "Stopping stream")
            repeat(destinationCount) { index ->
                multiStream.stopStream(MultiType.RTMP, index)
            }
        }
        videoPaused = false
    }

    /** Reconfigures the audio source during an active stream without reconnecting RTMP. */
    @Synchronized
    fun updateLiveAudio(
        enableMicrophone: Boolean,
        enableSystemAudio: Boolean,
        resultCode: Int? = null,
        data: Intent? = null,
    ): Boolean {
        if (!::multiStream.isInitialized || !multiStream.isStreaming) {
            return false
        }

        val previousMicrophone = micEnabled
        val previousSystemAudio = systemAudioEnabled
        var createdProjection = false

        return runCatching {
            if (
                enableSystemAudio &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                mediaProjection == null
            ) {
                require(resultCode != null && data != null) {
                    "Screen capture permission is required for system audio"
                }

                val projection =
                    mediaProjectionManager.getMediaProjection(resultCode, data)
                        ?: error("Failed to obtain screen capture permission")

                installMediaProjection(projection)
                createdProjection = true
            }

            startForegroundNotification(
                usesMediaProjection = mediaProjection != null,
                usesCamera = selectedVideoSource.usesCamera,
                usesMicrophone = enableMicrophone,
            )

            micEnabled = enableMicrophone
            systemAudioEnabled = enableSystemAudio

            configureAudioSource(mediaProjection)

            updateNotification(
                if (videoPaused) {
                    "Video hidden — stream remains live"
                } else {
                    "Streaming in progress…"
                },
            )

            true
        }.getOrElse { error ->
            Log.e(TAG, "Failed to update live audio", error)

            micEnabled = previousMicrophone
            systemAudioEnabled = previousSystemAudio

            if (createdProjection) {
                clearMediaProjection()
            }

            runCatching {
                configureAudioSource(mediaProjection)
            }.onFailure { rollbackError ->
                Log.e(TAG, "Failed to restore previous audio source", rollbackError)
            }

            startForegroundNotification(
                usesMediaProjection = mediaProjection != null,
                usesCamera = selectedVideoSource.usesCamera,
                usesMicrophone = previousMicrophone,
            )

            false
        }
    }

    /** Whether the current session already holds reusable screen-capture consent. */
    @Synchronized
    fun hasMediaProjection(): Boolean = mediaProjection != null

    /**
     * Changes the full-frame source without rebuilding the encoder or RTMP
     * clients. Calls are serialized so rapid taps cannot overlap camera and
     * MediaProjection transitions.
     */
    @Synchronized
    fun switchVideoSource(
        source: VideoSource,
        resultCode: Int? = null,
        data: Intent? = null,
    ): Boolean {
        if (!multiStream.isStreaming) return false
        if (source == selectedVideoSource) return true

        val previousSource = selectedVideoSource
        val previousProjection = mediaProjection
        var createdProjection: MediaProjection? = null
        return runCatching {
            if (source == VideoSource.SCREEN && mediaProjection == null) {
                require(resultCode != null && data != null) {
                    "Screen capture permission is required"
                }
                startForegroundNotification(
                    usesMediaProjection = true,
                    usesCamera = previousSource.usesCamera,
                    usesMicrophone = micEnabled,
                )
                createdProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
                    ?: error("Failed to obtain screen capture")
                installMediaProjection(createdProjection)
            }
            if (source.usesCamera) {
                startForegroundNotification(
                    usesMediaProjection = mediaProjection != null,
                    usesCamera = true,
                    usesMicrophone = micEnabled,
                )
            }

            selectedVideoSource = source
            multiStream.changeVideoSource(createVideoSource(source, mediaProjection))
            multiStream.requestKeyframe()
            applyCameraOrientation()
            multiStream.getGlInterface().setAspectRatioMode(
                if (source.usesCamera) AspectRatioMode.Fill else AspectRatioMode.Adjust,
            )
            startForegroundNotification(
                usesMediaProjection = mediaProjection != null,
                usesCamera = source.usesCamera,
                usesMicrophone = micEnabled,
            )
            updateNotification(
                if (videoPaused) "Video hidden — source selected" else "Streaming in progress…",
            )
            true
        }.getOrElse { error ->
            Log.e(TAG, "Failed to switch video source", error)
            selectedVideoSource = previousSource
            if (createdProjection != null && previousProjection == null) {
                closeScreenSource()
                clearMediaProjection()
            }
            runCatching {
                multiStream.changeVideoSource(
                    createVideoSource(previousSource, previousProjection),
                )
                multiStream.requestKeyframe()
            }.onFailure { rollbackError ->
                Log.e(TAG, "Failed to restore the previous video source", rollbackError)
            }
            applyCameraOrientation()
            startForegroundNotification(
                usesMediaProjection = previousProjection != null,
                usesCamera = previousSource.usesCamera,
                usesMicrophone = micEnabled,
            )
            false
        }
    }

    /** Ends a session and releases projection/audio data while keeping a bound preview host usable. */
    @Synchronized
    fun finishSession() {
        stopStream()
        if (multiStream.isOnPreview) runCatching { multiStream.stopPreview() }
        multiStream.release()
        closeScreenSource()
        clearMediaProjection()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        previewSurface = null
        preparedPreview = null
        sessionPrepared = false
        connectionCallback = null
        destinationCount = 1
        initializeStream(destinationCount)
        stopSelf()
    }

    /** Whether the stream is currently publishing. */
    @Synchronized
    fun isStreaming(): Boolean = multiStream.isStreaming

    /** Releases preparation resources when startup fails before RTMP begins. */
    @Synchronized
    fun abortPreparation() {
        finishSession()
    }

    private fun initializeStream(count: Int) {
        preparedPreview = null
        connectedDestinations = BooleanArray(count)
        terminalDestinations = BooleanArray(count)
        multiStream = MultiStream(
            baseContext,
            Array(count) { index -> DestinationConnectChecker(index) },
            null,
            null,
            null,
            NoVideoSource(),
            NoAudioSource(),
        ).apply {
            getGlInterface().setForceRender(true, 15)
        }
    }

    private fun prepareVideoEncoder(): Boolean = try {
        multiStream.getGlInterface().setAspectRatioMode(
            if (selectedVideoSource.usesCamera) {
                AspectRatioMode.Fill
            } else {
                AspectRatioMode.Adjust
            },
        )
    
        val prepared = multiStream.prepareVideo(
            videoConfig.width,
            videoConfig.height,
            videoConfig.bitrate,
            fps = videoConfig.fps,
            rotation = 0,
        )
    
        if (prepared) {
            applyCameraOrientation()
        }
    
        prepared
    } catch (error: IllegalArgumentException) {
        Log.e(TAG, "Failed to prepare video encoder", error)
        false
    }

    @Synchronized
    private fun onPhysicalOrientationChanged(orientation: PhysicalOrientation) {
        if (!::multiStream.isInitialized || !selectedVideoSource.usesCamera) return
        applyCameraOrientation(orientation)
    }

    private fun applyCameraOrientation(
        orientation: PhysicalOrientation = physicalOrientationTracker.current,
    ) {
        if (!selectedVideoSource.usesCamera || !::multiStream.isInitialized) return

        multiStream.getGlInterface().apply {
            // Keeps the encoded camera image upright.
            setCameraOrientation(orientation.degrees)

            // Keep this preview workaround unchanged.
            setPreviewRotation((360 - orientation.degrees) % 360)

            // Changes only the stream viewport.
            // Do not derive this from encoder rotation or preview dimensions.
            setStreamIsPortrait(orientation.isPortrait)
        }
    }

    private fun encodedVideoDimensions(): Pair<Int, Int> =
        videoConfig.width to videoConfig.height

    // --- Notification ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ogh Streaming",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Active streaming notification"
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ogh")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_notification)
            .setSilent(true)
            .setOngoing(true)
            .build()

    @SuppressLint("InlinedApi") // ServiceCompat ignores service-type bits before Android 10.
    private fun startForegroundNotification(
        usesMediaProjection: Boolean,
        usesCamera: Boolean,
        usesMicrophone: Boolean,
    ) {
        val notification = buildNotification("Preparing stream…")

        val foregroundServiceType =
            (if (usesMediaProjection) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0) or
                (if (usesCamera) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0) or
                (if (usesMicrophone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            foregroundServiceType,
        )
    }

    private fun updateNotification(text: String) {
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private inner class DestinationConnectChecker(private val index: Int) : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            terminalDestinations[index] = false
            Log.i(TAG, "Destination ${index + 1} connecting")
            if (connectedDestinations.none { it }) connectionCallback?.onConnectionStarted("")
        }

        override fun onConnectionSuccess() {
            val wasAnyConnected = connectedDestinations.any { it }
            connectedDestinations[index] = true
            terminalDestinations[index] = false
            Log.i(TAG, "Destination ${index + 1} connected")
            if (!wasAnyConnected) connectionCallback?.onConnectionSuccess()
        }

        override fun onConnectionFailed(reason: String) {
            connectedDestinations[index] = false
            terminalDestinations[index] = true
            // RootEncoder errors may contain a complete RTMP URL and stream key.
            Log.e(TAG, "Destination ${index + 1} failed")
            if (connectedDestinations.none { it } && terminalDestinations.all { it }) {
                connectionCallback?.onConnectionFailed("All destinations failed to connect")
            }
        }

        override fun onNewBitrate(bitrate: Long) {
            connectionCallback?.onNewBitrate(bitrate)
        }

        override fun onDisconnect() {
            connectedDestinations[index] = false
            terminalDestinations[index] = true
            Log.i(TAG, "Destination ${index + 1} disconnected")
            if (connectedDestinations.none { it } && terminalDestinations.all { it }) {
                connectionCallback?.onDisconnect()
            }
        }

        override fun onAuthError() {
            connectedDestinations[index] = false
            terminalDestinations[index] = true
            Log.e(TAG, "Destination ${index + 1} authentication failed")
            if (connectedDestinations.none { it } && terminalDestinations.all { it }) {
                connectionCallback?.onAuthError()
            }
        }

        override fun onAuthSuccess() {
            Log.i(TAG, "Destination ${index + 1} authentication succeeded")
            connectionCallback?.onAuthSuccess()
        }
    }
}
