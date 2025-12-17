package com.tau.nexusnote

import androidx.compose.ui.geometry.Offset
import com.tau.nexusnote.datamodels.*
import com.tau.nexusnote.utils.labelToColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Repository for managing the Codex Graph data.
 * Implements Phase 3: Repository & Data Access Layer
 * - Step 3.1: Create Logic
 * - Step 3.2: Read Logic
 */
class CodexRepository(
    private val dbService: SqliteDbService,
    private val coroutineScope: CoroutineScope
) {

    // --- State Flows ---
    private val _schema = MutableStateFlow<SchemaData?>(null)
    val schema = _schema.asStateFlow()

    private val _nodeList = MutableStateFlow<List<NodeDisplayItem>>(emptyList())
    val nodeList = _nodeList.asStateFlow()

    private val _edgeList = MutableStateFlow<List<EdgeDisplayItem>>(emptyList())
    val edgeList = _edgeList.asStateFlow()

    private val _constraints = MutableStateFlow<List<LayoutConstraintItem>>(emptyList())
    val constraints = _constraints.asStateFlow()

    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow = _errorFlow.asStateFlow()

    fun clearError() { _errorFlow.value = null }

    // ============================================================================================
    // STEP 3.2: READ LOGIC (Graph Loader)
    // ============================================================================================

    suspend fun refreshAll() {
        refreshSchema()
        refreshNodes()
        refreshEdges()
    }

    suspend fun refreshSchema() = withContext(Dispatchers.Default) {
        try {
            val allSchemaDefs = dbService.getAllSchemas()
            val allAttrDefs = dbService.getAllAttributeDefs()
            val allRoleDefs = dbService.getAllRoleDefs()

            val schemaDefinitions = allSchemaDefs.map { sDef ->
                val props = allAttrDefs
                    .filter { it.schemaId == sDef.id }
                    .map {
                        SchemaProperty(
                            id = it.id,
                            name = it.name,
                            type = try {
                                CodexPropertyDataTypes.valueOf(it.dataType.name)
                            } catch (e: Exception) {
                                CodexPropertyDataTypes.TEXT
                            },
                            // In a real app, this flag would be in DB. We default first property to true.
                            isDisplayProperty = false
                        )
                    }
                    .mapIndexed { index, prop -> if (index == 0) prop.copy(isDisplayProperty = true) else prop }

                val roles = allRoleDefs
                    .filter { it.schemaId == sDef.id }
                    .map {
                        RoleDefinition(
                            id = it.id,
                            name = it.name,
                            direction = it.direction,
                            cardinality = it.cardinality
                        )
                    }

                SchemaDefinition(
                    id = sDef.id,
                    name = sDef.name,
                    isRelation = sDef.kind == SchemaKind.RELATION,
                    properties = props,
                    roles = roles
                )
            }

            _schema.value = SchemaData(
                nodeSchemas = schemaDefinitions.filter { !it.isRelation },
                edgeSchemas = schemaDefinitions.filter { it.isRelation }
            )
        } catch (e: Exception) {
            _errorFlow.value = "Error loading schema: ${e.message}"
            e.printStackTrace()
        }
    }

    suspend fun refreshNodes() = withContext(Dispatchers.Default) {
        // We use the full graph loader to ensure consistency
        val graph = loadGraph()
        val displayItems = graph.nodes.map { node ->
            val primarySchema = node.schemas.firstOrNull()
            NodeDisplayItem(
                id = node.id,
                label = primarySchema?.name ?: "Entity",
                displayProperty = node.displayProperty,
                schemaId = primarySchema?.id ?: 0L
            )
        }
        _nodeList.value = displayItems
    }

    suspend fun refreshEdges() = withContext(Dispatchers.Default) {
        val graph = loadGraph()
        val displayItems = graph.edges.map { edge ->
            val primarySchema = edge.schemas.firstOrNull()

            // Construct participant list for UI
            val participants = mutableListOf<EdgeParticipant>()

            // Helper to find ANY entity (Node or Edge) by ID and wrap it as a NodeDisplayItem for UI compatibility
            fun findParticipant(id: Long): NodeDisplayItem? {
                graph.nodes.find { it.id == id }?.let {
                    val pSchema = it.schemas.firstOrNull()
                    return NodeDisplayItem(it.id, pSchema?.name ?: "Node", it.displayProperty, pSchema?.id ?: 0)
                }
                graph.edges.find { it.id == id }?.let {
                    val pSchema = it.schemas.firstOrNull()
                    return NodeDisplayItem(it.id, pSchema?.name ?: "Edge", it.label, pSchema?.id ?: 0)
                }
                return null
            }

            findParticipant(edge.sourceId)?.let {
                participants.add(EdgeParticipant(node = it, role = "Source"))
            }
            findParticipant(edge.targetId)?.let {
                participants.add(EdgeParticipant(node = it, role = "Target"))
            }

            EdgeDisplayItem(
                id = edge.id,
                label = primarySchema?.name ?: "Relation",
                properties = edge.properties.associate { it.definition.name to it.value.toString() },
                participatingNodes = participants,
                schemaId = primarySchema?.id ?: 0L
            )
        }
        _edgeList.value = displayItems
    }

    /**
     * Core function for Step 3.2.
     * Fetches all entities, hydrations, and constructs the Graph model.
     */
    suspend fun loadGraph(): Graph = withContext(Dispatchers.Default) {
        try {
            // 1. Fetch All Entities (Hydrated)
            val entities = dbService.getAllEntities()
            val schemaData = _schema.value ?: return@withContext Graph(emptyList(), emptyList())
            val allSchemas = schemaData.nodeSchemas + schemaData.edgeSchemas
            val schemaMap = allSchemas.associateBy { it.id }

            val nodes = mutableListOf<GraphNode>()
            val edges = mutableListOf<GraphEdge>()

            entities.forEach { entity ->
                // Map DB Attributes back to Domain Schema Properties
                // Note: Entity types in DB only have schema ID. We match them to loaded schemas.
                val entitySchemas = entity.types.mapNotNull { schemaMap[it.id.toString()] }

                val domainProperties = entity.attributes.mapNotNull { (attrId, value) ->
                    // Find the definition across all schemas this entity implements
                    val propDef = entitySchemas.flatMap { it.properties }.find { it.id == attrId }
                    if (propDef != null) CodexProperty(propDef, value) else null
                }

                // Determine display text
                val displayProp = domainProperties.find { it.definition.isDisplayProperty }?.value?.toString()
                    ?: domainProperties.firstOrNull()?.value?.toString()
                    ?: "ID:${entity.id}"

                // Check if it's an Edge (Relation)
                // In this system, an entity is an Edge if it has any outgoing links (meaning it acts as a relation)
                // OR if its type is strictly defined as RELATION in Schema.
                val isRelation = entity.isRelation

                if (isRelation) {
                    // It's an Edge
                    // We need to determine Source and Target.
                    // Simplified: We assume binary roles for now or pick first two links.
                    // Step 3.1 says: "If it's a Relation, iterate through roles...".
                    // The loaded `entity.outgoingLinks` contains LinkModel(relationId, playerId, roleId).

                    val outgoing = entity.outgoingLinks
                    if (outgoing.size >= 2) {
                        // Very basic binary edge assumption for visualization
                        // A more robust graph view would support Hyperedges (N-nary)
                        val sourceLink = outgoing[0]
                        val targetLink = outgoing[1]

                        edges.add(GraphEdge(
                            id = entity.id,
                            schemas = entitySchemas,
                            properties = domainProperties,
                            sourceId = sourceLink.playerId,
                            targetId = targetLink.playerId,
                            strength = 1.0f,
                            colorInfo = labelToColor(entitySchemas.firstOrNull()?.name ?: "Edge")
                        ))
                    }
                } else {
                    // It's a Node
                    nodes.add(GraphNode(
                        id = entity.id,
                        schemas = entitySchemas,
                        displayProperty = displayProp,
                        pos = Offset.Zero, // Layout engine handles this
                        vel = Offset.Zero,
                        mass = 1.0f,
                        radius = 20.0f,
                        width = 40.0f,
                        height = 40.0f,
                        isCompound = false,
                        colorInfo = labelToColor(entitySchemas.firstOrNull()?.name ?: "Node"),
                        properties = domainProperties
                    ))
                }
            }
            return@withContext Graph(nodes, edges)

        } catch (e: Exception) {
            _errorFlow.value = "Error loading graph: ${e.message}"
            e.printStackTrace()
            return@withContext Graph(emptyList(), emptyList())
        }
    }

    // ============================================================================================
    // STEP 3.1: CREATE LOGIC
    // ============================================================================================

    suspend fun createNodeSchema(state: NodeSchemaCreationState) = withContext(Dispatchers.Default) {
        try {
            // Map UI State to DB Models
            val attributes = state.properties.map {
                AttributeDefModel(0, 0, it.name, DbValueType.valueOf(it.type.name))
            }
            dbService.createSchema(state.tableName, SchemaKind.ENTITY, attributes)
            refreshSchema()
        } catch (e: Exception) {
            _errorFlow.value = "Failed to create Node Schema: ${e.message}"
        }
    }

    suspend fun createEdgeSchema(state: EdgeSchemaCreationState) = withContext(Dispatchers.Default) {
        try {
            val attributes = state.properties.map {
                AttributeDefModel(0, 0, it.name, DbValueType.valueOf(it.type.name))
            }
            val roles = state.roles.map {
                RoleDefModel(0, 0, it.name, it.direction, it.cardinality)
            }

            // Note: UI models already use RelationCardinality, so simple map is enough.
            dbService.createSchema(state.tableName, SchemaKind.RELATION, attributes, roles)
            refreshSchema()
        } catch (e: Exception) {
            _errorFlow.value = "Failed to create Edge Schema: ${e.message}"
        }
    }

    suspend fun createNode(state: NodeCreationState) = withContext(Dispatchers.Default) {
        try {
            if (state.selectedSchemas.isEmpty()) throw Exception("No Schema Selected")
            val schemaIds = state.selectedSchemas.map { it.id }

            // Map string inputs to typed values based on definition
            val attributes = mutableMapOf<Long, Any?>()
            state.properties.forEach { (propName, propValue) ->
                // Find the definition across all selected schemas
                val def = state.selectedSchemas.flatMap { it.properties }.find { it.name == propName }
                if (def != null) {
                    val typedValue = parseValue(propValue, def.type)
                    attributes[def.id] = typedValue
                }
            }

            dbService.createEntity(schemaIds, attributes)
            refreshNodes()
        } catch (e: Exception) {
            _errorFlow.value = "Failed to create Node: ${e.message}"
        }
    }

    suspend fun createEdge(state: EdgeCreationState) = withContext(Dispatchers.Default) {
        try {
            val schemaDef = state.selectedSchema ?: throw Exception("No Schema Selected")
            val schemaId = schemaDef.id

            // 1. Attributes
            val attributes = mutableMapOf<Long, Any?>()
            state.properties.forEach { (propName, propValue) ->
                val def = schemaDef.properties.find { it.name == propName }
                if (def != null) {
                    val typedValue = parseValue(propValue, def.type)
                    attributes[def.id] = typedValue
                }
            }

            // 2. Links (Participants)
            val links = mutableListOf<LinkModel>()
            state.participants.forEach { part ->
                // Find the RoleDefinition ID for this participant's role name
                val roleDef = schemaDef.roles.find { it.name == part.role }
                if (roleDef != null && part.node != null) {
                    links.add(LinkModel(
                        relationId = 0, // Will be set by DB upon insertion of parent entity
                        playerId = part.node.id,
                        roleId = roleDef.id
                    ))
                }
            }

            if (links.size < 2) throw Exception("Edges require at least 2 participants.")

            dbService.createEntity(listOf(schemaId), attributes, links)
            refreshEdges()
        } catch (e: Exception) {
            _errorFlow.value = "Failed to create Edge: ${e.message}"
        }
    }

    // --- Update / Delete Stubs (Basic implementation) ---

    suspend fun deleteSchema(id: Long) = withContext(Dispatchers.Default) {
        try {
            dbService.deleteSchema(id)
            refreshAll()
        } catch (e: Exception) {
            _errorFlow.value = "Failed to delete schema: ${e.message}"
        }
    }

    suspend fun deleteNode(id: Long) = withContext(Dispatchers.Default) {
        dbService.deleteEntity(id)
        refreshNodes()
        refreshEdges() // Edges might be deleted cascade
    }

    suspend fun deleteEdge(id: Long) = withContext(Dispatchers.Default) {
        dbService.deleteEntity(id)
        refreshEdges()
    }

    suspend fun updateNode(state: NodeEditState) = withContext(Dispatchers.Default) {
        try {
            val attributes = mutableMapOf<Long, Any?>()
            state.properties.forEach { (propName, propValue) ->
                val def = state.schemas.flatMap { it.properties }.find { it.name == propName }
                if (def != null) {
                    attributes[def.id] = parseValue(propValue, def.type)
                }
            }
            dbService.updateEntityAttributes(state.id, attributes)
            refreshNodes()
        } catch (e: Exception) {
            _errorFlow.value = "Update failed: ${e.message}"
        }
    }

    suspend fun updateEdge(state: EdgeEditState) = withContext(Dispatchers.Default) {
        try {
            val attributes = mutableMapOf<Long, Any?>()
            state.properties.forEach { (propName, propValue) ->
                val def = state.schema.properties.find { it.name == propName }
                if (def != null) {
                    attributes[def.id] = parseValue(propValue, def.type)
                }
            }
            dbService.updateEntityAttributes(state.id, attributes)
            refreshEdges()
        } catch (e: Exception) {
            _errorFlow.value = "Update failed: ${e.message}"
        }
    }

    // Stub implementations for Schema Updates (Phase 3.1 focused on Create)
    suspend fun updateNodeSchema(state: NodeSchemaEditState) { /* Requires Alter Table logic */ }
    suspend fun updateEdgeSchema(state: EdgeSchemaEditState) { /* Requires Alter Table logic */ }
    suspend fun getNodeEditState(id: Long): NodeEditState? {
        // Hydrate node for editing
        val graph = loadGraph()
        val node = graph.nodes.find { it.id == id } ?: return null
        val schemas = node.schemas
        val props = node.properties.associate { it.definition.name to it.value.toString() }
        return NodeEditState(id, schemas, props)
    }

    suspend fun getEdgeEditState(item: EdgeDisplayItem): EdgeEditState? {
        val graph = loadGraph()
        val edge = graph.edges.find { it.id == item.id } ?: return null
        val schema = edge.schemas.firstOrNull() ?: return null
        val props = edge.properties.associate { it.definition.name to it.value.toString() }

        // Reconstruct participants
        val participants = item.participatingNodes.map {
            ParticipantSelection(id=it.node.id.toString(), role=it.role ?: "", node=it.node)
        }

        return EdgeEditState(item.id, schema, participants, props)
    }

    suspend fun setNodeCollapsed(id: Long, collapsed: Boolean) {} // Visual state only

    suspend fun getSchemaDependencyCount(id: Long): Long {
        // Query entity types for this schema
        return 0L // TODO: Implement countEntitiesBySchema in DbService
    }

    suspend fun getNodesPaginated(offset: Long, limit: Long): List<NodeDisplayItem> {
        val all = _nodeList.value
        if (offset >= all.size) return emptyList()
        val end = (offset + limit).toInt().coerceAtMost(all.size)
        return all.subList(offset.toInt(), end)
    }

    suspend fun getEdgesPaginated(offset: Long, limit: Long): List<EdgeDisplayItem> {
        val all = _edgeList.value
        if (offset >= all.size) return emptyList()
        val end = (offset + limit).toInt().coerceAtMost(all.size)
        return all.subList(offset.toInt(), end)
    }

    // --- Helpers ---
    private fun parseValue(value: String, type: CodexPropertyDataTypes): Any? {
        return try {
            when (type) {
                CodexPropertyDataTypes.NUMBER -> value.toDouble()
                CodexPropertyDataTypes.DATE -> value.toLongOrNull() ?: 0L
                else -> value
            }
        } catch (e: Exception) { null }
    }
}