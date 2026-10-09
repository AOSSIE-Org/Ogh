package org.aossie.ogh

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModelProvider
import org.aossie.ogh.oauth.OAuthConfig
import org.aossie.ogh.oauth.OAuthManager
import org.aossie.ogh.oauth.TokenManager
import org.aossie.ogh.oauth.TwitchDeviceAuthClient
import org.aossie.ogh.oauth.YouTubeApiClient
import org.aossie.ogh.oauth.GoogleTokenRevoker
import org.aossie.ogh.data.SecureDestinationStorage
import org.aossie.ogh.data.SettingsRepository
import org.aossie.ogh.viewmodel.StreamViewModel
import org.aossie.ogh.ui.StreamPreview
import com.ogh.shared.ui.AboutScreen
import com.ogh.shared.ui.AboutUiState
import com.ogh.shared.ui.AccountsScreen
import com.ogh.shared.ui.PreviewScreen
import com.ogh.shared.ui.PreviewUiState
import com.ogh.shared.ui.DestinationFormScreen
import com.ogh.shared.ui.DestinationsScreen
import com.ogh.shared.ui.SettingsScreen
import com.ogh.shared.ui.navigation.NavTab
import com.ogh.shared.ui.navigation.OghNavigationBar
import com.ogh.shared.ui.navigation.Screen
import com.ogh.shared.ui.theme.OghTheme
import com.ogh.shared.domain.StreamingProvider
import com.ogh.shared.domain.DestinationType
import com.ogh.shared.domain.AudioSettings
import com.ogh.shared.domain.StreamState
import com.ogh.shared.domain.VideoSettings
import com.ogh.shared.domain.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Main entry point for the Ogh application.
 *
 * Handles:
 * - Runtime permission requests (audio, notifications)
 * - Starting the [ScreenCaptureService] as a foreground service
 * - Launching the MediaProjection permission dialog
 * - OAuth authentication flow for streaming providers
 * - Hosting the Compose UI with screen navigation
 *
 * Navigation is handled via simple state — no navigation library needed
 * for this flat hierarchy.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: StreamViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                StreamViewModel(SettingsRepository(applicationContext)) as T
        }
    }

    private var currentScreen by mutableStateOf(Screen.PREVIEW)
    private var previousRootScreen by mutableStateOf(Screen.PREVIEW)
    private var editDestinationId by mutableStateOf<String?>(null)
    private var previewServiceBound = false
    private var previewServiceReady by mutableStateOf(false)
    private var pendingCameraSource: VideoSource? = null
    private var pendingLiveScreenSwitch = false
    private var pendingLiveAudioSettings: AudioSettings? = null

    private val previewServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            previewServiceBound = true
            previewServiceReady = ScreenCaptureService.instance != null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            previewServiceReady = false
        }
    }

    // OAuth
    lateinit var tokenManager: TokenManager
        private set
    private lateinit var oauthManager: OAuthManager
    private val twitchAuthClient = TwitchDeviceAuthClient()
    private val youTubeApiClient = YouTubeApiClient()
    private val googleTokenRevoker = GoogleTokenRevoker()
    private var pendingOAuthProvider by mutableStateOf<StreamingProvider?>(null)
    private val mediaProjectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    private val availableVideoSources by lazy {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        val lensFacings = runCatching {
            manager.cameraIdList.map { cameraId ->
                manager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.LENS_FACING)
            }
        }.getOrDefault(emptyList())
        videoSourcesForLensFacings(lensFacings)
    }

    /** Handles the result of the MediaProjection permission dialog. */
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val isLiveSwitch = pendingLiveScreenSwitch
        pendingLiveScreenSwitch = false
        val pendingAudio = pendingLiveAudioSettings

        if (result.resultCode == RESULT_OK && result.data != null) {
            when {
                pendingAudio != null -> {
                    applyLiveAudioUpdate(
                        settings = pendingAudio,
                        resultCode = result.resultCode,
                        data = result.data,
                    )
                }

                isLiveSwitch -> {
                    viewModel.switchLiveVideoSource(
                        VideoSource.SCREEN,
                        result.resultCode,
                        result.data,
                    )
                }

                continuesGoLive(viewModel.streamState.value) -> {
                    resolveEndpointsAndStartService(
                        result.resultCode,
                        result.data,
                    )
                }
            }
        } else {
            if (pendingAudio != null) {
                pendingLiveAudioSettings = null
                viewModel.reportLiveAudioFailure("Screen capture permission denied")
            } else if (!isLiveSwitch && continuesGoLive(viewModel.streamState.value)) {
                viewModel.preparationFailed("Screen capture permission denied", isError = false)
            }
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    /** Handles audio permission request result. */
    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pendingAudio = pendingLiveAudioSettings

        if (pendingAudio != null) {
            if (granted) {
                continueLiveAudioUpdate(pendingAudio)
            } else {
                pendingLiveAudioSettings = null
                viewModel.reportLiveAudioFailure("Audio permission denied")
                Toast.makeText(
                    this,
                    "Audio permission is required for this audio source",
                    Toast.LENGTH_SHORT,
                ).show()
            }

            return@registerForActivityResult
        }
        if (!continuesGoLive(viewModel.streamState.value)) return@registerForActivityResult

        if (granted) {
            checkCameraPermissionAndContinue()
        } else {
            viewModel.preparationFailed("Audio permission denied", isError = false)
            Toast.makeText(this, "Audio permission is required for the selected sources", Toast.LENGTH_SHORT).show()
        }
    }

    /** Handles camera permission only when a camera is the selected video source. */
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val requestedSource = pendingCameraSource
        pendingCameraSource = null
        if (requestedSource != null) {
            if (granted) {
                applyRequestedVideoSource(requestedSource)
            } else {
                Toast.makeText(
                    this,
                    "Camera permission is required for camera preview and streaming",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            return@registerForActivityResult
        }
        if (!continuesGoLive(viewModel.streamState.value)) return@registerForActivityResult
        if (granted) {
            requestProjectionOrStart()
        } else {
            viewModel.preparationFailed("Camera permission denied", isError = false)
            Toast.makeText(this, "Camera permission is required for camera streaming", Toast.LENGTH_SHORT).show()
        }
    }

    /** Keeps long-term read access to the user-selected pause image without copying it. */
    private val pauseImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        val previous = viewModel.videoSettings.value.pauseImageUri?.toUri()
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure {
            Toast.makeText(this, "Could not keep access to that image", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        if (previous != null && previous != uri) releasePauseImagePermission(previous)
        viewModel.updateVideoSettings(viewModel.videoSettings.value.copy(pauseImageUri = uri.toString()))
    }

    /** Handles notification permission request result (Android 13+). */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Continue regardless — notifications are nice-to-have, not blocking
        if (continuesGoLive(viewModel.streamState.value)) checkAudioPermissionAndStart()
    }

    /** Handles OAuth redirect after user authenticates in browser. */
    private val oauthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val provider = pendingOAuthProvider ?: return@registerForActivityResult
        pendingOAuthProvider = null

        if (result.data != null) {
            oauthManager.handleAuthResponse(
                intent = result.data!!,
                provider = provider,
                onSuccess = { account ->
                    lifecycleScope.launch {
                        val resolvedAccount = if (provider == StreamingProvider.YOUTUBE) {
                            val channelResult = runCatching {
                                val channel = withContext(Dispatchers.IO) {
                                    youTubeApiClient.getCurrentChannel(account.accessToken)
                                }
                                account.copy(userName = channel.title, channelId = channel.id)
                            }
                            if (channelResult.isFailure) {
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        googleTokenRevoker.revoke(
                                            account.refreshToken ?: account.accessToken,
                                        )
                                    }
                                }
                                oauthManager.disconnect(provider)
                                Toast.makeText(
                                    this@MainActivity,
                                    channelResult.exceptionOrNull()?.message
                                        ?: "Could not load the YouTube channel",
                                    Toast.LENGTH_LONG,
                                ).show()
                                return@launch
                            }
                            channelResult.getOrThrow()
                        } else {
                            account
                        }
                        if (resolvedAccount != account && !tokenManager.saveAccount(resolvedAccount)) {
                            oauthManager.disconnect(provider)
                            Toast.makeText(
                                this@MainActivity,
                                "Secure account storage is unavailable",
                                Toast.LENGTH_LONG,
                            ).show()
                            return@launch
                        }
                        viewModel.addProviderDestination(resolvedAccount.provider)
                        Toast.makeText(
                            this@MainActivity,
                            "Connected to ${provider.displayName}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        Toast.makeText(this, "Auth failed: $error", Toast.LENGTH_LONG).show()
                    }
                },
            )
        } else {
            Toast.makeText(this, "Authentication cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        savedInstanceState?.let(::restorePendingRequests)

        val destinationStorage = SecureDestinationStorage(applicationContext)
        viewModel.destinationRepository.restoreManualDestinations(destinationStorage.load())
        viewModel.destinationRepository.onChanged = destinationStorage::save

        // Initialize OAuth
        tokenManager = TokenManager(applicationContext)
        oauthManager = OAuthManager(applicationContext, tokenManager)
        tokenManager.accounts.value.keys.forEach(viewModel::addProviderDestination)
        currentScreen = if (viewModel.destinationRepository.destinations.value.isEmpty()) {
            Screen.SETTINGS
        } else {
            Screen.PREVIEW
        }
        previousRootScreen = currentScreen

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.streamState,
                    viewModel.videoSettings,
                    viewModel.settingsLoaded,
                ) { state, video, settingsLoaded ->
                    Triple(state, video, settingsLoaded)
                }.collect { (state, video, settingsLoaded) ->
                    applyWindowPolicy(
                        isActive = state.locksConfiguration,
                        videoSettings = video,
                        settingsLoaded = settingsLoaded,
                    )
                }
            }
        }

        setContent {
            val streamState by viewModel.streamState.collectAsStateWithLifecycle()
            val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
            val streamingStats by viewModel.streamingStats.collectAsStateWithLifecycle()
            val isSourceSwitching by viewModel.isSourceSwitching.collectAsStateWithLifecycle()
            val destinations by viewModel.destinationRepository.destinations.collectAsStateWithLifecycle()
            val videoSettings by viewModel.videoSettings.collectAsStateWithLifecycle()
            val audioSettings by viewModel.audioSettings.collectAsStateWithLifecycle()
            val streamMetadata by viewModel.streamMetadata.collectAsStateWithLifecycle()
            val connectedAccounts by tokenManager.accounts.collectAsStateWithLifecycle()

            LaunchedEffect(destinations.isEmpty(), streamState) {
                when {
                    streamState.locksConfiguration -> currentScreen = Screen.PREVIEW
                    destinations.isEmpty() && currentScreen == Screen.PREVIEW -> {
                        currentScreen = Screen.SETTINGS
                    }
                }
            }

            OghTheme {
                val showNavigationBar = !streamState.locksConfiguration && currentScreen in setOf(
                    Screen.PREVIEW,
                    Screen.SETTINGS,
                    Screen.ABOUT,
                )

                // Handle system back gesture / button properly
                BackHandler(
                    enabled = currentScreen != Screen.PREVIEW &&
                        !(currentScreen == Screen.SETTINGS && destinations.isEmpty()),
                ) {
                    navigateBack()
                }

                Scaffold(
                    bottomBar = {
                        if (showNavigationBar) {
                            OghNavigationBar(
                                currentScreen = currentScreen,
                                canNavigateToStream = destinations.isNotEmpty(),
                                onTabSelected = { tab ->
                                    if (!streamState.locksConfiguration) {
                                        val targetScreen = when (tab) {
                                            NavTab.STREAM -> Screen.PREVIEW
                                            NavTab.SETTINGS -> Screen.SETTINGS
                                            NavTab.ABOUT -> Screen.ABOUT
                                        }
                                        if (targetScreen != currentScreen) {
                                            if (targetScreen == Screen.ABOUT) {
                                                previousRootScreen = currentScreen
                                            }
                                            currentScreen = targetScreen
                                        }
                                    }
                                },
                            )
                        }
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding),
                    ) {
                        when (currentScreen) {
                            Screen.PREVIEW -> PreviewScreen(
                                state = PreviewUiState(
                                    streamState = streamState,
                                    statusMessage = statusMessage,
                                    stats = streamingStats,
                                    destinations = destinations,
                                    videoSettings = videoSettings,
                                    audioSettings = audioSettings,
                                    availableVideoSources = availableVideoSources,
                                    isSourceSwitching = isSourceSwitching,
                                ),
                                previewContent = { modifier ->
                                    StreamPreview(
                                        videoSettings = videoSettings,
                                        streamState = streamState,
                                        enabledDestinationCount = destinations.count { it.enabled },
                                        serviceReady = previewServiceReady,
                                        modifier = modifier,
                                    )
                                },
                                onSelectScreen = { onVideoSourceRequested(VideoSource.SCREEN) },
                                onSelectCamera = ::onCameraControlRequested,
                                onToggleMicrophone = ::onMicrophoneToggleRequested,
                                onToggleSystemAudio = ::onSystemAudioToggleRequested,
                                onStartStream = { onStartStreamRequested() },
                                onStopStream = { onStopStreamRequested() },
                                onPauseVideo = viewModel::pauseVideo,
                                onResumeVideo = viewModel::resumeVideo,
                            )
                            Screen.DESTINATIONS -> DestinationsScreen(
                                destinations = destinations,
                                onToggleDestination = viewModel::toggleDestination,
                                onDeleteDestination = viewModel::removeDestination,
                                onBack = { navigateBack() },
                                onAddDestination = { currentScreen = Screen.ADD_DESTINATION },
                                onEditDestination = { id ->
                                    editDestinationId = id
                                    currentScreen = Screen.EDIT_DESTINATION
                                },
                            )
                            Screen.ADD_DESTINATION -> DestinationFormScreen(
                                onSubmit = { _, name, url, key, colorHex ->
                                    viewModel.addDestination(name, url, key, colorHex)
                                },
                                onBack = { navigateBack() },
                            )
                            Screen.EDIT_DESTINATION -> DestinationFormScreen(
                                existing = editDestinationId?.let(viewModel.destinationRepository::getById),
                                onSubmit = { existing, name, url, key, colorHex ->
                                    if (existing == null) {
                                        "Destination not found"
                                    } else {
                                        viewModel.updateDestination(
                                            existing.id,
                                            name,
                                            url,
                                            key,
                                            colorHex,
                                        )
                                    }
                                },
                                onBack = { navigateBack() },
                            )
                            Screen.SETTINGS -> SettingsScreen(
                                videoSettings = videoSettings,
                                metadata = streamMetadata,
                                destinations = destinations,
                                onVideoSettingsChanged = viewModel::updateVideoSettings,
                                onMetadataChanged = viewModel::updateStreamMetadata,
                                onChoosePauseImage = { pauseImageLauncher.launch(arrayOf("image/*")) },
                                onUseDefaultPauseImage = { clearPauseImage() },
                                onNavigateDestinations = { navigateToConfiguration(Screen.DESTINATIONS) },
                                onAddDestination = { navigateToConfiguration(Screen.ADD_DESTINATION) },
                                onNavigateAccounts = { navigateToConfiguration(Screen.ACCOUNTS) },
                                onConnectProvider = ::connectProvider,
                            )
                            Screen.ACCOUNTS -> AccountsScreen(
                                connectedAccounts = connectedAccounts,
                                onConnect = { provider -> connectProvider(provider) },
                                onDisconnect = ::disconnectProvider,
                                onBack = { navigateBack() },
                            )
                            Screen.ABOUT -> AboutScreen(
                                state = AboutUiState(
                                    versionName = BuildConfig.VERSION_NAME,
                                    buildType = if (BuildConfig.DEBUG) "Debug" else "Release",
                                    platform = "Android · Compose Multiplatform",
                                    minimumPlatform = "Android 7.0 (API 24)",
                                    repositoryUrl = BuildConfig.REPO_URL,
                                ),
                                onOpenUrl = ::openExternalUrl,
                                onBack = { navigateBack() },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!previewServiceBound) {
            previewServiceBound = bindService(
                Intent(this, ScreenCaptureService::class.java),
                previewServiceConnection,
                BIND_AUTO_CREATE,
            )
        }
    }

    override fun onStop() {
        if (previewServiceBound) {
            unbindService(previewServiceConnection)
            previewServiceBound = false
            previewServiceReady = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::oauthManager.isInitialized) {
            oauthManager.dispose()
        }
    }

    /**
     * Keeps permission and consent requests attributable after recreation, so a
     * result for a preview or live-session request is never mistaken for Go Live.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PENDING_CAMERA_SOURCE, pendingCameraSource?.name)
        outState.putBoolean(STATE_PENDING_LIVE_SCREEN_SWITCH, pendingLiveScreenSwitch)
        pendingLiveAudioSettings?.let { settings ->
            outState.putBooleanArray(
                STATE_PENDING_LIVE_AUDIO,
                booleanArrayOf(settings.enableMicrophone, settings.enableSystemAudio),
            )
        }
    }

    private fun restorePendingRequests(state: Bundle) {
        pendingCameraSource = state.getString(STATE_PENDING_CAMERA_SOURCE)
            ?.let { name -> VideoSource.entries.firstOrNull { it.name == name } }
        pendingLiveScreenSwitch = state.getBoolean(STATE_PENDING_LIVE_SCREEN_SWITCH)
        pendingLiveAudioSettings = state.getBooleanArray(STATE_PENDING_LIVE_AUDIO)
            ?.takeIf { it.size == 2 }
            ?.let { (microphone, systemAudio) ->
                viewModel.audioSettings.value.copy(
                    enableMicrophone = microphone,
                    enableSystemAudio = systemAudio,
                )
            }
    }

    /**
     * Navigates back through the screen hierarchy.
     * Forms return to Destinations; configuration children return to Settings.
     */
    private fun navigateBack() {
        currentScreen = when (currentScreen) {
            Screen.ADD_DESTINATION, Screen.EDIT_DESTINATION -> Screen.DESTINATIONS
            Screen.DESTINATIONS, Screen.ACCOUNTS -> Screen.SETTINGS
            Screen.ABOUT -> if (viewModel.destinationRepository.count() > 0 && previousRootScreen != Screen.ABOUT) {
                previousRootScreen
            } else {
                Screen.SETTINGS
            }
            Screen.SETTINGS -> if (viewModel.destinationRepository.count() > 0) {
                Screen.PREVIEW
            } else {
                Screen.SETTINGS
            }
            Screen.PREVIEW -> Screen.PREVIEW
        }
    }

    /** Keeps session-affecting setup immutable from preparation through stop. */
    private fun navigateToConfiguration(screen: Screen) {
        if (!viewModel.streamState.value.locksConfiguration) {
            currentScreen = screen
        }
    }

    private fun onMicrophoneToggleRequested() {
        val current = viewModel.audioSettings.value
        val updated = current.copy(
            enableMicrophone = !current.enableMicrophone,
        )

        if (viewModel.streamState.value !in setOf(
                StreamState.STREAMING,
                StreamState.PAUSED,
            )
        ) {
            viewModel.toggleMicrophone()
            return
        }

        requestLiveAudioUpdate(updated)
    }

    private fun onSystemAudioToggleRequested() {
        val current = viewModel.audioSettings.value
        val updated = current.copy(
            enableSystemAudio = !current.enableSystemAudio,
        )

        if (viewModel.streamState.value !in setOf(
                StreamState.STREAMING,
                StreamState.PAUSED,
            )
        ) {
            viewModel.toggleSystemAudio()
            return
        }

        requestLiveAudioUpdate(updated)
    }

    private fun requestLiveAudioUpdate(settings: AudioSettings) {
        pendingLiveAudioSettings = settings

        val audioPermissionGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED

        if (requiresRecordAudio(settings) && !audioPermissionGranted) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        continueLiveAudioUpdate(settings)
    }

    private fun continueLiveAudioUpdate(settings: AudioSettings) {
        val needsProjection =
            settings.enableSystemAudio &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ScreenCaptureService.instance?.hasMediaProjection() != true

        if (needsProjection) {
            pendingLiveAudioSettings = settings
            screenCaptureLauncher.launch(
                mediaProjectionManager.createScreenCaptureIntent(),
            )
            return
        }

        applyLiveAudioUpdate(settings)
    }

    private fun applyLiveAudioUpdate(
        settings: AudioSettings,
        resultCode: Int? = null,
        data: Intent? = null,
    ) {
        val service = ScreenCaptureService.instance

        val applied = service?.updateLiveAudio(
            enableMicrophone = settings.enableMicrophone,
            enableSystemAudio = settings.enableSystemAudio,
            resultCode = resultCode,
            data = data,
        ) == true

        pendingLiveAudioSettings = null

        if (applied) {
            viewModel.commitLiveAudioSettings(settings)
        } else {
            viewModel.reportLiveAudioFailure("Could not change audio source")
            Toast.makeText(
                this,
                "Could not change audio source",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun onCameraControlRequested() {
        val target = nextCameraSource(
            current = viewModel.videoSettings.value.source,
            availableSources = availableVideoSources,
        ) ?: run {
            Toast.makeText(this, "No compatible camera is available", Toast.LENGTH_SHORT).show()
            return
        }
        onVideoSourceRequested(target)
    }

    private fun onVideoSourceRequested(source: VideoSource) {
        if (viewModel.streamState.value == com.ogh.shared.domain.StreamState.PREPARING) return
        if (source.usesCamera) {
            if (!hasCamera(source)) {
                Toast.makeText(this, "The selected camera is unavailable", Toast.LENGTH_SHORT).show()
                return
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                pendingCameraSource = source
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                return
            }
        }
        applyRequestedVideoSource(source)
    }

    private fun applyRequestedVideoSource(source: VideoSource) {
        val state = viewModel.streamState.value
        if (state in setOf(
                com.ogh.shared.domain.StreamState.STREAMING,
                com.ogh.shared.domain.StreamState.PAUSED,
            )
        ) {
            if (source == VideoSource.SCREEN &&
                ScreenCaptureService.instance?.hasMediaProjection() != true
            ) {
                pendingLiveScreenSwitch = true
                screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
            } else {
                viewModel.switchLiveVideoSource(source)
            }
            return
        }

        viewModel.updateVideoSettings(viewModel.videoSettings.value.copy(source = source))
        if (source == VideoSource.SCREEN) ScreenCaptureService.instance?.clearPreview()
    }

    /** Initiates OAuth flow for a streaming provider. */
    private fun connectProvider(provider: StreamingProvider) {
        if (provider == StreamingProvider.TWITCH) {
            connectTwitch()
            return
        }
        val intent = oauthManager.createAuthIntent(provider)
        if (intent == null) {
            Toast.makeText(
                this,
                "No ${provider.displayName} client ID configured. Set ${provider.name.lowercase()}_client_id in res/values/strings.xml",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        pendingOAuthProvider = provider
        oauthLauncher.launch(intent)
    }

    private fun connectTwitch() {
        val clientId = OAuthConfig.getClientId(StreamingProvider.TWITCH, this)
        if (clientId == null) {
            Toast.makeText(
                this,
                "No Twitch client ID configured. Set twitch_client_id in res/values/strings.xml",
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        lifecycleScope.launch {
            runCatching {
                val authorization = withContext(Dispatchers.IO) {
                    twitchAuthClient.requestDeviceAuthorization(clientId)
                }
                startActivity(Intent(Intent.ACTION_VIEW, authorization.verificationUri.toUri()))
                Toast.makeText(
                    this@MainActivity,
                    "Approve code ${authorization.userCode} in Twitch",
                    Toast.LENGTH_LONG,
                ).show()
                val account = withContext(Dispatchers.IO) {
                    twitchAuthClient.awaitAccount(clientId, authorization)
                }
                if (!tokenManager.saveAccount(account)) {
                    error("Secure token storage is unavailable")
                }
                viewModel.addProviderDestination(StreamingProvider.TWITCH)
                Toast.makeText(this@MainActivity, "Connected to Twitch", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(
                    this@MainActivity,
                    error.message ?: "Twitch authentication failed",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun disconnectProvider(provider: StreamingProvider) {
        lifecycleScope.launch {
            val account = tokenManager.getAccount(provider) ?: return@launch
            if (provider == StreamingProvider.YOUTUBE) {
                val token = account.refreshToken ?: account.accessToken
                val revoked = runCatching {
                    withContext(Dispatchers.IO) { googleTokenRevoker.revoke(token) }
                }
                if (revoked.isFailure) {
                    Toast.makeText(
                        this@MainActivity,
                        "Could not revoke YouTube access. Check your connection and try again.",
                        Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }
            } else if (provider == StreamingProvider.TWITCH) {
                val clientId = OAuthConfig.getClientId(provider, this@MainActivity)
                    ?: error("Configure the Twitch client ID")
                val revoked = runCatching {
                    withContext(Dispatchers.IO) {
                        twitchAuthClient.revoke(clientId, account.accessToken)
                    }
                }
                if (revoked.isFailure) {
                    Toast.makeText(
                        this@MainActivity,
                        "Could not revoke Twitch access. Check your connection and try again.",
                        Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }
            }
            oauthManager.disconnect(provider)
            viewModel.removeProviderDestination(provider)
            Toast.makeText(
                this@MainActivity,
                "Disconnected from ${provider.displayName}",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /**
     * Called when the user taps "Go Live".
     *
     * Validates destinations, then chains through permission requests
     * before starting the stream.
     */
    private fun onStartStreamRequested() {
        val error = viewModel.validateForStreaming()
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            return
        }
        if (!viewModel.beginPreparing()) return

        continueStreamingPermissionFlow()
    }

    private suspend fun resolveProviderEndpoints(): Map<DestinationType, String> =
        withContext(Dispatchers.IO) {
            val enabledTypes = viewModel.destinationRepository.getEnabledDestinations()
                .mapTo(mutableSetOf()) { it.type }
            buildMap {
                if (DestinationType.TWITCH_OAUTH in enabledTypes) {
                    var account = tokenManager.getAccount(StreamingProvider.TWITCH)
                        ?: error("Reconnect Twitch before going live")
                    val clientId = OAuthConfig.getClientId(StreamingProvider.TWITCH, this@MainActivity)
                        ?: error("Configure the Twitch client ID")
                    if (account.expiresAtMs <= System.currentTimeMillis() + TOKEN_EXPIRY_SKEW_MS) {
                        account = twitchAuthClient.refreshAccount(clientId, account)
                        if (!tokenManager.saveAccount(account)) {
                            error("Secure token storage is unavailable")
                        }
                    }
                    put(
                        DestinationType.TWITCH_OAUTH,
                        twitchAuthClient.getStreamEndpoint(clientId, account),
                    )
                }
                if (DestinationType.YOUTUBE_OAUTH in enabledTypes) {
                    val accessToken = getValidYouTubeAccessToken()
                    val broadcast = youTubeApiClient.createAndBindBroadcast(
                        accessToken,
                        viewModel.streamMetadata.value,
                    )
                    put(DestinationType.YOUTUBE_OAUTH, broadcast.fullEndpoint)
                }
            }
        }

    private suspend fun getValidYouTubeAccessToken(): String {
        val account = tokenManager.getAccount(StreamingProvider.YOUTUBE)
            ?: error("Reconnect YouTube before going live")
        if (account.expiresAtMs > System.currentTimeMillis() + TOKEN_EXPIRY_SKEW_MS) {
            return account.accessToken
        }
        return suspendCoroutine { continuation ->
            oauthManager.refreshToken(
                provider = StreamingProvider.YOUTUBE,
                onSuccess = continuation::resume,
                onError = { continuation.resumeWithException(IllegalStateException(it)) },
            )
        }
    }

    private fun continueStreamingPermissionFlow() {
        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        checkAudioPermissionAndStart()
    }

    private fun checkAudioPermissionAndStart() {
        // Android playback capture also requires RECORD_AUDIO, even without mic capture.
        val audioSettings = viewModel.audioSettings.value
        if (requiresRecordAudio(audioSettings) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        checkCameraPermissionAndContinue()
    }

    private fun checkCameraPermissionAndContinue() {
        val videoSettings = viewModel.videoSettings.value
        if (videoSettings.source.usesCamera) {
            if (!hasCamera(videoSettings.source)) {
                val message =
                    "The selected ${videoSettings.source.displayName.lowercase()} is unavailable"
                viewModel.preparationFailed(message)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                return
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                return
            }
        }
        requestProjectionOrStart()
    }

    private fun hasCamera(source: VideoSource): Boolean {
        val expectedFacing = when (source) {
            VideoSource.SCREEN -> return true
            VideoSource.BACK_CAMERA -> CameraCharacteristics.LENS_FACING_BACK
            VideoSource.FRONT_CAMERA -> CameraCharacteristics.LENS_FACING_FRONT
        }
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        return runCatching {
            manager.cameraIdList.any { cameraId ->
                manager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.LENS_FACING) == expectedFacing
            }
        }.getOrDefault(false)
    }

    private fun requestProjectionOrStart() {
        if (requiresMediaProjection(
                viewModel.videoSettings.value,
                viewModel.audioSettings.value,
                Build.VERSION.SDK_INT,
            )
        ) {
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        } else {
            resolveEndpointsAndStartService(resultCode = null, data = null)
        }
    }

    /** Creates short-lived provider broadcasts only after all capture permissions are granted. */
    private fun resolveEndpointsAndStartService(resultCode: Int?, data: Intent?) {
        lifecycleScope.launch {
            runCatching { resolveProviderEndpoints() }
                .onSuccess { endpoints ->
                    viewModel.setProviderEndpoints(endpoints)
                    startCaptureService(resultCode, data)
                }
                .onFailure { error ->
                    val message = error.message ?: "Could not prepare connected accounts"
                    viewModel.preparationFailed(message)
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun startCaptureService(resultCode: Int?, data: Intent?) {
        val video = viewModel.videoSettings.value
        val audio = viewModel.audioSettings.value
        val needsProjection = requiresMediaProjection(video, audio, Build.VERSION.SDK_INT)
        val serviceIntent = ScreenCaptureService.startIntent(
            this,
            usesMediaProjection = needsProjection,
            usesCamera = video.source.usesCamera,
            usesMicrophone = audio.enableMicrophone,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        lifecycleScope.launch {
            repeat(SERVICE_START_ATTEMPTS) {
                if (ScreenCaptureService.instance != null) {
                    viewModel.startPreparedStream(resultCode, data)
                    return@launch
                }
                delay(SERVICE_START_POLL_MS)
            }
            viewModel.preparationFailed("Failed to start streaming service")
            stopService(serviceIntent)
            Toast.makeText(this@MainActivity, "Failed to start streaming service", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearPauseImage() {
        viewModel.videoSettings.value.pauseImageUri?.toUri()?.let(::releasePauseImagePermission)
        viewModel.updateVideoSettings(viewModel.videoSettings.value.copy(pauseImageUri = null))
    }

    private fun openExternalUrl(url: String) {
        runCatching {
            require(url.startsWith("https://"))
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }.onFailure {
            Toast.makeText(this, "Could not open this link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun releasePauseImagePermission(uri: Uri) {
        runCatching {
            contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun applyWindowPolicy(
        isActive: Boolean,
        videoSettings: VideoSettings,
        settingsLoaded: Boolean,
    ) {
        requestedOrientation = requestedOrientationFor(
            lockOrientation = videoSettings.lockOrientation,
            settingsLoaded = settingsLoaded,
        )
        if (isActive && videoSettings.keepScreenAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun onStopStreamRequested() {
        viewModel.stopStreaming()
        ScreenCaptureService.instance?.stopSelf()
    }

    private companion object {
        const val TOKEN_EXPIRY_SKEW_MS = 60_000L
        const val SERVICE_START_ATTEMPTS = 30
        const val SERVICE_START_POLL_MS = 100L
        const val STATE_PENDING_CAMERA_SOURCE = "pending_camera_source"
        const val STATE_PENDING_LIVE_SCREEN_SWITCH = "pending_live_screen_switch"
        const val STATE_PENDING_LIVE_AUDIO = "pending_live_audio"
    }
}

/**
 * Permission and consent results may advance the Go Live chain only while a
 * user-initiated start is preparing; any other result must not start a session.
 */
internal fun continuesGoLive(state: StreamState): Boolean = state == StreamState.PREPARING

/** Returns whether the selected capture sources require Android audio permission. */
internal fun requiresRecordAudio(audioSettings: com.ogh.shared.domain.AudioSettings): Boolean =
    audioSettings.enableMicrophone || audioSettings.enableSystemAudio

/** Applies the persisted orientation preference across the entire app lifecycle. */
internal fun requestedOrientationFor(lockOrientation: Boolean, settingsLoaded: Boolean): Int =
    if (settingsLoaded && lockOrientation) {
        ActivityInfo.SCREEN_ORIENTATION_LOCKED
    } else {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

/** Screen capture and Android 10+ playback audio both use one MediaProjection grant. */
internal fun requiresMediaProjection(
    videoSettings: VideoSettings,
    audioSettings: AudioSettings,
    sdkInt: Int,
): Boolean = videoSettings.source == com.ogh.shared.domain.VideoSource.SCREEN ||
    (audioSettings.enableSystemAudio && sdkInt >= Build.VERSION_CODES.Q)

/** Maps Android camera capabilities to the facing-based inputs RootEncoder opens safely. */
internal fun videoSourcesForLensFacings(lensFacings: Iterable<Int?>): List<VideoSource> = buildList {
    add(VideoSource.SCREEN)
    if (CameraCharacteristics.LENS_FACING_BACK in lensFacings) add(VideoSource.BACK_CAMERA)
    if (CameraCharacteristics.LENS_FACING_FRONT in lensFacings) add(VideoSource.FRONT_CAMERA)
}

/** Selects the first camera, or flips between the available front/back cameras. */
internal fun nextCameraSource(
    current: VideoSource,
    availableSources: List<VideoSource>,
): VideoSource? {
    val cameras = availableSources.filter(VideoSource::usesCamera)
    if (cameras.isEmpty()) return null
    val currentIndex = cameras.indexOf(current)
    return if (currentIndex < 0) cameras.first() else cameras[(currentIndex + 1) % cameras.size]
}
