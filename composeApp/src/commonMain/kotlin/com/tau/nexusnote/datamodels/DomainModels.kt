package com.tau.nexusnote.datamodels

import kotlinx.serialization.Serializable

/**
 * Step 2.1: Abstract the "Entity"
 * Base interface for any entity in the graph (Node or Edge).
 */
interface GraphEntity {
    val id: Long
    val properties: List<CodexProperty>
}

/**
 * Step 2.2: Redefine Schemas
 * Represents a full Schema definition (e.g., "Person", "KNOWS").
 */
@Serializable
data class SchemaDefinition(
    val id: Long,
    val name: String,
    val isRelation: Boolean,
    val properties: List<SchemaProperty>,
    val roles: List<RoleDefinition> = emptyList() // Only for Relations
)

/**
 * Refactored SchemaProperty to include the database ID.
 */
@Serializable
data class SchemaProperty(
    val id: Long = 0,
    val name: String,
    val type: CodexPropertyDataTypes,
    val isDisplayProperty: Boolean = false
)

/**
 * Distinct model class for Roles.
 */
@Serializable
data class RoleDefinition(
    val id: Long = 0,
    val name: String,
    val direction: RelationDirection,
    val cardinality: RelationCardinality,
    val allowedNodeSchemas: List<Long> = emptyList()
)

/**
 * Represents a concrete property value on an entity.
 */
data class CodexProperty(
    val definition: SchemaProperty,
    val value: Any?
)

/**
 * Represents an on-disk codex database.
 */
data class CodexItem(
    val name: String,
    val path: String
)

/**
 * Wrapper for schema grouping.
 */
data class SchemaData(
    val nodeSchemas: List<SchemaDefinition>,
    val edgeSchemas: List<SchemaDefinition>
)