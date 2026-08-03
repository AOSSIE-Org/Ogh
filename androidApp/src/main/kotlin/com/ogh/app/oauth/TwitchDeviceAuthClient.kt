package org.aossie.ogh.oauth

import com.ogh.shared.domain.ProviderAccount
import com.ogh.shared.domain.StreamingProvider
import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Implements Twitch's Device Code Flow for native public clients. */
class TwitchDeviceAuthClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val identityBaseUrl: String = "https://id.twitch.tv/oauth2",
    private val helixBaseUrl: String = "https://api.twitch.tv/helix",
) {

    /** Resolves the authenticated broadcaster's private RTMP endpoint. */
    fun getStreamEndpoint(clientId: String, account: ProviderAccount): String {
        require(account.provider == StreamingProvider.TWITCH) { "A Twitch account is required" }
        require(account.channelId.isNotBlank()) { "Twitch channel ID is missing; reconnect the account" }

        val json = executeJson(
            Request.Builder()
                .url("$helixBaseUrl/streams/key?broadcaster_id=${account.channelId}")
                .header("Authorization", "Bearer ${account.accessToken}")
                .header("Client-Id", clientId)
                .get()
                .build(),
        )
        val keys = json.getJSONArray("data")
        if (keys.length() == 0) throw TwitchAuthException("Twitch did not return a stream key")
        return "rtmp://live.twitch.tv/app/${keys.getJSONObject(0).getString("stream_key")}"
    }

    /** Exchanges a public-client refresh token and returns the rotated token pair. */
    fun refreshAccount(clientId: String, account: ProviderAccount): ProviderAccount {
        require(account.provider == StreamingProvider.TWITCH) { "A Twitch account is required" }
        val refreshToken = account.refreshToken
            ?: throw TwitchAuthException("Reconnect Twitch to renew access")
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build()
        val result = execute(
            Request.Builder().url("$identityBaseUrl/token").post(body).build(),
        )
        if (result.code !in 200..299) {
            throw TwitchAuthException("Reconnect Twitch to renew access")
        }
        return account.copy(
            accessToken = result.json.getString("access_token"),
            refreshToken = result.json.getString("refresh_token"),
            expiresAtMs = nowMs() + result.json.getLong("expires_in") * 1_000,
        )
    }

    /** Revokes a Twitch access token before its local copy is removed. */
    fun revoke(clientId: String, accessToken: String) {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("token", accessToken)
            .build()
        val result = execute(
            Request.Builder().url("$identityBaseUrl/revoke").post(body).build(),
        )
        if (result.code !in 200..299) {
            throw TwitchAuthException("Could not revoke Twitch access")
        }
    }

    /** Starts device authorization and returns the code the user must approve. */
    fun requestDeviceAuthorization(clientId: String): TwitchDeviceAuthorization {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scopes", SCOPES.joinToString(" "))
            .build()
        val json = executeJson(
            Request.Builder()
                .url("$identityBaseUrl/device")
                .post(body)
                .build(),
        )
        return TwitchDeviceAuthorization(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUri = json.getString("verification_uri"),
            expiresInSeconds = json.getLong("expires_in"),
            intervalSeconds = json.optLong("interval", DEFAULT_POLL_INTERVAL_SECONDS),
        )
    }

    /** Polls until the user approves, then returns the connected Twitch account. */
    suspend fun awaitAccount(
        clientId: String,
        authorization: TwitchDeviceAuthorization,
    ): ProviderAccount {
        val deadline = nowMs() + authorization.expiresInSeconds * 1_000
        var currentInterval = authorization.intervalSeconds
        while (nowMs() < deadline) {
            delay(currentInterval * 1_000)
            val token = try {
                requestToken(clientId, authorization.deviceCode) ?: continue
            } catch (e: TwitchSlowDownException) {
                currentInterval += 5
                continue
            }
            val user = requestCurrentUser(clientId, token.accessToken)
            return ProviderAccount(
                provider = StreamingProvider.TWITCH,
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                expiresAtMs = nowMs() + token.expiresInSeconds * 1_000,
                userName = user.displayName,
                channelId = user.id,
            )
        }
        throw TwitchAuthException("Twitch authorization expired")
    }

    private fun requestToken(clientId: String, deviceCode: String): TwitchToken? {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scopes", SCOPES.joinToString(" "))
            .add("device_code", deviceCode)
            .add("grant_type", DEVICE_GRANT_TYPE)
            .build()
        val request = Request.Builder()
            .url("$identityBaseUrl/token")
            .post(body)
            .build()
        val result = execute(request)
        if (result.code == 400) {
            when (result.json.optString("message")) {
                "authorization_pending" -> return null
                "slow_down" -> throw TwitchSlowDownException()
                "access_denied" -> throw TwitchAuthException("Authorization was denied")
                "expired_token" -> throw TwitchAuthException("Device code expired")
            }
        }
        if (result.code !in 200..299) throw apiException(result)
        return TwitchToken(
            accessToken = result.json.getString("access_token"),
            refreshToken = result.json.getString("refresh_token"),
            expiresInSeconds = result.json.getLong("expires_in"),
        )
    }

    private fun requestCurrentUser(clientId: String, accessToken: String): TwitchUser {
        val json = executeJson(
            Request.Builder()
                .url("$helixBaseUrl/users")
                .header("Authorization", "Bearer $accessToken")
                .header("Client-Id", clientId)
                .get()
                .build(),
        )
        val users = json.getJSONArray("data")
        if (users.length() == 0) throw TwitchAuthException("Twitch account was not returned")
        val user = users.getJSONObject(0)
        return TwitchUser(
            id = user.getString("id"),
            displayName = user.getString("display_name"),
        )
    }

    private fun executeJson(request: Request): JSONObject {
        val result = execute(request)
        if (result.code !in 200..299) throw apiException(result)
        return result.json
    }

    private fun execute(request: Request): TwitchResponse =
        httpClient.newCall(request).execute().use { response ->
            val json = try {
                JSONObject(response.body.string().ifBlank { "{}" })
            } catch (e: org.json.JSONException) {
                JSONObject()
            }
            TwitchResponse(
                code = response.code,
                json = json,
            )
        }

    private fun apiException(response: TwitchResponse): TwitchAuthException {
        val message = response.json.optString("message").ifBlank { "HTTP ${response.code}" }
        return TwitchAuthException("Twitch authentication failed: $message")
    }

    private companion object {
        const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        const val DEFAULT_POLL_INTERVAL_SECONDS = 5L
        val SCOPES = listOf("channel:read:stream_key")
    }
}

/** Values returned when Twitch Device Code Flow starts. */
data class TwitchDeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
)

private data class TwitchToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

private data class TwitchUser(val id: String, val displayName: String)

private data class TwitchResponse(val code: Int, val json: JSONObject)

/** A classified Twitch authorization failure safe to display to the user. */
class TwitchAuthException(message: String) : Exception(message)

private class TwitchSlowDownException : Exception()
