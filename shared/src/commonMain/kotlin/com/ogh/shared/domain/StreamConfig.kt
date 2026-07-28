package com.ogh.shared.domain

/**
 * Represents the current state of a streaming session.
 *
 * Transitions follow this lifecycle:
 * Idle → Preparing → Streaming ↔ Paused → Stopped → Idle
 *
 * Error can be reached from any active state.
 */
enum class StreamState {
    /** No active streaming session. Ready to start. */
    IDLE,

    /** MediaProjection permission granted, encoders being configured. */
    PREPARING,

    /** Actively encoding and transmitting to RTMP destination(s). */
    STREAMING,

    /** RTMP and audio remain live while a still image replaces the video source. */
    PAUSED,

    /** Stream has been stopped. Resources being released. */
    STOPPED,

    /** An unrecoverable error occurred. See logs for details. */
    ERROR,
    ;

    /** Setup must remain immutable while capture is being prepared or transmitted. */
    val locksConfiguration: Boolean
        get() = when (this) {
            PREPARING, STREAMING, PAUSED -> true
            IDLE, STOPPED, ERROR -> false
        }
}

/**
 * Video encoding settings.
 *
 * @property width Horizontal resolution in pixels.
 * @property height Vertical resolution in pixels.
 * @property fps Frames per second.
 * @property bitrate Target video bitrate in bits per second.
 */
data class VideoConfig(
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val bitrate: Int = 2_500_000,
)

/**
 * Audio encoding settings.
 *
 * @property sampleRate Audio sample rate in Hz.
 * @property isStereo Whether to encode in stereo (true) or mono (false).
 * @property bitrate Target audio bitrate in bits per second.
 */
data class AudioConfig(
    val sampleRate: Int = 44_100,
    val isStereo: Boolean = false,
    val bitrate: Int = 128_000,
)
