package com.tau.nexusnote.datamodels

/**
 * Represents the entire state of the "Edit" tab.
 */
sealed interface EditScreenState {
    data object None : EditScreenState
    data class CreateNode(val state: NodeCreationState) : EditScreenState
    data class CreateEdge(val state: EdgeCreationState) : EditScreenState
    data class CreateNodeSchema(val state: NodeSchemaCreationState) : EditScreenState
    data class CreateEdgeSchema(val state: EdgeSchemaCreationState) : EditScreenState
    data class EditNode(val state: NodeEditState) : EditScreenState
    data class EditEdge(val state: EdgeEditState) : EditScreenState
    data class EditNodeSchema(val state: NodeSchemaEditState) : EditScreenState
    data class EditEdgeSchema(val state: EdgeSchemaEditState) : EditScreenState
}

// --- Data class for Node Creation UI State ---
data class NodeCreationState(
    val schemas: List<SchemaDefinitionItem>, // All available NODE schemas

    // Selection
    val selectedNodeType: NodeType = NodeType.MAP,

    // Schema Mode Data
    val selectedSchema: SchemaDefinitionItem? = null,
    val properties: Map<String, String> = emptyMap(),

    // Text Mode Data (Heading, Note, ShortText, LongText)
    val textContent: String = "",

    // Image Mode Data
    val imagePath: String? = null,
    val imageCaption: String = "",

    // --- Draft State for Specialized Editors ---
    val tableHeaders: List<String> = emptyList(),
    val tableRows: List<List<String>> = emptyList(),

    val codeLanguage: String = "kotlin",
    val codeFilename: String = "",
    val codeContent: String = "",

    val listItems: List<String> = emptyList(), // For Ordered/Unordered/Set
    val taskListItems: List<TaskItem> = emptyList(),

    val tags: List<String> = emptyList(),
    val timestamp: Long = 0L,

    // Validation
    val validationError: String? = null
)

// --- Helper for Dynamic Edge Participants ---
data class ParticipantSelection(
    val id: String, // Unique ID for UI keys
    val role: String, // Matches RoleDefinition.name
    val node: NodeDisplayItem? = null
)

// --- Helper for Task List Editing ---
data class TaskItem(
    val text: String,
    val isCompleted: Boolean = false
)

// --- Data class for Edge Creation UI State ---
data class EdgeCreationState(
    val schemas: List<SchemaDefinitionItem>, // All available EDGE schemas
    val availableNodes: List<NodeDisplayItem>,
    val selectedSchema: SchemaDefinitionItem? = null,
    val participants: List<ParticipantSelection> = emptyList(),
    val properties: Map<String, String> = emptyMap()
)

// --- Data class for Node Schema Creation UI State ---
data class NodeSchemaCreationState(
    val tableName: String = "",
    val selectedNodeType: NodeType = NodeType.MAP,

    // Sub-Type Selections
    val textSchemaType: String = "Plain", // "Plain", "Heading", "Title"
    val listSchemaType: String = "Ordered", // "Ordered", "Unordered", "Task"

    // MAP Config Data
    val properties: List<SchemaProperty> = listOf(SchemaProperty("name", CodexPropertyDataTypes.TEXT, isDisplayProperty = true)),

    // TABLE Config Data
    val tableRowHeaderType: String = "None", // None, Numeric, Alpha
    val tableShowColumnHeaders: Boolean = true,
    val tableMaxRows: String = "", // String for easy editing

    // CODE Config Data
    val codeDefaultLanguage: String = "kotlin",
    val codeShowFilename: Boolean = false,

    // TITLE/HEADING Config Data
    val textCasing: String = "TitleCase", // TitleCase, SentenceCase
    val headingLevel: Float = 1f, // Slider 1..6

    // SHORT_TEXT Config Data
    val shortTextCharLimit: String = "140",

    // ORDERED_LIST Config Data
    val listIndicatorStyle: String = "1.",

    // Validation
    val tableNameError: String? = null,
    val propertyErrors: Map<Int, String?> = emptyMap()
)

// --- Data class for Edge Schema Creation UI State ---
data class EdgeSchemaCreationState(
    val tableName: String = "",
    val roles: List<RoleDefinition> = emptyList(),
    val properties: List<SchemaProperty> = emptyList(),
    val allNodeSchemas: List<SchemaDefinitionItem> = emptyList(),
    val tableNameError: String? = null,
    val propertyErrors: Map<Int, String?> = emptyMap(),
    val roleErrors: Map<Int, String?> = emptyMap()
)

// --- Data classes for Editing Instances ---

data class NodeEditState(
    val id: Long,
    val nodeType: NodeType,

    // Schema Data (nullable, used if nodeType == MAP)
    val schema: SchemaDefinitionItem? = null,
    val properties: Map<String, String> = emptyMap(),

    // Text Data
    val textContent: String = "",

    // Image Data
    val imagePath: String? = null,
    val imageCaption: String = "",

    // --- Draft State for Specialized Editors ---
    val tableHeaders: List<String> = emptyList(),
    val tableRows: List<List<String>> = emptyList(),

    val codeLanguage: String = "kotlin",
    val codeFilename: String = "",
    val codeContent: String = "",

    val listItems: List<String> = emptyList(),
    val taskListItems: List<TaskItem> = emptyList(),

    val tags: List<String> = emptyList(),
    val timestamp: Long = 0L,

    // Validation
    val validationError: String? = null
)

data class EdgeEditState(
    val id: Long,
    val schema: SchemaDefinitionItem,
    val participants: List<ParticipantSelection>,
    val properties: Map<String, String>
)

// --- Data classes for Editing Schemas ---

data class NodeSchemaEditState(
    val originalSchema: SchemaDefinitionItem,
    val currentName: String,
    val properties: List<SchemaProperty>,
    val currentNameError: String? = null,
    val propertyErrors: Map<Int, String?> = emptyMap()
)

data class EdgeSchemaEditState(
    val originalSchema: SchemaDefinitionItem,
    val currentName: String,
    val roles: List<RoleDefinition>,
    val properties: List<SchemaProperty>,
    val currentNameError: String? = null,
    val propertyErrors: Map<Int, String?> = emptyMap(),
    val roleErrors: Map<Int, String?> = emptyMap(),
    val allNodeSchemas: List<SchemaDefinitionItem> = emptyList()
)