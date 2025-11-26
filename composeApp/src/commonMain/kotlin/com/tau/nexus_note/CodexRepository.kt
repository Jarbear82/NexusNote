package com.tau.nexus_note

import com.tau.nexus_note.datamodels.ConnectionPair
import com.tau.nexus_note.datamodels.EdgeCreationState
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.EdgeEditState
import com.tau.nexus_note.datamodels.EdgeSchemaCreationState
import com.tau.nexus_note.datamodels.EdgeSchemaEditState
import com.tau.nexus_note.datamodels.NodeCreationState
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.datamodels.NodeEditState
import com.tau.nexus_note.datamodels.NodeSchemaCreationState
import com.tau.nexus_note.datamodels.NodeSchemaEditState
import com.tau.nexus_note.datamodels.SchemaDefinitionItem
import com.tau.nexus_note.datamodels.SchemaProperty
import com.tau.nexus_note.codex.schema.SchemaData
import com.tau.nexus_note.datamodels.CodexPropertyDataTypes
import com.tau.nexus_note.doc_parser.StandardSchemas
import com.tau.nexus_note.doc_parser.DocumentNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class CodexRepository(
    private val dbService: SqliteDbService,
    private val repositoryScope: CoroutineScope
) {
    // Expose the DB path for media handling
    val dbPath: String
        get() = dbService.filePath

    private val _schema = MutableStateFlow<SchemaData?>(null)
    val schema = _schema.asStateFlow()

    private val _nodeList = MutableStateFlow<List<NodeDisplayItem>>(emptyList())
    val nodeList = _nodeList.asStateFlow()

    private val _edgeList = MutableStateFlow<List<EdgeDisplayItem>>(emptyList())
    val edgeList = _edgeList.asStateFlow()

    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow = _errorFlow.asStateFlow()

    fun clearError() {
        _errorFlow.value = null
    }

    suspend fun refreshAll() {
        refreshSchema()
        refreshNodes()
        refreshEdges()
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
            _nodeList.value = emptyList()
            return@withContext
        }
        try {
            val dbNodes = dbService.database.appDatabaseQueries.selectAllNodes().executeAsList()
            _nodeList.value = dbNodes.mapNotNull { dbNode ->
                val nodeSchema = schemaMap[dbNode.schema_id]
                if (nodeSchema == null) {
                    null
                } else {
                    NodeDisplayItem(dbNode.id, nodeSchema.name, dbNode.display_label, nodeSchema.id)
                }
            }
        } catch (e: Exception) {
            _nodeList.value = emptyList()
        }
    }

    suspend fun refreshEdges() = withContext(Dispatchers.IO) {
        val schemaMap = _schema.value?.edgeSchemas?.associateBy { it.id } ?: emptyMap()
        val nodeMap = _nodeList.value.associateBy { it.id }
        if (_schema.value == null) {
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
                    null
                } else {
                    EdgeDisplayItem(dbEdge.id, schema.name, srcNode, dstNode, schema.id)
                }
            }
        } catch (e: Exception) {
            _edgeList.value = emptyList()
        }
    }

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
                    NodeDisplayItem(dbNode.id, nodeSchema.name, dbNode.display_label, nodeSchema.id)
                }
            }
        } catch (e: Exception) {
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
            emptyList()
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

    fun getSchemaDependencyCount(schemaId: Long): Long {
        return try {
            val nodeCount = dbService.database.appDatabaseQueries.countNodesForSchema(schemaId).executeAsOne()
            val edgeCount = dbService.database.appDatabaseQueries.countEdgesForSchema(schemaId).executeAsOne()
            nodeCount + edgeCount
        } catch (e: Exception) {
            -1L
        }
    }

    // --- ADDED: Helper to find existing node by label (e.g. for Tags) ---
    suspend fun findNodeByLabel(schemaName: String, label: String): Long? = withContext(Dispatchers.IO) {
        // Ensure schema is loaded
        if (_schema.value == null) refreshSchema()
        val schema = _schema.value?.nodeSchemas?.find { it.name == schemaName } ?: return@withContext null

        // Try finding in loaded memory list first
        val memoryMatch = _nodeList.value.find { it.schemaId == schema.id && it.displayProperty == label }
        if (memoryMatch != null) return@withContext memoryMatch.id

        // Fallback to DB scan (since we don't have a specific query yet)
        try {
            val dbNodes = dbService.database.appDatabaseQueries.selectAllNodes().executeAsList()
            val dbMatch = dbNodes.find { it.schema_id == schema.id && it.display_label == label }
            return@withContext dbMatch?.id
        } catch (e: Exception) {
            return@withContext null
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
                val rawLabel = state.properties[displayKey] ?: "Node"
                val displayLabel = rawLabel.take(20)

                dbService.database.appDatabaseQueries.insertNode(
                    schema_id = state.selectedSchema.id,
                    display_label = displayLabel,
                    properties_json = state.properties
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
        val properties = dbNode.properties_json
        return NodeEditState(id = dbNode.id, schema = schema, properties = properties)
    }

    fun updateNode(state: NodeEditState) {
        repositoryScope.launch {
            try {
                val displayKey = state.schema.properties.firstOrNull { it.isDisplayProperty }?.name
                val rawLabel = state.properties[displayKey] ?: "Node ${state.id}"
                val displayLabel = rawLabel.take(20)

                dbService.database.appDatabaseQueries.updateNodeProperties(
                    id = state.id,
                    display_label = displayLabel,
                    properties_json = state.properties
                )

                val updatedItem = NodeDisplayItem(
                    id = state.id,
                    label = state.schema.name,
                    displayProperty = displayLabel,
                    schemaId = state.schema.id
                )

                _nodeList.update { currentList ->
                    currentList.map { node ->
                        if (node.id == updatedItem.id) updatedItem else node
                    }
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
        val properties = dbEdge.properties_json
        return EdgeEditState(id = dbEdge.id, schema = schema, src = item.src, dst = item.dst, properties = properties)
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

    suspend fun saveInMemoryDbToFile(filePath: String) = withContext(Dispatchers.IO) {
        try {
            dbService.driver.execute(
                identifier = null,
                sql = "VACUUM INTO ?",
                parameters = 1
            ) {
                bindString(0, filePath)
            }
        } catch (e: Exception) {
            throw Exception("Failed to save database: ${e.message}")
        }
    }

    suspend fun bootstrapDocumentSchemas() = withContext(Dispatchers.IO) {
        val schemaDefinitions = mapOf(
            StandardSchemas.DOC_NODE_DOCUMENT to listOf(StandardSchemas.PROP_URI, StandardSchemas.PROP_NAME, StandardSchemas.PROP_CREATED_AT, StandardSchemas.PROP_FRONTMATTER),
            StandardSchemas.DOC_NODE_SECTION to listOf(StandardSchemas.PROP_TITLE, StandardSchemas.PROP_LEVEL),
            StandardSchemas.DOC_NODE_BLOCK to listOf(StandardSchemas.PROP_CONTENT),
            StandardSchemas.DOC_NODE_CODE_BLOCK to listOf(StandardSchemas.PROP_CONTENT, StandardSchemas.PROP_LANGUAGE, StandardSchemas.PROP_FILENAME, StandardSchemas.PROP_CAPTION),
            StandardSchemas.DOC_NODE_CALLOUT to listOf(StandardSchemas.PROP_CALLOUT_TYPE, StandardSchemas.PROP_TITLE, StandardSchemas.PROP_IS_FOLDABLE),StandardSchemas.DOC_NODE_TABLE to listOf(StandardSchemas.PROP_HEADERS, StandardSchemas.PROP_DATA, StandardSchemas.PROP_CAPTION),
            StandardSchemas.DOC_NODE_ORDERED_ITEM to listOf(StandardSchemas.PROP_CONTENT, StandardSchemas.PROP_NUMBER),
            StandardSchemas.DOC_NODE_UNORDERED_ITEM to listOf(StandardSchemas.PROP_CONTENT, StandardSchemas.PROP_BULLET_CHAR),
            StandardSchemas.DOC_NODE_TASK_ITEM to listOf(StandardSchemas.PROP_CONTENT, StandardSchemas.PROP_IS_CHECKED, StandardSchemas.PROP_MARKER),
            StandardSchemas.DOC_NODE_TAG to listOf(StandardSchemas.PROP_NAME, StandardSchemas.PROP_NESTED_PATH),
            StandardSchemas.DOC_NODE_URL to listOf(StandardSchemas.PROP_ADDRESS, StandardSchemas.PROP_DOMAIN),
            StandardSchemas.DOC_NODE_ATTACHMENT to listOf(StandardSchemas.PROP_NAME, StandardSchemas.PROP_MIME_TYPE, StandardSchemas.PROP_URI)
        )

        val edgeDefinitions = mapOf(
            StandardSchemas.EDGE_CONTAINS to listOf(SchemaProperty(StandardSchemas.PROP_ORDER, CodexPropertyDataTypes.NUMBER, true)),
            StandardSchemas.EDGE_REFERENCES to listOf(SchemaProperty("alias", CodexPropertyDataTypes.TEXT, true)),
            StandardSchemas.EDGE_TAGGED to emptyList(),
            StandardSchemas.EDGE_EMBEDS to emptyList()
        )

        // Helper to assign the correct UI type to standard properties
        fun getTypeForProp(name: String): CodexPropertyDataTypes {
            return when (name) {
                StandardSchemas.PROP_HEADERS -> CodexPropertyDataTypes.LIST
                StandardSchemas.PROP_FRONTMATTER -> CodexPropertyDataTypes.MAP
                StandardSchemas.PROP_IS_CHECKED, StandardSchemas.PROP_IS_FOLDABLE -> CodexPropertyDataTypes.BOOLEAN
                StandardSchemas.PROP_LEVEL, StandardSchemas.PROP_NUMBER, StandardSchemas.PROP_ORDER, StandardSchemas.PROP_CREATED_AT -> CodexPropertyDataTypes.NUMBER
                StandardSchemas.PROP_DATA -> CodexPropertyDataTypes.LONG_TEXT
                StandardSchemas.PROP_CONTENT -> CodexPropertyDataTypes.MARKDOWN
                else -> CodexPropertyDataTypes.TEXT
            }
        }

        try {
            schemaDefinitions.forEach { (name, props) ->
                dbService.database.appDatabaseQueries.insertSchema(
                    type = "NODE",
                    name = name,
                    properties_json = props.map {
                        val isDisplay = it == StandardSchemas.PROP_CONTENT || it == StandardSchemas.PROP_TITLE || it == StandardSchemas.PROP_NAME
                        SchemaProperty(it, getTypeForProp(it), isDisplay)
                    },
                    connections_json = emptyList()
                )
            }

            edgeDefinitions.forEach { (name, props) ->
                dbService.database.appDatabaseQueries.insertSchema(
                    type = "EDGE",
                    name = name,
                    properties_json = props,
                    connections_json = emptyList()
                )
            }
            refreshSchema()
        } catch (e: Exception) {
            println("Schema bootstrap warning: ${e.message}")
        }
    }

    suspend fun insertDocumentNode(node: DocumentNode): Long = withContext(Dispatchers.IO) {
        val schema = _schema.value?.nodeSchemas?.find { it.name == node.schemaName }
            ?: throw IllegalStateException("Schema '${node.schemaName}' not found. Did you run bootstrap?")

        val propsMap = node.toPropertiesMap()
        val displayKey = schema.properties.firstOrNull { it.isDisplayProperty }?.name
        val rawLabel = propsMap[displayKey] ?: node.schemaName
        val displayLabel = rawLabel.take(20)

        dbService.database.appDatabaseQueries.transactionWithResult {
            dbService.database.appDatabaseQueries.insertNode(
                schema_id = schema.id,
                display_label = displayLabel,
                properties_json = propsMap
            )
            dbService.database.appDatabaseQueries.lastInsertRowId().executeAsOne()
        }
    }

    suspend fun insertDocumentEdge(edgeName: String, fromId: Long, toId: Long, properties: Map<String, String> = emptyMap()) = withContext(Dispatchers.IO) {
        val schema = _schema.value?.edgeSchemas?.find { it.name == edgeName }
            ?: throw IllegalStateException("Edge Schema '$edgeName' not found.")

        dbService.database.appDatabaseQueries.insertEdge(
            schema_id = schema.id,
            from_node_id = fromId,
            to_node_id = toId,
            properties_json = properties
        )
    }
}