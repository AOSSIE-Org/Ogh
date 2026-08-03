package org.aossie.ogh.oauth

import android.content.Context
import androidx.core.net.toUri
import org.aossie.ogh.R
import com.ogh.shared.domain.StreamingProvider
import net.openid.appauth.AuthorizationServiceConfiguration

/**
 * OAuth configuration for each supported streaming provider.
 *
 * Authentication uses Custom Tabs / system browser — no Play Services.
 * Public client IDs live in the tracked string resources. OAuth credentials
 * and user tokens must never be stored there.
 */
object OAuthConfig {

    /** Redirect URI scheme — must match AndroidManifest.xml intent filter. */
    const val REDIRECT_URI = "org.aossie.ogh:/oauth2callback"

    fun getServiceConfig(provider: StreamingProvider): AuthorizationServiceConfiguration {
        return when (provider) {
            StreamingProvider.YOUTUBE -> AuthorizationServiceConfiguration(
                "https://accounts.google.com/o/oauth2/v2/auth".toUri(),
                "https://oauth2.googleapis.com/token".toUri(),
            )
            StreamingProvider.TWITCH -> error("Twitch uses Device Code Flow")
        }
    }

    fun getScopes(provider: StreamingProvider): List<String> {
        return when (provider) {
            StreamingProvider.YOUTUBE -> listOf(
                "https://www.googleapis.com/auth/youtube",
            )
            StreamingProvider.TWITCH -> error("Twitch scopes are owned by TwitchDeviceAuthClient")
        }
    }

    /**
     * Returns the client ID for a provider.
     * Blank resource values mean that the provider has not been configured.
     */
    fun getClientId(provider: StreamingProvider, context: Context): String? {
        val resourceId = when (provider) {
            StreamingProvider.YOUTUBE -> R.string.youtube_client_id
            StreamingProvider.TWITCH -> R.string.twitch_client_id
        }
        return context.getString(resourceId).trim().takeIf(String::isNotEmpty)
    }
}
