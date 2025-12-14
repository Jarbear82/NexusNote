package com.tau.nexusnote

import com.tau.nexusnote.db.AppDatabase
expect class SqliteDbService() {
    val database: AppDatabase

    /**
     * Initializes the .sqlite file at the given path.
     * This will also create the companion .media directory.
     */
    fun initialize(path: String)
    fun close()
}