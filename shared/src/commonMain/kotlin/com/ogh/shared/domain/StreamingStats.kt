package com.ogh.shared.domain

/**
 * Real-time streaming statistics displayed on the live dashboard.
 *
 * @property elapsedSeconds Total seconds since stream started.
 * @property currentBitrate Current video bitrate in bits per second.
 */
data class StreamingStats(
    val elapsedSeconds: Long = 0,
    val currentBitrate: Long = 0,
) {
    /** Formatted elapsed time as HH:MM:SS. */
    val formattedTime: String
        get() {
            val hours = elapsedSeconds / 3600
            val minutes = (elapsedSeconds % 3600) / 60
            val seconds = elapsedSeconds % 60
            return if (hours > 0) {
                "${hours.pad()}:${minutes.pad()}:${seconds.pad()}"
            } else {
                "${minutes.pad()}:${seconds.pad()}"
            }
        }

    /** Formatted bitrate as human-readable string. */
    val formattedBitrate: String
        get() = when {
            currentBitrate >= 1_000_000 -> "${currentBitrate / 1_000_000.0}".take(4) + " Mbps"
            currentBitrate >= 1_000 -> "${currentBitrate / 1_000} Kbps"
            else -> "$currentBitrate bps"
        }

    private fun Long.pad(): String = this.toString().padStart(2, '0')
}
