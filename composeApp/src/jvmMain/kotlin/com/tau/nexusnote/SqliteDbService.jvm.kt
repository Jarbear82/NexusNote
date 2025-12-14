package com.tau.nexusnote

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.tau.nexusnote.db.AppDatabase
import com.tau.nexusnote.db.connectionPairAdapter
import com.tau.nexusnote.db.schemaPropertyAdapter
import com.tau.nexusnote.db.stringMapAdapter
import com.tau.nexusnote.db.longListAdapter
import com.tau.nexusnote.db.booleanLongAdapter
import com.tau.nexusnote.db.Edge
import com.tau.nexusnote.db.Node
import com.tau.nexusnote.db.LayoutConstraint
import com.tau.nexusnote.db.SchemaDefinition
import java.io.File

actual class SqliteDbService actual constructor() {
    private var driver: SqlDriver? = null

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
        driver = JdbcSqliteDriver("jdbc:sqlite:$path")

        if (!dbExists) { // Only create the schema if the database is new
            AppDatabase.Schema.create(driver!!)
        }

        // Assign to the private backing field, providing all necessary adapters
        _database = AppDatabase(
            driver = driver!!,
            SchemaDefinitionAdapter = SchemaDefinition.Adapter(
                properties_jsonAdapter = schemaPropertyAdapter,
                connections_jsonAdapter = connectionPairAdapter
            ),
            NodeAdapter = Node.Adapter(
                properties_jsonAdapter = stringMapAdapter
                // is_collapsedAdapter is handled automatically by SQLDelight for INTEGER AS Boolean
            ),
            EdgeAdapter = Edge.Adapter(
                properties_jsonAdapter = stringMapAdapter
            ),
            LayoutConstraintAdapter = LayoutConstraint.Adapter(
                node_ids_jsonAdapter = longListAdapter,
                params_jsonAdapter = stringMapAdapter
            )
        )
    }

    actual fun close() {
        driver?.close()
    }
}