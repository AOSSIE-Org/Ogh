package org.aossie.ogh.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedro.common.ConnectChecker
import org.aossie.ogh.ScreenCaptureService
import org.aossie.ogh.data.AppSettingsStore
import com.ogh.shared.data.DestinationRepository
import com.ogh.shared.domain.AppSettings
import com.ogh.shared.domain.AudioSettings
import com.ogh.shared.domain.Destination
import com.ogh.shared.domain.DestinationType
import com.ogh.shared.domain.LogLevel
import com.ogh.shared.domain.StreamState
import com.ogh.shared.domain.StreamMetadata
import com.ogh.shared.domain.StreamEndpointPlanner
import com.ogh.shared.domain.StreamingStats
import com.ogh.shared.domain.StreamingProvider
import com.ogh.shared.domain.Subsystems
import com.ogh.shared.domain.VideoSettings
import com.ogh.shared.domain.VideoSource
import com.ogh.shared.validation.RtmpValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.logging.Level
import java.util.logging.Logger

/**
 * ViewModel managing streaming UI state and service coordination.
 *
 * Bridges the Compose UI layer with the [ScreenCaptureService].
 * Owns all mutable state and exposes it as observable [StateFlow]s.
 *
 * Supports multi-destination streaming via [DestinationRepository].
 */
