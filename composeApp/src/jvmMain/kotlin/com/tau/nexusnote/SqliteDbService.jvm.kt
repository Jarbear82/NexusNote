package com.tau.nexusnote

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.tau.nexusnote.datamodels.*
import com.tau.nexusnote.db.AppDatabase
import kotlin.time.Clock
import java.io.File
import java.util.Properties

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
        // Note: checking if file exists isn't enough for valid schema,
        // but for this phase we assume Schema creation via SQLDelight logic or pre-existing
        AppDatabase.Schema.create(driver!!)
        database = AppDatabase(driver!!)
    }

    actual fun close() {
        driver?.close()
    }

    // --- Schema Management ---

    actual fun getAllSchemas(): List<SchemaDefModel> {
        return database.schemaDefQueries.selectAllSchemas().executeAsList().map {
            SchemaDefModel(it.id, it.name, SchemaKind.valueOf(it.kind))
        }
    }

    actual fun getAllAttributeDefs(): List<AttributeDefModel> {
        return database.attributeDefQueries.selectAllAttributeDefs().executeAsList().map {
            AttributeDefModel(it.id, it.schema_id, it.name, DbValueType.valueOf(it.data_type))
        }
    }

    actual fun getAllRoleDefs(): List<RoleDefModel> {
        return database.roleDefQueries.selectAllRoleDefs().executeAsList().map {
            RoleDefModel(
                it.id, it.schema_id, it.name,
                RelationDirection.valueOf(it.direction),
                RelationCardinality.valueOf(it.cardinality)
            )
        }
    }

    actual fun createSchema(name: String, kind: SchemaKind, attributes: List<AttributeDefModel>, roles: List<RoleDefModel> = emptyList()): Long {
        var schemaId: Long = -1
        database.transaction {
            database.schemaDefQueries.insertSchema(name, kind.name)
            schemaId = database.schemaDefQueries.lastInsertRowId().executeAsOne()

            attributes.forEach { attr ->
                database.attributeDefQueries.insertAttributeDef(schemaId, attr.name, attr.dataType.name)
            }

            if (kind == SchemaKind.RELATION) {
                roles.forEach { role ->
                    database.roleDefQueries.insertRoleDef(schemaId, role.name, role.direction.name, role.cardinality.name)
                }
            }
        }
        return schemaId
    }

    actual fun deleteSchema(id: Long) {
        database.schemaDefQueries.deleteSchemaById(id)
    }

    // --- Entity Management ---

    actual fun getAllEntities(): List<CodexEntity> {
        // 1. Fetch all raw data
        val entities = database.entityQueries.selectAllEntities().executeAsList()
        val types = database.entityTypeQueries.selectAllEntityTypes().executeAsList()
        val attributes = database.attributeValueQueries.selectAllAttributeValues().executeAsList()
        val links = database.relationLinkQueries.selectAllRelationLinks().executeAsList()
        val schemas = getAllSchemas().associateBy { it.id }

        // 2. Index data by EntityID for fast lookup
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

        val allLinks = links.map {
            LinkModel(it.relation_entity_id, it.player_entity_id, it.role_def_id)
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

    actual fun createEntity(
        schemaIds: List<Long>,
        attributes: Map<Long, Any?>,
        links: List<LinkModel>
    ): Long {
        var newId: Long = -1
        database.transaction {
            database.entityQueries.insertEntity(Clock.System.now().toEpochMilliseconds())
            newId = database.entityQueries.lastInsertRowId().executeAsOne()

            schemaIds.forEach { sId ->
                database.entityTypeQueries.insertEntityType(newId, sId)
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
                    database.attributeValueQueries.insertAttributeValue(
                        newId, defId, valText, valInt, valReal, valBool
                    )
                }
            }

            links.forEach { link ->
                database.relationLinkQueries.insertRelationLink(newId, link.playerId, link.roleId)
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

                // Using update - assuming attribute exists. If not, logic should handle insert.
                // For simplified Phase 3, we update existing rows.
                // A robust implementation would do Upsert.
                database.attributeValueQueries.updateAttributeValue(valText, valInt, valReal, valBool, entityId, defId)
            }
        }
    }

    actual fun deleteEntity(id: Long) {
        database.entityQueries.deleteEntityById(id)
    }

    // --- Constraints ---

    actual fun getAllConstraints(): List<Pair<Long, String>> {
        // Return dummy list or implement query if needed for GraphView persistence
        // Using the queries defined in sq file:
        return database.layoutConstraintQueries.selectAllConstraints().executeAsList().map {
            it.id to "{}" // simplified for now
        }
    }

    actual fun saveConstraint(type: String, entityIdsJson: String, paramsJson: String) {
        database.layoutConstraintQueries.insertConstraint(type, emptyList(), emptyMap())
    }

    actual fun deleteConstraint(id: Long) {
        database.layoutConstraintQueries.deleteConstraintById(id)
    }
}