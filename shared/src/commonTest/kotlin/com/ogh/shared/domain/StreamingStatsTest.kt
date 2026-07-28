package com.ogh.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class StreamingStatsTest {

    @Test
    fun formattedTime_zeroSeconds() {
        val stats = StreamingStats(elapsedSeconds = 0)
        assertEquals("00:00", stats.formattedTime)
    }

    @Test
    fun formattedTime_minutesAndSeconds() {
        val stats = StreamingStats(elapsedSeconds = 125)
        assertEquals("02:05", stats.formattedTime)
    }

    @Test
    fun formattedTime_includesHours() {
        val stats = StreamingStats(elapsedSeconds = 3661)
        assertEquals("01:01:01", stats.formattedTime)
    }

    @Test
    fun formattedBitrate_kbps() {
        val stats = StreamingStats(currentBitrate = 2_500_000)
        assertEquals("2.5 Mbps", stats.formattedBitrate)
    }

    @Test
    fun formattedBitrate_lowValue() {
        val stats = StreamingStats(currentBitrate = 500)
        assertEquals("500 bps", stats.formattedBitrate)
    }

}