class StreamViewModel(
    private val settingsStore: AppSettingsStore? = null,
) : ViewModel(), ConnectChecker {

    // --- Destination Management ---
    val destinationRepository = DestinationRepository()

    // --- Stream State ---
    private val _streamState = MutableStateFlow(StreamState.IDLE)
    val streamState: StateFlow<StreamState> = _streamState.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready to stream")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _streamingStats = MutableStateFlow(StreamingStats())
    val streamingStats: StateFlow<StreamingStats> = _streamingStats.asStateFlow()

    private val _isSourceSwitching = MutableStateFlow(false)
    val isSourceSwitching: StateFlow<Boolean> = _isSourceSwitching.asStateFlow()

    // --- Settings ---
    private val _videoSettings = MutableStateFlow(VideoSettings())
    val videoSettings: StateFlow<VideoSettings> = _videoSettings.asStateFlow()

    private val _audioSettings = MutableStateFlow(AudioSettings())
    val audioSettings: StateFlow<AudioSettings> = _audioSettings.asStateFlow()

    private val _streamMetadata = MutableStateFlow(StreamMetadata())
    val streamMetadata: StateFlow<StreamMetadata> = _streamMetadata.asStateFlow()

    private val _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded.asStateFlow()

    private var streamStartTimeMs: Long = 0
    private var providerEndpoints: Map<DestinationType, String> = emptyMap()
    private var settingsSnapshot = AppSettings()
    private val settingsSaveRequests = Channel<AppSettings>(Channel.CONFLATED)
    private var stopRequested = false

    init {
        if (settingsStore == null) {
            _settingsLoaded.value = true
        } else {
            viewModelScope.launch {
                applySettingsSnapshot(settingsStore.load())
                _settingsLoaded.value = true
                for (snapshot in settingsSaveRequests) {
                    settingsStore.save(snapshot)
                }
            }
        }
    }

    // --- Destination CRUD ---

    /** Adds a new destination after validation. Returns error message or null on success. */
    fun addDestination(
        name: String,
        url: String,
        key: String,
        colorHex: String = Destination.DEFAULT_COLOR_HEX,
    ): String? {
        if (_streamState.value.locksConfiguration) return CONFIGURATION_LOCKED_MESSAGE
        val error = RtmpValidator.validateDestination(url, key, name)
        if (error != null) return error

        val dest = Destination(
            id = generateId(),
            name = name.trim(),
            rtmpUrl = url.trim(),
            streamKey = key.trim(),
            colorHex = colorHex,
        )
        destinationRepository.addDestination(dest)
        log(Subsystems.STREAMER, LogLevel.INFO, "Destination added: ${dest.name}")
        return null
    }

    /** Updates an existing destination. Returns error message or null on success. */
    fun updateDestination(id: String, name: String, url: String, key: String, colorHex: String): String? {
        if (_streamState.value.locksConfiguration) return CONFIGURATION_LOCKED_MESSAGE
        val error = RtmpValidator.validateDestination(url, key, name)
        if (error != null) return error

        val existing = destinationRepository.getById(id) ?: return "Destination not found"
        destinationRepository.updateDestination(
            existing.copy(
                name = name.trim(),
                rtmpUrl = url.trim(),
                streamKey = key.trim(),
                colorHex = colorHex,
            )
        )
        log(Subsystems.STREAMER, LogLevel.INFO, "Destination updated: ${name.trim()}")
        return null
    }

    /** Removes a destination by ID. */
    fun removeDestination(id: String) {
        if (_streamState.value.locksConfiguration) return
        val dest = destinationRepository.getById(id)
        destinationRepository.removeDestination(id)
        log(Subsystems.STREAMER, LogLevel.INFO, "Destination removed: ${dest?.name ?: id}")
    }

    /** Toggles enabled/disabled state of a destination. */
    fun toggleDestination(id: String) {
        if (_streamState.value.locksConfiguration) return
        destinationRepository.toggleEnabled(id)
    }

    /** Adds the one destination owned by a newly connected provider account. */
    fun addProviderDestination(provider: StreamingProvider) {
        if (_streamState.value.locksConfiguration) return
        destinationRepository.addProviderDestination(provider)
    }

    /** Removes a provider destination after its account is disconnected. */
    fun removeProviderDestination(provider: StreamingProvider) {
        if (_streamState.value.locksConfiguration) return
        destinationRepository.removeProviderDestination(provider)
    }

    // --- Settings Updates ---

    fun updateVideoSettings(settings: VideoSettings) {
        if (_streamState.value.locksConfiguration) return
        _videoSettings.value = settings
        persistSettings()
    }

    fun updateAudioSettings(settings: AudioSettings) {
        if (_streamState.value.locksConfiguration) return
        _audioSettings.value = settings
        persistSettings()
    }

    fun toggleMicrophone() {
        val current = _audioSettings.value
        updateAudioSettings(
            current.copy(
                enableMicrophone = !current.enableMicrophone,
            ),
        )
    }

    fun toggleSystemAudio() {
        val current = _audioSettings.value
        updateAudioSettings(
            current.copy(
                enableSystemAudio = !current.enableSystemAudio,
            ),
        )
    }

    fun commitLiveAudioSettings(settings: AudioSettings) {
        if (_streamState.value !in setOf(
                StreamState.STREAMING,
                StreamState.PAUSED,
            )
        ) {
            return
        }

        _audioSettings.value = settings
        persistSettings()
    }

    fun reportLiveAudioFailure(message: String) {
        if (_streamState.value in setOf(
                StreamState.STREAMING,
                StreamState.PAUSED,
            )
        ) {
            _statusMessage.value = message
        }
    }

    /** Updates global metadata used for every supported provider broadcast. */
    fun updateStreamMetadata(metadata: StreamMetadata) {
        if (_streamState.value.locksConfiguration) return
        _streamMetadata.value = metadata
        persistSettings()
    }

    private fun applySettingsSnapshot(settings: AppSettings) {
        settingsSnapshot = settings
        _videoSettings.value = settings.videoSettings
        _audioSettings.value = settings.audioSettings
        _streamMetadata.value = settings.streamMetadata
    }

    private fun persistSettings() {
        val snapshot = settingsSnapshot.copy(
            videoSettings = _videoSettings.value,
            audioSettings = _audioSettings.value,
            streamMetadata = _streamMetadata.value,
        )
        settingsSnapshot = snapshot
        if (settingsStore != null) settingsSaveRequests.trySend(snapshot)
    }

    // --- Streaming ---

    /**
     * Validates that streaming can begin.
     *
     * @return null if valid, or an error message.
     */
    fun validateForStreaming(): String? {
        if (!_settingsLoaded.value) return "Settings are still loading. Try again in a moment."
        val enabled = destinationRepository.getEnabledDestinations()
        if (enabled.isEmpty()) return "No enabled destinations. Add at least one destination."
        return null
    }

    /** Enters a deterministic busy state before any provider or permission work begins. */
    fun beginPreparing(): Boolean {
        if (_streamState.value.locksConfiguration) return false
        _streamState.value = StreamState.PREPARING
        _statusMessage.value = "Preparing stream…"
        stopRequested = false
        return true
    }

    /** Returns to an actionable state when preparation is cancelled or fails. */
    fun preparationFailed(message: String, isError: Boolean = true) {
        _streamState.value = if (isError) StreamState.ERROR else StreamState.IDLE
        _statusMessage.value = message
        _isSourceSwitching.value = false
        providerEndpoints = emptyMap()
    }

    /** Supplies short-lived provider ingest endpoints resolved immediately before capture. */
    fun setProviderEndpoints(endpoints: Map<DestinationType, String>) {
        providerEndpoints = endpoints
    }

    /**
     * Called after the user grants screen capture permission.
     * Prepares the service and starts streaming to all enabled destinations.
     */
    fun startPreparedStream(resultCode: Int? = null, data: Intent? = null) {
        val service = ScreenCaptureService.instance ?: run {
            _streamState.value = StreamState.ERROR
            _statusMessage.value = "Service not available"
            log(Subsystems.SERVICE, LogLevel.ERROR, "ScreenCaptureService instance is null")
            return
        }

        _streamState.value = StreamState.PREPARING
        _statusMessage.value = "Preparing stream…"
        log(Subsystems.CAPTURE, LogLevel.INFO, "Capture permissions granted; preparing stream")

        service.connectionCallback = this

        val endpoints = enabledStreamEndpoints()
        if (endpoints.isEmpty()) {
            _streamState.value = StreamState.ERROR
            _statusMessage.value = "No stream endpoints are available"
            service.abortPreparation()
            return
        }

        // RootEncoder's MultiStream creates one RTMP client per enabled destination.
        service.configureDestinations(endpoints.size)
        val prepared = service.prepareStream(
            resultCode,
            data,
            _videoSettings.value,
            _audioSettings.value,
        )
        if (!prepared) {
            _streamState.value = StreamState.ERROR
            _statusMessage.value = "Failed to prepare capture source"
            log(Subsystems.ENCODER, LogLevel.ERROR, "Failed to prepare stream encoders")
            service.abortPreparation()
            return
        }

        log(
            Subsystems.RTMP,
            LogLevel.INFO,
            "Starting stream to ${endpoints.size} enabled destination(s)",
        )
        streamStartTimeMs = System.currentTimeMillis()

        if (!service.startStreams(endpoints)) {
            streamStartTimeMs = 0

            preparationFailed(
                "Failed to start all stream destinations",
            )

            service.abortPreparation()
        }
    }

    /** Keeps RTMP and audio live while replacing only the video. */
    fun pauseVideo() {
        if (_streamState.value != StreamState.STREAMING) return
        if (ScreenCaptureService.instance?.pauseVideo() == true) {
            _streamState.value = StreamState.PAUSED
            _statusMessage.value = "Video hidden — audio remains live"
            log(Subsystems.CAPTURE, LogLevel.INFO, "Video replaced with pause image")
        } else {
            _statusMessage.value = "Could not hide video"
        }
    }

    /** Restores the configured screen or camera source without reconnecting RTMP. */
    fun resumeVideo() {
        if (_streamState.value != StreamState.PAUSED) return
        if (ScreenCaptureService.instance?.resumeVideo() == true) {
            _streamState.value = StreamState.STREAMING
            _statusMessage.value = "Live"
            log(Subsystems.CAPTURE, LogLevel.INFO, "Live video restored")
        } else {
            _statusMessage.value = "Could not restore video"
        }
    }

    /** Switches the active full-frame source without reconnecting RTMP outputs. */
    fun switchLiveVideoSource(
        source: VideoSource,
        resultCode: Int? = null,
        data: Intent? = null,
    ) {
        if (_streamState.value !in setOf(StreamState.STREAMING, StreamState.PAUSED)) return
        if (_isSourceSwitching.value || source == _videoSettings.value.source) return
        val service = ScreenCaptureService.instance ?: run {
            _statusMessage.value = "Streaming service is unavailable"
            return
        }

        _isSourceSwitching.value = true
        _statusMessage.value = "Switching to ${source.displayName}…"
        viewModelScope.launch {
            try {
                val switched = withContext(Dispatchers.IO) {
                    service.switchVideoSource(source, resultCode, data)
                }
                if (_streamState.value in setOf(StreamState.STREAMING, StreamState.PAUSED)) {
                    if (switched) {
                        _videoSettings.value = _videoSettings.value.copy(source = source)
                        persistSettings()
                        _statusMessage.value = if (_streamState.value == StreamState.PAUSED) {
                            "Video hidden — ${source.displayName} selected"
                        } else {
                            "Live"
                        }
                        log(Subsystems.CAPTURE, LogLevel.INFO, "Live video source changed")
                    } else {
                        _statusMessage.value = "Could not switch video source"
                    }
                }
            } finally {
                _isSourceSwitching.value = false
            }
        }
    }

    internal fun enabledManualDestinations(): List<Destination> =
        StreamEndpointPlanner.enabledManualDestinations(
            destinationRepository.destinations.value,
        )

    internal fun enabledStreamEndpoints(): List<String> =
        StreamEndpointPlanner.enabledEndpoints(
            destinations = destinationRepository.destinations.value,
            providerEndpoints = providerEndpoints,
        )

    /** Stops the active stream. */
    fun stopStreaming() {
        stopRequested = true
        ScreenCaptureService.instance?.finishSession()
        _streamState.value = StreamState.STOPPED
        _statusMessage.value = "Stream stopped"
        _isSourceSwitching.value = false
        _streamingStats.value = StreamingStats()
        providerEndpoints = emptyMap()
        log(Subsystems.STREAMER, LogLevel.INFO, "Stream stopped by user")
    }

    // --- Logging ---

    private fun log(subsystem: String, level: LogLevel, message: String) {
        val taggedMessage = "[$subsystem] $message"
        LOGGER.log(
            when (level) {
                LogLevel.TRACE, LogLevel.DEBUG -> Level.FINE
                LogLevel.INFO -> Level.INFO
                LogLevel.WARNING -> Level.WARNING
                LogLevel.ERROR, LogLevel.FATAL -> Level.SEVERE
            },
            taggedMessage,
        )
    }

    // --- ConnectChecker callbacks ---

    override fun onConnectionStarted(url: String) {
        _streamState.value = StreamState.PREPARING
        _statusMessage.value = "Connecting…"
        log(Subsystems.RTMP, LogLevel.INFO, "Connection started")
    }

    override fun onConnectionSuccess() {
        if (_streamState.value != StreamState.PAUSED) {
            _streamState.value = StreamState.STREAMING
            _statusMessage.value = "Live"
        }
        log(Subsystems.RTMP, LogLevel.INFO, "Connection successful — stream is live")
    }

    override fun onNewBitrate(bitrate: Long) {
        val elapsed = if (streamStartTimeMs > 0) {
            (System.currentTimeMillis() - streamStartTimeMs) / 1000
        } else 0

        _streamingStats.value = _streamingStats.value.copy(
            currentBitrate = bitrate,
            elapsedSeconds = elapsed,
        )
    }

    override fun onConnectionFailed(reason: String) {
        if (stopRequested) return
        _streamState.value = StreamState.ERROR
        _statusMessage.value = "Connection failed: $reason"
        log(Subsystems.RTMP, LogLevel.ERROR, "Connection failed: $reason")
    }

    override fun onDisconnect() {
        if (stopRequested) {
            _streamState.value = StreamState.STOPPED
            _statusMessage.value = "Stream stopped"
            return
        }
        _streamState.value = StreamState.STOPPED
        _statusMessage.value = "Disconnected"
        _streamingStats.value = StreamingStats()
        log(Subsystems.RTMP, LogLevel.WARNING, "Disconnected from server")
    }

    override fun onAuthError() {
        if (stopRequested) return
        _streamState.value = StreamState.ERROR
        _statusMessage.value = "Authentication failed"
        log(Subsystems.RTMP, LogLevel.ERROR, "Authentication error")
    }

    override fun onAuthSuccess() {
        _statusMessage.value = "Authenticated"
        log(Subsystems.RTMP, LogLevel.INFO, "Authentication successful")
    }

    // --- Utilities ---

    private var idCounter = 0L

    private fun generateId(): String = "dest-${System.currentTimeMillis()}-${idCounter++}"

    private companion object {
        const val CONFIGURATION_LOCKED_MESSAGE = "Stop streaming to change setup."
        val LOGGER: Logger = Logger.getLogger(StreamViewModel::class.java.name)
    }
}
