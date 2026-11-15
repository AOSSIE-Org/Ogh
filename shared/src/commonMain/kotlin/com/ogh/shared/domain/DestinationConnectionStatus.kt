package com.ogh.shared.domain

/** Connection state of one destination's RTMP output during a session. */
enum class DestinationConnectionStatus(val displayName: String) {
    CONNECTING("Connecting"),
    LIVE("Live"),
    FAILED("Failed"),
    DISCONNECTED("Disconnected"),
}
