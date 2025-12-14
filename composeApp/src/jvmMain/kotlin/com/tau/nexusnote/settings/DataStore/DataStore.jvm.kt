package com.tau.nexusnote.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.tau.nexusnote.utils.getHomeDirectoryPath
import java.io.File

// JVM implementation that saves the settings file in the user's home directory
actual fun createDataStore(): DataStore<Preferences> {
    return PreferenceDataStoreFactory.create {
        // --- THIS IS THE FIX ---
        // Just return the file, don't use 'produceFile'
        File(getHomeDirectoryPath(), "nexusnote_settings.preferences_pb")
        // --- END FIX ---
    }
}