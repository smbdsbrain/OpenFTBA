package io.openftba.data

import android.content.Context
import io.openftba.settings.AppSettings
import kotlinx.serialization.json.Json

/** Persists AppSettings as JSON in SharedPreferences. */
class AndroidSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("openftba", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): AppSettings = runCatching {
        prefs.getString("settings", null)?.let { json.decodeFromString(AppSettings.serializer(), it) }
    }.getOrNull() ?: AppSettings()

    fun save(settings: AppSettings) {
        runCatching {
            prefs.edit().putString("settings", json.encodeToString(AppSettings.serializer(), settings)).apply()
        }
    }
}
