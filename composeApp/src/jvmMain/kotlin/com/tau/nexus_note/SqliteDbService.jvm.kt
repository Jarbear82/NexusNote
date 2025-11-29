package com.tau.nexus_note

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.tau.nexus_note.db.AppDatabase
import com.tau.nexus_note.db.connectionPairAdapter
import com.tau.nexus_note.db.jsonContentAdapter
import com.tau.nexus_note.db.schemaPropertyAdapter
import com.tau.nexusnote.db.Edge
import com.tau.nexusnote.db.Node
import com.tau.nexusnote.db.SchemaDefinition
import java.io.File
import java.nio.file.Files

actual class SqliteDbService actual constructor() {

    private var _driver: SqlDriver? = null
    actual val driver: SqlDriver
        get() = _driver ?: throw IllegalStateException("Driver not initialized. Call initialize() first.")

    private var _database: AppDatabase? = null
    actual val database: AppDatabase
        get() = _database ?: throw IllegalStateException("Database not initialized. Call initialize() first.")

    private var _filePath: String = ""
    actual val filePath: String
        get() = _filePath

    private var _mediaDirectoryPath: String = ""
    actual val mediaDirectoryPath: String
        get() = _mediaDirectoryPath

    actual fun initialize(path: String) {
        _filePath = path

        // --- Media Directory Resolution ---
        if (path == ":memory:") {
            // Create a temporary directory for this session's media
            val tempDir = Files.createTempDirectory("nexus_note_session_").toFile()
            tempDir.deleteOnExit() // Attempt to clean up on JVM exit
            _mediaDirectoryPath = tempDir.absolutePath
        } else {
            val dbFile = File(path)
            val mediaDir = File(dbFile.parent, "${dbFile.nameWithoutExtension}.media")
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }
            _mediaDirectoryPath = mediaDir.absolutePath
        }

        // --- Driver Setup ---
        val dbExists = if (path == ":memory:") false else File(path).exists()
        _driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        val driver = _driver!!

        if (!dbExists) {
            AppDatabase.Schema.create(driver)
        }

        _database = AppDatabase(
            driver = driver,
            SchemaDefinitionAdapter = SchemaDefinition.Adapter(
                properties_jsonAdapter = schemaPropertyAdapter,
                connections_jsonAdapter = connectionPairAdapter
            ),
            NodeAdapter = Node.Adapter(
                properties_jsonAdapter = jsonContentAdapter
            ),
            EdgeAdapter = Edge.Adapter(
                properties_jsonAdapter = jsonContentAdapter
            )
        )
    }

    actual fun close() {
        driver.close()
        // Optional: If it was a temp directory, we could recursively delete it here.
        // For now, rely on deleteOnExit() or OS cleanup.
    }
}