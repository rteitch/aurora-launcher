package app.aurora.launcher.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.aurora.launcher.core.model.LauncherSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.auroraPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aurora_launcher_settings",
)

@Singleton
class LauncherSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : LauncherSettingsRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val settings: Flow<LauncherSettings> = context.auroraPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val raw = prefs[SETTINGS_JSON_KEY]
            if (raw.isNullOrBlank()) {
                LauncherSettings()
            } else {
                runCatching { json.decodeFromString<LauncherSettings>(raw) }
                    .getOrDefault(LauncherSettings())
            }
        }

    override suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        context.auroraPreferencesDataStore.edit { prefs ->
            val current = prefs[SETTINGS_JSON_KEY]?.let {
                runCatching { json.decodeFromString<LauncherSettings>(it) }.getOrNull()
            } ?: LauncherSettings()
            val updated = transform(current)
            prefs[SETTINGS_JSON_KEY] = json.encodeToString(updated)
        }
    }

    override suspend fun resetToDefaults() {
        context.auroraPreferencesDataStore.edit { prefs ->
            prefs.clear()
        }
    }

    companion object {
        private val SETTINGS_JSON_KEY = stringPreferencesKey("launcher_settings_v1")
    }
}
