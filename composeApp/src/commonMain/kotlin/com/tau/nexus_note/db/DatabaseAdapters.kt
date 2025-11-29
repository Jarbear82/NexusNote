package com.tau.nexus_note.db

import app.cash.sqldelight.ColumnAdapter
import com.tau.nexus_note.datamodels.ConnectionPair
import com.tau.nexus_note.datamodels.SchemaProperty
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Reusable Json instance for database serialization.
 */
val dbJson = Json {
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
 * Adapter for `Node.properties_json` and `Edge.properties_json`
 * Converts Map<String, String> to/from a JSON String.
 * (Retained for legacy/migration if needed, but primary use is now jsonContentAdapter)
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
 * NEW: Polymorphic Content Adapter.
 * Stores arbitrary JSON (Primitive, Array, or Object) in the database.
 */
val jsonContentAdapter = object : ColumnAdapter<JsonElement, String> {
    override fun decode(databaseValue: String): JsonElement {
        return dbJson.parseToJsonElement(databaseValue)
    }

    override fun encode(value: JsonElement): String {
        return dbJson.encodeToString(JsonElement.serializer(), value)
    }
}