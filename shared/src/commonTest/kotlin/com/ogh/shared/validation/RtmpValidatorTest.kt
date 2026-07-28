package com.ogh.shared.validation

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class RtmpValidatorTest {

    // --- URL validation ---

    @Test
    fun validRtmpUrl_accepted() {
        assertTrue(RtmpValidator.isValidUrl("rtmp://a.rtmp.youtube.com/live2"))
    }

    @Test
    fun validRtmpsUrl_accepted() {
        assertTrue(RtmpValidator.isValidUrl("rtmps://a.rtmp.youtube.com/live2"))
    }

    @Test
    fun rtmpUrlWithPort_accepted() {
        assertTrue(RtmpValidator.isValidUrl("rtmp://localhost:1935/live"))
    }

    @Test
    fun rtmpUrlWithoutPath_accepted() {
        assertTrue(RtmpValidator.isValidUrl("rtmp://example.com"))
    }

    @Test
    fun blankUrl_rejected() {
        assertFalse(RtmpValidator.isValidUrl(""))
        assertFalse(RtmpValidator.isValidUrl("   "))
    }

    @Test
    fun httpUrl_rejected() {
        assertFalse(RtmpValidator.isValidUrl("http://example.com/live"))
    }

    @Test
    fun noScheme_rejected() {
        assertFalse(RtmpValidator.isValidUrl("a.rtmp.youtube.com/live2"))
    }

    @Test
    fun urlWithSpaces_rejected() {
        assertFalse(RtmpValidator.isValidUrl("rtmp://example .com/live"))
    }

    // --- Stream key validation ---

    @Test
    fun validStreamKey_accepted() {
        assertTrue(RtmpValidator.isValidStreamKey("xxxx-xxxx-xxxx-xxxx"))
    }

    @Test
    fun alphanumericKey_accepted() {
        assertTrue(RtmpValidator.isValidStreamKey("abc123XYZ"))
    }

    @Test
    fun blankKey_rejected() {
        assertFalse(RtmpValidator.isValidStreamKey(""))
        assertFalse(RtmpValidator.isValidStreamKey("   "))
    }

    @Test
    fun keyWithSpaces_rejected() {
        assertFalse(RtmpValidator.isValidStreamKey("key with spaces"))
    }

    @Test
    fun keyWithTabs_rejected() {
        assertFalse(RtmpValidator.isValidStreamKey("key\twith\ttabs"))
    }

    // --- Name validation ---

    @Test
    fun validName_accepted() {
        assertTrue(RtmpValidator.isValidName("YouTube"))
    }

    @Test
    fun blankName_rejected() {
        assertFalse(RtmpValidator.isValidName(""))
        assertFalse(RtmpValidator.isValidName("   "))
    }

    @Test
    fun longName_rejected() {
        val longName = "A".repeat(51)
        assertFalse(RtmpValidator.isValidName(longName))
    }

    @Test
    fun maxLengthName_accepted() {
        val maxName = "A".repeat(50)
        assertTrue(RtmpValidator.isValidName(maxName))
    }
}
