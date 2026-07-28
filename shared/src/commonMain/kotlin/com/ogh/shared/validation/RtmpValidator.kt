package com.ogh.shared.validation

/**
 * Validates RTMP-related inputs.
 *
 * Centralizes all validation logic for stream configuration
 * so it can be reused across platforms and tested independently.
 */
object RtmpValidator {

    private val RTMP_URL_PATTERN = Regex(
        "^rtmps?://[a-zA-Z0-9._-]+(:[0-9]+)?(/[a-zA-Z0-9._/-]*)?\$"
    )

    /**
     * Validates an RTMP URL.
     *
     * Accepts both `rtmp://` and `rtmps://` schemes.
     * The URL must contain a valid hostname and optional path.
     *
     * @param url The RTMP URL to validate.
     * @return `true` if the URL is syntactically valid.
     */
    fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val match = RTMP_URL_PATTERN.matchEntire(url.trim()) ?: return false
        val portGroup = match.groups[1]?.value
        if (portGroup != null) {
            val port = portGroup.drop(1).toIntOrNull()
            if (port == null || port == 0 || port > 65535) return false
        }
        return true
    }

    /**
     * Validates a stream key.
     *
     * Stream keys must be non-blank and contain no whitespace.
     *
     * @param key The stream key to validate.
     * @return `true` if the key is valid.
     */
    fun isValidStreamKey(key: String): Boolean {
        if (key.isBlank()) return false
        return !key.any { it.isWhitespace() || it.isISOControl() }
    }

    /**
     * Validates a destination name.
     *
     * Names must be non-blank and at most 50 characters.
     *
     * @param name The destination name to validate.
     * @return `true` if the name is valid.
     */
    fun isValidName(name: String): Boolean {
        return name.isNotBlank() && name.trim().length <= 50
    }

    /**
     * Validates a complete destination configuration.
     *
     * @param url The RTMP URL.
     * @param key The stream key.
     * @param name The destination name.
     * @return null if valid, or an error message string.
     */
    fun validateDestination(url: String, key: String, name: String): String? {
        if (!isValidName(name)) return "Destination name is required (max 50 characters)."
        if (!isValidUrl(url)) return "Invalid RTMP URL. Must start with rtmp:// or rtmps://"
        if (!isValidStreamKey(key)) return "Invalid stream key. Must be non-empty with no spaces."
        return null
    }
}
