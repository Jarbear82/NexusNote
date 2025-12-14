package com.tau.nexusnote

import com.tau.nexusnote.datamodels.EdgeCreationState
import com.tau.nexusnote.datamodels.EdgeDisplayItem
import com.tau.nexusnote.datamodels.EdgeEditState
import com.tau.nexusnote.datamodels.EdgeSchemaCreationState
import com.tau.nexusnote.datamodels.EdgeSchemaEditState
import com.tau.nexusnote.datamodels.LayoutConstraintItem
import com.tau.nexusnote.datamodels.NodeCreationState
import com.tau.nexusnote.datamodels.NodeDisplayItem
import com.tau.nexusnote.datamodels.NodeEditState
import com.tau.nexusnote.datamodels.NodeSchemaCreationState
import com.tau.nexusnote.datamodels.NodeSchemaEditState
import com.tau.nexusnote.datamodels.SchemaDefinitionItem
import com.tau.nexusnote.codex.schema.SchemaData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Centralizes all database logic and state for an open Codex.
 * This class owns the database connection and the primary data flows.
 */
class CodexRepository(
    private val dbService: com.tau.nexusnote.SqliteDbService,
    private val repositoryScope: CoroutineScope
) {

    // --- Central State Flows ---
    private val _schema = MutableStateFlow<com.tau.nexusnote.codex.schema.SchemaData?>(null)
    val schema = _schema.asStateFlow()

    private val _nodeList = MutableStateFlow<List<com.tau.nexusnote.datamodels.NodeDisplayItem>>(emptyList())
    val nodeList = _nodeList.asStateFlow()

    private val _edgeList = MutableStateFlow<List<com.tau.nexusnote.datamodels.EdgeDisplayItem>>(emptyList())
    val edgeList = _edgeList.asStateFlow()

    private val _constraints = MutableStateFlow<List<com.tau.nexusnote.datamodels.LayoutConstraintItem>>(emptyList())
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
                val properties = dbSchema.properties_json

                if (dbSchema.type == "NODE") {
                    nodeSchemas.add(
                        SchemaDefinitionItem(dbSchema.id, dbSchema.type, dbSchema.name, properties, null)
                    )
                } else if (dbSchema.type == "EDGE") {
                    val connections = dbSchema.connections_json
                    edgeSchemas.add(
                        SchemaDefinitionItem(dbSchema.id, dbSchema.type, dbSchema.name, properties, connections)
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
                val nodeSchema = schemaMap[dbNode.schema_id]
                if (nodeSchema == null) {
                    println("Warning: Found node with unknown schema ID ${dbNode.schema_id}")
                    null
                } else {
                    NodeDisplayItem(
                        id = dbNode.id,
                        label = nodeSchema.name,
                        displayProperty = dbNode.display_label,
                        schemaId = nodeSchema.id,
                        parentId = dbNode.parent_id,
                        isCollapsed = dbNode.is_collapsed ?: false
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
            val dbEdges = dbService.database.appDatabaseQueries.selectAllEdges().executeAsList()
            _edgeList.value = dbEdges.mapNotNull { dbEdge ->
                val schema = schemaMap[dbEdge.schema_id]
                val srcNode = nodeMap[dbEdge.from_node_id]
                val dstNode = nodeMap[dbEdge.to_node_id]

                if (schema == null || srcNode == null || dstNode == null) {
                    println("Warning: Skipping edge ${dbEdge.id} due to missing schema or node link.")
                    null
                } else {
                    EdgeDisplayItem(dbEdge.id, schema.name, srcNode, dstNode, schema.id)
                }
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

    // --- NEW: Compound Node Support ---

    fun createCompoundNode(label: String, childrenIds: List<Long>) {
        repositoryScope.launch {
            try {
                val schema = _schema.value?.nodeSchemas?.find {
                    it.name.equals("Group", ignoreCase = true) || it.name.equals("Compound", ignoreCase = true)
                } ?: _schema.value?.nodeSchemas?.firstOrNull()

                if (schema == null) {
                    _errorFlow.value = "No node schemas available to create compound node."
                    return@launch
                }

                dbService.database.appDatabaseQueries.insertNode(
                    schema_id = schema.id,
                    display_label = label,
                    properties_json = mapOf("name" to label),
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

    // --- NEW: Layout Constraints Support ---

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
        if (_schema.value == null) {
            return@withContext emptyList()
        }

        return@withContext try {
            val dbNodes = dbService.database.appDatabaseQueries.getNodesPaginated(limit, offset).executeAsList()
            dbNodes.mapNotNull { dbNode ->
                val nodeSchema = schemaMap[dbNode.schema_id]
                if (nodeSchema == null) {
                    null
                } else {
                    NodeDisplayItem(
                        id = dbNode.id,
                        label = nodeSchema.name,
                        displayProperty = dbNode.display_label,
                        schemaId = nodeSchema.id,
                        parentId = dbNode.parent_id,
                        isCollapsed = dbNode.is_collapsed ?: false
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

        if (_schema.value == null) {
            return@withContext emptyList()
        }

        return@withContext try {
            val dbEdges = dbService.database.appDatabaseQueries.getEdgesPaginated(limit, offset).executeAsList()
            dbEdges.mapNotNull { dbEdge ->
                val schema = schemaMap[dbEdge.schema_id]
                val srcNode = nodeMap[dbEdge.from_node_id]
                val dstNode = nodeMap[dbEdge.to_node_id]

                if (schema == null || srcNode == null || dstNode == null) {
                    null
                } else {
                    EdgeDisplayItem(dbEdge.id, schema.name, srcNode, dstNode, schema.id)
                }
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

    fun createNodeSchema(state: NodeSchemaCreationState) {
        repositoryScope.launch {
            try {
                dbService.database.appDatabaseQueries.insertSchema(
                    type = "NODE",
                    name = state.tableName,
                    properties_json = state.properties,
                    connections_json = emptyList()
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
                dbService.database.appDatabaseQueries.insertSchema(
                    type = "EDGE",
                    name = state.tableName,
                    properties_json = state.properties,
                    connections_json = state.connections
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
                dbService.database.appDatabaseQueries.updateSchema(
                    id = state.originalSchema.id,
                    name = state.currentName,
                    properties_json = state.properties,
                    connections_json = emptyList()
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
                dbService.database.appDatabaseQueries.updateSchema(
                    id = state.originalSchema.id,
                    name = state.currentName,
                    properties_json = state.properties,
                    connections_json = state.connections
                )
                refreshSchema()
                refreshEdges()
            } catch (e: Exception) {
                _errorFlow.value = "Error updating edge schema: ${e.message}"
            }
        }
    }

    fun createNode(state: NodeCreationState) {
        repositoryScope.launch {
            if (state.selectedSchema == null) return@launch
            try {
                val displayKey = state.selectedSchema.properties.firstOrNull { it.isDisplayProperty }?.name
                val displayLabel = state.properties[displayKey] ?: "Node"

                dbService.database.appDatabaseQueries.insertNode(
                    schema_id = state.selectedSchema.id,
                    display_label = displayLabel,
                    properties_json = state.properties,
                    parent_id = null,
                    is_collapsed = false
                )
                refreshNodes()
            } catch (e: Exception) {
                _errorFlow.value = "Error creating node: ${e.message}"
            }
        }
    }

    fun getNodeEditState(itemId: Long): NodeEditState? {
        val dbNode = dbService.database.appDatabaseQueries.selectNodeById(itemId).executeAsOneOrNull() ?: return null
        val schema = _schema.value?.nodeSchemas?.firstOrNull { it.id == dbNode.schema_id } ?: return null
        return NodeEditState(id = dbNode.id, schema = schema, properties = dbNode.properties_json)
    }

    fun updateNode(state: NodeEditState) {
        repositoryScope.launch {
            try {
                val displayKey = state.schema.properties.firstOrNull { it.isDisplayProperty }?.name
                val displayLabel = state.properties[displayKey] ?: "Node ${state.id}"

                dbService.database.appDatabaseQueries.updateNodeProperties(
                    id = state.id,
                    display_label = displayLabel,
                    properties_json = state.properties
                )
                val currentItem = _nodeList.value.find { it.id == state.id }
                val updatedItem = NodeDisplayItem(
                    id = state.id,
                    label = state.schema.name,
                    displayProperty = displayLabel,
                    schemaId = state.schema.id,
                    parentId = currentItem?.parentId,
                    isCollapsed = currentItem?.isCollapsed ?: false
                )

                _nodeList.update { currentList ->
                    currentList.map { node -> if (node.id == updatedItem.id) updatedItem else node }
                }
            } catch (e: Exception) {
                _errorFlow.value = "Error updating node: ${e.message}"
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

    fun createEdge(state: EdgeCreationState) {
        repositoryScope.launch {
            if (state.selectedSchema == null || state.src == null || state.dst == null) return@launch
            try {
                dbService.database.appDatabaseQueries.insertEdge(
                    schema_id = state.selectedSchema.id,
                    from_node_id = state.src.id,
                    to_node_id = state.dst.id,
                    properties_json = state.properties
                )
                refreshEdges()
            } catch (e: Exception) {
                _errorFlow.value = "Error creating edge: ${e.message}"
            }
        }
    }

    fun getEdgeEditState(item: EdgeDisplayItem): EdgeEditState? {
        val dbEdge = dbService.database.appDatabaseQueries.selectEdgeById(item.id).executeAsOneOrNull() ?: return null
        val schema = _schema.value?.edgeSchemas?.firstOrNull { it.id == dbEdge.schema_id } ?: return null
        return EdgeEditState(id = dbEdge.id, schema = schema, src = item.src, dst = item.dst, properties = dbEdge.properties_json)
    }

    fun updateEdge(state: EdgeEditState) {
        repositoryScope.launch {
            try {
                dbService.database.appDatabaseQueries.updateEdgeProperties(
                    id = state.id,
                    properties_json = state.properties
                )
            } catch (e: Exception) {
                _errorFlow.value = "Error updating edge: ${e.message}"
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