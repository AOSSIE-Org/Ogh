package com.ogh.shared.ui

import com.ogh.shared.domain.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsPresetsTest {

    @Test
    fun videoPresets_coverLowThroughUltraHdUseCases() {
        assertEquals(640, Resolution.LOW_360.width)
        assertEquals(3840, Resolution.UHD_2160.width)
        assertTrue(25 in supportedFrameRates)
        assertTrue(50 in supportedFrameRates)
        assertTrue(800_000 in supportedVideoBitrates)
        assertTrue(35_000_000 in supportedVideoBitrates)
    }

    @Test
    fun bitrateLabels_preserveFractionalMegabits() {
        assertEquals("0.8 Mbps", formatVideoBitrate(800_000))
        assertEquals("1.5 Mbps", formatVideoBitrate(1_500_000))
        assertEquals("35 Mbps", formatVideoBitrate(35_000_000))
    }
}
