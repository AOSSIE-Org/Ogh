package org.aossie.ogh.oauth

import com.ogh.shared.domain.StreamMetadata
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class YouTubeApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: YouTubeApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = YouTubeApiClient(baseUrl = server.url("/youtube/v3").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun getCurrentChannel_returnsAuthorizedChannelWithoutTokenInUrl() {
        server.enqueue(jsonResponse(CHANNEL_RESPONSE))

        val channel = client.getCurrentChannel("private-access-token")

        assertEquals("channel-42", channel.id)
        assertEquals("Ogh Tester", channel.title)
        val request = server.takeRequest()
        assertEquals("/youtube/v3/channels?part=id,snippet&mine=true", request.path)
        assertEquals("Bearer private-access-token", request.getHeader("Authorization"))
        assertFalse(request.path.orEmpty().contains("private-access-token"))
    }

    @Test
    fun createAndBindBroadcast_usesBearerHeaderForEveryRequest() {
        server.enqueue(jsonResponse("""{"id":"broadcast-1"}"""))
        server.enqueue(jsonResponse(STREAM_RESPONSE))
        server.enqueue(jsonResponse("""{"id":"broadcast-1"}"""))

        val result = client.createAndBindBroadcast("private-access-token", StreamMetadata())

        assertEquals("rtmp://example.test/live/stream-key", result.fullEndpoint)
        repeat(3) {
            val request = server.takeRequest()
            assertEquals("Bearer private-access-token", request.getHeader("Authorization"))
            assertFalse(request.path.orEmpty().contains("private-access-token"))
        }
    }

    @Test
    fun apiError_doesNotExposeResponseBody() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("private provider details"))

        val error = assertFailsWith<YouTubeApiException> {
            client.getCurrentChannel("private-access-token")
        }

        assertEquals("YouTube request failed (HTTP 403)", error.message)
    }

    @Test
    fun createAndBindBroadcast_explainsDisabledLiveStreaming() {
        server.enqueue(errorResponse(403, "liveStreamingNotEnabled"))

        val error = assertFailsWith<YouTubeApiException> {
            client.createAndBindBroadcast("private-access-token", StreamMetadata())
        }

        assertEquals(
            "Live streaming isn't enabled on this YouTube channel. Enable it in YouTube Studio; " +
                "activation can take up to 24 hours.",
            error.message,
        )
        assertEquals(1, server.requestCount)
    }

    @Test
    fun apiError_mapsQuotaReasonWithoutProviderMessage() {
        server.enqueue(errorResponse(403, "quotaExceeded"))

        val error = assertFailsWith<YouTubeApiException> {
            client.getCurrentChannel("private-access-token")
        }

        assertEquals(
            "Ogh's daily YouTube API limit has been reached. Try again after it resets at midnight Pacific Time.",
            error.message,
        )
        assertFalse(error.message.orEmpty().contains(PROVIDER_MESSAGE))
    }

    @Test
    fun apiError_appendsOnlyUnknownReasonIdentifier() {
        server.enqueue(errorResponse(400, "invalidScheduledStartTime"))

        val error = assertFailsWith<YouTubeApiException> {
            client.getCurrentChannel("private-access-token")
        }

        assertEquals("YouTube request failed (HTTP 400: invalidScheduledStartTime)", error.message)
    }

    @Test
    fun apiError_ignoresMalformedReason() {
        server.enqueue(errorResponse(403, "Channel Ogh Tester <channel-42>"))

        val error = assertFailsWith<YouTubeApiException> {
            client.getCurrentChannel("private-access-token")
        }

        assertEquals("YouTube request failed (HTTP 403)", error.message)
    }

    @Test
    fun unauthorizedWithoutReason_asksUserToReconnect() {
        server.enqueue(MockResponse().setResponseCode(401))

        val error = assertFailsWith<YouTubeApiException> {
            client.getCurrentChannel("private-access-token")
        }

        assertEquals(
            "YouTube sign-in has expired or was revoked. Disconnect and reconnect YouTube.",
            error.message,
        )
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun errorResponse(code: Int, reason: String): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(
            JSONObject().put(
                "error",
                JSONObject()
                    .put("code", code)
                    .put("message", PROVIDER_MESSAGE)
                    .put(
                        "errors",
                        JSONArray().put(
                            JSONObject()
                                .put("message", PROVIDER_MESSAGE)
                                .put("domain", "youtube.liveBroadcast")
                                .put("reason", reason),
                        ),
                    ),
            ).toString(),
        )

    private companion object {
        const val PROVIDER_MESSAGE = "Channel Ogh Tester cannot do this"
        const val CHANNEL_RESPONSE =
            """{"items":[{"id":"channel-42","snippet":{"title":"Ogh Tester"}}]}"""
        const val STREAM_RESPONSE = """
            {
              "id": "stream-1",
              "cdn": {
                "ingestionInfo": {
                  "ingestionAddress": "rtmp://example.test/live",
                  "streamName": "stream-key"
                }
              }
            }
        """
    }
}
