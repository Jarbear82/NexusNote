package com.tau.nexus_note.doc_parser

import com.tau.nexus_note.CodexRepository
import com.tau.nexus_note.utils.copyFileToMediaDir
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser as IntelliJMarkdownParser
import java.io.File

class MarkdownParser(private val repository: CodexRepository) : DocumentParser {

    override val supportedExtensions = listOf("md", "markdown")

    private val flavour = GFMFlavourDescriptor()
    private val parser = IntelliJMarkdownParser(flavour)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    // Regex for inline concepts
    private val tagRegex = Regex("#([\\w/-]+)")

    // Regex for Obsidan-style Embeds: ![[image.png]]
    private val wikiEmbedRegex = Regex("!\\[\\[(.*?)(?:\\|(.*?))?\\]\\]")
    // Regex for Standard Markdown Images: ![alt](path)
    private val mdImageRegex = Regex("!\\[(.*?)\\]\\((.*?)\\)")

    private data class SpineContext(
        val nodeId: Long,
        val level: Int,
        var childOrderCounter: Int = 0
    )

    private var currentEdgeActions: List<suspend (Long) -> Unit>? = null

    override suspend fun parse(
        fileUri: String,
        content: String,
        repository: CodexRepository,
        sourceDirectory: String
    ): Result<Unit> {
        return try {
            val fileName = fileUri.substringAfterLast('/').substringBeforeLast('.')
            val rootNode = DocRootNode(
                filepath = fileUri,
                name = fileName,
                createdAt = 0L
            )
            val rootId = repository.insertDocumentNode(rootNode)

            val parsedTree = parser.buildMarkdownTreeFromString(content)
            val spineStack = Stack<SpineContext>()
            spineStack.push(SpineContext(rootId, 0))

            walkTree(parsedTree, content, spineStack, sourceDirectory)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun walkTree(node: ASTNode, rawText: String, spineStack: Stack<SpineContext>, sourceDir: String) {
        if (node.type == MarkdownElementTypes.MARKDOWN_FILE) {
            node.children.forEach { walkTree(it, rawText, spineStack, sourceDir) }
            return
        }

        // --- NEW: Consolidated List Handling ---
        // If we hit a list container, process it as a single node instead of recursing immediately
        if (node.type == MarkdownElementTypes.ORDERED_LIST || node.type == MarkdownElementTypes.UNORDERED_LIST) {
            processConsolidatedList(node, rawText, spineStack, sourceDir)
            return
        }

        // 1. Map AST to DocumentNode
        val docNode = mapAstToDocumentNode(node, rawText)

        // 2. Recursion for Containers (BlockQuotes)
        if (docNode == null) {
            if (node.type == MarkdownElementTypes.BLOCK_QUOTE) {
                node.children.forEach { walkTree(it, rawText, spineStack, sourceDir) }
            }
            return
        }

        // 3. Handle Hierarchy (Spine Logic)
        if (docNode is SectionNode) {
            while (spineStack.isNotEmpty() && spineStack.peek().level >= docNode.level) {
                spineStack.pop()
            }
        }

        // 4. Templating & Rib Extraction
        val finalDocNode = when (docNode) {
            is BlockNode -> processBlockContent(docNode, sourceDir)
            // Lists are now handled separately, so these cases are unreachable via mapAst, but good to keep safe
            is OrderedListItemNode -> docNode
            is UnorderedListItemNode -> docNode
            is TaskListItemNode -> docNode
            else -> docNode
        }

        // 5. Insert Node
        val nodeId = repository.insertDocumentNode(finalDocNode)

        // 6. Execute Pending Edge Actions
        currentEdgeActions?.let { actions ->
            actions.forEach { action -> action(nodeId) }
            currentEdgeActions = null
        }

        val currentParent = spineStack.peek()

        // 7. Connect Spine
        repository.insertDocumentEdge(
            StandardSchemas.EDGE_CONTAINS,
            currentParent.nodeId,
            nodeId,
            mapOf(StandardSchemas.PROP_ORDER to currentParent.childOrderCounter.toString())
        )
        currentParent.childOrderCounter++

        // 8. Update Stack for Recursion
        if (finalDocNode is SectionNode) {
            spineStack.push(SpineContext(nodeId, finalDocNode.level))
        } else if (finalDocNode is CalloutNode) {
            spineStack.push(SpineContext(nodeId, 99))
            // Recurse into children (paragraphs inside the quote)
            node.children.forEach { walkTree(it, rawText, spineStack, sourceDir) }
            spineStack.pop()
        }
    }

    private suspend fun processConsolidatedList(
        listNode: ASTNode,
        rawText: String,
        spineStack: Stack<SpineContext>,
        sourceDir: String
    ) {
        val items = mutableListOf<String>()
        val allEdgeActions = mutableListOf<suspend (Long) -> Unit>()

        // Determine list type based on AST type initially
        // Note: Task lists are typically Unordered Lists in AST but check contents
        var listType = if (listNode.type == MarkdownElementTypes.ORDERED_LIST) "ordered" else "unordered"

        // Walk children (List Items)
        listNode.children.forEach { child ->
            if (child.type == MarkdownElementTypes.LIST_ITEM) {
                // Get full text of item including bullet/number
                val itemText = child.getTextInNode(rawText).toString().trim()

                // Detect Task List based on content
                if (itemText.startsWith("- [ ]") || itemText.startsWith("- [x]") ||
                    itemText.startsWith("* [ ]") || itemText.startsWith("* [x]")) {
                    listType = "task"
                }

                // Clean the text (remove bullet/number/checkbox) to store just content
                val cleanedText = cleanListItemText(itemText)

                // Process content for tags/links extraction
                val extraction = extractAndLinkConcepts(cleanedText, sourceDir)

                // Add templated text to list
                items.add(extraction.templatedText)

                // Accumulate edge actions (tags linked to this List Node)
                allEdgeActions.addAll(extraction.edges)
            }
        }

        if (items.isEmpty()) return

        // Create the consolidated node
        val listDocNode = ListNode(
            itemsJson = json.encodeToString(items),
            listType = listType
        )

        // Insert Node
        val nodeId = repository.insertDocumentNode(listDocNode)

        // Execute all collected edge actions on this single List Node
        allEdgeActions.forEach { action -> action(nodeId) }

        // Connect to Spine
        val currentParent = spineStack.peek()
        repository.insertDocumentEdge(
            StandardSchemas.EDGE_CONTAINS,
            currentParent.nodeId,
            nodeId,
            mapOf(StandardSchemas.PROP_ORDER to currentParent.childOrderCounter.toString())
        )
        currentParent.childOrderCounter++
    }

    private fun cleanListItemText(raw: String): String {
        // 1. Remove task markers: "- [x] ", "- [ ] ", "* [ ] "
        var text = raw.replace(Regex("^[-*+] \\[[x ]\\]"), "").trim()

        // 2. Remove ordered markers: "1. ", "1) "
        text = text.replace(Regex("^\\d+[.)]"), "").trim()

        // 3. Remove unordered markers: "- ", "* ", "+ "
        text = text.replace(Regex("^[-*+]"), "").trim()

        return text
    }

    private suspend fun processBlockContent(node: BlockNode, sourceDir: String): BlockNode {
        val extraction = extractAndLinkConcepts(node.content, sourceDir)
        currentEdgeActions = extraction.edges
        return node.copy(content = extraction.templatedText)
    }

    private data class ExtractionResult(
        val templatedText: String,
        val edges: List<suspend (Long) -> Unit>
    )

    private suspend fun replaceAsync(
        input: String,
        regex: Regex,
        transform: suspend (MatchResult) -> String
    ): String {
        val sb = StringBuilder()
        var lastIndex = 0
        val matches = regex.findAll(input)

        for (match in matches) {
            sb.append(input, lastIndex, match.range.first)
            val replacement = transform(match)
            sb.append(replacement)
            lastIndex = match.range.last + 1
        }
        if (lastIndex < input.length) {
            sb.append(input, lastIndex, input.length)
        }
        return sb.toString()
    }

    private suspend fun extractAndLinkConcepts(text: String, sourceDir: String): ExtractionResult {
        val edgeActions = mutableListOf<suspend (Long) -> Unit>()
        var processedText = text

        // --- 1. Handle Tags ---
        processedText = replaceAsync(processedText, tagRegex) { match ->
            val tagName = match.groupValues[1]
            val existingTagId = repository.findNodeByLabel(StandardSchemas.DOC_NODE_TAG, tagName)
            val tagId = existingTagId ?: repository.insertDocumentNode(TagNode(name = tagName))

            edgeActions.add { blockId ->
                repository.insertDocumentEdge(StandardSchemas.EDGE_TAGGED, blockId, tagId)
            }
            "{{tag:$tagId}}"
        }

        // --- 2. Handle WikiLink Embeds: ![[image.png]] ---
        processedText = replaceAsync(processedText, wikiEmbedRegex) { match ->
            val rawFilename = match.groupValues[1]
            val storedPath = handleAttachmentImport(rawFilename, sourceDir)

            val attachId = repository.insertDocumentNode(
                AttachmentNode(filename = rawFilename, mimeType = "image/auto", path = storedPath)
            )

            edgeActions.add { blockId ->
                repository.insertDocumentEdge(StandardSchemas.EDGE_EMBEDS, blockId, attachId)
            }
            "{{embed:$attachId}}"
        }

        // --- 3. Handle Markdown Images: ![alt](path) ---
        processedText = replaceAsync(processedText, mdImageRegex) { match ->
            val alt = match.groupValues[1]
            val path = match.groupValues[2]
            val filename = path.substringAfterLast('/')

            // For markdown images, 'path' might be relative or absolute
            val storedPath = handleAttachmentImport(path, sourceDir)

            val attachId = repository.insertDocumentNode(
                AttachmentNode(filename = filename, path = storedPath, mimeType = "image/auto")
            )

            edgeActions.add { blockId ->
                repository.insertDocumentEdge(StandardSchemas.EDGE_EMBEDS, blockId, attachId)
            }
            "{{embed:$attachId}}"
        }

        return ExtractionResult(processedText, edgeActions)
    }

    /**
     * Copies the file from sourceDir to the Codex media directory.
     * Returns the relative filename to be stored in the database.
     */
    private fun handleAttachmentImport(relativePath: String, sourceDir: String): String {
        val sourceFile = File(sourceDir, relativePath)

        // 1. Check relative path from source document
        if (sourceFile.exists()) {
            return copyFileToMediaDir(sourceFile.absolutePath, repository.mediaDirectoryPath)
        }

        // 2. Check as absolute path
        val absFile = File(relativePath)
        if (absFile.exists()) {
            return copyFileToMediaDir(absFile.absolutePath, repository.mediaDirectoryPath)
        }

        // 3. Fallback: file not found, keep original string
        return relativePath
    }

    private fun mapAstToDocumentNode(node: ASTNode, rawText: String): DocumentNode? {
        val text = node.getTextInNode(rawText).toString()

        return when (node.type) {
            MarkdownElementTypes.ATX_1 -> SectionNode(extractHeaderText(text), 1)
            MarkdownElementTypes.ATX_2 -> SectionNode(extractHeaderText(text), 2)
            MarkdownElementTypes.ATX_3 -> SectionNode(extractHeaderText(text), 3)
            MarkdownElementTypes.ATX_4 -> SectionNode(extractHeaderText(text), 4)
            MarkdownElementTypes.ATX_5 -> SectionNode(extractHeaderText(text), 5)
            MarkdownElementTypes.ATX_6 -> SectionNode(extractHeaderText(text), 6)

            MarkdownElementTypes.PARAGRAPH -> BlockNode(text.trim())

            MarkdownElementTypes.CODE_FENCE -> parseCodeFence(node, rawText)
            MarkdownElementTypes.CODE_BLOCK -> parseIndentedCodeBlock(node, rawText)

            MarkdownElementTypes.BLOCK_QUOTE -> parseBlockQuote(node, rawText)

            // Lists are handled at the higher level now, this is fallback
            MarkdownElementTypes.ORDERED_LIST -> null
            MarkdownElementTypes.UNORDERED_LIST -> null

            // Individual List Items should not be reached via this call in normal flow
            MarkdownElementTypes.LIST_ITEM -> parseListItem(text)

            GFMElementTypes.TABLE -> parseTable(node, rawText)

            else -> null
        }
    }

    private fun parseBlockQuote(node: ASTNode, rawText: String): DocumentNode? {
        val fullText = node.getTextInNode(rawText).toString()
        val calloutRegex = Regex("^>\\s*\\[!([\\w-]+)\\](.*)", RegexOption.MULTILINE)
        val match = calloutRegex.find(fullText)

        return if (match != null) {
            val type = match.groupValues[1]
            val title = match.groupValues[2].trim()
            CalloutNode(type, title, isFoldable = true)
        } else {
            CalloutNode("quote", "Quote", isFoldable = false)
        }
    }

    // Retained for robustness if mapAstToDocumentNode encounters a stray item
    private fun parseListItem(text: String): DocumentNode {
        val trimmed = text.trim()
        return if (trimmed.startsWith("- [ ]") || trimmed.startsWith("- [x]")) {
            val isChecked = trimmed.contains("[x]")
            val content = trimmed.replace(Regex("^- \\[[x ]\\]"), "").trim()
            TaskListItemNode(content, isChecked, "- [ ]")
        } else if (trimmed.matches(Regex("^\\d+\\..*"))) {
            val num = trimmed.substringBefore(".").toIntOrNull() ?: 1
            val content = trimmed.replace(Regex("^\\d+\\."), "").trim()
            OrderedListItemNode(content, num)
        } else {
            val content = trimmed.removePrefix("-").removePrefix("*").removePrefix("+").trim()
            UnorderedListItemNode(content, "-")
        }
    }

    private fun parseCodeFence(node: ASTNode, rawText: String): CodeBlockNode {
        var infoString = ""
        val contentBuilder = StringBuilder()

        for (child in node.children) {
            when (child.type) {
                MarkdownTokenTypes.FENCE_LANG -> {
                    infoString = child.getTextInNode(rawText).toString().trim()
                }
                MarkdownTokenTypes.CODE_FENCE_CONTENT, MarkdownTokenTypes.TEXT -> {
                    contentBuilder.append(child.getTextInNode(rawText))
                }
                MarkdownTokenTypes.EOL -> {
                    contentBuilder.append(child.getTextInNode(rawText))
                }
            }
        }

        val meta = parseCodeFenceInfo(infoString)

        return CodeBlockNode(
            content = contentBuilder.toString().trim(),
            language = meta.language,
            filename = meta.title,
            caption = meta.caption
        )
    }

    private data class CodeFenceMeta(
        val language: String,
        val title: String,
        val caption: String
    )

    private fun parseCodeFenceInfo(info: String): CodeFenceMeta {
        if (info.isBlank()) return CodeFenceMeta("", "", "")

        // 1. Extract Language (first word)
        val parts = info.split(Regex("\\s+"), limit = 2)
        val language = parts.getOrNull(0) ?: ""
        val attributes = parts.getOrNull(1) ?: ""

        // 2. Extract Attributes using Regex
        // Matches key="value" or key='value'
        val attrRegex = Regex("(\\w+)=[\"'](.*?)[\"']")
        val matches = attrRegex.findAll(attributes)

        var title = ""
        var caption = ""

        matches.forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            when (key) {
                "title", "filename" -> title = value
                "caption" -> caption = value
            }
        }

        // Fallback for simple "rust:main.rs" format
        if (title.isBlank() && info.contains(":") && !info.contains("=")) {
            val simpleParts = info.split(":", limit = 2)
            if (simpleParts.size == 2) {
                return CodeFenceMeta(simpleParts[0].trim(), simpleParts[1].trim(), "")
            }
        }

        return CodeFenceMeta(language, title, caption)
    }

    private fun parseIndentedCodeBlock(node: ASTNode, rawText: String): CodeBlockNode {
        return CodeBlockNode(node.getTextInNode(rawText).toString().trim(), "", "")
    }

    private fun parseTable(node: ASTNode, rawText: String): TableNode {
        var headers: List<String> = emptyList()
        val data = mutableListOf<Map<String, String>>()

        for (child in node.children) {
            if (child.type == GFMElementTypes.HEADER) {
                val headerRow = child.children.find { it.type == GFMElementTypes.ROW }
                headers = if (headerRow != null) {
                    extractRowCells(headerRow, rawText)
                } else {
                    extractRowCells(child, rawText)
                }
            } else if (child.type == GFMElementTypes.ROW) {
                val cells = extractRowCells(child, rawText)
                val rowMap = mutableMapOf<String, String>()
                headers.forEachIndexed { index, header ->
                    val cellValue = cells.getOrNull(index) ?: ""
                    rowMap[header] = cellValue
                }
                data.add(rowMap)
            }
        }

        return TableNode(
            headersJson = json.encodeToString(headers),
            dataJson = json.encodeToString(data),
            caption = ""
        )
    }

    private fun extractRowCells(rowNode: ASTNode, rawText: String): List<String> {
        val rawCells = mutableListOf<String>()
        val builder = StringBuilder()
        val separatorType = GFMTokenTypes.TABLE_SEPARATOR

        for (child in rowNode.children) {
            if (child.type == separatorType) {
                rawCells.add(builder.toString())
                builder.clear()
            } else {
                builder.append(child.getTextInNode(rawText))
            }
        }
        rawCells.add(builder.toString())

        if (rawCells.isNotEmpty() && rawCells.first().isBlank()) rawCells.removeAt(0)
        if (rawCells.isNotEmpty() && rawCells.last().isBlank()) rawCells.removeAt(rawCells.lastIndex)

        return rawCells.map { it.trim() }
    }

    private fun extractHeaderText(raw: String): String = raw.trimStart('#', ' ').trim()
}

class Stack<T> {
    private val elements = ArrayDeque<T>()
    fun push(item: T) = elements.addLast(item)
    fun pop(): T = elements.removeLast()
    fun peek(): T = elements.last()
    fun isNotEmpty() = elements.isNotEmpty()
    fun isEmpty() = elements.isEmpty()
}