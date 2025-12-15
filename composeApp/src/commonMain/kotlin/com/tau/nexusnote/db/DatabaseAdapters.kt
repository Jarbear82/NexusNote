package com.tau.nexusnote.db

import app.cash.sqldelight.ColumnAdapter
import com.tau.nexusnote.datamodels.NodeContent
import com.tau.nexusnote.datamodels.RoleDefinition
import com.tau.nexusnote.datamodels.SchemaConfig
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
    classDiscriminator = "type" // For polymorphic NodeContent and SchemaConfig
}

/**
 * Adapter for `SchemaDefinition.config_json`
 * Converts SchemaConfig polymorphic hierarchy to/from a JSON String.
 */
val schemaConfigAdapter = object : ColumnAdapter<SchemaConfig, String> {
    override fun decode(databaseValue: String): SchemaConfig {
        return dbJson.decodeFromString(SchemaConfig.serializer(), databaseValue)
    }
    override fun encode(value: SchemaConfig): String {
        return dbJson.encodeToString(SchemaConfig.serializer(), value)
    }
}

/**
 * Adapter for `SchemaDefinition.roles_json`
 * Converts List<RoleDefinition> to/from a NON-NULL JSON String.
 */
val roleDefinitionListAdapter = object : ColumnAdapter<List<RoleDefinition>, String> {
    override fun decode(databaseValue: String): List<RoleDefinition> {
        return dbJson.decodeFromString(ListSerializer(RoleDefinition.serializer()), databaseValue)
    }

    override fun encode(value: List<RoleDefinition>): String {
        return dbJson.encodeToString(ListSerializer(RoleDefinition.serializer()), value)
    }
}

/**
 * Adapter for `Node.content_json`
 * Converts NodeContent polymorphic hierarchy to/from a JSON String.
 */
val nodeContentAdapter = object : ColumnAdapter<NodeContent, String> {
    override fun decode(databaseValue: String): NodeContent {
        return dbJson.decodeFromString(NodeContent.serializer(), databaseValue)
    }
    override fun encode(value: NodeContent): String {
        return dbJson.encodeToString(NodeContent.serializer(), value)
    }
}

/**
 * Adapter for `Edge.properties_json`, and `LayoutConstraint.params_json`
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