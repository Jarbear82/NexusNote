package com.tau.nexusnote

import com.tau.nexusnote.datamodels.AttributeDefModel
import com.tau.nexusnote.datamodels.CodexEntity
import com.tau.nexusnote.datamodels.LinkModel
import com.tau.nexusnote.datamodels.RoleDefModel
import com.tau.nexusnote.datamodels.SchemaDefModel
import com.tau.nexusnote.datamodels.SchemaKind
import com.tau.nexusnote.db.AppDatabase

expect class SqliteDbService() {
    // We don't expose the database directly to common code to enforce the abstraction
    // constructor(db: AppDatabase)

    fun initialize(path: String)
    fun close()

    // --- Schemas ---
    fun getAllSchemas(): List<SchemaDefModel>
    fun getAllAttributeDefs(): List<AttributeDefModel>
    fun getAllRoleDefs(): List<RoleDefModel>
    fun createSchema(name: String, kind: SchemaKind, attributes: List<AttributeDefModel>, roles: List<RoleDefModel> = emptyList()): Long
    fun updateSchema(id: Long, name: String, attributes: List<AttributeDefModel>, roles: List<RoleDefModel> = emptyList())
    fun deleteSchema(id: Long)

    // --- Entities (Nodes & Edges) ---
    fun getAllEntities(): List<CodexEntity>
    fun createEntity(
        schemaIds: List<Long>,
        attributes: Map<Long, Any?>, // Map<AttributeDefID, Value>
        links: List<LinkModel> = emptyList() // Only for Relations
    ): Long
    fun updateEntityAttributes(entityId: Long, newAttributes: Map<Long, Any?>)
    fun deleteEntity(id: Long)

    // --- Layout Constraints ---
    // Simple pass-through for layout persistence
    fun getAllConstraints(): List<Pair<Long, String>> // Returns ID -> JSON
    fun saveConstraint(type: String, entityIdsJson: String, paramsJson: String)
    fun deleteConstraint(id: Long)
}