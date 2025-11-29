package com.tau.nexus_note.doc_parser

/**
 * Base interface for all nodes in the Document Graph.
 */
sealed interface DocumentNode {
    val schemaName: String
    fun toPropertiesMap(): Map<String, String>
}

// --- Structural Nodes ---

data class DocRootNode(
    val filepath: String,
    val name: String,
    val createdAt: Long = 0L,
    val frontmatterJson: String = "{}"
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_DOCUMENT
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_URI to filepath,
        StandardSchemas.PROP_NAME to name,
        StandardSchemas.PROP_CREATED_AT to createdAt.toString(),
        StandardSchemas.PROP_FRONTMATTER to frontmatterJson
    )
}

data class SectionNode(
    val title: String,
    val level: Int,
    // Transient ID used during parsing stack operations, not saved to JSON
    val dbId: Long? = null
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_SECTION
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_TITLE to title,
        StandardSchemas.PROP_LEVEL to level.toString()
    )
}

// --- Content Nodes ---

data class BlockNode(
    val content: String // This will contain templates like {{tag:123}}
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_BLOCK
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_CONTENT to content
    )
}

data class CodeBlockNode(
    val content: String,
    val language: String,
    val filename: String = "",
    val caption: String = ""
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_CODE_BLOCK
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_CONTENT to content,
        StandardSchemas.PROP_LANGUAGE to language,
        StandardSchemas.PROP_FILENAME to filename,
        StandardSchemas.PROP_CAPTION to caption
    )
}

data class CalloutNode(
    val type: String,
    val title: String,
    val isFoldable: Boolean = false
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_CALLOUT
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_CALLOUT_TYPE to type,
        StandardSchemas.PROP_TITLE to title,
        StandardSchemas.PROP_IS_FOLDABLE to isFoldable.toString()
    )
}

data class TableNode(
    val headersJson: String, // List<String>
    val dataJson: String,    // List<Map<String, String>>
    val caption: String = ""
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_TABLE
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_HEADERS to headersJson,
        StandardSchemas.PROP_DATA to dataJson,
        StandardSchemas.PROP_CAPTION to caption
    )
}

// --- NEW: Consolidated List Node ---

data class ListNode(
    val itemsJson: String, // List<String> - JSON Array
    val listType: String   // "unordered", "ordered", "task"
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_LIST
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_LIST_ITEMS to itemsJson,
        StandardSchemas.PROP_LIST_TYPE to listType
    )
}

// --- List Items (Legacy/Deprecated in Parser, kept for types) ---

data class OrderedListItemNode(
    val content: String,
    val number: Int
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_ORDERED_ITEM
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_CONTENT to content,
        StandardSchemas.PROP_NUMBER to number.toString()
    )
}

data class UnorderedListItemNode(
    val content: String,
    val bulletChar: String
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_UNORDERED_ITEM
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_CONTENT to content,
        StandardSchemas.PROP_BULLET_CHAR to bulletChar
    )
}

data class TaskListItemNode(
    val content: String,
    val isChecked: Boolean,
    val marker: String
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_TASK_ITEM
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_CONTENT to content,
        StandardSchemas.PROP_IS_CHECKED to isChecked.toString(),
        StandardSchemas.PROP_MARKER to marker
    )
}

// --- Concept Nodes (Ribs) ---

data class TagNode(
    val name: String,
    val nestedPath: String = ""
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_TAG
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_NAME to name,
        StandardSchemas.PROP_NESTED_PATH to nestedPath
    )
}

data class UrlNode(
    val address: String,
    val domain: String = ""
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_URL
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_ADDRESS to address,
        StandardSchemas.PROP_DOMAIN to domain
    )
}

data class AttachmentNode(
    val filename: String,
    val mimeType: String = "",
    val path: String = ""
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_ATTACHMENT
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_NAME to filename,
        StandardSchemas.PROP_MIME_TYPE to mimeType,
        StandardSchemas.PROP_URI to path
    )
}