package com.ogh.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ogh.shared.domain.AppSettings
import com.ogh.shared.domain.AudioSettings
import com.ogh.shared.domain.StreamMetadata
import com.ogh.shared.domain.VideoSettings
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

/** Durable owner for the user's non-secret application settings. */
interface AppSettingsStore {
    suspend fun load(): AppSettings
    suspend fun save(settings: AppSettings)
}

/** Stores the complete immutable settings snapshot transactionally in DataStore. */
class SettingsRepository(context: Context) : AppSettingsStore {
    private val dataStore = context.applicationContext.settingsDataStore

    override suspend fun load(): AppSettings = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            preferences[SETTINGS_JSON]?.let(SettingsJsonCodec::decode) ?: AppSettings()
        }
        .first()

    override suspend fun save(settings: AppSettings) {
        dataStore.edit { preferences ->
            preferences[SETTINGS_JSON] = SettingsJsonCodec.encode(settings)
        }
    }

    private companion object {
        val SETTINGS_JSON: Preferences.Key<String> = stringPreferencesKey("settings_json_v1")
    }
}

/** Versioned codec kept separate from DataStore so migrations remain testable. */
internal object SettingsJsonCodec {
    fun encode(settings: AppSettings): String = JSONObject().apply {
        put("schemaVersion", 1)
        put("video", JSONObject().apply {
            put("source", settings.videoSettings.source.name)
            put("resolution", settings.videoSettings.resolution.name)
            put("fps", settings.videoSettings.fps)
            put("bitrate", settings.videoSettings.bitrate)
            put("lockOrientation", settings.videoSettings.lockOrientation)
            put("keepScreenAwake", settings.videoSettings.keepScreenAwake)
            put("pauseImageUri", settings.videoSettings.pauseImageUri)
        })
        put("audio", JSONObject().apply {
            put("enableMicrophone", settings.audioSettings.enableMicrophone)
            put("enableSystemAudio", settings.audioSettings.enableSystemAudio)
            put("bitrate", settings.audioSettings.bitrate)
            put("sampleRate", settings.audioSettings.sampleRate)
            put("stereo", settings.audioSettings.stereo)
        })
        put("metadata", JSONObject().apply {
            put("title", settings.streamMetadata.title)
            put("description", settings.streamMetadata.description)
            put("privacyStatus", settings.streamMetadata.privacyStatus.name)
            put("latencyMode", settings.streamMetadata.latencyMode.name)
        })
    }.toString()

    fun decode(encoded: String): AppSettings = runCatching {
        val json = JSONObject(encoded)
        val defaults = AppSettings()
        val video = json.optJSONObject("video")
        val audio = json.optJSONObject("audio")
        val metadata = json.optJSONObject("metadata")

        AppSettings(
            videoSettings = VideoSettings(
                source = enumValue(video?.optString("source"), defaults.videoSettings.source),
                resolution = enumValue(
                    video?.optString("resolution"),
                    defaults.videoSettings.resolution,
                ),
                fps = video?.optInt("fps", defaults.videoSettings.fps)
                    ?: defaults.videoSettings.fps,
                bitrate = video?.optInt("bitrate", defaults.videoSettings.bitrate)
                    ?: defaults.videoSettings.bitrate,
                lockOrientation = video?.optBoolean(
                    "lockOrientation",
                    defaults.videoSettings.lockOrientation,
                ) ?: defaults.videoSettings.lockOrientation,
                keepScreenAwake = video?.optBoolean(
                    "keepScreenAwake",
                    defaults.videoSettings.keepScreenAwake,
                ) ?: defaults.videoSettings.keepScreenAwake,
                pauseImageUri = video?.nullableString("pauseImageUri"),
            ),
            audioSettings = AudioSettings(
                enableMicrophone = audio?.optBoolean(
                    "enableMicrophone",
                    defaults.audioSettings.enableMicrophone,
                ) ?: defaults.audioSettings.enableMicrophone,
                enableSystemAudio = audio?.optBoolean(
                    "enableSystemAudio",
                    defaults.audioSettings.enableSystemAudio,
                ) ?: defaults.audioSettings.enableSystemAudio,
                bitrate = audio?.optInt("bitrate", defaults.audioSettings.bitrate)
                    ?: defaults.audioSettings.bitrate,
                sampleRate = audio?.optInt("sampleRate", defaults.audioSettings.sampleRate)
                    ?: defaults.audioSettings.sampleRate,
                stereo = audio?.optBoolean("stereo", defaults.audioSettings.stereo)
                    ?: defaults.audioSettings.stereo,
            ),
            streamMetadata = StreamMetadata(
                title = metadata?.optString("title", defaults.streamMetadata.title)
                    ?: defaults.streamMetadata.title,
                description = metadata?.optString(
                    "description",
                    defaults.streamMetadata.description,
                ) ?: defaults.streamMetadata.description,
                privacyStatus = enumValue(
                    metadata?.optString("privacyStatus"),
                    defaults.streamMetadata.privacyStatus,
                ),
                latencyMode = enumValue(
                    metadata?.optString("latencyMode"),
                    defaults.streamMetadata.latencyMode,
                ),
            ),
        )
    }.getOrDefault(AppSettings())

    private inline fun <reified T : Enum<T>> enumValue(value: String?, default: T): T =
        value?.let { candidate -> enumValues<T>().firstOrNull { it.name == candidate } } ?: default

    private fun JSONObject.nullableString(key: String): String? =
        if (has(key) && !isNull(key)) getString(key).takeIf(String::isNotBlank) else null
}
