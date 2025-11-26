package com.tau.nexus_note

import app.cash.sqldelight.db.SqlDriver
import com.tau.nexus_note.db.AppDatabase

expect class SqliteDbService() {
    val database: AppDatabase
    val driver: SqlDriver
    // New property to expose the path
    val filePath: String

    fun initialize(path: String)
    fun close()
}