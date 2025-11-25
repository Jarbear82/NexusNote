package com.tau.nexus_note

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.tau.nexus_note.db.AppDatabase
import com.tau.nexus_note.db.connectionPairAdapter
import com.tau.nexus_note.db.schemaPropertyAdapter
import com.tau.nexus_note.db.stringMapAdapter
import com.tau.nexusnote.db.Edge
import com.tau.nexusnote.db.Node
import com.tau.nexusnote.db.SchemaDefinition
import java.io.File

actual class SqliteDbService actual constructor() {

    private var _driver: SqlDriver? = null

    actual val driver: SqlDriver
        get() = _driver ?: throw IllegalStateException("Driver not initialized. Call initialize() first.")

    // Store the database in a private, nullable backing field
    private var _database: AppDatabase? = null

    // Implement the 'expect val' with a custom getter
    actual val database: AppDatabase
        get() = _database ?: throw IllegalStateException("Database not initialized. Call initialize() first.")

    actual fun initialize(path: String) {
        val isMemoryDb = path == ":memory:"
        val dbFile = File(path)
        val dbExists = if (isMemoryDb) false else dbFile.exists() // Check if the file exists

        if (!isMemoryDb) {
            val mediaDir = File(dbFile.parent, "${dbFile.nameWithoutExtension}.media")
            // Create media directory
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }
        }

        // Setup driver
        _driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        val driver = _driver!! // local var for use below

        if (!dbExists) { // Only create the schema if the database is new
            AppDatabase.Schema.create(driver!!)
        }

        // Assign to the private backing field, providing all necessary adapters
         _database = AppDatabase(
            driver = driver,
            SchemaDefinitionAdapter = SchemaDefinition.Adapter(
                properties_jsonAdapter = schemaPropertyAdapter,
                connections_jsonAdapter = connectionPairAdapter
            ),
            NodeAdapter = Node.Adapter(
                properties_jsonAdapter = stringMapAdapter
            ),
            EdgeAdapter = Edge.Adapter(
                properties_jsonAdapter = stringMapAdapter
            )
         )
    }

    actual fun close() {
        driver?.close()
    }
}