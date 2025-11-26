package com.tau.nexus_note.doc_parser

/**
 * Base interface for all nodes in the Document Graph.
 * Used by Parsers to generate a standardized AST before DB insertion.
 */
sealed interface DocumentNode {
    val schemaName: String
    fun toPropertiesMap(): Map<String, String>
}

// --- Container Nodes ---

data class DocRootNode(
    val uri: String,
    val name: String,
    val extension: String,
    val frontmatterJson: String = "{}"
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_DOCUMENT
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_URI to uri,
        "name" to name,
        "extension" to extension,
        "frontmatter" to frontmatterJson
    )
}

data class SectionNode(
    val title: String,
    val level: Int,
    val dbId: Long? = null
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_SECTION
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_TITLE to title,
        StandardSchemas.PROP_LEVEL to level.toString()
    )
}

data class ListNode(
    val type: String, // "ordered", "bullet"
    val tight: Boolean,
    val items: List<String>
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_LIST
    override fun toPropertiesMap(): Map<String, String> {
        val baseMap = mapOf(
            StandardSchemas.PROP_LIST_TYPE to type,
            StandardSchemas.PROP_TIGHT to tight.toString()
        )

        return if (type == "ordered") {
            // Ordered lists: Use numbers as keys (1, 2, 3...)
            baseMap + items.mapIndexed { index, content ->
                (index + 1).toString() to content
            }.toMap()
        } else {
            // Unordered lists: Single text block with bullets
            val textBlock = items.joinToString("\n") { "- $it" }
            baseMap + (StandardSchemas.PROP_CONTENT to textBlock)
        }
    }
}

data class CalloutNode(
    val type: String, // "info", "warning", etc.
    val title: String,
    val isFoldable: Boolean = false,
    val isCollapsed: Boolean = false
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_CALLOUT
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_CALLOUT_TYPE to type,
        StandardSchemas.PROP_TITLE to title,
        StandardSchemas.PROP_IS_FOLDABLE to isFoldable.toString(),
        "isCollapsed" to isCollapsed.toString(),
        StandardSchemas.PROP_CONTENT to title
    )
}

data class TableNode(
    val alignmentsJson: String // List<String> serialized, e.g. ["left", "center"]
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_TABLE
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_ALIGNMENT to alignmentsJson
    )
}

// --- Content Leaf Nodes ---

data class ParagraphNode(
    val content: String
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_PARAGRAPH
    override fun toPropertiesMap() = mapOf(StandardSchemas.PROP_CONTENT to content)
}

data class HeadingNode(
    val content: String,
    val level: Int
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_HEADING
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_CONTENT to content,
        StandardSchemas.PROP_LEVEL to level.toString()
    )
}

data class CodeBlockNode(
    val content: String,
    val language: String = "",
    val fenceChar: String = "`"
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_CODE_BLOCK
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_CONTENT to content,
        StandardSchemas.PROP_LANGUAGE to language,
        "fenceChar" to fenceChar
    )
}

data class QuoteNode(
    val content: String // Raw content if not parsed into children
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_QUOTE
    override fun toPropertiesMap() = mapOf(StandardSchemas.PROP_CONTENT to content)
}

data class HTMLBlockNode(
    val content: String
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_HTML
    override fun toPropertiesMap() = mapOf(StandardSchemas.PROP_CONTENT to content)
}

data class ThematicBreakNode(
    val marker: String = "---"
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_THEMATIC_BREAK
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_MARKER to marker,
        StandardSchemas.PROP_CONTENT to marker
    )
}

data class TableCellNode(
    val content: String,
    val isHeader: Boolean,
    val row: Int,
    val col: Int
) : DocumentNode {
    override val schemaName = StandardSchemas.DOC_NODE_CELL
    override fun toPropertiesMap() = mapOf(
        StandardSchemas.PROP_CONTENT to content,
        "isHeader" to isHeader.toString(),
        StandardSchemas.PROP_ROW to row.toString(),
        StandardSchemas.PROP_COL to col.toString()
    )
}