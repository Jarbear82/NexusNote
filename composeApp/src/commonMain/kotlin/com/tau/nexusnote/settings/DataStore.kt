package com.tau.nexusnote.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

// This expect fun will be implemented by each platform
expect fun createDataStore(): DataStore<Preferences>