package org.aossie.ogh.data

import com.ogh.shared.domain.AppSettings
import com.ogh.shared.domain.AudioSettings
import com.ogh.shared.domain.LatencyMode
import com.ogh.shared.domain.PrivacyStatus
import com.ogh.shared.domain.Resolution
import com.ogh.shared.domain.StreamMetadata
import com.ogh.shared.domain.VideoSettings
import com.ogh.shared.domain.VideoSource
import org.junit.Test
import kotlin.test.assertEquals

class SettingsJsonCodecTest {

    @Test
    fun roundTrip_preservesUserConfiguration() {
        val settings = AppSettings(
            videoSettings = VideoSettings(
                source = VideoSource.FRONT_CAMERA,
                resolution = Resolution.FHD_1080,
                fps = 60,
                bitrate = 6_000_000,
                lockOrientation = false,
                keepScreenAwake = false,
                pauseImageUri = "content://images/pause",
            ),
            audioSettings = AudioSettings(
                enableMicrophone = false,
                enableSystemAudio = true,
                bitrate = 192_000,
                sampleRate = 48_000,
                stereo = true,
            ),
            streamMetadata = StreamMetadata(
                title = "Launch",
                description = "Production stream",
                privacyStatus = PrivacyStatus.UNLISTED,
                latencyMode = LatencyMode.LOW,
            ),
        )

        assertEquals(settings, SettingsJsonCodec.decode(SettingsJsonCodec.encode(settings)))
    }

    @Test
    fun decode_corruptDocument_fallsBackToDefaults() {
        assertEquals(AppSettings(), SettingsJsonCodec.decode("not json"))
    }
}
