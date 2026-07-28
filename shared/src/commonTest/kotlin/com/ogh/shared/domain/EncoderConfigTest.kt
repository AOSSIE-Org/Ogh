package com.ogh.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EncoderConfigTest {

    @Test
    fun videoConfig_defaults() {
        val config = VideoConfig()
        assertEquals(1280, config.width)
        assertEquals(720, config.height)
        assertEquals(30, config.fps)
        assertEquals(2_500_000, config.bitrate)
    }

    @Test
    fun audioConfig_defaultsMono() {
        val config = AudioConfig()
        assertEquals(44_100, config.sampleRate)
        assertFalse(config.isStereo)
        assertEquals(128_000, config.bitrate)
    }
}
