package com.tau.nexus_note.doc_parser

import com.tau.nexus_note.CodexRepository
import com.tau.nexus_note.utils.copyFileToMediaDir
import com.tau.nexus_note.utils.PropertySerialization
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
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

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
    private val wikiEmbedRegex = Regex("!\\[\\[(.*?)(?:\\|(.*?))?\\]\\]")
    private val mdImageRegex = Regex("!\\[(.*?)\\]\\((.*?)\\)")

    private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")

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
            val (frontmatterMap, bodyContent) = extractFrontmatter(content)
            val frontmatterJson = json.encodeToString(frontmatterMap)
            val fileName = fileUri.substringAfterLast('/').substringBeforeLast('.')

            val rootNode = DocRootNode(
                filepath = fileUri,
                name = fileName,
                createdAt = 0L,
                frontmatterJson = frontmatterJson
            )
            val rootId = repository.insertDocumentNode(rootNode)

            val parsedTree = parser.buildMarkdownTreeFromString(bodyContent)
            val spineStack = Stack<SpineContext>()
            spineStack.push(SpineContext(rootId, 0))

            walkTree(parsedTree, bodyContent, spineStack, sourceDirectory)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractFrontmatter(fullText: String): Pair<Map<String, String>, String> {
        val trimmed = fullText.trimStart()
        if (!trimmed.startsWith("---")) return Pair(emptyMap(), fullText)
        val endIdx = trimmed.indexOf("\n---", 3)
        if (endIdx == -1) return Pair(emptyMap(), fullText)

        val yamlBlock = trimmed.substring(3, endIdx)
        val body = trimmed.substring(endIdx + 4).trimStart()

        val map = mutableMapOf<String, String>()
        yamlBlock.lines().forEach { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim().removeSurrounding("\"").removeSurrounding("'")
                if (key.isNotBlank()) map[key] = value
            }
        }
        return Pair(map, body)
    }

    private suspend fun walkTree(node: ASTNode, rawText: String, spineStack: Stack<SpineContext>, sourceDir: String) {
        if (node.type == MarkdownElementTypes.MARKDOWN_FILE) {
            node.children.forEach { walkTree(it, rawText, spineStack, sourceDir) }
            return
        }

        if (node.type == MarkdownElementTypes.ORDERED_LIST || node.type == MarkdownElementTypes.UNORDERED_LIST) {
            processConsolidatedList(node, rawText, spineStack, sourceDir)
            return
        }

        val docNode = mapAstToDocumentNode(node, rawText)

        if (docNode == null) {
            if (node.type == MarkdownElementTypes.BLOCK_QUOTE) {
                node.children.forEach { walkTree(it, rawText, spineStack, sourceDir) }
            }
            return
        }

        if (docNode is SectionNode) {
            while (spineStack.isNotEmpty() && spineStack.peek().level >= docNode.level) {
                spineStack.pop()
            }
        }

        val finalDocNode = when (docNode) {
            is BlockNode -> processBlockContent(docNode, sourceDir)
            else -> docNode
        }

        val nodeId = repository.insertDocumentNode(finalDocNode)

        currentEdgeActions?.let { actions ->
            actions.forEach { action -> action(nodeId) }
            currentEdgeActions = null
        }

        val currentParent = spineStack.peek()

        repository.insertDocumentEdge(
            StandardSchemas.EDGE_CONTAINS,
            currentParent.nodeId,
            nodeId,
            mapOf(StandardSchemas.PROP_ORDER to currentParent.childOrderCounter.toString())
        )
        currentParent.childOrderCounter++

        if (finalDocNode is SectionNode) {
            spineStack.push(SpineContext(nodeId, finalDocNode.level))
        } else if (finalDocNode is CalloutNode) {
            spineStack.push(SpineContext(nodeId, 99))
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

        var isOrdered = (listNode.type == MarkdownElementTypes.ORDERED_LIST)

        listNode.children.forEach { child ->
            if (child.type == MarkdownElementTypes.LIST_ITEM) {
                val itemText = child.getTextInNode(rawText).toString().trim()
                if (itemText.startsWith("- [ ]") || itemText.startsWith("- [x]") || itemText.startsWith("* [ ]")) {
                    isOrdered = false
                }
                val cleanedText = cleanListItemText(itemText)
                val extraction = extractAndLinkConcepts(cleanedText, sourceDir)
                items.add(extraction.templatedText)
                allEdgeActions.addAll(extraction.edges)
            }
        }

        if (items.isEmpty()) return

        val schemaName = if (isOrdered) StandardSchemas.DOC_NODE_ORDERED_LIST else StandardSchemas.DOC_NODE_UNORDERED_LIST
        val specializedNode = DynamicDocumentNode(
            schemaName = schemaName,
            properties = mapOf(StandardSchemas.PROP_LIST_ITEMS to json.encodeToString(items))
        )

        val nodeId = repository.insertDocumentNode(specializedNode)

        allEdgeActions.forEach { action -> action(nodeId) }

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
        var text = raw.replace(Regex("^[-*+] \\[[x ]\\]"), "").trim()
        text = text.replace(Regex("^\\d+[.)]"), "").trim()
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

    private suspend fun replaceAsync(input: String, regex: Regex, transform: suspend (MatchResult) -> String): String {
        val sb = StringBuilder()
        var lastIndex = 0
        val matches = regex.findAll(input)

        for (match in matches) {
            sb.append(input, lastIndex, match.range.first)
            val replacement = transform(match)
            sb.append(replacement)
            lastIndex = match.range.last + 1
        }
        if (lastIndex < input.length) sb.append(input, lastIndex, input.length)
        return sb.toString()
    }

    // Helper to decide node type based on extension
    private suspend fun createAttachmentOrImage(rawFilename: String, altText: String = "", sourceDir: String): Long {
        val storedPath = handleAttachmentImport(rawFilename, sourceDir)
        val extension = File(rawFilename).extension.lowercase()
        val mimeType = "application/octet-stream" // Can be improved

        return if (imageExtensions.contains(extension)) {
            // It's an Image -> Measure it!
            val (w, h) = getImageDimensions(storedPath, repository.mediaDirectoryPath)
            repository.insertDocumentNode(
                ImageNode(
                    filename = rawFilename,
                    mimeType = "image/$extension",
                    path = storedPath,
                    altText = altText,
                    width = w,
                    height = h
                )
            )
        } else {
            // Generic Attachment
            repository.insertDocumentNode(
                AttachmentNode(
                    filename = rawFilename,
                    mimeType = mimeType,
                    path = storedPath
                )
            )
        }
    }

    private suspend fun extractAndLinkConcepts(text: String, sourceDir: String): ExtractionResult {
        val edgeActions = mutableListOf<suspend (Long) -> Unit>()
        var processedText = text

        processedText = replaceAsync(processedText, tagRegex) { match ->
            val tagName = match.groupValues[1]
            val existingTagId = repository.findNodeByLabel(StandardSchemas.DOC_NODE_TAG, tagName)
            val tagId = existingTagId ?: repository.insertDocumentNode(TagNode(name = tagName))
            edgeActions.add { blockId -> repository.insertDocumentEdge(StandardSchemas.EDGE_TAGGED, blockId, tagId) }
            "{{tag:$tagId}}"
        }

        // Wiki Embeds ![[filename]]
        processedText = replaceAsync(processedText, wikiEmbedRegex) { match ->
            val rawFilename = match.groupValues[1]
            // Optional pipe for alt text not fully implemented in wiki regex group, usually group 2 is alias
            val attachId = createAttachmentOrImage(rawFilename, "", sourceDir)
            edgeActions.add { blockId -> repository.insertDocumentEdge(StandardSchemas.EDGE_EMBEDS, blockId, attachId) }
            "{{embed:$attachId}}"
        }

        // MD Images ![alt](url)
        processedText = replaceAsync(processedText, mdImageRegex) { match ->
            val alt = match.groupValues[1]
            val path = match.groupValues[2]
            val attachId = createAttachmentOrImage(path, alt, sourceDir)
            edgeActions.add { blockId -> repository.insertDocumentEdge(StandardSchemas.EDGE_EMBEDS, blockId, attachId) }
            "{{embed:$attachId}}"
        }

        return ExtractionResult(processedText, edgeActions)
    }

    private fun handleAttachmentImport(relativePath: String, sourceDir: String): String {
        val sourceFile = File(sourceDir, relativePath)
        if (sourceFile.exists()) return copyFileToMediaDir(sourceFile.absolutePath, repository.mediaDirectoryPath)
        val absFile = File(relativePath)
        if (absFile.exists()) return copyFileToMediaDir(absFile.absolutePath, repository.mediaDirectoryPath)
        return relativePath
    }

    // Read dimensions without fully loading image into memory if possible (using ImageIO)
    private fun getImageDimensions(relativePath: String, mediaRoot: String): Pair<Int, Int> {
        return try {
            val file = File(mediaRoot, relativePath)
            if (!file.exists()) return Pair(0, 0)
            val img: BufferedImage? = ImageIO.read(file)
            if (img != null) {
                Pair(img.width, img.height)
            } else {
                Pair(0, 0)
            }
        } catch (e: Exception) {
            Pair(0, 0)
        }
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
            MarkdownElementTypes.CODE_FENCE, MarkdownElementTypes.CODE_BLOCK -> parseCodeFence(node, rawText)
            MarkdownElementTypes.BLOCK_QUOTE -> parseBlockQuote(node, rawText)
            GFMElementTypes.TABLE -> parseTable(node, rawText)
            else -> null
        }
    }

    private fun parseBlockQuote(node: ASTNode, rawText: String): DocumentNode? {
        return BlockNode("> " + node.getTextInNode(rawText).toString())
    }

    private fun parseCodeFence(node: ASTNode, rawText: String): CodeBlockNode {
        var infoString = ""
        val contentBuilder = StringBuilder()
        for (child in node.children) {
            when (child.type) {
                MarkdownTokenTypes.FENCE_LANG -> infoString = child.getTextInNode(rawText).toString().trim()
                MarkdownTokenTypes.CODE_FENCE_CONTENT, MarkdownTokenTypes.TEXT, MarkdownTokenTypes.EOL -> contentBuilder.append(child.getTextInNode(rawText))
            }
        }
        val language = infoString.split(" ").firstOrNull() ?: "text"
        return CodeBlockNode(contentBuilder.toString().trim(), language)
    }

    private fun parseTable(node: ASTNode, rawText: String): TableNode {
        val headers = mutableListOf<String>()
        val rows = mutableListOf<Map<String, String>>()

        var headerNode: ASTNode? = null
        val rowNodes = mutableListOf<ASTNode>()

        for (child in node.children) {
            if (child.type == GFMElementTypes.HEADER) {
                headerNode = child
            } else if (child.type == GFMElementTypes.ROW) {
                rowNodes.add(child)
            }
        }

        if (headerNode != null) {
            headerNode.children.forEach { cell ->
                if (cell.type == GFMTokenTypes.CELL) {
                    headers.add(cell.getTextInNode(rawText).toString().trim())
                }
            }
        }

        rowNodes.forEach { rowNode ->
            val rowMap = mutableMapOf<String, String>()
            var cellIndex = 0
            rowNode.children.forEach { cell ->
                if (cell.type == GFMTokenTypes.CELL) {
                    val content = cell.getTextInNode(rawText).toString().trim()
                    val key = if (cellIndex < headers.size) headers[cellIndex] else "Col ${cellIndex + 1}"
                    rowMap[key] = content
                    cellIndex++
                }
            }
            if (rowMap.isNotEmpty()) {
                rows.add(rowMap)
            }
        }

        val headersJson = PropertySerialization.serializeList(headers)
        val rowsJson = PropertySerialization.serializeListOfMaps(rows)

        return TableNode(headersJson, rowsJson, "Table")
    }

    private fun extractHeaderText(raw: String): String = raw.trimStart('#', ' ').trim()
}

class Stack<T> {
    private val elements = ArrayDeque<T>()
    fun push(item: T) = elements.addLast(item)
    fun pop(): T = elements.removeLast()
    fun peek(): T = elements.last()
    fun isNotEmpty() = elements.isNotEmpty()
}