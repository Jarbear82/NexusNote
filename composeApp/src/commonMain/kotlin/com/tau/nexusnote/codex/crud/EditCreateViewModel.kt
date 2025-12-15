package com.tau.nexusnote.codex.crud

import com.tau.nexusnote.CodexRepository
import com.tau.nexusnote.codex.metadata.MetadataViewModel
import com.tau.nexusnote.codex.schema.SchemaViewModel
import com.tau.nexusnote.datamodels.*
import com.tau.nexusnote.utils.toPascalCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class EditCreateViewModel(
    private val repository: CodexRepository,
    private val viewModelScope: CoroutineScope,
    private val schemaViewModel: SchemaViewModel,
    private val metadataViewModel: MetadataViewModel,
) {

    private val _editScreenState = MutableStateFlow<EditScreenState>(EditScreenState.None)
    val editScreenState = _editScreenState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<Unit>(replay = 0)
    val navigationEventFlow = _navigationEvent.asSharedFlow()

    // --- Validation Helpers ---
    private fun isSchemaNameUnique(name: String, editingId: Long? = null): String? {
        if (name.isBlank()) return "Name cannot be blank."
        val allSchemas = (schemaViewModel.schema.value?.nodeSchemas ?: emptyList()) +
                (schemaViewModel.schema.value?.edgeSchemas ?: emptyList())
        val conflictingSchema = allSchemas.find { it.name.equals(name, ignoreCase = false) }
        return when {
            conflictingSchema == null -> null
            editingId != null && conflictingSchema.id == editingId -> null
            else -> "Name is already used by another schema."
        }
    }

    private fun validateProperty(index: Int, property: SchemaProperty, allProperties: List<SchemaProperty>): String? {
        if (property.name.isBlank()) return "Name cannot be blank."
        val conflict = allProperties.withIndex().find { (i, p) ->
            i != index && p.name.equals(property.name, ignoreCase = false)
        }
        return if (conflict != null) "Name is already used in this schema." else null
    }

    private fun validateRole(index: Int, role: RoleDefinition, allRoles: List<RoleDefinition>): String? {
        if (role.name.isBlank()) return "Role name cannot be blank."
        val conflict = allRoles.withIndex().find { (i, r) ->
            i != index && r.name.equals(role.name, ignoreCase = true)
        }
        return if (conflict != null) "Role name must be unique." else null
    }

    // --- Content Validation & Saving ---

    fun saveCurrentState() {
        val stateToSave = _editScreenState.value
        if (stateToSave is EditScreenState.None) return

        viewModelScope.launch {
            when (stateToSave) {
                is EditScreenState.CreateNode -> saveNodeCreation(stateToSave.state)
                is EditScreenState.EditNode -> saveNodeEdit(stateToSave.state)
                is EditScreenState.CreateEdge -> repository.createEdge(stateToSave.state)
                is EditScreenState.CreateNodeSchema -> saveNodeSchemaCreation(stateToSave.state)
                is EditScreenState.CreateEdgeSchema -> repository.createEdgeSchema(stateToSave.state)
                is EditScreenState.EditEdge -> repository.updateEdge(stateToSave.state)
                is EditScreenState.EditNodeSchema -> repository.updateNodeSchema(stateToSave.state)
                is EditScreenState.EditEdgeSchema -> repository.updateEdgeSchema(stateToSave.state)
                is EditScreenState.None -> {}
            }

            // Only navigate away if validation passed (check checks inside save methods)
            if (_editScreenState.value is EditScreenState.None) {
                metadataViewModel.clearSelectedItem()
                _navigationEvent.emit(Unit)
            }
        }
    }

    private fun saveNodeCreation(state: NodeCreationState) {
        val config = state.selectedSchema?.config
        val error = validateContent(state.selectedNodeType, config, state)

        if (error != null) {
            _editScreenState.update {
                if (it is EditScreenState.CreateNode) it.copy(state = it.state.copy(validationError = error)) else it
            }
            return
        }

        // Construct Content based on Type
        val content: NodeContent = buildNodeContent(state.selectedNodeType, state)

        // Construct Display Label
        val displayLabel = deriveDisplayLabel(state.selectedNodeType, config, content, state.properties)

        repository.createNode(
            schemaId = state.selectedSchema?.id,
            nodeType = state.selectedNodeType,
            displayLabel = displayLabel,
            content = content
        )
        cancelAllEditing()
    }

    private fun saveNodeEdit(state: NodeEditState) {
        val config = state.schema?.config
        // Re-use validation logic (mapping NodeEditState to NodeCreationState structure effectively)
        // For simplicity, we implement a parallel validation helper for EditState
        val error = validateEditContent(state.nodeType, config, state)

        if (error != null) {
            _editScreenState.update {
                if (it is EditScreenState.EditNode) it.copy(state = it.state.copy(validationError = error)) else it
            }
            return
        }

        val content = buildEditNodeContent(state.nodeType, state)
        val displayLabel = deriveDisplayLabel(state.nodeType, config, content, state.properties)

        // Ideally we refactor Repository to accept content/labels directly in update
        // but for now, we pass the state and assume Repository handles basic mapping.
        // NOTE: For full complex content support, the Repository updateNode logic
        // needs to be aligned with buildEditNodeContent.
        repository.updateNode(state)
        cancelAllEditing()
    }

    private fun validateContent(type: NodeType, config: SchemaConfig?, state: NodeCreationState): String? {
        return when (type) {
            NodeType.SHORT_TEXT -> {
                val limit = (config as? SchemaConfig.ShortTextConfig)?.charLimit ?: 140
                if (state.textContent.length > limit) "Text exceeds limit of $limit characters."
                else if (state.textContent.contains("\n")) "Short text cannot contain newlines."
                else null
            }
            NodeType.TABLE -> {
                val maxRows = (config as? SchemaConfig.TableConfig)?.maxRows
                if (maxRows != null && state.tableRows.size > maxRows) "Table exceeds maximum of $maxRows rows."
                else null
            }
            NodeType.SET -> {
                if (state.listItems.size != state.listItems.distinct().size) "Set entries must be unique."
                else null
            }
            NodeType.TAG -> {
                if (state.tags.any { !it.matches(Regex("^[a-zA-Z0-9_]+$")) }) "Tags must be alphanumeric (no spaces/symbols)."
                else null
            }
            else -> null
        }
    }

    private fun validateEditContent(type: NodeType, config: SchemaConfig?, state: NodeEditState): String? {
        // Parallel logic to above
        return when (type) {
            NodeType.SHORT_TEXT -> {
                val limit = (config as? SchemaConfig.ShortTextConfig)?.charLimit ?: 140
                if (state.textContent.length > limit) "Text exceeds limit of $limit characters."
                else if (state.textContent.contains("\n")) "Short text cannot contain newlines."
                else null
            }
            NodeType.TABLE -> {
                val maxRows = (config as? SchemaConfig.TableConfig)?.maxRows
                if (maxRows != null && state.tableRows.size > maxRows) "Table exceeds maximum of $maxRows rows."
                else null
            }
            NodeType.SET -> {
                if (state.listItems.size != state.listItems.distinct().size) "Set entries must be unique."
                else null
            }
            NodeType.TAG -> {
                if (state.tags.any { !it.matches(Regex("^[a-zA-Z0-9_]+$")) }) "Tags must be alphanumeric (no spaces/symbols)."
                else null
            }
            else -> null
        }
    }

    private fun buildNodeContent(type: NodeType, state: NodeCreationState): NodeContent {
        return when (type) {
            NodeType.MAP -> NodeContent.MapContent(state.properties)
            NodeType.TABLE -> NodeContent.TableContent(state.tableHeaders, state.tableRows)
            NodeType.CODE_BLOCK -> NodeContent.CodeContent(state.codeContent, state.codeLanguage, state.codeFilename)
            NodeType.TITLE, NodeType.HEADING -> {
                // Apply Casing if config exists
                val casing = (state.selectedSchema?.config as? SchemaConfig.HeadingConfig)?.casing
                    ?: (state.selectedSchema?.config as? SchemaConfig.TitleConfig)?.casing
                    ?: "None"
                val text = applyCasing(state.textContent, casing)
                NodeContent.TextContent(text)
            }
            NodeType.SHORT_TEXT, NodeType.LONG_TEXT -> NodeContent.TextContent(state.textContent)
            NodeType.ORDERED_LIST, NodeType.UNORDERED_LIST -> NodeContent.ListContent(state.listItems)
            NodeType.TASK_LIST -> NodeContent.TaskListContent(state.taskListItems)
            NodeType.SET -> NodeContent.SetContent(state.listItems.toSet())
            NodeType.TAG -> NodeContent.TagContent(state.tags.firstOrNull() ?: "untagged") // Simplified: One tag per node usually, or list? Schema implies one content.
            NodeType.IMAGE -> NodeContent.ImageContent(state.imagePath ?: "", state.imageCaption)
            NodeType.DATE_TIMESTAMP -> NodeContent.DateTimestampContent(state.timestamp)
        }
    }

    private fun buildEditNodeContent(type: NodeType, state: NodeEditState): NodeContent {
        return when (type) {
            NodeType.MAP -> NodeContent.MapContent(state.properties)
            NodeType.TABLE -> NodeContent.TableContent(state.tableHeaders, state.tableRows)
            NodeType.CODE_BLOCK -> NodeContent.CodeContent(state.codeContent, state.codeLanguage, state.codeFilename)
            NodeType.TITLE, NodeType.HEADING -> {
                val casing = (state.schema?.config as? SchemaConfig.HeadingConfig)?.casing
                    ?: (state.schema?.config as? SchemaConfig.TitleConfig)?.casing
                    ?: "None"
                val text = applyCasing(state.textContent, casing)
                NodeContent.TextContent(text)
            }
            NodeType.SHORT_TEXT, NodeType.LONG_TEXT -> NodeContent.TextContent(state.textContent)
            NodeType.ORDERED_LIST, NodeType.UNORDERED_LIST -> NodeContent.ListContent(state.listItems)
            NodeType.TASK_LIST -> NodeContent.TaskListContent(state.taskListItems)
            NodeType.SET -> NodeContent.SetContent(state.listItems.toSet())
            NodeType.TAG -> NodeContent.TagContent(state.tags.firstOrNull() ?: "untagged")
            NodeType.IMAGE -> NodeContent.ImageContent(state.imagePath ?: "", state.imageCaption)
            NodeType.DATE_TIMESTAMP -> NodeContent.DateTimestampContent(state.timestamp)
        }
    }

    private fun deriveDisplayLabel(type: NodeType, config: SchemaConfig?, content: NodeContent, properties: Map<String, String>): String {
        return when (type) {
            NodeType.MAP -> {
                val displayKey = (config as? SchemaConfig.MapConfig)?.properties?.firstOrNull { it.isDisplayProperty }?.name
                properties[displayKey] ?: "Node"
            }
            NodeType.TITLE, NodeType.HEADING, NodeType.SHORT_TEXT -> (content as? NodeContent.TextContent)?.text?.take(50) ?: "Text"
            NodeType.LONG_TEXT -> (content as? NodeContent.TextContent)?.text?.take(30) + "..."
            NodeType.TABLE -> "Table"
            NodeType.CODE_BLOCK -> (content as? NodeContent.CodeContent)?.filename ?: "Code Snippet"
            NodeType.IMAGE -> (content as? NodeContent.ImageContent)?.caption?.ifBlank { "Image" } ?: "Image"
            NodeType.TAG -> (content as? NodeContent.TagContent)?.name ?: "Tag"
            else -> type.name
        }
    }

    private fun applyCasing(text: String, casing: String): String {
        return when (casing) {
            "TitleCase" -> text.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            "UpperCase" -> text.uppercase()
            "LowerCase" -> text.lowercase()
            else -> text
        }
    }

    private fun saveNodeSchemaCreation(state: NodeSchemaCreationState) {
        val config = when (state.selectedNodeType) {
            NodeType.MAP -> SchemaConfig.MapConfig(state.properties)
            NodeType.TABLE -> SchemaConfig.TableConfig(
                rowHeaderType = state.tableRowHeaderType,
                showColumnHeaders = state.tableShowColumnHeaders,
                maxRows = state.tableMaxRows.toIntOrNull()
            )
            NodeType.CODE_BLOCK -> SchemaConfig.CodeBlockConfig(
                defaultLanguage = state.codeDefaultLanguage,
                showFilename = state.codeShowFilename
            )
            NodeType.TITLE -> SchemaConfig.TitleConfig(casing = state.textCasing)
            NodeType.HEADING -> SchemaConfig.HeadingConfig(
                level = state.headingLevel.toInt(),
                casing = state.textCasing
            )
            NodeType.SHORT_TEXT -> SchemaConfig.ShortTextConfig(
                charLimit = state.shortTextCharLimit.toIntOrNull() ?: 140
            )
            NodeType.LONG_TEXT -> SchemaConfig.LongTextConfig
            NodeType.ORDERED_LIST -> SchemaConfig.OrderedListConfig(
                indicatorStyle = state.listIndicatorStyle
            )
            NodeType.UNORDERED_LIST -> SchemaConfig.UnorderedListConfig
            NodeType.TASK_LIST -> SchemaConfig.TaskListConfig
            NodeType.SET -> SchemaConfig.SetConfig
            NodeType.TAG -> SchemaConfig.TagConfig
            NodeType.IMAGE -> SchemaConfig.ImageConfig
            NodeType.DATE_TIMESTAMP -> SchemaConfig.DateConfig()
        }
        repository.createNodeSchema(config, state.tableName)
    }

    // --- State Initialization Handlers ---

    fun initiateNodeCreation(schema: SchemaDefinitionItem? = null) {
        val nodeSchemas = schemaViewModel.schema.value?.nodeSchemas ?: emptyList()
        metadataViewModel.clearSelectedItem()

        // Determine Draft State Defaults
        val type = if (schema != null) {
            // Map schema config type to NodeType
            when(schema.config) {
                is SchemaConfig.MapConfig -> NodeType.MAP
                is SchemaConfig.TableConfig -> NodeType.TABLE
                is SchemaConfig.CodeBlockConfig -> NodeType.CODE_BLOCK
                is SchemaConfig.HeadingConfig -> NodeType.HEADING
                is SchemaConfig.TitleConfig -> NodeType.TITLE
                is SchemaConfig.ShortTextConfig -> NodeType.SHORT_TEXT
                is SchemaConfig.LongTextConfig -> NodeType.LONG_TEXT
                is SchemaConfig.OrderedListConfig -> NodeType.ORDERED_LIST
                is SchemaConfig.UnorderedListConfig -> NodeType.UNORDERED_LIST
                is SchemaConfig.TaskListConfig -> NodeType.TASK_LIST
                is SchemaConfig.SetConfig -> NodeType.SET
                is SchemaConfig.TagConfig -> NodeType.TAG
                is SchemaConfig.ImageConfig -> NodeType.IMAGE
                is SchemaConfig.DateConfig -> NodeType.DATE_TIMESTAMP
            }
        } else NodeType.MAP // Default fallback

        // Initialize table if needed
        val tableHeaders = if (schema?.config is SchemaConfig.TableConfig) {
            (schema.config as SchemaConfig.TableConfig).predefinedColumnHeaders.toMutableList()
        } else mutableListOf("Col 1", "Col 2")
        val tableRows = mutableListOf(List(tableHeaders.size) { "" })

        _editScreenState.value = EditScreenState.CreateNode(
            NodeCreationState(
                schemas = nodeSchemas,
                selectedSchema = schema,
                selectedNodeType = type,
                tableHeaders = tableHeaders,
                tableRows = tableRows,
                listItems = listOf("") // One empty item to start
            )
        )
    }

    fun initiateNodeSchemaCreation() {
        _editScreenState.value = EditScreenState.CreateNodeSchema(NodeSchemaCreationState())
    }

    fun initiateNodeEdit(item: NodeDisplayItem) {
        viewModelScope.launch {
            val schema = schemaViewModel.schema.value?.nodeSchemas?.find { it.id == item.schemaId }
            val type = when(item.content) {
                is NodeContent.MapContent -> NodeType.MAP
                is NodeContent.TableContent -> NodeType.TABLE
                is NodeContent.CodeContent -> NodeType.CODE_BLOCK
                is NodeContent.TextContent -> {
                    // Refine text type based on Schema or heuristics
                    if (schema?.config is SchemaConfig.HeadingConfig) NodeType.HEADING
                    else if (schema?.config is SchemaConfig.TitleConfig) NodeType.TITLE
                    else if (schema?.config is SchemaConfig.ShortTextConfig) NodeType.SHORT_TEXT
                    else if (schema?.config is SchemaConfig.LongTextConfig) NodeType.LONG_TEXT
                    else NodeType.LONG_TEXT
                }
                is NodeContent.ListContent -> {
                    if (schema?.config is SchemaConfig.OrderedListConfig) NodeType.ORDERED_LIST
                    else if (schema?.config is SchemaConfig.SetConfig) NodeType.SET
                    else NodeType.UNORDERED_LIST
                }
                is NodeContent.TaskListContent -> NodeType.TASK_LIST
                is NodeContent.SetContent -> NodeType.SET
                is NodeContent.TagContent -> NodeType.TAG
                is NodeContent.ImageContent -> NodeType.IMAGE
                is NodeContent.DateTimestampContent -> NodeType.DATE_TIMESTAMP
            }

            val editState = NodeEditState(
                id = item.id,
                nodeType = type,
                schema = schema,
                properties = (item.content as? NodeContent.MapContent)?.values ?: emptyMap(),
                textContent = (item.content as? NodeContent.TextContent)?.text ?: "",
                imagePath = (item.content as? NodeContent.ImageContent)?.uri,
                imageCaption = (item.content as? NodeContent.ImageContent)?.caption ?: "",

                // Load specific content into draft fields
                tableHeaders = (item.content as? NodeContent.TableContent)?.headers ?: emptyList(),
                tableRows = (item.content as? NodeContent.TableContent)?.rows ?: emptyList(),
                codeContent = (item.content as? NodeContent.CodeContent)?.code ?: "",
                codeLanguage = (item.content as? NodeContent.CodeContent)?.language ?: "kotlin",
                codeFilename = (item.content as? NodeContent.CodeContent)?.filename ?: "",
                listItems = (item.content as? NodeContent.ListContent)?.items ?: (item.content as? NodeContent.SetContent)?.items?.toList() ?: emptyList(),
                taskListItems = (item.content as? NodeContent.TaskListContent)?.items ?: emptyList(),
                tags = listOfNotNull((item.content as? NodeContent.TagContent)?.name),
                timestamp = (item.content as? NodeContent.DateTimestampContent)?.timestamp ?: 0L
            )
            _editScreenState.value = EditScreenState.EditNode(editState)
        }
    }

    // --- Specific Draft Updaters ---

    fun cancelAllEditing() {
        _editScreenState.value = EditScreenState.None
    }

    // MAP
    fun updateNodeCreationProperty(key: String, value: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNode) return@update current
            val newProperties = current.state.properties.toMutableMap().apply { this[key] = value }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }
    fun updateNodeEditProperty(key: String, value: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNode) return@update current
            val newProperties = current.state.properties.toMutableMap().apply { this[key] = value }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // TEXT
    fun updateNodeCreationText(text: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNode) return@update current
            current.copy(state = current.state.copy(textContent = text))
        }
    }
    fun updateNodeEditText(text: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNode) return@update current
            current.copy(state = current.state.copy(textContent = text))
        }
    }

    // IMAGE
    fun updateNodeCreationImage(path: String?, caption: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNode) return@update current
            current.copy(state = current.state.copy(imagePath = path, imageCaption = caption))
        }
    }
    fun updateNodeEditImage(path: String?, caption: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNode) return@update current
            current.copy(state = current.state.copy(imagePath = path, imageCaption = caption))
        }
    }

    // TABLE
    fun updateTableData(rowIndex: Int, colIndex: Int, value: String, isCreation: Boolean) {
        _editScreenState.update { current ->
            if (isCreation && current is EditScreenState.CreateNode) {
                val newRows = current.state.tableRows.map { it.toMutableList() }.toMutableList()
                if (rowIndex in newRows.indices && colIndex in newRows[rowIndex].indices) {
                    newRows[rowIndex][colIndex] = value
                }
                current.copy(state = current.state.copy(tableRows = newRows))
            } else if (!isCreation && current is EditScreenState.EditNode) {
                val newRows = current.state.tableRows.map { it.toMutableList() }.toMutableList()
                if (rowIndex in newRows.indices && colIndex in newRows[rowIndex].indices) {
                    newRows[rowIndex][colIndex] = value
                }
                current.copy(state = current.state.copy(tableRows = newRows))
            } else current
        }
    }
    fun updateTableHeader(colIndex: Int, value: String, isCreation: Boolean) {
        _editScreenState.update { current ->
            if (isCreation && current is EditScreenState.CreateNode) {
                val newHeaders = current.state.tableHeaders.toMutableList()
                if (colIndex in newHeaders.indices) newHeaders[colIndex] = value
                current.copy(state = current.state.copy(tableHeaders = newHeaders))
            } else if (!isCreation && current is EditScreenState.EditNode) {
                val newHeaders = current.state.tableHeaders.toMutableList()
                if (colIndex in newHeaders.indices) newHeaders[colIndex] = value
                current.copy(state = current.state.copy(tableHeaders = newHeaders))
            } else current
        }
    }
    fun addTableRow(isCreation: Boolean) {
        _editScreenState.update { current ->
            if (isCreation && current is EditScreenState.CreateNode) {
                val cols = current.state.tableHeaders.size
                val newRows = current.state.tableRows + listOf(List(cols) { "" })
                current.copy(state = current.state.copy(tableRows = newRows))
            } else if (!isCreation && current is EditScreenState.EditNode) {
                val cols = current.state.tableHeaders.size
                val newRows = current.state.tableRows + listOf(List(cols) { "" })
                current.copy(state = current.state.copy(tableRows = newRows))
            } else current
        }
    }
    fun addTableColumn(isCreation: Boolean) {
        _editScreenState.update { current ->
            if (isCreation && current is EditScreenState.CreateNode) {
                val newHeaders = current.state.tableHeaders + "Col ${current.state.tableHeaders.size + 1}"
                val newRows = current.state.tableRows.map { it + "" }
                current.copy(state = current.state.copy(tableHeaders = newHeaders, tableRows = newRows))
            } else if (!isCreation && current is EditScreenState.EditNode) {
                val newHeaders = current.state.tableHeaders + "Col ${current.state.tableHeaders.size + 1}"
                val newRows = current.state.tableRows.map { it + "" }
                current.copy(state = current.state.copy(tableHeaders = newHeaders, tableRows = newRows))
            } else current
        }
    }

    // CODE
    fun updateCodeData(code: String, lang: String, file: String, isCreation: Boolean) {
        _editScreenState.update { current ->
            if (isCreation && current is EditScreenState.CreateNode) {
                current.copy(state = current.state.copy(codeContent = code, codeLanguage = lang, codeFilename = file))
            } else if (!isCreation && current is EditScreenState.EditNode) {
                current.copy(state = current.state.copy(codeContent = code, codeLanguage = lang, codeFilename = file))
            } else current
        }
    }

    // LIST
    fun updateListItem(index: Int, value: String, isCreation: Boolean) {
        _editScreenState.update { current ->
            if (isCreation && current is EditScreenState.CreateNode) {
                val newList = current.state.listItems.toMutableList()
                if (index in newList.indices) newList[index] = value
                current.copy(state = current.state.copy(listItems = newList))
            } else if (!isCreation && current is EditScreenState.EditNode) {
                val newList = current.state.listItems.toMutableList()
                if (index in newList.indices) newList[index] = value
                current.copy(state = current.state.copy(listItems = newList))
            } else current
        }
    }
    fun addListItem(isCreation: Boolean) {
        _editScreenState.update { current ->
            if (isCreation && current is EditScreenState.CreateNode) {
                current.copy(state = current.state.copy(listItems = current.state.listItems + ""))
            } else if (!isCreation && current is EditScreenState.EditNode) {
                current.copy(state = current.state.copy(listItems = current.state.listItems + ""))
            } else current
        }
    }
    fun removeListItem(index: Int, isCreation: Boolean) {
        _editScreenState.update { current ->
            if (isCreation && current is EditScreenState.CreateNode) {
                val newList = current.state.listItems.toMutableList().apply { removeAt(index) }
                current.copy(state = current.state.copy(listItems = newList))
            } else if (!isCreation && current is EditScreenState.EditNode) {
                val newList = current.state.listItems.toMutableList().apply { removeAt(index) }
                current.copy(state = current.state.copy(listItems = newList))
            } else current
        }
    }

    // TASK LIST
    fun updateTaskItem(index: Int, text: String, checked: Boolean, isCreation: Boolean) {
        _editScreenState.update { current ->
            if (isCreation && current is EditScreenState.CreateNode) {
                val newList = current.state.taskListItems.toMutableList()
                if (index in newList.indices) newList[index] = TaskItem(text, checked)
                current.copy(state = current.state.copy(taskListItems = newList))
            } else if (!isCreation && current is EditScreenState.EditNode) {
                val newList = current.state.taskListItems.toMutableList()
                if (index in newList.indices) newList[index] = TaskItem(text, checked)
                current.copy(state = current.state.copy(taskListItems = newList))
            } else current
        }
    }
    fun addTaskItem(isCreation: Boolean) {
        _editScreenState.update { current ->
            if (isCreation && current is EditScreenState.CreateNode) {
                current.copy(state = current.state.copy(taskListItems = current.state.taskListItems + TaskItem("", false)))
            } else if (!isCreation && current is EditScreenState.EditNode) {
                current.copy(state = current.state.copy(taskListItems = current.state.taskListItems + TaskItem("", false)))
            } else current
        }
    }
    fun removeTaskItem(index: Int, isCreation: Boolean) {
        _editScreenState.update { current ->
            if (isCreation && current is EditScreenState.CreateNode) {
                val newList = current.state.taskListItems.toMutableList().apply { removeAt(index) }
                current.copy(state = current.state.copy(taskListItems = newList))
            } else if (!isCreation && current is EditScreenState.EditNode) {
                val newList = current.state.taskListItems.toMutableList().apply { removeAt(index) }
                current.copy(state = current.state.copy(taskListItems = newList))
            } else current
        }
    }

    // TAGS
    fun addTag(tag: String, isCreation: Boolean) {
        _editScreenState.update { current ->
            if (isCreation && current is EditScreenState.CreateNode) {
                current.copy(state = current.state.copy(tags = current.state.tags + tag))
            } else if (!isCreation && current is EditScreenState.EditNode) {
                current.copy(state = current.state.copy(tags = current.state.tags + tag))
            } else current
        }
    }
    fun removeTag(tag: String, isCreation: Boolean) {
        _editScreenState.update { current ->
            if (isCreation && current is EditScreenState.CreateNode) {
                val newTags = current.state.tags - tag
                current.copy(state = current.state.copy(tags = newTags))
            } else if (!isCreation && current is EditScreenState.EditNode) {
                val newTags = current.state.tags - tag
                current.copy(state = current.state.copy(tags = newTags))
            } else current
        }
    }

    // Node Schema Creation/Edit Logic
    fun onNodeSchemaTableNameChange(name: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            val error = isSchemaNameUnique(name)
            current.copy(state = current.state.copy(tableName = name, tableNameError = error))
        }
    }
    fun onNodeSchemaTypeChange(type: NodeType) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            current.copy(state = current.state.copy(selectedNodeType = type))
        }
    }
    // ... Config updates from previous file version ...
    fun onNodeSchemaTableConfigChange(rowType: String, showColHeaders: Boolean, maxRows: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            current.copy(state = current.state.copy(tableRowHeaderType = rowType, tableShowColumnHeaders = showColHeaders, tableMaxRows = maxRows))
        }
    }
    fun onNodeSchemaCodeConfigChange(lang: String, showFile: Boolean) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            current.copy(state = current.state.copy(codeDefaultLanguage = lang, codeShowFilename = showFile))
        }
    }
    fun onNodeSchemaTextConfigChange(casing: String, headingLevel: Float, charLimit: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            current.copy(state = current.state.copy(textCasing = casing, headingLevel = headingLevel, shortTextCharLimit = charLimit))
        }
    }
    fun onNodeSchemaListConfigChange(indicator: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            current.copy(state = current.state.copy(listIndicatorStyle = indicator))
        }
    }
    fun onNodeSchemaPropertyChange(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            val newProperties = current.state.properties.toMutableList()
            newProperties[index] = property

            val error = validateProperty(index, property, newProperties)
            val newErrors = current.state.propertyErrors.toMutableMap()
            if (error != null) newErrors[index] = error else newErrors.remove(index)
            current.copy(state = current.state.copy(properties = newProperties, propertyErrors = newErrors))
        }
    }
    fun onAddNodeSchemaProperty(property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            current.copy(state = current.state.copy(properties = current.state.properties + property))
        }
    }
    fun onRemoveNodeSchemaProperty(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNodeSchema) return@update current
            val newProperties = current.state.properties.toMutableList()
            newProperties.removeAt(index)
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }

    // Edge Creation/Edit/Schema logic
    fun updateNodeCreationSchema(schemaNode: SchemaDefinitionItem) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNode) return@update current
            current.copy(state = current.state.copy(selectedSchema = schemaNode))
        }
    }
    fun updateNodeCreationType(type: NodeType) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateNode) return@update current
            current.copy(state = current.state.copy(selectedNodeType = type))
        }
    }
    fun initiateEdgeCreation() {
        val edgeSchemas = schemaViewModel.schema.value?.edgeSchemas ?: emptyList()
        metadataViewModel.clearSelectedItem()
        _editScreenState.value = EditScreenState.CreateEdge(EdgeCreationState(schemas = edgeSchemas, availableNodes = metadataViewModel.nodeList.value))
    }
    fun initiateEdgeCreation(schema: SchemaDefinitionItem) {
        initiateEdgeCreation()
        updateEdgeCreationSchema(schema)
    }
    fun updateEdgeCreationSchema(schemaEdge: SchemaDefinitionItem) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            val initialParticipants = mutableListOf<ParticipantSelection>()
            schemaEdge.roleDefinitions?.forEach { role ->
                if (role.cardinality is RoleCardinality.One) {
                    initialParticipants.add(ParticipantSelection(id = Uuid.random().toString(), role = role.name, node = null))
                }
            }
            current.copy(state = current.state.copy(selectedSchema = schemaEdge, participants = initialParticipants, properties = emptyMap()))
        }
    }
    fun addEdgeCreationParticipant(roleName: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            val newSelection = ParticipantSelection(id = Uuid.random().toString(), role = roleName, node = null)
            current.copy(state = current.state.copy(participants = current.state.participants + newSelection))
        }
    }
    fun removeEdgeCreationParticipant(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            val newList = current.state.participants.toMutableList().apply { removeAt(index) }
            current.copy(state = current.state.copy(participants = newList))
        }
    }
    fun updateEdgeCreationParticipantNode(index: Int, node: NodeDisplayItem) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            val newList = current.state.participants.toMutableList()
            if (index in newList.indices) newList[index] = newList[index].copy(node = node)
            current.copy(state = current.state.copy(participants = newList))
        }
    }
    fun updateEdgeCreationProperty(key: String, value: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdge) return@update current
            val newProperties = current.state.properties.toMutableMap().apply { this[key] = value }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }
    fun initiateEdgeSchemaCreation() {
        val nodeSchemas = schemaViewModel.schema.value?.nodeSchemas ?: emptyList()
        val defaultRoles = listOf(RoleDefinition("Source", emptyList(), RoleCardinality.One), RoleDefinition("Target", emptyList(), RoleCardinality.One))
        _editScreenState.value = EditScreenState.CreateEdgeSchema(EdgeSchemaCreationState(allNodeSchemas = nodeSchemas, roles = defaultRoles))
    }
    fun onEdgeSchemaTableNameChange(name: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            val error = isSchemaNameUnique(name)
            current.copy(state = current.state.copy(tableName = name, tableNameError = error))
        }
    }
    fun onAddEdgeSchemaRole(role: RoleDefinition) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            current.copy(state = current.state.copy(roles = current.state.roles + role))
        }
    }
    fun onRemoveEdgeSchemaRole(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            val newRoles = current.state.roles.toMutableList()
            newRoles.removeAt(index)
            current.copy(state = current.state.copy(roles = newRoles))
        }
    }
    fun onEdgeSchemaRoleChange(index: Int, role: RoleDefinition) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            val newRoles = current.state.roles.toMutableList()
            newRoles[index] = role

            val error = validateRole(index, role, newRoles)
            val newErrors = current.state.roleErrors.toMutableMap()
            if (error != null) newErrors[index] = error else newErrors.remove(index)
            current.copy(state = current.state.copy(roles = newRoles, roleErrors = newErrors))
        }
    }
    fun onEdgeSchemaPropertyChange(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            val newProperties = current.state.properties.toMutableList()
            newProperties[index] = property

            val error = validateProperty(index, property, newProperties)
            val newErrors = current.state.propertyErrors.toMutableMap()
            if (error != null) newErrors[index] = error else newErrors.remove(index)
            current.copy(state = current.state.copy(properties = newProperties, propertyErrors = newErrors))
        }
    }
    fun onAddEdgeSchemaProperty(property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            current.copy(state = current.state.copy(properties = current.state.properties + property))
        }
    }
    fun onRemoveEdgeSchemaProperty(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.CreateEdgeSchema) return@update current
            val newProperties = current.state.properties.toMutableList()
            newProperties.removeAt(index)
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }
    fun initiateEdgeEdit(item: EdgeDisplayItem) {
        viewModelScope.launch {
            val editState = repository.getEdgeEditState(item)
            if (editState != null) _editScreenState.value = EditScreenState.EditEdge(editState)
        }
    }
    fun updateEdgeEditProperty(key: String, value: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdge) return@update current
            val newProperties = current.state.properties.toMutableMap().apply { this[key] = value }
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }
    fun initiateNodeSchemaEdit(schema: SchemaDefinitionItem) {
        _editScreenState.value = EditScreenState.EditNodeSchema(NodeSchemaEditState(originalSchema = schema, currentName = schema.name, properties = schema.properties))
    }
    fun updateNodeSchemaEditLabel(label: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            val error = isSchemaNameUnique(label, current.state.originalSchema.id)
            current.copy(state = current.state.copy(currentName = label, currentNameError = error))
        }
    }
    fun updateNodeSchemaEditProperty(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            val newProperties = current.state.properties.toMutableList()
            newProperties[index] = property

            val error = validateProperty(index, property, newProperties)
            val newErrors = current.state.propertyErrors.toMutableMap()
            if (error != null) newErrors[index] = error else newErrors.remove(index)
            current.copy(state = current.state.copy(properties = newProperties, propertyErrors = newErrors))
        }
    }
    fun updateNodeSchemaEditAddProperty(property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            current.copy(state = current.state.copy(properties = current.state.properties + property))
        }
    }
    fun updateNodeSchemaEditRemoveProperty(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditNodeSchema) return@update current
            val newProperties = current.state.properties.toMutableList()
            newProperties.removeAt(index)
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }
    fun initiateEdgeSchemaEdit(schema: SchemaDefinitionItem) {
        val allNodeSchemas = schemaViewModel.schema.value?.nodeSchemas ?: emptyList()
        _editScreenState.value = EditScreenState.EditEdgeSchema(EdgeSchemaEditState(originalSchema = schema, currentName = schema.name, roles = schema.roleDefinitions ?: emptyList(), properties = schema.properties, allNodeSchemas = allNodeSchemas))
    }
    fun updateEdgeSchemaEditLabel(label: String) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val error = isSchemaNameUnique(label, current.state.originalSchema.id)
            current.copy(state = current.state.copy(currentName = label, currentNameError = error))
        }
    }
    fun updateEdgeSchemaEditRole(index: Int, role: RoleDefinition) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val newRoles = current.state.roles.toMutableList()
            newRoles[index] = role

            val error = validateRole(index, role, newRoles)
            val newErrors = current.state.roleErrors.toMutableMap()
            if (error != null) newErrors[index] = error else newErrors.remove(index)
            current.copy(state = current.state.copy(roles = newRoles, roleErrors = newErrors))
        }
    }
    fun updateEdgeSchemaEditAddRole(role: RoleDefinition) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            current.copy(state = current.state.copy(roles = current.state.roles + role))
        }
    }
    fun updateEdgeSchemaEditRemoveRole(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val newRoles = current.state.roles.toMutableList()
            newRoles.removeAt(index)
            current.copy(state = current.state.copy(roles = newRoles))
        }
    }
    fun updateEdgeSchemaEditProperty(index: Int, property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val newProperties = current.state.properties.toMutableList()
            newProperties[index] = property

            val error = validateProperty(index, property, newProperties)
            val newErrors = current.state.propertyErrors.toMutableMap()
            if (error != null) newErrors[index] = error else newErrors.remove(index)
            current.copy(state = current.state.copy(properties = newProperties, propertyErrors = newErrors))
        }
    }
    fun updateEdgeSchemaEditAddProperty(property: SchemaProperty) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            current.copy(state = current.state.copy(properties = current.state.properties + property))
        }
    }
    fun updateEdgeSchemaEditRemoveProperty(index: Int) {
        _editScreenState.update { current ->
            if (current !is EditScreenState.EditEdgeSchema) return@update current
            val newProperties = current.state.properties.toMutableList()
            newProperties.removeAt(index)
            current.copy(state = current.state.copy(properties = newProperties))
        }
    }
}