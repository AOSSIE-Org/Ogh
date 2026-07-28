package com.ogh.app.oauth

import com.ogh.shared.domain.ProviderAccount
import com.ogh.shared.domain.StreamingProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TwitchDeviceAuthClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: TwitchDeviceAuthClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("/").toString().removeSuffix("/")
        client = TwitchDeviceAuthClient(
            nowMs = { 1_000L },
            identityBaseUrl = baseUrl,
            helixBaseUrl = baseUrl,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun getStreamEndpoint_usesAuthenticatedBroadcasterAndNeverReturnsKeySeparately() {
        server.enqueue(
            MockResponse().setBody("""{"data":[{"stream_key":"live_private_key"}]}"""),
        )
        val account = ProviderAccount(
            provider = StreamingProvider.TWITCH,
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAtMs = Long.MAX_VALUE,
            channelId = "1234",
        )

        val endpoint = client.getStreamEndpoint("client-id", account)

        assertEquals("rtmp://live.twitch.tv/app/live_private_key", endpoint)
        val request = server.takeRequest()
        assertEquals("/streams/key?broadcaster_id=1234", request.path)
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
        assertEquals("client-id", request.getHeader("Client-Id"))
    }

    @Test
    fun deviceFlow_returnsConnectedAccount() = runBlocking {
        server.enqueue(jsonResponse(DEVICE_RESPONSE))
        server.enqueue(jsonResponse(TOKEN_RESPONSE))
        server.enqueue(jsonResponse(USER_RESPONSE))

        val authorization = client.requestDeviceAuthorization("public-client")
        val account = client.awaitAccount("public-client", authorization)

        assertEquals("Streamer", account.userName)
        assertEquals("42", account.channelId)
        assertEquals("access-token", account.accessToken)
        assertEquals("refresh-token", account.refreshToken)
        assertEquals(3_601_000L, account.expiresAtMs)
        assertEquals(
            "https://www.twitch.tv/activate?public=true&device-code=ABCDEFGH",
            authorization.verificationUri,
        )
        assertEquals(
            "client_id=public-client&scopes=channel%3Aread%3Astream_key",
            server.takeRequest().body.readUtf8(),
        )
    }

    @Test
    fun refreshAccount_rotatesPublicClientTokensWithoutASecret() {
        server.enqueue(
            jsonResponse(
                TOKEN_RESPONSE.replace("access-token", "new-access")
                    .replace("refresh-token", "new-refresh"),
            ),
        )

        val refreshed = client.refreshAccount("public-client", twitchAccount())

        assertEquals("new-access", refreshed.accessToken)
        assertEquals("new-refresh", refreshed.refreshToken)
        assertEquals(3_601_000L, refreshed.expiresAtMs)
        val body = server.takeRequest().body.readUtf8()
        assertEquals(
            "client_id=public-client&grant_type=refresh_token&refresh_token=refresh-token",
            body,
        )
        assertFalse("client_secret" in body)
    }

    @Test
    fun revoke_usesClientIdAndTokenWithoutASecret() {
        server.enqueue(MockResponse().setResponseCode(200))

        client.revoke("public-client", "access-token")

        val request = server.takeRequest()
        assertEquals("/revoke", request.path)
        val body = request.body.readUtf8()
        assertEquals("client_id=public-client&token=access-token", body)
        assertFalse("client_secret" in body)
    }

    private fun twitchAccount() = ProviderAccount(
        provider = StreamingProvider.TWITCH,
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAtMs = 0,
        channelId = "42",
    )

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private companion object {
        const val DEVICE_RESPONSE = """
            {
              "device_code": "device-code",
              "expires_in": 1800,
              "interval": 0,
              "user_code": "ABCDEFGH",
              "verification_uri": "https://www.twitch.tv/activate?public=true&device-code=ABCDEFGH"
            }
        """
        const val TOKEN_RESPONSE = """
            {
              "access_token": "access-token",
              "expires_in": 3600,
              "refresh_token": "refresh-token",
              "token_type": "bearer"
            }
        """
        const val USER_RESPONSE = """
            {"data":[{"id":"42","display_name":"Streamer"}]}
        """
    }
}
