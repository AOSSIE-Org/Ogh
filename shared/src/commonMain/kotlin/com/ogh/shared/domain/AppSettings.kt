package com.ogh.shared.domain

/**
 * Application-wide settings.
 *
 * This immutable snapshot is the source of truth for persisted configurable values.
 *
 * @property videoSettings Video encoding configuration.
 * @property audioSettings Audio configuration.
 * @property streamMetadata Metadata applied to supported OAuth providers.
 */
data class AppSettings(
    val videoSettings: VideoSettings = VideoSettings(),
    val audioSettings: AudioSettings = AudioSettings(),
    val streamMetadata: StreamMetadata = StreamMetadata(),
)

/**
 * Video-specific settings.
 *
 * @property resolution Display resolution preset.
 * @property fps Frames per second.
 * @property bitrate Video bitrate in bits per second.
 * @property lockOrientation Whether to lock the app to its current orientation.
 * @property keepScreenAwake Whether to keep the screen on during streaming.
 */
data class VideoSettings(
    val source: VideoSource = VideoSource.SCREEN,
    val resolution: Resolution = Resolution.HD_720,
    val fps: Int = 30,
    val bitrate: Int = 2_500_000,
    val lockOrientation: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val pauseImageUri: String? = null,
)

/** Video inputs supported without introducing a second encoding pipeline. */
enum class VideoSource(val displayName: String) {
    SCREEN("Screen"),
    BACK_CAMERA("Back camera"),
    FRONT_CAMERA("Front camera"),
    ;

    val usesCamera: Boolean
        get() = this != SCREEN
}

/**
 * Audio-specific settings.
 *
 * @property enableMicrophone Whether to capture microphone audio.
 * @property enableSystemAudio Whether to capture system/playback audio.
 * @property bitrate Audio bitrate in bits per second.
 * @property sampleRate Audio sample rate in Hz.
 * @property stereo Whether to encode in stereo.
 */
data class AudioSettings(
    val enableMicrophone: Boolean = true,
    val enableSystemAudio: Boolean = true,
    val bitrate: Int = 128_000,
    val sampleRate: Int = 44_100,
    val stereo: Boolean = false,
)

/**
 * Supported resolution presets.
 *
 * Each preset maps to a concrete width×height pair.
 */
enum class Resolution(val width: Int, val height: Int, val label: String) {
    LOW_360(640, 360, "360p"),
    SD_480(854, 480, "480p"),
    QHD_540(960, 540, "540p"),
    HD_720(1280, 720, "720p"),
    FHD_1080(1920, 1080, "1080p"),
    QHD_1440(2560, 1440, "1440p"),
    UHD_2160(3840, 2160, "2160p (4K)"),
}
