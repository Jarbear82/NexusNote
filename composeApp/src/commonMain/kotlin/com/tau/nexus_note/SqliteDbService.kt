package com.tau.nexus_note

import app.cash.sqldelight.db.SqlDriver
import com.tau.nexus_note.db.AppDatabase
expect class SqliteDbService() {
    val database: AppDatabase
    val driver: SqlDriver

    /**
     * Initializes the .sqlite file at the given path.
     * This will also create the companion .media directory.
     */
    fun initialize(path: String)
    fun close()
}