package com.ogh.shared.domain

/**
 * Represents an RTMP streaming destination.
 *
 * Each destination contains the connection details for a single
 * RTMP endpoint. Users can configure unlimited destinations and
 * enable/disable them independently.
 *
 * @property id Unique identifier for this destination.
 * @property name Human-readable label (e.g. "YouTube", "Twitch").
 * @property rtmpUrl The RTMP server URL (e.g. "rtmp://a.rtmp.youtube.com/live2").
 * @property streamKey The secret stream key provided by the platform.
 * @property enabled Whether this destination participates in streaming sessions.
 * @property colorHex Optional hex color for UI identification (e.g. "#FF0000").
 * @property type Whether connection details are manual or provider-managed.
 */
data class Destination(
    val id: String,
    val name: String,
    val rtmpUrl: String,
    val streamKey: String,
    val enabled: Boolean = true,
    val colorHex: String = DEFAULT_COLOR_HEX,
    val type: DestinationType = DestinationType.RTMP_MANUAL,
) {
    companion object {
        const val DEFAULT_COLOR_HEX = "#D946EF"
    }

    /**
     * Returns the full RTMP endpoint by combining URL and stream key.
     *
     * Format: "rtmp://server/app/streamKey"
     */
    val fullEndpoint: String
        get() = if (rtmpUrl.endsWith("/")) {
            "$rtmpUrl$streamKey"
        } else {
            "$rtmpUrl/$streamKey"
        }
}

/** Identifies how a destination obtains its RTMP ingestion credentials. */
enum class DestinationType {
    /** User-managed RTMP URL and stream key. */
    RTMP_MANUAL,

    /** YouTube destination managed through the connected Google account. */
    YOUTUBE_OAUTH,

    /** Twitch destination managed through the connected Twitch account. */
    TWITCH_OAUTH,
}
