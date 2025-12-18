package com.tau.nexusnote

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.tau.nexusnote.datamodels.*
import com.tau.nexusnote.db.AppDatabase
import com.tau.nexusnote.db.LayoutConstraint
import com.tau.nexusnote.db.longListAdapter
import com.tau.nexusnote.db.stringMapAdapter
import kotlin.time.Clock
import java.io.File
import java.util.Properties
import kotlin.time.ExperimentalTime

actual class SqliteDbService {

    lateinit var database: AppDatabase
    private var driver: JdbcSqliteDriver? = null

    actual fun initialize(path: String) {
        val url = "jdbc:sqlite:$path"
        val props = Properties()
        // Enforce FK constraints for SQLite
        props.setProperty("foreign_keys", "true")

        driver = JdbcSqliteDriver(url, props)

        // Create tables if they don't exist
        AppDatabase.Schema.create(driver!!)
        database = AppDatabase(
            driver = driver!!,
            LayoutConstraintAdapter = LayoutConstraint.Adapter(
                entity_ids_jsonAdapter = longListAdapter,
                params_jsonAdapter = stringMapAdapter
            )
        )
    }

    actual fun close() {
        driver?.close()
    }

    // --- Schema Management ---

    actual fun getAllSchemas(): List<SchemaDefModel> {
        return database.appDatabaseQueries.selectAllSchemas().executeAsList().map { schema ->
            SchemaDefModel(schema.id, schema.name, SchemaKind.valueOf(schema.kind))
        }
    }

    actual fun getAllAttributeDefs(): List<AttributeDefModel> {
        return database.appDatabaseQueries.selectAllAttributeDefs().executeAsList().map { attr ->
            AttributeDefModel(attr.id, attr.schema_id, attr.name, DbValueType.valueOf(attr.data_type))
        }
    }

    actual fun getAllRoleDefs(): List<RoleDefModel> {
        val roles = database.appDatabaseQueries.selectAllRoleDefs().executeAsList()
        val allAllowed = database.appDatabaseQueries.selectAllRoleAllowedSchemas().executeAsList()
        val allowedMap = allAllowed.groupBy { it.role_id }

        return roles.map { role ->
            val allowedSchemas = allowedMap[role.id]?.map { it.allowed_schema_id } ?: emptyList()
            RoleDefModel(
                role.id, role.schema_id, role.name,
                RelationDirection.valueOf(role.direction),
                RelationCardinality.valueOf(role.cardinality),
                allowedSchemas
            )
        }
    }

    actual fun createSchema(name: String, kind: SchemaKind, attributes: List<AttributeDefModel>, roles: List<RoleDefModel>): Long {
        var schemaId: Long = -1
        database.transaction {
            database.appDatabaseQueries.insertSchema(name, kind.name)
            schemaId = database.appDatabaseQueries.lastInsertRowId().executeAsOne()

            attributes.forEach { attr ->
                database.appDatabaseQueries.insertAttributeDef(schemaId, attr.name, attr.dataType.name)
            }

            if (kind == SchemaKind.RELATION) {
                roles.forEach { role ->
                    database.appDatabaseQueries.insertRoleDef(schemaId, role.name, role.direction.name, role.cardinality.name)
                    val roleId = database.appDatabaseQueries.lastInsertRowId().executeAsOne()
                    role.allowedNodeSchemas.forEach { allowedId ->
                        database.appDatabaseQueries.insertRoleAllowedSchema(roleId, allowedId)
                    }
                }
            }
        }
        return schemaId
    }

    actual fun updateSchema(
        id: Long,
        name: String,
        attributes: List<AttributeDefModel>,
        roles: List<RoleDefModel>
    ) {
        database.transaction {
            database.appDatabaseQueries.updateSchemaName(name, id)
            attributes.forEach { attr ->
                if (attr.id == 0L) {
                    database.appDatabaseQueries.insertAttributeDef(id, attr.name, attr.dataType.name)
                } else {
                    database.appDatabaseQueries.updateAttributeDef(attr.name, attr.dataType.name, attr.id)
                }
            }

            roles.forEach { role ->
                val roleId: Long
                if (role.id == 0L) {
                    database.appDatabaseQueries.insertRoleDef(id, role.name, role.direction.name, role.cardinality.name)
                    roleId = database.appDatabaseQueries.lastInsertRowId().executeAsOne()
                } else {
                    database.appDatabaseQueries.updateRoleDef(role.name, role.direction.name, role.cardinality.name, role.id)
                    roleId = role.id
                }

                database.appDatabaseQueries.deleteAllowedSchemasForRole(roleId)
                role.allowedNodeSchemas.forEach { allowedId ->
                    database.appDatabaseQueries.insertRoleAllowedSchema(roleId, allowedId)
                }
            }
        }
    }

    actual fun deleteSchema(id: Long) {
        database.appDatabaseQueries.deleteSchemaById(id)
    }

    // --- Entity Management ---

    actual fun countEntitiesByKind(kind: SchemaKind): Long {
        return database.appDatabaseQueries.countEntitiesByKind(kind.name).executeAsOne()
    }

    actual fun getEntitiesByKindPaginated(kind: SchemaKind, limit: Long, offset: Long): List<CodexEntity> {
        val entities = database.appDatabaseQueries.selectEntitiesByKindPaginated(kind.name, limit, offset).executeAsList()
        if (entities.isEmpty()) return emptyList()

        val entityIds = entities.map { it.id }
        
        val types = database.appDatabaseQueries.selectEntityTypesByEntityIds(entityIds).executeAsList()
        val attributes = database.appDatabaseQueries.selectAttributeValuesByEntityIds(entityIds).executeAsList()
        val links = database.appDatabaseQueries.selectRelationLinksByEntityIds(entityIds).executeAsList()
        val schemas = getAllSchemas().associateBy { it.id }

        val typesMap = types.groupBy { it.entity_id }.mapValues { entry ->
            entry.value.mapNotNull { schemas[it.schema_id] }
        }

        val attributesMap = attributes.groupBy { it.entity_id }.mapValues { entry ->
            entry.value.associate { row ->
                val value: Any? = when {
                    row.val_text != null -> row.val_text
                    row.val_int != null -> row.val_int
                    row.val_real != null -> row.val_real
                    row.val_bool != null -> row.val_bool == 1L
                    else -> null
                }
                row.attribute_def_id to value
            }
        }

        val allLinks = links.map { link ->
            LinkModel(link.relation_entity_id, link.player_entity_id, link.role_def_id)
        }

        val outgoingMap = allLinks.groupBy { it.relationId }
        val incomingMap = allLinks.groupBy { it.playerId }

        return entities.map { entity ->
            CodexEntity(
                id = entity.id,
                createdAt = entity.created_at,
                types = typesMap[entity.id] ?: emptyList(),
                attributes = attributesMap[entity.id] ?: emptyMap(),
                outgoingLinks = outgoingMap[entity.id] ?: emptyList(),
                incomingRelations = incomingMap[entity.id] ?: emptyList()
            )
        }
    }

    actual fun getAllEntities(): List<CodexEntity> {
        val entities = database.appDatabaseQueries.selectAllEntities().executeAsList()
        val types = database.appDatabaseQueries.selectAllEntityTypes().executeAsList()
        val attributes = database.appDatabaseQueries.selectAllAttributeValues().executeAsList()
        val links = database.appDatabaseQueries.selectAllRelationLinks().executeAsList()
        val schemas = getAllSchemas().associateBy { it.id }

        val typesMap = types.groupBy { it.entity_id }.mapValues { entry ->
            entry.value.mapNotNull { schemas[it.schema_id] }
        }

        val attributesMap = attributes.groupBy { it.entity_id }.mapValues { entry ->
            entry.value.associate { row ->
                val value: Any? = when {
                    row.val_text != null -> row.val_text
                    row.val_int != null -> row.val_int
                    row.val_real != null -> row.val_real
                    row.val_bool != null -> row.val_bool == 1L
                    else -> null
                }
                row.attribute_def_id to value
            }
        }

        val allLinks = links.map { link ->
            LinkModel(link.relation_entity_id, link.player_entity_id, link.role_def_id)
        }

        val outgoingMap = allLinks.groupBy { it.relationId }
        val incomingMap = allLinks.groupBy { it.playerId }

        return entities.map { entity ->
            CodexEntity(
                id = entity.id,
                createdAt = entity.created_at,
                types = typesMap[entity.id] ?: emptyList(),
                attributes = attributesMap[entity.id] ?: emptyMap(),
                outgoingLinks = outgoingMap[entity.id] ?: emptyList(),
                incomingRelations = incomingMap[entity.id] ?: emptyList()
            )
        }
    }

    actual fun getEntitiesPaginated(limit: Long, offset: Long): List<CodexEntity> {
        val entities = database.appDatabaseQueries.selectEntitiesPaginated(limit, offset).executeAsList()
        if (entities.isEmpty()) return emptyList()

        val entityIds = entities.map { it.id }
        
        val types = database.appDatabaseQueries.selectEntityTypesByEntityIds(entityIds).executeAsList()
        val attributes = database.appDatabaseQueries.selectAttributeValuesByEntityIds(entityIds).executeAsList()
        val links = database.appDatabaseQueries.selectRelationLinksByEntityIds(entityIds).executeAsList()
        val schemas = getAllSchemas().associateBy { it.id }

        val typesMap = types.groupBy { it.entity_id }.mapValues { entry ->
            entry.value.mapNotNull { schemas[it.schema_id] }
        }

        val attributesMap = attributes.groupBy { it.entity_id }.mapValues { entry ->
            entry.value.associate { row ->
                val value: Any? = when {
                    row.val_text != null -> row.val_text
                    row.val_int != null -> row.val_int
                    row.val_real != null -> row.val_real
                    row.val_bool != null -> row.val_bool == 1L
                    else -> null
                }
                row.attribute_def_id to value
            }
        }

        val allLinks = links.map { link ->
            LinkModel(link.relation_entity_id, link.player_entity_id, link.role_def_id)
        }

        val outgoingMap = allLinks.groupBy { it.relationId }
        val incomingMap = allLinks.groupBy { it.playerId }

        return entities.map { entity ->
            CodexEntity(
                id = entity.id,
                createdAt = entity.created_at,
                types = typesMap[entity.id] ?: emptyList(),
                attributes = attributesMap[entity.id] ?: emptyMap(),
                outgoingLinks = outgoingMap[entity.id] ?: emptyList(),
                incomingRelations = incomingMap[entity.id] ?: emptyList()
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    actual fun createEntity(
        schemaIds: List<Long>,
        attributes: Map<Long, Any?>,
        links: List<LinkModel>
    ): Long {
        var newId: Long = -1
        database.transaction {
            database.appDatabaseQueries.insertEntity(Clock.System.now().toEpochMilliseconds())
            newId = database.appDatabaseQueries.lastInsertRowId().executeAsOne()

            schemaIds.forEach { sId ->
                database.appDatabaseQueries.insertEntityType(newId, sId)
            }

            attributes.forEach { (defId, value) ->
                var valText: String? = null
                var valInt: Long? = null
                var valReal: Double? = null
                var valBool: Long? = null

                when (value) {
                    is String -> valText = value
                    is Int -> valInt = value.toLong()
                    is Long -> valInt = value
                    is Double -> valReal = value
                    is Float -> valReal = value.toDouble()
                    is Boolean -> valBool = if (value) 1L else 0L
                }

                if (valText != null || valInt != null || valReal != null || valBool != null) {
                    database.appDatabaseQueries.insertAttributeValue(
                        newId, defId, valText, valInt, valReal, valBool
                    )
                }
            }

            links.forEach { link ->
                database.appDatabaseQueries.insertRelationLink(newId, link.playerId, link.roleId)
            }
        }
        return newId
    }

    actual fun updateEntityAttributes(entityId: Long, newAttributes: Map<Long, Any?>) {
        database.transaction {
            newAttributes.forEach { (defId, value) ->
                var valText: String? = null
                var valInt: Long? = null
                var valReal: Double? = null
                var valBool: Long? = null

                when (value) {
                    is String -> valText = value
                    is Int -> valInt = value.toLong()
                    is Long -> valInt = value
                    is Double -> valReal = value
                    is Float -> valReal = value.toDouble()
                    is Boolean -> valBool = if (value) 1L else 0L
                }

                database.appDatabaseQueries.updateAttributeValue(valText, valInt, valReal, valBool, entityId, defId)
            }
        }
    }

    actual fun updateEntityTypes(entityId: Long, schemaIds: List<Long>) {
        database.transaction {
            database.appDatabaseQueries.deleteEntityTypes(entityId)
            schemaIds.forEach { sId ->
                database.appDatabaseQueries.insertEntityType(entityId, sId)
            }
        }
    }

    actual fun deleteEntity(id: Long) {
        database.appDatabaseQueries.deleteEntityById(id)
    }

    // --- Constraints ---

    actual fun getAllConstraints(): List<Pair<Long, String>> {
        return database.appDatabaseQueries.selectAllConstraints().executeAsList().map {
            it.id to "{}" 
        }
    }

    actual fun saveConstraint(type: String, entityIdsJson: String, paramsJson: String) {
        database.appDatabaseQueries.insertConstraint(type, emptyList(), emptyMap())
    }

    actual fun deleteConstraint(id: Long) {
        database.appDatabaseQueries.deleteConstraintById(id)
    }
}