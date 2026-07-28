package com.ogh.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class AppSettingsTest {

    @Test
    fun defaultVideoSettings() {
        val settings = VideoSettings()
        assertEquals(Resolution.HD_720, settings.resolution)
        assertEquals(30, settings.fps)
        assertEquals(2_500_000, settings.bitrate)
        assertTrue(settings.lockOrientation)
        assertTrue(settings.keepScreenAwake)
    }

    @Test
    fun defaultAudioSettings() {
        val settings = AudioSettings()
        assertTrue(settings.enableMicrophone)
        assertTrue(settings.enableSystemAudio)
        assertEquals(128_000, settings.bitrate)
        assertEquals(44_100, settings.sampleRate)
        assertFalse(settings.stereo)
    }

    @Test
    fun resolution_720p_dimensions() {
        assertEquals(1280, Resolution.HD_720.width)
        assertEquals(720, Resolution.HD_720.height)
    }

    @Test
    fun resolution_1080p_dimensions() {
        assertEquals(1920, Resolution.FHD_1080.width)
        assertEquals(1080, Resolution.FHD_1080.height)
    }

    @Test
    fun appSettings_containsSafeDefaultStreamMetadata() {
        val metadata = AppSettings().streamMetadata
        assertEquals("Ogh Live", metadata.title)
        assertEquals(PrivacyStatus.PRIVATE, metadata.privacyStatus)
        assertEquals(LatencyMode.NORMAL, metadata.latencyMode)
    }
}
