package com.ogh.app

import com.ogh.app.data.AppSettingsStore
import com.ogh.app.viewmodel.StreamViewModel
import com.ogh.shared.domain.AppSettings
import com.ogh.shared.domain.AudioSettings
import com.ogh.shared.domain.LatencyMode
import com.ogh.shared.domain.PrivacyStatus
import com.ogh.shared.domain.StreamMetadata
import com.ogh.shared.domain.StreamingProvider
import com.ogh.shared.domain.DestinationType
import com.ogh.shared.domain.StreamState
import com.ogh.shared.domain.VideoSettings
import com.ogh.shared.domain.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class StreamViewModelTest {

    @Test
    fun initialState_isIdle() {
        val vm = StreamViewModel()
        assertEquals(StreamState.IDLE, vm.streamState.value)
    }

    @Test
    fun initialStatusMessage_isReady() {
        val vm = StreamViewModel()
        assertEquals("Ready to stream", vm.statusMessage.value)
    }

    @Test
    fun initialDestinations_isEmpty() {
        val vm = StreamViewModel()
        assertTrue(vm.destinationRepository.destinations.value.isEmpty())
    }

    // --- Destination Management ---

    @Test
    fun addDestination_validInputs_succeeds() {
        val vm = StreamViewModel()
        val error = vm.addDestination("YouTube", "rtmp://a.rtmp.youtube.com/live2", "test-key")
        assertNull(error)
        assertEquals(1, vm.destinationRepository.count())
    }

    @Test
    fun addDestination_invalidUrl_returnsError() {
        val vm = StreamViewModel()
        val error = vm.addDestination("YouTube", "http://invalid", "test-key")
        assertNotNull(error)
        assertEquals(0, vm.destinationRepository.count())
    }

    @Test
    fun addDestination_emptyName_returnsError() {
        val vm = StreamViewModel()
        val error = vm.addDestination("", "rtmp://test.com/live", "test-key")
        assertNotNull(error)
    }

    @Test
    fun addDestination_emptyKey_returnsError() {
        val vm = StreamViewModel()
        val error = vm.addDestination("Test", "rtmp://test.com/live", "")
        assertNotNull(error)
    }

    @Test
    fun removeDestination_removesCorrectly() {
        val vm = StreamViewModel()
        vm.addDestination("YouTube", "rtmp://a.rtmp.youtube.com/live2", "key1")
        vm.addDestination("Twitch", "rtmp://live.twitch.tv/app", "key2")
        assertEquals(2, vm.destinationRepository.count())

        val firstId = vm.destinationRepository.destinations.value.first().id
        vm.removeDestination(firstId)
        assertEquals(1, vm.destinationRepository.count())
    }

    @Test
    fun toggleDestination_togglesEnabledState() {
        val vm = StreamViewModel()
        vm.addDestination("YouTube", "rtmp://a.rtmp.youtube.com/live2", "key1")
        val id = vm.destinationRepository.destinations.value.first().id

        assertTrue(vm.destinationRepository.getById(id)!!.enabled)
        vm.toggleDestination(id)
        assertFalse(vm.destinationRepository.getById(id)!!.enabled)
        vm.toggleDestination(id)
        assertTrue(vm.destinationRepository.getById(id)!!.enabled)
    }

    // --- Streaming Validation ---

    @Test
    fun validateForStreaming_noDestinations_returnsError() {
        val vm = StreamViewModel()
        assertNotNull(vm.validateForStreaming())
    }

    @Test
    fun validateForStreaming_withEnabledDestination_returnsNull() {
        val vm = StreamViewModel()
        vm.addDestination("YouTube", "rtmp://a.rtmp.youtube.com/live2", "key1")
        assertNull(vm.validateForStreaming())
    }

    @Test
    fun validateForStreaming_allDisabled_returnsError() {
        val vm = StreamViewModel()
        vm.addDestination("YouTube", "rtmp://a.rtmp.youtube.com/live2", "key1")
        val id = vm.destinationRepository.destinations.value.first().id
        vm.toggleDestination(id)
        assertNotNull(vm.validateForStreaming())
    }

    @Test
    fun enabledManualDestinations_returnsEveryEnabledManualTarget() {
        val vm = StreamViewModel()
        vm.addDestination("Primary", "rtmp://one.example/live", "key1")
        vm.addDestination("Backup", "rtmps://two.example/live", "key2")
        vm.addDestination("Disabled", "rtmp://three.example/live", "key3")
        vm.toggleDestination(vm.destinationRepository.destinations.value.last().id)
        vm.addProviderDestination(StreamingProvider.TWITCH)

        assertEquals(
            listOf("Primary", "Backup"),
            vm.enabledManualDestinations().map { it.name },
        )
    }

    @Test
    fun enabledStreamEndpoints_combinesManualAndResolvedProviderTargets() {
        val vm = StreamViewModel()
        vm.addDestination("Manual", "rtmp://one.example/live", "key1")
        vm.addProviderDestination(StreamingProvider.TWITCH)
        vm.setProviderEndpoints(
            mapOf(DestinationType.TWITCH_OAUTH to "rtmp://live.twitch.tv/app/user-key"),
        )

        assertEquals(
            listOf(
                "rtmp://one.example/live/key1",
                "rtmp://live.twitch.tv/app/user-key",
            ),
            vm.enabledStreamEndpoints(),
        )
    }

    @Test
    fun enabledStreamEndpoints_excludesDisabledProviderTarget() {
        val vm = StreamViewModel()
        vm.addProviderDestination(StreamingProvider.TWITCH)
        val twitch = vm.destinationRepository.destinations.value.single()
        vm.toggleDestination(twitch.id)
        vm.setProviderEndpoints(
            mapOf(DestinationType.TWITCH_OAUTH to "rtmp://live.twitch.tv/app/user-key"),
        )

        assertTrue(vm.enabledStreamEndpoints().isEmpty())
    }

    @Test
    fun preparingSession_rejectsDestinationAndSettingsChanges() {
        val vm = StreamViewModel()
        vm.addDestination("Primary", "rtmps://example.com/live", "key")
        val destination = vm.destinationRepository.destinations.value.single()
        val originalVideo = vm.videoSettings.value
        val originalAudio = vm.audioSettings.value
        val originalMetadata = vm.streamMetadata.value

        assertTrue(vm.beginPreparing())

        assertEquals(
            "Stop streaming to change setup.",
            vm.addDestination("Other", "rtmps://other.example/live", "key"),
        )
        assertEquals(
            "Stop streaming to change setup.",
            vm.updateDestination(
                destination.id,
                "Changed",
                destination.rtmpUrl,
                destination.streamKey,
                destination.colorHex,
            ),
        )
        vm.toggleDestination(destination.id)
        vm.removeDestination(destination.id)
        vm.addProviderDestination(StreamingProvider.YOUTUBE)
        vm.updateVideoSettings(originalVideo.copy(fps = 60))
        vm.toggleMicrophone()
        vm.toggleSystemAudio()
        vm.updateStreamMetadata(originalMetadata.copy(title = "Changed"))

        assertEquals(listOf(destination), vm.destinationRepository.destinations.value)
        assertEquals(originalVideo, vm.videoSettings.value)
        assertEquals(originalAudio, vm.audioSettings.value)
        assertEquals(originalMetadata, vm.streamMetadata.value)
    }

    @Test
    fun audioControls_toggleSourcesIndependently() {
        val vm = StreamViewModel()

        vm.toggleMicrophone()
        assertFalse(vm.audioSettings.value.enableMicrophone)
        assertTrue(vm.audioSettings.value.enableSystemAudio)

        vm.toggleSystemAudio()
        assertFalse(vm.audioSettings.value.enableMicrophone)
        assertFalse(vm.audioSettings.value.enableSystemAudio)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun rapidSettingsChanges_persistLatestSnapshotLast() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val store = DelayedFirstSaveStore()
            val vm = StreamViewModel(store)
            advanceUntilIdle()

            vm.toggleMicrophone()
            runCurrent()
            vm.toggleMicrophone()
            advanceUntilIdle()

            assertEquals(2, store.saveCount)
            assertTrue(store.persisted.audioSettings.enableMicrophone)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun stoppedSession_allowsConfigurationChangesAgain() {
        val vm = StreamViewModel()
        assertTrue(vm.beginPreparing())
        vm.stopStreaming()

        assertNull(vm.addDestination("Primary", "rtmps://example.com/live", "key"))
        assertEquals(1, vm.destinationRepository.count())
    }

    // --- State Changes ---

    @Test
    fun stopStreaming_setsStoppedState() {
        val vm = StreamViewModel()
        vm.stopStreaming()
        assertEquals(StreamState.STOPPED, vm.streamState.value)
        assertEquals("Stream stopped", vm.statusMessage.value)
    }

    @Test
    fun onConnectionSuccess_setsStreamingState() {
        val vm = StreamViewModel()
        vm.onConnectionSuccess()
        assertEquals(StreamState.STREAMING, vm.streamState.value)
    }

    @Test
    fun onConnectionFailed_setsErrorState() {
        val vm = StreamViewModel()
        vm.onConnectionFailed("timeout")
        assertEquals(StreamState.ERROR, vm.streamState.value)
    }

    @Test
    fun onDisconnect_setsStoppedState() {
        val vm = StreamViewModel()
        vm.onDisconnect()
        assertEquals(StreamState.STOPPED, vm.streamState.value)
    }

    @Test
    fun userStop_isNotOverwrittenByDisconnectCallback() {
        val vm = StreamViewModel()

        vm.stopStreaming()
        vm.onDisconnect()

        assertEquals(StreamState.STOPPED, vm.streamState.value)
        assertEquals("Stream stopped", vm.statusMessage.value)
    }

    @Test
    fun onNewBitrate_updatesStats() {
        val vm = StreamViewModel()
        vm.onNewBitrate(5000000L)
        assertEquals(5000000L, vm.streamingStats.value.currentBitrate)
    }

    @Test
    fun audioPermission_isRequiredForEveryActiveAudioSource() {
        assertFalse(
            requiresRecordAudio(AudioSettings(enableMicrophone = false, enableSystemAudio = false)),
        )
        assertTrue(
            requiresRecordAudio(AudioSettings(enableMicrophone = true, enableSystemAudio = false)),
        )
        assertTrue(
            requiresRecordAudio(AudioSettings(enableMicrophone = false, enableSystemAudio = true)),
        )
        assertTrue(
            requiresRecordAudio(AudioSettings(enableMicrophone = true, enableSystemAudio = true)),
        )
    }

    @Test
    fun mediaProjection_isRequiredOnlyForScreenOrModernPlaybackAudio() {
        val noAudio = AudioSettings(enableMicrophone = false, enableSystemAudio = false)
        val playbackAudio = noAudio.copy(enableSystemAudio = true)

        assertTrue(
            requiresMediaProjection(VideoSettings(source = VideoSource.SCREEN), noAudio, 35),
        )
        assertFalse(
            requiresMediaProjection(VideoSettings(source = VideoSource.BACK_CAMERA), noAudio, 35),
        )
        assertTrue(
            requiresMediaProjection(
                VideoSettings(source = VideoSource.FRONT_CAMERA),
                playbackAudio,
                35,
            ),
        )
        assertFalse(
            requiresMediaProjection(
                VideoSettings(source = VideoSource.FRONT_CAMERA),
                playbackAudio,
                28,
            ),
        )
    }

    @Test
    fun updateStreamMetadata_replacesGlobalMetadata() {
        val vm = StreamViewModel()
        val metadata = StreamMetadata(
            title = "Launch",
            description = "Release stream",
            privacyStatus = PrivacyStatus.UNLISTED,
            latencyMode = LatencyMode.LOW,
        )

        vm.updateStreamMetadata(metadata)

        assertEquals(metadata, vm.streamMetadata.value)
    }

    @Test
    fun providerAccount_hasExactlyOneToggleableDestination() {
        val vm = StreamViewModel()

        vm.addProviderDestination(StreamingProvider.YOUTUBE)
        vm.addProviderDestination(StreamingProvider.YOUTUBE)

        assertEquals(1, vm.destinationRepository.count())
        val destination = vm.destinationRepository.destinations.value.single()
        vm.toggleDestination(destination.id)
        assertFalse(vm.destinationRepository.getById(destination.id)!!.enabled)
    }

    @Test
    fun disconnectProvider_removesItsDestinationOnly() {
        val vm = StreamViewModel()
        vm.addProviderDestination(StreamingProvider.YOUTUBE)
        vm.addProviderDestination(StreamingProvider.TWITCH)

        vm.removeProviderDestination(StreamingProvider.YOUTUBE)

        assertEquals(
            listOf("Twitch"),
            vm.destinationRepository.destinations.value.map { it.name },
        )
    }

}

private class DelayedFirstSaveStore : AppSettingsStore {
    var saveCount = 0
    var persisted = AppSettings()

    override suspend fun load(): AppSettings = AppSettings()

    override suspend fun save(settings: AppSettings) {
        saveCount += 1
        if (saveCount == 1) delay(100)
        persisted = settings
    }
}
