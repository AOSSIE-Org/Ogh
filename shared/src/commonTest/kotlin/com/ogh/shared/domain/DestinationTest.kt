package com.ogh.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DestinationTest {

    @Test
    fun fullEndpoint_combinesUrlAndKey() {
        val dest = Destination(
            id = "1",
            name = "YouTube",
            rtmpUrl = "rtmp://a.rtmp.youtube.com/live2",
            streamKey = "xxxx-xxxx",
        )
        assertEquals("rtmp://a.rtmp.youtube.com/live2/xxxx-xxxx", dest.fullEndpoint)
    }

    @Test
    fun fullEndpoint_handlesTrailingSlash() {
        val dest = Destination(
            id = "1",
            name = "YouTube",
            rtmpUrl = "rtmp://a.rtmp.youtube.com/live2/",
            streamKey = "xxxx-xxxx",
        )
        assertEquals("rtmp://a.rtmp.youtube.com/live2/xxxx-xxxx", dest.fullEndpoint)
    }

    @Test
    fun defaultEnabled_isTrue() {
        val dest = Destination(id = "1", name = "Test", rtmpUrl = "rtmp://test", streamKey = "key")
        assertTrue(dest.enabled)
    }

    @Test
    fun defaultColor_isViolet() {
        val dest = Destination(id = "1", name = "Test", rtmpUrl = "rtmp://test", streamKey = "key")
        assertEquals(Destination.DEFAULT_COLOR_HEX, dest.colorHex)
    }

    @Test
    fun equality_basedOnAllFields() {
        val a = Destination(id = "1", name = "YT", rtmpUrl = "rtmp://yt", streamKey = "k1")
        val b = Destination(id = "1", name = "YT", rtmpUrl = "rtmp://yt", streamKey = "k1")
        val c = Destination(id = "2", name = "YT", rtmpUrl = "rtmp://yt", streamKey = "k1")
        assertEquals(a, b)
        assertFalse(a == c)
    }
}
