package com.ogh.app.oauth

import com.ogh.shared.domain.LatencyMode
import com.ogh.shared.domain.PrivacyStatus
import com.ogh.shared.domain.StreamMetadata
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * YouTube Live Streaming API v3 client.
 *
 * Manages the full broadcast lifecycle:
 * 1. Create a liveBroadcast (with enableAutoStart + enableAutoStop)
 * 2. Create a liveStream (provides RTMP ingestion URL + stream key)
 * 3. Bind broadcast to stream
 *
 * With enableAutoStart=true, YouTube automatically transitions the
 * broadcast to "live" as soon as the RTMP encoder starts sending data.
 * The user never needs to visit YouTube Studio.
 */
class YouTubeApiClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = YOUTUBE_API_BASE_URL,
) {

    companion object {
        private const val YOUTUBE_API_BASE_URL = "https://www.googleapis.com/youtube/v3"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Creates a YouTube broadcast + stream, binds them, returns RTMP endpoint.
     *
     * @param accessToken Valid OAuth2 access token.
     * @param metadata Stream title, description, privacy, and latency settings.
     * @return [YouTubeBroadcastResult] with RTMP URL, stream key, and broadcast ID.
     */
    fun createAndBindBroadcast(
        accessToken: String,
        metadata: StreamMetadata,
    ): YouTubeBroadcastResult {
        val title = metadata.title.ifBlank { "Ogh Live" }

        // 1. Create broadcast
        val broadcastBody = JSONObject().apply {
            put("snippet", JSONObject().apply {
                put("title", title)
                put("description", metadata.description)
                put("scheduledStartTime", currentRfc3339Timestamp())
            })
            put("status", JSONObject().apply {
                put("privacyStatus", when (metadata.privacyStatus) {
                    PrivacyStatus.PUBLIC -> "public"
                    PrivacyStatus.UNLISTED -> "unlisted"
                    PrivacyStatus.PRIVATE -> "private"
                })
                put("selfDeclaredMadeForKids", false)
            })
            put("contentDetails", JSONObject().apply {
                put("enableAutoStart", true)
                put("enableAutoStop", true)
                put("latencyPreference", metadata.latencyMode.apiValue)
            })
        }

        val broadcastResponse = post(
            "$baseUrl/liveBroadcasts?part=snippet,status,contentDetails",
            broadcastBody.toString(),
            accessToken,
        )
        val broadcastId = broadcastResponse.getString("id")

        // 2. Create stream
        val streamBody = JSONObject().apply {
            put("snippet", JSONObject().apply {
                put("title", "$title - Stream")
            })
            put("cdn", JSONObject().apply {
                put("frameRate", "variable")
                put("ingestionType", "rtmp")
                put("resolution", "variable")
            })
        }

        val streamResponse = post(
            "$baseUrl/liveStreams?part=snippet,cdn",
            streamBody.toString(),
            accessToken,
        )
        val streamId = streamResponse.getString("id")
        val cdn = streamResponse.getJSONObject("cdn")
        val ingestionInfo = cdn.getJSONObject("ingestionInfo")
        val rtmpUrl = ingestionInfo.optString("rtmpsIngestionAddress").takeIf { it.isNotBlank() }
            ?: ingestionInfo.getString("ingestionAddress")
        val streamKey = ingestionInfo.getString("streamName")

        // 3. Bind broadcast to stream
        post(
            "$baseUrl/liveBroadcasts/bind?id=$broadcastId&part=id&streamId=$streamId",
            "",
            accessToken,
        )

        return YouTubeBroadcastResult(
            rtmpUrl = rtmpUrl,
            streamKey = streamKey,
        )
    }

    /** Returns the channel owned by the currently authorized Google account. */
    fun getCurrentChannel(accessToken: String): YouTubeChannel {
        val response = execute(
            Request.Builder()
                .url("$baseUrl/channels?part=id,snippet&mine=true")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .get()
                .build(),
        )
        val items = response.optJSONArray("items")
        if (items == null || items.length() == 0) {
            throw YouTubeApiException("No YouTube channel is available for this account")
        }
        val channel = items.getJSONObject(0)
        return YouTubeChannel(
            id = channel.getString("id"),
            title = channel.getJSONObject("snippet").getString("title"),
        )
    }

    private fun currentRfc3339Timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    private fun post(url: String, body: String, accessToken: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Accept", "application/json")
            .let { builder ->
                if (body.isNotEmpty()) {
                    builder.post(body.toRequestBody(JSON))
                } else {
                    builder.post("".toRequestBody(null))
                }
            }
            .build()

        return execute(request)
    }

    private fun execute(request: Request): JSONObject =
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                throw YouTubeApiException("YouTube request failed (HTTP ${response.code})")
            }
            runCatching { JSONObject(responseBody.ifEmpty { "{}" }) }
                .getOrElse { throw YouTubeApiException("YouTube returned an invalid response") }
        }
}

data class YouTubeBroadcastResult(
    val rtmpUrl: String,
    val streamKey: String,
) {
    val fullEndpoint: String
        get() = "$rtmpUrl/$streamKey"
}

data class YouTubeChannel(val id: String, val title: String)

class YouTubeApiException(message: String) : Exception(message)
