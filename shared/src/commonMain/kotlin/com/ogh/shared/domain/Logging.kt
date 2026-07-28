package com.ogh.shared.domain

enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    FATAL,
}

object Subsystems {
    const val CAPTURE = "Capture"
    const val ENCODER = "Encoder"
    const val RTMP = "RTMP"
    const val SERVICE = "Service"
    const val STREAMER = "Streamer"
}
