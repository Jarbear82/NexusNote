package com.tau.nexus_note.doc_parser

/**
 * Base interface for all nodes in the Document Graph.
 */
interface DocumentNode {
    val schemaName: String
    fun toPropertiesMap(): Map<String, String>
}

// --- New: Dynamic Document Node ---
data class DynamicDocumentNode(
    override val schemaName: String,
    val properties: Map<String, String>
) : DocumentNode {
    override fun toPropertiesMap() = properties
}

// --- Structural Nodes ---

data class DocRootNode(
    val filepath: String,
    val name: String,
    val createdAt: Long = 0L,
    val frontmatterJson: String = "{}"
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_TITLE
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_URI to filepath,
        StandardSchemas.PROP_NAME to name,
        StandardSchemas.PROP_CREATED_AT to createdAt.toString(),
        StandardSchemas.PROP_FRONTMATTER to frontmatterJson,
        StandardSchemas.PROP_TITLE to name
    )
}

data class SectionNode(
    val title: String,
    val level: Int,
    val dbId: Long? = null
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_HEADING
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_TITLE to title,
        StandardSchemas.PROP_LEVEL to level.toString()
    )
}

// --- Content Nodes ---

data class BlockNode(
    val content: String
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_LONG_TEXT
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

data class ListNode(
    val itemsJson: String,
    val listType: String
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_UNORDERED_LIST
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_LIST_ITEMS to itemsJson,
        StandardSchemas.PROP_LIST_TYPE to listType
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
    val headersJson: String,
    val dataJson: String,
    val caption: String = ""
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_TABLE
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_HEADERS to headersJson,
        StandardSchemas.PROP_DATA to dataJson,
        StandardSchemas.PROP_CAPTION to caption
    )
}

// --- Concept Nodes ---

data class TagNode(
    val name: String
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_TAG
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_NAME to name
    )
}

// NEW: Dedicated Image Node (Visual)
data class ImageNode(
    val filename: String,
    val mimeType: String,
    val path: String,
    val altText: String = "",
    val width: Int = 0,
    val height: Int = 0
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_IMAGE
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_NAME to filename,
        StandardSchemas.PROP_MIME_TYPE to mimeType,
        StandardSchemas.PROP_URI to path,
        StandardSchemas.PROP_ALT_TEXT to altText,
        StandardSchemas.PROP_IMG_WIDTH to width.toString(),
        StandardSchemas.PROP_IMG_HEIGHT to height.toString()
    )
}

// Generic Attachment Node (Non-Visual)
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