package com.tau.nexusnote

import com.tau.nexusnote.datamodels.EdgeCreationState
import com.tau.nexusnote.datamodels.EdgeDisplayItem
import com.tau.nexusnote.datamodels.EdgeEditState
import com.tau.nexusnote.datamodels.EdgeParticipant
import com.tau.nexusnote.datamodels.EdgeSchemaCreationState
import com.tau.nexusnote.datamodels.EdgeSchemaEditState
import com.tau.nexusnote.datamodels.LayoutConstraintItem
import com.tau.nexusnote.datamodels.NodeContent
import com.tau.nexusnote.datamodels.NodeCreationState
import com.tau.nexusnote.datamodels.NodeDisplayItem
import com.tau.nexusnote.datamodels.NodeEditState
import com.tau.nexusnote.datamodels.NodeSchemaCreationState
import com.tau.nexusnote.datamodels.NodeSchemaEditState
import com.tau.nexusnote.datamodels.NodeType
import com.tau.nexusnote.datamodels.ParticipantSelection
import com.tau.nexusnote.datamodels.SchemaConfig
import com.tau.nexusnote.datamodels.SchemaDefinitionItem
import com.tau.nexusnote.codex.schema.SchemaData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Centralizes all database logic and state for an open Codex.
 */
class CodexRepository(
    private val dbService: SqliteDbService,
    private val repositoryScope: CoroutineScope
) {

    // --- Central State Flows ---
    private val _schema = MutableStateFlow<SchemaData?>(null)
    val schema = _schema.asStateFlow()

    private val _nodeList = MutableStateFlow<List<NodeDisplayItem>>(emptyList())
    val nodeList = _nodeList.asStateFlow()

    private val _edgeList = MutableStateFlow<List<EdgeDisplayItem>>(emptyList())
    val edgeList = _edgeList.asStateFlow()

    private val _constraints = MutableStateFlow<List<LayoutConstraintItem>>(emptyList())
    val constraints = _constraints.asStateFlow()

    // --- Error State Flow ---
    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow = _errorFlow.asStateFlow()

    // --- Public API ---

    fun clearError() {
        _errorFlow.value = null
    }

    suspend fun refreshAll() {
        refreshSchema()
        refreshNodes()
        refreshEdges()
        refreshConstraints()
    }

    suspend fun refreshSchema() = withContext(Dispatchers.IO) {
        try {
            val dbSchemas = dbService.database.appDatabaseQueries.selectAllSchemas().executeAsList()
            val nodeSchemas = mutableListOf<SchemaDefinitionItem>()
            val edgeSchemas = mutableListOf<SchemaDefinitionItem>()

            dbSchemas.forEach { dbSchema ->
                val config = dbSchema.config_json

                if (dbSchema.type == "NODE") {
                    nodeSchemas.add(
                        SchemaDefinitionItem(dbSchema.id, dbSchema.type, dbSchema.name, config, null)
                    )
                } else if (dbSchema.type == "EDGE") {
                    val roles = dbSchema.roles_json
                    edgeSchemas.add(
                        SchemaDefinitionItem(dbSchema.id, dbSchema.type, dbSchema.name, config, roles)
                    )
                }
            }
            _schema.value = SchemaData(nodeSchemas, edgeSchemas)
        } catch (e: Exception) {
            _errorFlow.value = "Error refreshing schema: ${e.message}"
            _schema.value = SchemaData(emptyList(), emptyList())
        }
    }

    suspend fun refreshNodes() = withContext(Dispatchers.IO) {
        val schemaMap = _schema.value?.nodeSchemas?.associateBy { it.id } ?: emptyMap()

        if (_schema.value == null) {
            _errorFlow.value = "Error refreshing nodes: Schema was not loaded first."
            _nodeList.value = emptyList()
            return@withContext
        }

        try {
            val dbNodes = dbService.database.appDatabaseQueries.selectAllNodes().executeAsList()
            _nodeList.value = dbNodes.mapNotNull { dbNode ->
                val schemaId = dbNode.schema_id
                val nodeSchema = if (schemaId != null) schemaMap[schemaId] else null

                // If it has a schema ID but we can't find it, that's an error.
                if (schemaId != null && nodeSchema == null) {
                    println("Warning: Found node with unknown schema ID $schemaId")
                    null
                } else {
                    val content = dbNode.content_json
                    // Derive dynamic display property based on Content Type
                    val displayProp = deriveDisplayProperty(content, nodeSchema, dbNode.display_label)
                    val typeLabel = nodeSchema?.name ?: dbNode.node_type // e.g. "Person" or "HEADING"

                    NodeDisplayItem(
                        id = dbNode.id,
                        label = typeLabel,
                        displayProperty = displayProp,
                        schemaId = schemaId ?: -1L,
                        content = content,
                        parentId = dbNode.parent_id,
                        isCollapsed = dbNode.is_collapsed
                    )
                }
            }
        } catch (e: Exception) {
            _errorFlow.value = "Error refreshing nodes: ${e.message}"
            _nodeList.value = emptyList()
        }
    }

    suspend fun refreshEdges() = withContext(Dispatchers.IO) {
        val schemaMap = _schema.value?.edgeSchemas?.associateBy { it.id } ?: emptyMap()
        val nodeMap = _nodeList.value.associateBy { it.id }

        if (_schema.value == null) {
            _errorFlow.value = "Error refreshing edges: Schema was not loaded first."
            _edgeList.value = emptyList()
            return@withContext
        }

        try {
            // 1. Fetch All Edges
            val dbEdges = dbService.database.appDatabaseQueries.selectAllEdges().executeAsList()

            // 2. Fetch All Links (Efficient Bulk Fetch)
            val dbLinks = dbService.database.appDatabaseQueries.selectAllEdgeLinks().executeAsList()
            val linksByEdge = dbLinks.groupBy { it.edge_id }

            // 3. Reconstruct N-nary Display Items
            _edgeList.value = dbEdges.mapNotNull { dbEdge ->
                val schema = schemaMap[dbEdge.schema_id]
                val links = linksByEdge[dbEdge.id]

                if (schema == null || links == null) return@mapNotNull null

                // Gather all participating nodes with their roles
                val participants = links.mapNotNull { link ->
                    val node = nodeMap[link.node_id]
                    if (node != null) {
                        EdgeParticipant(node, link.role)
                    } else null
                }

                EdgeDisplayItem(
                    id = dbEdge.id,
                    label = schema.name,
                    properties = dbEdge.properties_json,
                    participatingNodes = participants,
                    schemaId = schema.id
                )
            }
        } catch (e: Exception) {
            _errorFlow.value = "Error refreshing edges: ${e.message}"
            _edgeList.value = emptyList()
        }
    }

    suspend fun refreshConstraints() = withContext(Dispatchers.IO) {
        try {
            val dbConstraints = dbService.database.appDatabaseQueries.selectAllConstraints().executeAsList()
            _constraints.value = dbConstraints.map { dbItem ->
                LayoutConstraintItem(
                    id = dbItem.id,
                    type = dbItem.type,
                    nodeIds = dbItem.node_ids_json,
                    params = dbItem.params_json
                )
            }
        } catch (e: Exception) {
            _errorFlow.value = "Error refreshing constraints: ${e.message}"
            _constraints.value = emptyList()
        }
    }

    // --- Helper: Display Property Derivation ---
    private fun deriveDisplayProperty(
        content: NodeContent,
        schema: SchemaDefinitionItem?,
        fallbackLabel: String
    ): String {
        return when (content) {
            is NodeContent.MapContent -> {
                // Look up display key from schema config if it is a MapConfig
                val displayKey = (schema?.config as? SchemaConfig.MapConfig)
                    ?.properties?.find { it.isDisplayProperty }?.name

                if (displayKey != null) {
                    content.values[displayKey] ?: fallbackLabel
                } else {
                    fallbackLabel
                }
            }
            is NodeContent.TextContent -> {
                if (content.text.length > 50) content.text.take(50) + "..." else content.text
            }
            is NodeContent.ImageContent -> {
                content.caption?.takeIf { it.isNotBlank() } ?: "Image"
            }
            is NodeContent.CodeContent -> {
                val name = content.filename ?: "Snippet"
                "$name (${content.language})"
            }
            is NodeContent.ListContent -> {
                "${content.items.size} items"
            }
            is NodeContent.TaskListContent -> {
                val completed = content.items.count { it.isCompleted }
                "$completed/${content.items.size} tasks"
            }
            is NodeContent.SetContent -> {
                "${content.items.size} items"
            }
            is NodeContent.TableContent -> {
                "Table (${content.rows.size} rows)"
            }
            is NodeContent.DateTimestampContent -> {
                "Date: ${content.timestamp}"
            }
            is NodeContent.TagContent -> {
                content.name
            }
        }
    }


    // --- Compound Nodes ---

    fun createCompoundNode(label: String, childrenIds: List<Long>) {
        repositoryScope.launch {
            try {
                val schema = _schema.value?.nodeSchemas?.find {
                    it.name.equals("Group", ignoreCase = true) || it.name.equals("Compound", ignoreCase = true)
                }

                val content = NodeContent.MapContent(mapOf("name" to label))

                dbService.database.appDatabaseQueries.insertNode(
                    schema_id = schema?.id,
                    node_type = NodeType.MAP.name,
                    display_label = label,
                    content_json = content,
                    parent_id = null,
                    is_collapsed = false
                )

                val parentId = dbService.database.appDatabaseQueries.getLastInsertId().executeAsOne()

                childrenIds.forEach { childId ->
                    dbService.database.appDatabaseQueries.updateNodeParent(parentId, childId)
                }

                refreshNodes()

            } catch (e: Exception) {
                _errorFlow.value = "Error creating compound node: ${e.message}"
            }
        }
    }

    fun setNodeCollapsed(id: Long, collapsed: Boolean) {
        repositoryScope.launch {
            try {
                dbService.database.appDatabaseQueries.updateNodeCollapsed(collapsed, id)
                refreshNodes()
            } catch (e: Exception) {
                _errorFlow.value = "Error updating node collapse state: ${e.message}"
            }
        }
    }

    // --- Layout Constraints ---

    fun addConstraint(type: String, nodes: List<Long>, params: Map<String, Any>) {
        repositoryScope.launch {
            try {
                val paramsStringMap = params.mapValues { it.value.toString() }

                dbService.database.appDatabaseQueries.insertConstraint(
                    type = type,
                    node_ids_json = nodes,
                    params_json = paramsStringMap
                )
                refreshConstraints()
            } catch (e: Exception) {
                _errorFlow.value = "Error adding constraint: ${e.message}"
            }
        }
    }

    fun deleteConstraint(id: Long) {
        repositoryScope.launch {
            try {
                dbService.database.appDatabaseQueries.deleteConstraintById(id)
                refreshConstraints()
            } catch (e: Exception) {
                _errorFlow.value = "Error deleting constraint: ${e.message}"
            }
        }
    }

    // --- Pagination ---

    suspend fun getNodesPaginated(offset: Long, limit: Long): List<NodeDisplayItem> = withContext(Dispatchers.IO) {
        val schemaMap = _schema.value?.nodeSchemas?.associateBy { it.id } ?: emptyMap()
        if (_schema.value == null) return@withContext emptyList()

        return@withContext try {
            val dbNodes = dbService.database.appDatabaseQueries.getNodesPaginated(limit, offset).executeAsList()
            dbNodes.mapNotNull { dbNode ->
                val schemaId = dbNode.schema_id
                val nodeSchema = if (schemaId != null) schemaMap[schemaId] else null

                if (schemaId != null && nodeSchema == null) {
                    null
                } else {
                    val content = dbNode.content_json
                    val displayProp = deriveDisplayProperty(content, nodeSchema, dbNode.display_label)
                    val typeLabel = nodeSchema?.name ?: dbNode.node_type

                    NodeDisplayItem(
                        id = dbNode.id,
                        label = typeLabel,
                        displayProperty = displayProp,
                        schemaId = schemaId ?: -1L,
                        content = content,
                        parentId = dbNode.parent_id,
                        isCollapsed = dbNode.is_collapsed
                    )
                }
            }
        } catch (e: Exception) {
            _errorFlow.value = "Error fetching paginated nodes: ${e.message}"
            emptyList()
        }
    }

    suspend fun getEdgesPaginated(offset: Long, limit: Long): List<EdgeDisplayItem> = withContext(Dispatchers.IO) {
        val schemaMap = _schema.value?.edgeSchemas?.associateBy { it.id } ?: emptyMap()
        val nodeMap = _nodeList.value.associateBy { it.id }

        if (_schema.value == null) return@withContext emptyList()

        return@withContext try {
            val dbEdges = dbService.database.appDatabaseQueries.getEdgesPaginated(limit, offset).executeAsList()
            if (dbEdges.isEmpty()) return@withContext emptyList()

            val edgeIds = dbEdges.map { it.id }
            val dbLinks = dbService.database.appDatabaseQueries.selectLinksForEdgeIds(edgeIds).executeAsList()
            val linksByEdge = dbLinks.groupBy { it.edge_id }

            dbEdges.mapNotNull { dbEdge ->
                val schema = schemaMap[dbEdge.schema_id]
                val links = linksByEdge[dbEdge.id]

                if (schema == null || links == null) return@mapNotNull null

                val participants = links.mapNotNull { link ->
                    val node = nodeMap[link.node_id]
                    if (node != null) EdgeParticipant(node, link.role) else null
                }

                EdgeDisplayItem(
                    id = dbEdge.id,
                    label = schema.name,
                    properties = dbEdge.properties_json,
                    participatingNodes = participants,
                    schemaId = schema.id
                )
            }
        } catch (e: Exception) {
            _errorFlow.value = "Error fetching paginated edges: ${e.message}"
            emptyList()
        }
    }

    // --- CRUD Operations (Nodes/Edges/Schemas) ---

    fun getSchemaDependencyCount(schemaId: Long): Long {
        return try {
            val nodeCount = dbService.database.appDatabaseQueries.countNodesForSchema(schemaId).executeAsOne()
            val edgeCount = dbService.database.appDatabaseQueries.countEdgesForSchema(schemaId).executeAsOne()
            nodeCount + edgeCount
        } catch (e: Exception) {
            _errorFlow.value = "Error checking schema dependencies: ${e.message}"
            -1L
        }
    }

    fun deleteSchema(schemaId: Long) {
        repositoryScope.launch {
            try {
                dbService.database.appDatabaseQueries.deleteSchemaById(schemaId)
                refreshAll()
            } catch (e: Exception) {
                _errorFlow.value = "Error deleting schema: ${e.message}"
            }
        }
    }

    fun createNodeSchema(config: SchemaConfig, name: String) {
        repositoryScope.launch {
            try {
                dbService.database.appDatabaseQueries.insertSchema(
                    type = "NODE",
                    name = name,
                    config_json = config,
                    roles_json = emptyList()
                )
                refreshSchema()
            } catch (e: Exception) {
                _errorFlow.value = "Error creating node schema: ${e.message}"
            }
        }
    }

    fun createEdgeSchema(state: EdgeSchemaCreationState) {
        repositoryScope.launch {
            try {
                // Default Edge Schemas to MapConfig for their properties
                val config = SchemaConfig.MapConfig(state.properties)
                dbService.database.appDatabaseQueries.insertSchema(
                    type = "EDGE",
                    name = state.tableName,
                    config_json = config,
                    roles_json = state.roles
                )
                refreshSchema()
            } catch (e: Exception) {
                _errorFlow.value = "Error creating edge schema: ${e.message}"
            }
        }
    }

    fun updateNodeSchema(state: NodeSchemaEditState) {
        repositoryScope.launch {
            try {
                // Reconstruct config (legacy assumption: MapConfig)
                // If we support editing other configs later, this needs logic.
                val config = SchemaConfig.MapConfig(state.properties)

                dbService.database.appDatabaseQueries.updateSchema(
                    id = state.originalSchema.id,
                    name = state.currentName,
                    config_json = config,
                    roles_json = emptyList()
                )
                refreshSchema()
                refreshNodes()
            } catch (e: Exception) {
                _errorFlow.value = "Error updating node schema: ${e.message}"
            }
        }
    }

    fun updateEdgeSchema(state: EdgeSchemaEditState) {
        repositoryScope.launch {
            try {
                val config = SchemaConfig.MapConfig(state.properties)
                dbService.database.appDatabaseQueries.updateSchema(
                    id = state.originalSchema.id,
                    name = state.currentName,
                    config_json = config,
                    roles_json = state.roles
                )
                refreshSchema()
                refreshEdges()
            } catch (e: Exception) {
                _errorFlow.value = "Error updating edge schema: ${e.message}"
            }
        }
    }

    fun createNode(
        schemaId: Long?,
        nodeType: NodeType,
        displayLabel: String,
        content: NodeContent,
        parentId: Long? = null
    ) {
        repositoryScope.launch {
            try {
                dbService.database.appDatabaseQueries.insertNode(
                    schema_id = schemaId,
                    node_type = nodeType.name,
                    display_label = displayLabel,
                    content_json = content,
                    parent_id = parentId,
                    is_collapsed = false
                )
                refreshNodes()
            } catch (e: Exception) {
                _errorFlow.value = "Error creating node: ${e.message}"
            }
        }
    }

    // Overload for UI convenience (Schema-based Map nodes)
    fun createNode(state: NodeCreationState) {
        val schema = state.selectedSchema
        if (schema == null) return

        // Extract display key from the schema config
        val displayKey = (schema.config as? SchemaConfig.MapConfig)
            ?.properties?.firstOrNull { it.isDisplayProperty }?.name

        val displayLabel = state.properties[displayKey] ?: "Node"
        val content = NodeContent.MapContent(state.properties)

        createNode(
            schemaId = schema.id,
            nodeType = NodeType.MAP,
            displayLabel = displayLabel,
            content = content
        )
    }

    fun createEdge(state: EdgeCreationState) {
        repositoryScope.launch {
            if (state.selectedSchema == null || state.participants.isEmpty()) return@launch

            val validParticipants = state.participants.filter { it.node != null }
            if (validParticipants.size < 2) {
                _errorFlow.value = "Edge must have at least 2 participants."
                return@launch
            }

            try {
                dbService.database.transaction {
                    dbService.database.appDatabaseQueries.insertEdge(
                        schema_id = state.selectedSchema.id,
                        properties_json = state.properties
                    )
                    val edgeId = dbService.database.appDatabaseQueries.getLastInsertId().executeAsOne()

                    validParticipants.forEach { selection ->
                        dbService.database.appDatabaseQueries.insertEdgeLink(
                            edge_id = edgeId,
                            node_id = selection.node!!.id,
                            role = selection.role
                        )
                    }
                }
                refreshEdges()
            } catch (e: Exception) {
                _errorFlow.value = "Error creating edge: ${e.message}"
            }
        }
    }

    fun getNodeEditState(itemId: Long): NodeEditState? {
        val dbNode = dbService.database.appDatabaseQueries.selectNodeById(itemId).executeAsOneOrNull() ?: return null
        val schemaId = dbNode.schema_id ?: return null
        val schema = _schema.value?.nodeSchemas?.firstOrNull { it.id == schemaId } ?: return null

        val props = (dbNode.content_json as? NodeContent.MapContent)?.values ?: emptyMap()

        return NodeEditState(
            id = dbNode.id,
            nodeType = NodeType.MAP,
            schema = schema,
            properties = props
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    fun getEdgeEditState(item: EdgeDisplayItem): EdgeEditState? {
        val dbEdge = dbService.database.appDatabaseQueries.selectEdgeById(item.id).executeAsOneOrNull() ?: return null
        val schema = _schema.value?.edgeSchemas?.firstOrNull { it.id == dbEdge.schema_id } ?: return null

        val participants = item.participatingNodes.map { part ->
            ParticipantSelection(
                id = Uuid.random().toString(),
                role = part.role ?: "",
                node = part.node
            )
        }

        return EdgeEditState(
            id = dbEdge.id,
            schema = schema,
            participants = participants,
            properties = dbEdge.properties_json
        )
    }

    fun updateNode(state: NodeEditState) {
        repositoryScope.launch {
            try {
                val displayLabel = if (state.nodeType == NodeType.MAP && state.schema != null) {
                    val displayKey = (state.schema.config as? SchemaConfig.MapConfig)
                        ?.properties?.firstOrNull { it.isDisplayProperty }?.name
                    state.properties[displayKey] ?: "Node ${state.id}"
                } else {
                    when(state.nodeType) {
                        NodeType.HEADING -> state.textContent.take(30)
                        NodeType.IMAGE -> state.imageCaption.ifBlank { "Image" }
                        else -> "Node ${state.id}"
                    }
                }

                val content = when(state.nodeType) {
                    NodeType.MAP -> NodeContent.MapContent(state.properties)
                    NodeType.HEADING -> NodeContent.TextContent(state.textContent)
                    NodeType.IMAGE -> NodeContent.ImageContent(state.imagePath ?: "", state.imageCaption)
                    else -> NodeContent.MapContent(emptyMap())
                }

                dbService.database.appDatabaseQueries.updateNodeProperties(
                    display_label = displayLabel,
                    content_json = content,
                    id = state.id
                )

                refreshNodes()

            } catch (e: Exception) {
                _errorFlow.value = "Error updating node: ${e.message}"
            }
        }
    }

    fun updateEdge(state: EdgeEditState) {
        repositoryScope.launch {
            try {
                dbService.database.appDatabaseQueries.updateEdgeProperties(
                    properties_json = state.properties,
                    id = state.id
                )
                refreshEdges()
            } catch (e: Exception) {
                _errorFlow.value = "Error updating edge: ${e.message}"
            }
        }
    }

    fun deleteNode(itemId: Long) {
        repositoryScope.launch {
            try {
                dbService.database.appDatabaseQueries.deleteNodeById(itemId)
                _nodeList.update { it.filterNot { node -> node.id == itemId } }
                refreshEdges()
            } catch (e: Exception) {
                _errorFlow.value = "Error deleting node: ${e.message}"
            }
        }
    }

    fun deleteEdge(itemId: Long) {
        repositoryScope.launch {
            try {
                dbService.database.appDatabaseQueries.deleteEdgeById(itemId)
                _edgeList.update { it.filterNot { edge -> edge.id == itemId } }
            } catch (e: Exception) {
                _errorFlow.value = "Error deleting edge: ${e.message}"
            }
        }
    }
}