package com.ogh.app.data

import android.content.Context
import android.util.Log
import com.ogh.app.oauth.AndroidKeystoreTokenStorage
import com.ogh.shared.domain.Destination
import com.ogh.shared.domain.DestinationType
import org.json.JSONArray
import org.json.JSONObject

/** Persists manual destinations as one Android Keystore-encrypted document. */
class SecureDestinationStorage(context: Context) {

    private val storage = runCatching { AndroidKeystoreTokenStorage(context) }
        .onFailure { Log.e(TAG, "Secure destination storage is unavailable", it) }
        .getOrNull()

    fun load(): List<Destination> = runCatching {
        storage?.read(STORAGE_KEY)?.let(DestinationJsonCodec::decode).orEmpty()
    }.onFailure {
        Log.e(TAG, "Stored destinations could not be decrypted", it)
        storage?.remove(STORAGE_KEY)
    }.getOrDefault(emptyList())

    fun save(destinations: List<Destination>) {
        val manual = destinations.filter { it.type == DestinationType.RTMP_MANUAL }
        runCatching { storage?.write(STORAGE_KEY, DestinationJsonCodec.encode(manual)) }
            .onFailure { Log.e(TAG, "Destinations could not be stored securely", it) }
    }

    private companion object {
        const val TAG = "DestinationStorage"
        const val STORAGE_KEY = "manual_destinations_v1"
    }
}

internal object DestinationJsonCodec {
    fun encode(destinations: List<Destination>): String = JSONArray().apply {
        destinations.forEach { destination ->
            put(JSONObject().apply {
                put("id", destination.id)
                put("name", destination.name)
                put("rtmpUrl", destination.rtmpUrl)
                put("streamKey", destination.streamKey)
                put("enabled", destination.enabled)
                put("colorHex", destination.colorHex)
            })
        }
    }.toString()

    fun decode(value: String): List<Destination> {
        val array = JSONArray(value)
        return buildList(array.length()) {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                add(
                    Destination(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        rtmpUrl = item.getString("rtmpUrl"),
                        streamKey = item.getString("streamKey"),
                        enabled = item.optBoolean("enabled", true),
                        colorHex = item.optString("colorHex", Destination.DEFAULT_COLOR_HEX),
                    ),
                )
            }
        }
    }
}
