package com.tau.nexusnote.datamodels

import kotlinx.serialization.Serializable

/**
 * Supported data types for Attributes in the strict database schema.
 */
@Serializable
enum class DbValueType {
    TEXT, INTEGER, REAL, BOOLEAN, LONG_TEXT, NUMBER, DATE, IMAGE, AUDIO
}

/**
 * Directions for Relations relative to the Node.
 */
@Serializable
enum class RelationDirection {
    SOURCE, TARGET
}

@Serializable
enum class RelationCardinality {
    ONE, MANY
}

@Serializable
enum class SchemaKind {
    ENTITY, RELATION
}

/**
 * Metadata: Represents a defined Schema (e.g., "Person", "Meeting").
 */
@Serializable
data class SchemaDefModel(
    val id: Long,
    val name: String,
    val kind: SchemaKind
)

/**
 * Metadata: Represents a property definition within a Schema.
 */
@Serializable
data class AttributeDefModel(
    val id: Long,
    val schemaId: Long,
    val name: String,
    val dataType: DbValueType
)

/**
 * Metadata: Represents a role definition within a Relation Schema.
 */
@Serializable
data class RoleDefModel(
    val id: Long,
    val schemaId: Long,
    val name: String,
    val direction: RelationDirection,
    val cardinality: RelationCardinality,
    val allowedNodeSchemas: List<Long> = emptyList()
)

/**
 * A linkage instance.
 * Represents a connection in the graph where [relationId] connects to [playerId] via [roleId].
 */
data class LinkModel(
    val relationId: Long,
    val playerId: Long,
    val roleId: Long
)

/**
 * The primary Aggregate Root for the application.
 * Represents a Node or Edge fully hydrated with all its types, attributes, and connections.
 */
data class CodexEntity(
    val id: Long,
    val createdAt: Long,

    // The schemas this entity implements (e.g. [Person, Wizard])
    val types: List<SchemaDefModel>,

    // The data: Map of Attribute Definition ID to the actual value
    val attributes: Map<Long, Any?>,

    // If this entity is a RELATION, these are the nodes (or edges) it connects.
    // e.g. Marriage(ID:5) -> [Husband: Bob(ID:1), Wife: Alice(ID:2)]
    val outgoingLinks: List<LinkModel> = emptyList(),

    // Relations where this entity is the PLAYER (target/source).
    // e.g. Bob(ID:1) -> [Marriage(ID:5), Employment(ID:9)]
    val incomingRelations: List<LinkModel> = emptyList()
) {
    /**
     * Helper to check if this Entity acts as a Node or a Relation.
     * In the new system, everything is an Entity, but visual logic needs to distinguish.
     */
    val isRelation: Boolean
        get() = types.any { it.kind == SchemaKind.RELATION }
}