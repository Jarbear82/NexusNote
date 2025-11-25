package com.tau.nexus_note.doc_parser

object StandardSchemas {
    // --- Schema Names (Node Types) ---
    const val DOC_NODE_DOCUMENT = "Document"
    const val DOC_NODE_SECTION = "Section"
    const val DOC_NODE_PARAGRAPH = "Paragraph"
    const val DOC_NODE_HEADING = "Heading"
    const val DOC_NODE_CODE_BLOCK = "CodeBlock"
    const val DOC_NODE_QUOTE = "Quote"
    const val DOC_NODE_CALLOUT = "Callout"
    const val DOC_NODE_HTML = "HTMLBlock"
    const val DOC_NODE_THEMATIC_BREAK = "ThematicBreak"
    const val DOC_NODE_LIST = "List"
    const val DOC_NODE_LIST_ITEM = "ListItem"
    const val DOC_NODE_TABLE = "Table"
    const val DOC_NODE_CELL = "Cell"

    // --- Edge Names ---
    const val EDGE_CONTAINS = "CONTAINS"       // Parent -> Child (Nesting)
    const val EDGE_NEXT = "NEXT"               // Sibling -> Sibling (Reading Order)
    const val EDGE_HAS_ITEM = "HAS_ITEM"       // List -> ListItem
    const val EDGE_CELL_AT = "CELL_AT"         // Table -> Cell
    const val EDGE_LINKS_TO = "LINKS_TO"       // Block -> URL/Document
    const val EDGE_EMBEDS = "EMBEDS"           // Block -> File

    // --- Property Keys ---
    const val PROP_CONTENT = "content"         // The raw text or HTML
    const val PROP_URI = "uri"
    const val PROP_TITLE = "title"
    const val PROP_LEVEL = "level"             // Int: 1-6
    const val PROP_LANGUAGE = "language"       // Code block language
    const val PROP_LIST_TYPE = "listType"      // "ordered", "bullet"
    const val PROP_TIGHT = "tight"             // Boolean
    const val PROP_MARKER = "marker"           // "1.", "-", "[x]"
    const val PROP_IS_TASK = "isTask"          // Boolean
    const val PROP_IS_COMPLETE = "isComplete"  // Boolean
    const val PROP_CALLOUT_TYPE = "calloutType" // "info", "warning"
    const val PROP_IS_FOLDABLE = "isFoldable"
    const val PROP_ROW = "row"
    const val PROP_COL = "col"
    const val PROP_ALIGNMENT = "alignment"     // JSON string of col alignments
}