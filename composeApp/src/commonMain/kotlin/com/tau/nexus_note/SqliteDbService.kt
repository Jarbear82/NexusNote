package com.tau.nexus_note

import app.cash.sqldelight.db.SqlDriver
import com.tau.nexus_note.db.AppDatabase

expect class SqliteDbService() {
    val database: AppDatabase
    val driver: SqlDriver
    val filePath: String

    // The absolute path to the directory where media assets are stored.
    // For file-based DBs, this is adjacent to the DB file.
    // For memory-based DBs, this is a temp directory.
    val mediaDirectoryPath: String

    fun initialize(path: String)
    fun close()
}