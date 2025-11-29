package com.tau.nexus_note.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    // Use the same serializer you have for your DB
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    // A single key to store the entire SettingsData object as a JSON string
    private val SETTINGS_KEY = stringPreferencesKey("app_settings")

    /**
     * A flow that emits the user's settings, or default settings if none saved.
     */
    val settings: Flow<SettingsData> = dataStore.data
        .catch { exception ->
            // Handle exceptions (e.g., IO)
            println("Error reading settings: ${exception.message}")
            emit(emptyPreferences())
        }
        .map { preferences ->
            val jsonString = preferences[SETTINGS_KEY]
            if (jsonString != null) {
                try {
                    // De-serialize the string back into our object
                    json.decodeFromString<SettingsData>(jsonString)
                } catch (e: Exception) {
                    println("Error decoding settings: ${e.message}")
                    SettingsData.Default // Corrupted data, fall back to default
                }
            } else {
                SettingsData.Default // No settings saved yet
            }
        }

    /**
     * Saves the entire SettingsData object to DataStore.
     */
    suspend fun saveSettings(settings: SettingsData) {
        dataStore.edit { preferences ->
            // Serialize the object into a JSON string
            val jsonString = json.encodeToString(SettingsData.serializer(), settings)
            preferences[SETTINGS_KEY] = jsonString
        }
    }
}