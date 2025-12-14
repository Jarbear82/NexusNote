package com.tau.nexusnote.db

import app.cash.sqldelight.ColumnAdapter
import com.tau.nexusnote.datamodels.ConnectionPair
import com.tau.nexusnote.datamodels.SchemaProperty
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Reusable Json instance for database serialization.
 */
private val dbJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
}

/**
 * Adapter for `SchemaDefinition.properties_json`
 * Converts List<SchemaProperty> to/from a JSON String.
 */
val schemaPropertyAdapter = object : ColumnAdapter<List<SchemaProperty>, String> {
    override fun decode(databaseValue: String): List<SchemaProperty> {
        return dbJson.decodeFromString(ListSerializer(SchemaProperty.serializer()), databaseValue)
    }
    override fun encode(value: List<SchemaProperty>): String {
        return dbJson.encodeToString(ListSerializer(SchemaProperty.serializer()), value)
    }
}

/**
 * Adapter for `SchemaDefinition.connections_json`
 * Converts List<ConnectionPair> to/from a NON-NULL JSON String.
 */
val connectionPairAdapter = object : ColumnAdapter<List<ConnectionPair>, String> {
    override fun decode(databaseValue: String): List<ConnectionPair> {
        // databaseValue will be "[]" for nodes, not null
        return dbJson.decodeFromString(ListSerializer(ConnectionPair.serializer()), databaseValue)
    }

    override fun encode(value: List<ConnectionPair>): String {
        // Must return a non-null string, handle the empty case
        // Encode empty list as "[]" string
        return dbJson.encodeToString(ListSerializer(ConnectionPair.serializer()), value)
    }
}


/**
 * Adapter for `Node.properties_json`, `Edge.properties_json`, and `LayoutConstraint.params_json`
 * Converts Map<String, String> to/from a JSON String.
 */
val stringMapAdapter = object : ColumnAdapter<Map<String, String>, String> {
    override fun decode(databaseValue: String): Map<String, String> {
        return dbJson.decodeFromString(MapSerializer(String.serializer(), String.serializer()), databaseValue)
    }
    override fun encode(value: Map<String, String>): String {
        return dbJson.encodeToString(MapSerializer(String.serializer(), String.serializer()), value)
    }
}

/**
 * Adapter for `LayoutConstraint.node_ids_json`
 * Converts List<Long> to/from a JSON String.
 */
val longListAdapter = object : ColumnAdapter<List<Long>, String> {
    override fun decode(databaseValue: String): List<Long> {
        return dbJson.decodeFromString(ListSerializer(Long.serializer()), databaseValue)
    }
    override fun encode(value: List<Long>): String {
        return dbJson.encodeToString(ListSerializer(Long.serializer()), value)
    }
}

/**
 * Adapter for SQLite Integers being treated as Booleans (0 = false, 1 = true).
 */
val booleanLongAdapter = object : ColumnAdapter<Boolean, Long> {
    override fun decode(databaseValue: Long): Boolean {
        return databaseValue == 1L
    }

    override fun encode(value: Boolean): Long {
        return if (value) 1L else 0L
    }
}