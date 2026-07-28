package com.ogh.shared.domain

/**
 * Represents a streaming platform that supports OAuth integration.
 */
enum class StreamingProvider(
    val displayName: String,
    val colorHex: String,
) {
    YOUTUBE("YouTube", "#FF0000"),
    TWITCH("Twitch", "#9146FF"),
    ;
}

/**
 * OAuth token pair for a connected provider account.
 */
data class ProviderAccount(
    val provider: StreamingProvider,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMs: Long,
    val userName: String = "",
    val channelId: String = "",
)

/**
 * Global stream metadata, applied to all connected platforms when going live.
 * Persisted in AppSettings until the user changes it.
 */
data class StreamMetadata(
    val title: String = "Ogh Live",
    val description: String = "",
    val privacyStatus: PrivacyStatus = PrivacyStatus.PRIVATE,
    val latencyMode: LatencyMode = LatencyMode.NORMAL,
)

/**
 * Stream privacy status for platforms that support it.
 */
enum class PrivacyStatus(val displayName: String) {
    PUBLIC("Public"),
    UNLISTED("Unlisted"),
    PRIVATE("Private"),
}

/**
 * Stream latency mode for platforms that support it (e.g., YouTube).
 */
enum class LatencyMode(val displayName: String, val apiValue: String) {
    ULTRA_LOW("Ultra Low", "ultraLow"),
    LOW("Low", "low"),
    NORMAL("Normal", "normal"),
}
