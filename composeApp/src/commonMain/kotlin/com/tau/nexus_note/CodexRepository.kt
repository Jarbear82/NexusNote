package com.tau.nexus_note

import com.tau.nexus_note.codex.schema.SchemaData
import com.tau.nexus_note.datamodels.*
import com.tau.nexus_note.doc_parser.DocumentNode
import com.tau.nexus_note.doc_parser.StandardSchemas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class CodexRepository(
    private val dbService: SqliteDbService,
    private val repositoryScope: CoroutineScope
) {
    val dbPath: String get() = dbService.filePath
    val mediaDirectoryPath: String get() = dbService.mediaDirectoryPath

    private val _schema = MutableStateFlow<SchemaData?>(null)
    val schema = _schema.asStateFlow()

    private val _nodeList = MutableStateFlow<List<NodeDisplayItem>>(emptyList())
    val nodeList = _nodeList.asStateFlow()

    private val _edgeList = MutableStateFlow<List<EdgeDisplayItem>>(emptyList())
    val edgeList = _edgeList.asStateFlow()

    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow = _errorFlow.asStateFlow()

    fun clearError() { _errorFlow.value = null }

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
                        SchemaDefinitionItem(
                            id = dbSchema.id, type = dbSchema.type, name = dbSchema.name,
                            properties = properties, connections = null,
                            nodeStyle = try { NodeStyle.valueOf(dbSchema.node_style) } catch (e: Exception) { NodeStyle.GENERIC }
                        )
                    )
                } else {
                    edgeSchemas.add(SchemaDefinitionItem(dbSchema.id, dbSchema.type, dbSchema.name, properties, dbSchema.connections_json))
                }
            }
            _schema.value = SchemaData(nodeSchemas, edgeSchemas)
        } catch (e: Exception) {
            _errorFlow.value = "Error refreshing schema: ${e.message}"
        }
    }

    private fun jsonToMap(json: JsonElement, schemaProperties: List<SchemaProperty>): Map<String, String> {
        return when (json) {
            is JsonObject -> {
                json.mapValues { (_, value) ->
                    if (value is JsonPrimitive && value.isString) value.content else value.toString()
                }
            }
            is JsonArray -> {
                val listProp = schemaProperties.find { it.type == CodexPropertyDataTypes.LIST }?.name ?: "items"
                mapOf(listProp to json.toString())
            }
            is JsonPrimitive -> {
                val contentProp = schemaProperties.find { it.type == CodexPropertyDataTypes.MARKDOWN || it.type == CodexPropertyDataTypes.TEXT || it.type == CodexPropertyDataTypes.LONG_TEXT }?.name ?: "content"
                mapOf(contentProp to json.content)
            }
        }
    }

    private fun mapToJson(properties: Map<String, String>, style: NodeStyle, schema: SchemaDefinitionItem): JsonElement {
        return when (style) {
            NodeStyle.SHORT_TEXT, NodeStyle.LONG_TEXT, NodeStyle.BLOCK -> {
                val contentProp = schema.properties.find { it.type == CodexPropertyDataTypes.MARKDOWN || it.type == CodexPropertyDataTypes.TEXT || it.type == CodexPropertyDataTypes.LONG_TEXT }?.name
                val content = if(contentProp != null) properties[contentProp] else null
                if (content != null && properties.size == 1) JsonPrimitive(content) else JsonObject(properties.mapValues { JsonPrimitive(it.value) })
            }
            NodeStyle.SET, NodeStyle.UNORDERED_LIST, NodeStyle.ORDERED_LIST, NodeStyle.LIST -> {
                val listProp = schema.properties.find { it.type == CodexPropertyDataTypes.LIST }?.name
                val listJson = if(listProp != null) properties[listProp] else null
                if (listJson != null && properties.size == 1) {
                    try { Json.parseToJsonElement(listJson) } catch (e: Exception) { JsonArray(emptyList()) }
                } else JsonObject(properties.mapValues { JsonPrimitive(it.value) })
            }
            else -> JsonObject(properties.mapValues { JsonPrimitive(it.value) })
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
                    val uiProperties = jsonToMap(dbNode.properties_json, nodeSchema.properties)
                    val backgroundProp = nodeSchema.properties.find { it.isBackgroundProperty }
                    val bgPath = if (backgroundProp != null) uiProperties[backgroundProp.name] else null

                    NodeDisplayItem(
                        id = dbNode.id,
                        label = nodeSchema.name,
                        displayProperty = dbNode.display_label,
                        schemaId = nodeSchema.id,
                        backgroundImagePath = bgPath,
                        properties = uiProperties,
                        style = nodeSchema.nodeStyle
                    )
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
        if (_schema.value == null) return@withContext emptyList()
        return@withContext try {
            val dbNodes = dbService.database.appDatabaseQueries.getNodesPaginated(limit, offset).executeAsList()
            dbNodes.mapNotNull { dbNode ->
                val nodeSchema = schemaMap[dbNode.schema_id]
                if (nodeSchema == null) null else {
                    val uiProperties = jsonToMap(dbNode.properties_json, nodeSchema.properties)
                    val backgroundProp = nodeSchema.properties.find { it.isBackgroundProperty }
                    val bgPath = if (backgroundProp != null) uiProperties[backgroundProp.name] else null
                    NodeDisplayItem(dbNode.id, nodeSchema.name, dbNode.display_label, nodeSchema.id, bgPath, uiProperties, nodeSchema.nodeStyle)
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getEdgesPaginated(offset: Long, limit: Long): List<EdgeDisplayItem> = withContext(Dispatchers.IO) {
        val schemaMap = _schema.value?.edgeSchemas?.associateBy { it.id } ?: emptyMap()
        val nodeMap = _nodeList.value.associateBy { it.id }
        if (_schema.value == null) return@withContext emptyList()
        return@withContext try {
            val dbEdges = dbService.database.appDatabaseQueries.getEdgesPaginated(limit, offset).executeAsList()
            dbEdges.mapNotNull { dbEdge ->
                val schema = schemaMap[dbEdge.schema_id]
                val srcNode = nodeMap[dbEdge.from_node_id]
                val dstNode = nodeMap[dbEdge.to_node_id]
                if (schema == null || srcNode == null || dstNode == null) null else EdgeDisplayItem(dbEdge.id, schema.name, srcNode, dstNode, schema.id)
            }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun findRootDocuments(): List<NodeDisplayItem> = withContext(Dispatchers.IO) {
        val allNodes = _nodeList.value
        val docNodes = allNodes.filter { it.label == StandardSchemas.DOC_NODE_TITLE }
        val containsEdges = _edgeList.value.filter { it.label == StandardSchemas.EDGE_CONTAINS }
        val containedNodeIds = containsEdges.map { it.dst.id }.toSet()
        docNodes.filter { it.id !in containedNodeIds }
    }

    suspend fun findOrphanedNodes(): List<NodeDisplayItem> = withContext(Dispatchers.IO) {
        val allNodes = _nodeList.value
        val containsEdges = _edgeList.value.filter { it.label == StandardSchemas.EDGE_CONTAINS }
        val containedNodeIds = containsEdges.map { it.dst.id }.toSet()
        allNodes.filter { it.label != StandardSchemas.DOC_NODE_TITLE && it.id !in containedNodeIds }
    }

    suspend fun getNodeEditState(itemId: Long): NodeEditState? = withContext(Dispatchers.IO) {
        val dbNode = dbService.database.appDatabaseQueries.selectNodeById(itemId).executeAsOneOrNull() ?: return@withContext null
        val schema = _schema.value?.nodeSchemas?.firstOrNull { it.id == dbNode.schema_id } ?: return@withContext null
        val properties = jsonToMap(dbNode.properties_json, schema.properties)
        NodeEditState(id = dbNode.id, schema = schema, properties = properties)
    }

    suspend fun getNodeById(itemId: Long): NodeEditState? = getNodeEditState(itemId)

    suspend fun getEdgeEditState(item: EdgeDisplayItem): EdgeEditState? = withContext(Dispatchers.IO) {
        val dbEdge = dbService.database.appDatabaseQueries.selectEdgeById(item.id).executeAsOneOrNull() ?: return@withContext null
        val schema = _schema.value?.edgeSchemas?.firstOrNull { it.id == dbEdge.schema_id } ?: return@withContext null
        val properties = jsonToMap(dbEdge.properties_json, schema.properties)
        EdgeEditState(id = dbEdge.id, schema = schema, src = item.src, dst = item.dst, properties = properties)
    }

    suspend fun getChildrenSorted(parentId: Long): List<NodeDisplayItem> = withContext(Dispatchers.IO) {
        val containsEdges = _edgeList.value.filter { it.label == StandardSchemas.EDGE_CONTAINS && it.src.id == parentId }
        val sortedEdges = containsEdges.sortedBy { edge ->
            val dbEdge = dbService.database.appDatabaseQueries.selectEdgeById(edge.id).executeAsOneOrNull()
            val edgeSchema = _schema.value?.edgeSchemas?.find { it.id == dbEdge?.schema_id }
            if(dbEdge != null && edgeSchema != null) {
                val props = jsonToMap(dbEdge.properties_json, edgeSchema.properties)
                props[StandardSchemas.PROP_ORDER]?.toIntOrNull() ?: Int.MAX_VALUE
            } else Int.MAX_VALUE
        }
        sortedEdges.map { it.dst }
    }

    fun deleteSchema(schemaId: Long) {
        repositoryScope.launch {
            try { dbService.database.appDatabaseQueries.deleteSchemaById(schemaId); refreshAll() } catch (e: Exception) { _errorFlow.value = "Error deleting schema: ${e.message}" }
        }
    }

    fun getSchemaDependencyCount(schemaId: Long): Long {
        return try {
            val nodeCount = dbService.database.appDatabaseQueries.countNodesForSchema(schemaId).executeAsOne()
            val edgeCount = dbService.database.appDatabaseQueries.countEdgesForSchema(schemaId).executeAsOne()
            nodeCount + edgeCount
        } catch (e: Exception) { -1L }
    }

    suspend fun findNodeByLabel(schemaName: String, label: String): Long? = withContext(Dispatchers.IO) {
        if (_schema.value == null) refreshSchema()
        val schema = _schema.value?.nodeSchemas?.find { it.name == schemaName } ?: return@withContext null
        val memoryMatch = _nodeList.value.find { it.schemaId == schema.id && it.displayProperty == label }
        if (memoryMatch != null) return@withContext memoryMatch.id
        try {
            val dbNodes = dbService.database.appDatabaseQueries.selectAllNodes().executeAsList()
            val dbMatch = dbNodes.find { it.schema_id == schema.id && it.display_label == label }
            return@withContext dbMatch?.id
        } catch (e: Exception) { return@withContext null }
    }

    fun createNodeSchema(state: NodeSchemaCreationState) {
        repositoryScope.launch {
            try {
                dbService.database.appDatabaseQueries.insertSchema(
                    type = "NODE", name = state.tableName, properties_json = state.properties,
                    connections_json = emptyList(), node_style = state.nodeStyle.name
                )
                refreshSchema()
            } catch (e: Exception) { _errorFlow.value = "Error creating node schema: ${e.message}" }
        }
    }

    fun createEdgeSchema(state: EdgeSchemaCreationState) {
        repositoryScope.launch {
            try {
                dbService.database.appDatabaseQueries.insertSchema(
                    type = "EDGE", name = state.tableName, properties_json = state.properties,
                    connections_json = state.connections, node_style = "GENERIC"
                )
                refreshSchema()
            } catch (e: Exception) { _errorFlow.value = "Error creating edge schema: ${e.message}" }
        }
    }

    fun updateNodeSchema(state: NodeSchemaEditState) {
        repositoryScope.launch {
            try {
                dbService.database.appDatabaseQueries.updateSchema(
                    id = state.originalSchema.id, name = state.currentName, properties_json = state.properties,
                    connections_json = emptyList(), node_style = state.currentNodeStyle.name
                )
                refreshSchema(); refreshNodes()
            } catch (e: Exception) { _errorFlow.value = "Error updating node schema: ${e.message}" }
        }
    }

    fun updateEdgeSchema(state: EdgeSchemaEditState) {
        repositoryScope.launch {
            try {
                dbService.database.appDatabaseQueries.updateSchema(
                    id = state.originalSchema.id, name = state.currentName, properties_json = state.properties,
                    connections_json = state.connections, node_style = "GENERIC"
                )
                refreshSchema(); refreshEdges()
            } catch (e: Exception) { _errorFlow.value = "Error updating edge schema: ${e.message}" }
        }
    }

    fun createNode(state: NodeCreationState) {
        repositoryScope.launch {
            if (state.selectedSchema == null) return@launch
            try {
                val displayKey = state.selectedSchema.properties.firstOrNull { it.isDisplayProperty }?.name
                val rawLabel = state.properties[displayKey] ?: "Node"
                val displayLabel = rawLabel.take(20)
                val dbJson = mapToJson(state.properties, state.selectedSchema.nodeStyle, state.selectedSchema)
                dbService.database.appDatabaseQueries.insertNode(
                    schema_id = state.selectedSchema.id,
                    display_label = displayLabel,
                    properties_json = dbJson
                )
                refreshNodes()
            } catch (e: Exception) { _errorFlow.value = "Error creating node: ${e.message}" }
        }
    }

    fun updateNode(state: NodeEditState) {
        repositoryScope.launch {
            try {
                val displayKey = state.schema.properties.firstOrNull { it.isDisplayProperty }?.name
                val rawLabel = state.properties[displayKey] ?: "Node ${state.id}"
                val displayLabel = rawLabel.take(20)
                val dbJson = mapToJson(state.properties, state.schema.nodeStyle, state.schema)
                dbService.database.appDatabaseQueries.updateNodeProperties(
                    display_label = displayLabel,
                    properties_json = dbJson,
                    id = state.id
                )

                val bgProp = state.schema.properties.find { it.isBackgroundProperty }
                val bgPath = if(bgProp != null) state.properties[bgProp.name] else null
                val updatedItem = NodeDisplayItem(state.id, state.schema.name, displayLabel, state.schema.id, bgPath, state.properties, state.schema.nodeStyle)
                _nodeList.update { currentList -> currentList.map { if (it.id == updatedItem.id) updatedItem else it } }
            } catch (e: Exception) { _errorFlow.value = "Error updating node: ${e.message}" }
        }
    }

    fun deleteNode(itemId: Long) {
        repositoryScope.launch {
            try { dbService.database.appDatabaseQueries.deleteNodeById(itemId); _nodeList.update { it.filterNot { n -> n.id == itemId } }; refreshEdges() } catch (e: Exception) { _errorFlow.value = "Error deleting node: ${e.message}" }
        }
    }

    fun createEdge(state: EdgeCreationState) {
        repositoryScope.launch {
            if (state.selectedSchema == null || state.src == null || state.dst == null) return@launch
            try {
                val jsonMap = state.properties.mapValues { JsonPrimitive(it.value) }
                val dbJson = JsonObject(jsonMap)
                dbService.database.appDatabaseQueries.insertEdge(state.selectedSchema.id, state.src.id, state.dst.id, dbJson)
                refreshEdges()
            } catch (e: Exception) { _errorFlow.value = "Error creating edge: ${e.message}" }
        }
    }

    fun updateEdge(state: EdgeEditState) {
        repositoryScope.launch {
            try {
                val jsonMap = state.properties.mapValues { JsonPrimitive(it.value) }
                val dbJson = JsonObject(jsonMap)
                dbService.database.appDatabaseQueries.updateEdgeProperties(dbJson, state.id)
            } catch (e: Exception) { _errorFlow.value = "Error updating edge: ${e.message}" }
        }
    }

    fun deleteEdge(itemId: Long) {
        repositoryScope.launch {
            try { dbService.database.appDatabaseQueries.deleteEdgeById(itemId); _edgeList.update { it.filterNot { e -> e.id == itemId } } } catch (e: Exception) { _errorFlow.value = "Error deleting edge: ${e.message}" }
        }
    }

    suspend fun saveInMemoryDbToFile(filePath: String) = withContext(Dispatchers.IO) {
        try {
            dbService.driver.execute(null, "VACUUM INTO ?", 1) { bindString(0, filePath) }
        } catch (e: Exception) { throw Exception("Failed to save database: ${e.message}") }
    }

    // --- BOOTSTRAP: Updated for Zig Types ---
    suspend fun bootstrapDocumentSchemas() = withContext(Dispatchers.IO) {
        val schemaDefinitions = mapOf(
            StandardSchemas.DOC_NODE_TITLE to Pair(listOf(StandardSchemas.PROP_TITLE, StandardSchemas.PROP_CREATED_AT, StandardSchemas.PROP_FRONTMATTER), NodeStyle.TITLE),
            StandardSchemas.DOC_NODE_HEADING to Pair(listOf(StandardSchemas.PROP_TITLE, StandardSchemas.PROP_LEVEL), NodeStyle.HEADING),
            StandardSchemas.DOC_NODE_SHORT_TEXT to Pair(listOf(StandardSchemas.PROP_CONTENT), NodeStyle.SHORT_TEXT),
            StandardSchemas.DOC_NODE_LONG_TEXT to Pair(listOf(StandardSchemas.PROP_CONTENT), NodeStyle.LONG_TEXT),
            StandardSchemas.DOC_NODE_CODE_BLOCK to Pair(listOf(StandardSchemas.PROP_CONTENT, StandardSchemas.PROP_LANGUAGE, StandardSchemas.PROP_FILENAME), NodeStyle.CODE_BLOCK),
            StandardSchemas.DOC_NODE_MAP to Pair(listOf(StandardSchemas.PROP_MAP_DATA), NodeStyle.MAP),
            StandardSchemas.DOC_NODE_SET to Pair(listOf(StandardSchemas.PROP_LIST_ITEMS), NodeStyle.SET),
            StandardSchemas.DOC_NODE_UNORDERED_LIST to Pair(listOf(StandardSchemas.PROP_LIST_ITEMS), NodeStyle.UNORDERED_LIST),
            StandardSchemas.DOC_NODE_ORDERED_LIST to Pair(listOf(StandardSchemas.PROP_LIST_ITEMS), NodeStyle.ORDERED_LIST),
            StandardSchemas.DOC_NODE_TAG to Pair(listOf(StandardSchemas.PROP_NAME), NodeStyle.TAG),
            StandardSchemas.DOC_NODE_TABLE to Pair(listOf(StandardSchemas.PROP_HEADERS, StandardSchemas.PROP_DATA, StandardSchemas.PROP_CAPTION), NodeStyle.TABLE),

            // NEW: Image Schema (Visual)
            StandardSchemas.DOC_NODE_IMAGE to Pair(
                listOf(
                    StandardSchemas.PROP_NAME,
                    StandardSchemas.PROP_URI,
                    StandardSchemas.PROP_ALT_TEXT,
                    StandardSchemas.PROP_MIME_TYPE,
                    StandardSchemas.PROP_IMG_WIDTH,
                    StandardSchemas.PROP_IMG_HEIGHT
                ),
                NodeStyle.IMAGE
            ),

            // UPDATED: Attachment Schema (Generic)
            StandardSchemas.DOC_NODE_ATTACHMENT to Pair(
                listOf(StandardSchemas.PROP_NAME, StandardSchemas.PROP_MIME_TYPE, StandardSchemas.PROP_URI),
                NodeStyle.ATTACHMENT
            )
        )

        val edgeDefinitions = mapOf(
            StandardSchemas.EDGE_CONTAINS to listOf(SchemaProperty(StandardSchemas.PROP_ORDER, CodexPropertyDataTypes.NUMBER, true)),
            StandardSchemas.EDGE_REFERENCES to listOf(SchemaProperty("alias", CodexPropertyDataTypes.TEXT, true)),
            StandardSchemas.EDGE_TAGGED to emptyList(),
            StandardSchemas.EDGE_EMBEDS to emptyList()
        )

        fun getTypeForProp(name: String): CodexPropertyDataTypes {
            return when (name) {
                StandardSchemas.PROP_LIST_ITEMS, StandardSchemas.PROP_HEADERS -> CodexPropertyDataTypes.LIST
                StandardSchemas.PROP_MAP_DATA, StandardSchemas.PROP_FRONTMATTER -> CodexPropertyDataTypes.MAP
                StandardSchemas.PROP_LEVEL, StandardSchemas.PROP_ORDER, StandardSchemas.PROP_CREATED_AT, StandardSchemas.PROP_IMG_WIDTH, StandardSchemas.PROP_IMG_HEIGHT -> CodexPropertyDataTypes.NUMBER
                StandardSchemas.PROP_CONTENT, StandardSchemas.PROP_DATA -> CodexPropertyDataTypes.MARKDOWN
                StandardSchemas.PROP_MIME_TYPE -> CodexPropertyDataTypes.TEXT
                StandardSchemas.PROP_URI -> CodexPropertyDataTypes.IMAGE // Or TEXT depending on how you want to edit it
                else -> CodexPropertyDataTypes.TEXT
            }
        }

        try {
            schemaDefinitions.forEach { (name, pair) ->
                dbService.database.appDatabaseQueries.insertSchema(
                    type = "NODE", name = name,
                    properties_json = pair.first.map {
                        SchemaProperty(it, getTypeForProp(it), isDisplayProperty = (it == StandardSchemas.PROP_TITLE || it == StandardSchemas.PROP_NAME || it == StandardSchemas.PROP_CONTENT))
                    },
                    connections_json = emptyList(), node_style = pair.second.name
                )
            }
            edgeDefinitions.forEach { (name, props) ->
                dbService.database.appDatabaseQueries.insertSchema(
                    type = "EDGE", name = name, properties_json = props,
                    connections_json = emptyList(), node_style = "GENERIC"
                )
            }
            refreshSchema()
        } catch (e: Exception) { println("Schema bootstrap warning: ${e.message}") }
    }

    suspend fun insertDocumentNode(node: DocumentNode): Long = withContext(Dispatchers.IO) {
        val schema = _schema.value?.nodeSchemas?.find { it.name == node.schemaName }
            ?: throw IllegalStateException("Schema '${node.schemaName}' not found. Did you run bootstrap?")

        val propsMap = node.toPropertiesMap()
        val displayKey = schema.properties.firstOrNull { it.isDisplayProperty }?.name
        val rawLabel = propsMap[displayKey] ?: node.schemaName
        val displayLabel = rawLabel.take(20)
        val dbJson = mapToJson(propsMap, schema.nodeStyle, schema)

        dbService.database.appDatabaseQueries.transactionWithResult {
            dbService.database.appDatabaseQueries.insertNode(schema.id, displayLabel, dbJson)
            dbService.database.appDatabaseQueries.lastInsertRowId().executeAsOne()
        }
    }

    suspend fun insertDocumentEdge(edgeName: String, fromId: Long, toId: Long, properties: Map<String, String> = emptyMap()) = withContext(Dispatchers.IO) {
        val schema = _schema.value?.edgeSchemas?.find { it.name == edgeName }
            ?: throw IllegalStateException("Edge Schema '$edgeName' not found.")
        val jsonMap = properties.mapValues { JsonPrimitive(it.value) }
        val dbJson = JsonObject(jsonMap)
        dbService.database.appDatabaseQueries.insertEdge(schema.id, fromId, toId, dbJson)
    }
}