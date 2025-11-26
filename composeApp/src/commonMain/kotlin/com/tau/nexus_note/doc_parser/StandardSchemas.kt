package com.tau.nexus_note.doc_parser

object StandardSchemas {
    // --- Node Types ---
    // Structural (Spine)
    const val DOC_NODE_DOCUMENT = "Document"
    const val DOC_NODE_SECTION = "Section"

    // Content (Spine Leaves)
    const val DOC_NODE_BLOCK = "Block" // Atomic unit of text
    const val DOC_NODE_CODE_BLOCK = "CodeBlock"
    const val DOC_NODE_CALLOUT = "Callout"
    const val DOC_NODE_TABLE = "Table"

    // List Items (Specific Containers)
    const val DOC_NODE_ORDERED_ITEM = "OrderedListItem"
    const val DOC_NODE_UNORDERED_ITEM = "UnorderedListItem"
    const val DOC_NODE_TASK_ITEM = "TaskListItem"

    // Concept Nodes (Rib Hubs)
    const val DOC_NODE_TAG = "Tag"
    const val DOC_NODE_ATTACHMENT = "Attachment"
    const val DOC_NODE_URL = "URL"

    // --- Edge Types ---
    const val EDGE_CONTAINS = "CONTAINS"       // Spine: Parent -> Child (Strict Hierarchy)
    const val EDGE_REFERENCES = "REFERENCES"   // Rib: Block -> Doc/Section/Block/URL (WikiLinks)
    const val EDGE_TAGGED = "TAGGED"           // Rib: Doc/Block -> Tag
    const val EDGE_EMBEDS = "EMBEDS"           // Rib: Block -> Attachment

    // --- Property Keys ---
    // Common
    const val PROP_CONTENT = "content"         // Templated text for Blocks
    const val PROP_NAME = "name"
    const val PROP_URI = "filepath"            // Changed from uri to match design
    const val PROP_CREATED_AT = "created_at"

    // Structural
    const val PROP_TITLE = "title"
    const val PROP_LEVEL = "level"             // Int 1-6
    const val PROP_ORDER = "order"             // Int (Edge Property for Spine)
    const val PROP_FRONTMATTER = "frontmatter" // Map

    // Content Specific
    const val PROP_LANGUAGE = "language"
    const val PROP_CAPTION = "caption"
    const val PROP_CALLOUT_TYPE = "type"       // info, warning
    const val PROP_IS_FOLDABLE = "is_foldable"

    // Table Specific
    const val PROP_HEADERS = "headers"         // List<String>
    const val PROP_DATA = "data"               // List<Map<String, String>> (Rows)

    // List Specific
    const val PROP_NUMBER = "number"           // Int
    const val PROP_BULLET_CHAR = "bullet_char" // -, *, +
    const val PROP_IS_CHECKED = "is_checked"   // Boolean
    const val PROP_MARKER = "marker"

    // Concept Specific
    const val PROP_NESTED_PATH = "nested_path" // for tags
    const val PROP_MIME_TYPE = "mime_type"
    const val PROP_ADDRESS = "address"
    const val PROP_DOMAIN = "domain"
}