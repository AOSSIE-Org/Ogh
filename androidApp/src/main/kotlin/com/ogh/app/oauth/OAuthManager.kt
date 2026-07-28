package com.ogh.app.oauth

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.util.Log
import com.ogh.shared.domain.ProviderAccount
import com.ogh.shared.domain.StreamingProvider
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.CodeVerifierUtil
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest

/**
 * Manages the AppAuth OAuth flow for connecting streaming provider accounts.
 *
 * Uses Custom Tabs (system browser) — no Google Play Services required.
 * Supports PKCE for secure public client authentication.
 */
class OAuthManager(
    private val context: Context,
    private val tokenManager: TokenManager,
) {
    companion object {
        private const val TAG = "OAuthManager"
    }

    private val authService = AuthorizationService(context)

    /**
     * Creates an authorization intent for the specified provider.
     * Returns null if no client ID is configured for this provider.
     */
    fun createAuthIntent(provider: StreamingProvider): Intent? {
        require(provider == StreamingProvider.YOUTUBE) {
            "Twitch authentication uses TwitchDeviceAuthClient"
        }
        val clientId = OAuthConfig.getClientId(provider, context) ?: run {
            Log.w(TAG, "No client ID configured for ${provider.displayName}")
            return null
        }

        val config = OAuthConfig.getServiceConfig(provider)
        val scopes = OAuthConfig.getScopes(provider)

        val request = AuthorizationRequest.Builder(
            config,
            clientId,
            ResponseTypeValues.CODE,
            OAuthConfig.REDIRECT_URI.toUri(),
        )
            .setScopes(scopes)
            .setCodeVerifier(CodeVerifierUtil.generateRandomCodeVerifier())
            .setAdditionalParameters(mapOf("access_type" to "offline"))
            .build()

        Log.i(TAG, "Creating auth intent for ${provider.displayName}")
        return authService.getAuthorizationRequestIntent(request)
    }

    /**
     * Handles the OAuth redirect response after user authenticates.
     */
    fun handleAuthResponse(
        intent: Intent,
        provider: StreamingProvider,
        onSuccess: (ProviderAccount) -> Unit,
        onError: (String) -> Unit,
    ) {
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)

        if (exception != null) {
            Log.w(TAG, "Authorization was not completed for ${provider.displayName}")
            onError("Authentication was cancelled or denied")
            return
        }

        if (response == null) {
            onError("No authorization response received")
            return
        }

        Log.i(TAG, "Authorization code received, exchanging for tokens...")

        authService.performTokenRequest(
            response.createTokenExchangeRequest(),
        ) { tokenResponse, tokenException ->
            if (tokenException != null) {
                Log.w(TAG, "Token exchange failed for ${provider.displayName}")
                onError("Secure token exchange failed")
                return@performTokenRequest
            }

            val accessToken = tokenResponse?.accessToken
            if (accessToken.isNullOrBlank()) {
                onError("Empty token response")
                return@performTokenRequest
            }

            val account = ProviderAccount(
                provider = provider,
                accessToken = accessToken,
                refreshToken = tokenResponse.refreshToken,
                expiresAtMs = tokenResponse.accessTokenExpirationTime ?: 0,
            )

            if (!tokenManager.saveAccount(account)) {
                onError("Secure token storage is unavailable")
                return@performTokenRequest
            }
            Log.i(TAG, "Token exchange successful for ${provider.displayName}")
            onSuccess(account)
        }
    }

    /**
     * Refreshes an expired access token.
     */
    fun refreshToken(
        provider: StreamingProvider,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        require(provider == StreamingProvider.YOUTUBE) {
            "Twitch refresh tokens are managed by TwitchDeviceAuthClient"
        }
        val account = tokenManager.getAccount(provider) ?: run {
            onError("No account found for ${provider.displayName}")
            return
        }

        val refreshToken = account.refreshToken ?: run {
            onError("No refresh token available")
            return
        }

        val clientId = OAuthConfig.getClientId(provider, context) ?: run {
            onError("No client ID configured")
            return
        }

        val config = OAuthConfig.getServiceConfig(provider)
        val request = TokenRequest.Builder(config, clientId)
            .setGrantType(GrantTypeValues.REFRESH_TOKEN)
            .setRefreshToken(refreshToken)
            .build()

        authService.performTokenRequest(request) { tokenResponse, exception ->
            if (exception != null) {
                Log.w(TAG, "Token refresh failed for ${provider.displayName}")
                onError("Authorization expired; reconnect ${provider.displayName}")
                return@performTokenRequest
            }

            if (tokenResponse?.accessToken != null) {
                tokenManager.updateAccessToken(
                    provider,
                    tokenResponse.accessToken!!,
                    tokenResponse.accessTokenExpirationTime ?: 0,
                )
                onSuccess(tokenResponse.accessToken!!)
            } else {
                onError("Empty token refresh response")
            }
        }
    }

    fun disconnect(provider: StreamingProvider) {
        tokenManager.removeAccount(provider)
        Log.i(TAG, "Disconnected ${provider.displayName}")
    }

    fun dispose() {
        authService.dispose()
    }
}
