package com.tau.nexus_note.doc_parser

object StandardSchemas {
    // --- New Zig-Aligned Node Types ---
    const val DOC_NODE_TITLE = "Title"
    const val DOC_NODE_HEADING = "Heading"
    const val DOC_NODE_SHORT_TEXT = "ShortText"
    const val DOC_NODE_LONG_TEXT = "LongText"
    const val DOC_NODE_CODE_BLOCK = "CodeBlock"
    const val DOC_NODE_MAP = "Map"
    const val DOC_NODE_SET = "Set"
    const val DOC_NODE_UNORDERED_LIST = "UnorderedList"
    const val DOC_NODE_ORDERED_LIST = "OrderedList"
    const val DOC_NODE_TAG = "Tag"
    const val DOC_NODE_TABLE = "Table" // Added missing TABLE

    // --- Legacy Constants (Mapped during parsing/bootstrapping) ---
    const val DOC_NODE_DOCUMENT = "Document" // -> Title
    const val DOC_NODE_SECTION = "Section"   // -> Heading
    const val DOC_NODE_BLOCK = "Block"       // -> LongText
    const val DOC_NODE_ATTACHMENT = "Attachment"
    const val DOC_NODE_URL = "URL"
    const val DOC_NODE_CALLOUT = "Callout"   // Re-added

    // Legacy List Item Nodes (for Exporter compatibility)
    const val DOC_NODE_ORDERED_ITEM = "OrderedListItem"
    const val DOC_NODE_UNORDERED_ITEM = "UnorderedListItem"
    const val DOC_NODE_TASK_ITEM = "TaskListItem"
    const val DOC_NODE_LIST = "List" // Legacy generic list

    // --- Edge Types ---
    const val EDGE_CONTAINS = "CONTAINS"
    const val EDGE_REFERENCES = "REFERENCES"
    const val EDGE_TAGGED = "TAGGED"
    const val EDGE_EMBEDS = "EMBEDS"

    // --- Property Keys ---
    const val PROP_CONTENT = "content"
    const val PROP_NAME = "name"
    const val PROP_URI = "filepath"
    const val PROP_CREATED_AT = "created_at"
    const val PROP_TITLE = "title"
    const val PROP_LEVEL = "level"
    const val PROP_ORDER = "order"
    const val PROP_FRONTMATTER = "frontmatter"

    // Code
    const val PROP_LANGUAGE = "language"
    const val PROP_FILENAME = "filename"
    const val PROP_CAPTION = "caption"

    // Lists & Sets
    const val PROP_LIST_ITEMS = "items" // JSON List<String>
    const val PROP_LIST_TYPE = "list_type" // Legacy
    const val PROP_NUMBER = "number" // Legacy
    const val PROP_BULLET_CHAR = "bullet_char" // Legacy
    const val PROP_IS_CHECKED = "is_checked" // Legacy
    const val PROP_MARKER = "marker" // Legacy

    // Map / Table
    const val PROP_MAP_DATA = "data" // JSON Map<String, String>
    const val PROP_HEADERS = "headers" // List<String>
    const val PROP_DATA = "data" // Table data (List<Map>)

    // Callout
    const val PROP_CALLOUT_TYPE = "type"
    const val PROP_IS_FOLDABLE = "is_foldable"

    // Concept
    const val PROP_NESTED_PATH = "nested_path"
    const val PROP_MIME_TYPE = "mime_type"
    const val PROP_ADDRESS = "address"
    const val PROP_DOMAIN = "domain"
}