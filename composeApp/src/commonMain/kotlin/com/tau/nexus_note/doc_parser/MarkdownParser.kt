package com.tau.nexus_note.doc_parser

import com.tau.nexus_note.CodexRepository
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser as IntelliJMarkdownParser

class MarkdownParser(private val repository: CodexRepository) : DocumentParser {

    override val supportedExtensions = listOf("md", "markdown")

    private val flavour = GFMFlavourDescriptor()
    private val parser = IntelliJMarkdownParser(flavour)

    // Regex for inline concepts
    private val tagRegex = Regex("#([\\w/-]+)")
    private val wikiLinkRegex = Regex("\\[\\[(.*?)(?:\\|(.*?))?\\]\\]")

    // Stack to manage the Spine (Hierarchy)
    private data class SpineContext(
        val nodeId: Long,
        val level: Int,
        var childOrderCounter: Int = 0
    )

    // Temporary storage for edges generated during content processing
    // These must be executed AFTER the block node is inserted and we have its ID.
    private var currentEdgeActions: List<suspend (Long) -> Unit>? = null

    override suspend fun parse(
        fileUri: String,
        content: String,
        repository: CodexRepository
    ): Result<Unit> {
        return try {
            // 1. Create Root Document Node (Level 0)
            val fileName = fileUri.substringAfterLast('/').substringBeforeLast('.')
            val rootNode = DocRootNode(
                filepath = fileUri,
                name = fileName,
                createdAt = 0L // TODO: Get actual file time
            )
            val rootId = repository.insertDocumentNode(rootNode)

            // 2. Parse AST
            val parsedTree = parser.buildMarkdownTreeFromString(content)

            // 3. Initialize Spine Stack
            val spineStack = Stack<SpineContext>()
            spineStack.push(SpineContext(rootId, 0))

            // 4. Walk the AST
            walkTree(parsedTree, content, spineStack)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun walkTree(node: ASTNode, rawText: String, spineStack: Stack<SpineContext>) {
        if (node.type == MarkdownElementTypes.MARKDOWN_FILE) {
            node.children.forEach { walkTree(it, rawText, spineStack) }
            return
        }

        // --- 1. Map AST to DocumentNode ---
        val docNode = mapAstToDocumentNode(node, rawText)

        if (docNode != null) {
            // --- 2. Handle Hierarchy (Spine Logic) ---

            // If it's a Section, we might need to pop the stack to find the correct parent
            if (docNode is SectionNode) {
                while (spineStack.isNotEmpty() && spineStack.peek().level >= docNode.level) {
                    spineStack.pop()
                }
            }

            // --- 3. Templating & Rib Extraction (Content Logic) ---
            // We process content to extract tags/links. This updates `currentEdgeActions`.
            val finalDocNode = when (docNode) {
                is BlockNode -> processBlockContent(docNode)
                is OrderedListItemNode -> processListItemContent(docNode)
                is UnorderedListItemNode -> processListItemContent(docNode)
                is TaskListItemNode -> processListItemContent(docNode)
                else -> docNode
            }

            // --- 4. Insert Node into Graph ---
            val nodeId = repository.insertDocumentNode(finalDocNode)

            // --- 4b. Execute Pending Edge Actions (Ribs) ---
            // Now that we have the block ID (nodeId), we can link it to the tags/concepts.
            currentEdgeActions?.let { actions ->
                actions.forEach { action -> action(nodeId) }
                currentEdgeActions = null // Reset for next node
            }

            val currentParent = spineStack.peek()

            // --- 5. Connect Spine (CONTAINS with Order) ---
            repository.insertDocumentEdge(
                StandardSchemas.EDGE_CONTAINS,
                currentParent.nodeId,
                nodeId,
                mapOf(StandardSchemas.PROP_ORDER to currentParent.childOrderCounter.toString())
            )
            currentParent.childOrderCounter++

            // --- 6. Update Stack for Recursion ---
            if (finalDocNode is SectionNode) {
                spineStack.push(SpineContext(nodeId, finalDocNode.level))
            } else if (finalDocNode is CalloutNode) {
                spineStack.push(SpineContext(nodeId, 99)) // Arbitrary high level
                node.children.forEach { walkTree(it, rawText, spineStack) }
                spineStack.pop()
                return // Skip default recursion
            }
        }

        // --- Recursion for generic containers ---
        // Note: We don't recurse automatically for everything to keep control over the stack
    }

    /**
     * Extracts Concepts (Tags) from text, inserts them into the graph,
     * and returns a new Node with the templated content string.
     */
    private suspend fun processBlockContent(node: BlockNode): BlockNode {
        val extraction = extractAndLinkConcepts(node.content)
        currentEdgeActions = extraction.edges
        return node.copy(content = extraction.templatedText)
    }

    private suspend fun processListItemContent(node: DocumentNode): DocumentNode {
        val text = when(node) {
            is OrderedListItemNode -> node.content
            is UnorderedListItemNode -> node.content
            is TaskListItemNode -> node.content
            else -> return node
        }

        val extraction = extractAndLinkConcepts(text)
        currentEdgeActions = extraction.edges

        return when(node) {
            is OrderedListItemNode -> node.copy(content = extraction.templatedText)
            is UnorderedListItemNode -> node.copy(content = extraction.templatedText)
            is TaskListItemNode -> node.copy(content = extraction.templatedText)
            else -> node
        }
    }

    private data class ExtractionResult(
        val templatedText: String,
        val edges: List<suspend (Long) -> Unit>
    )

    private suspend fun extractAndLinkConcepts(text: String): ExtractionResult {
        val edgeActions = mutableListOf<suspend (Long) -> Unit>()

        // --- 1. Handle Tags ---
        // We use findAll + StringBuilder to allow calling suspend functions (insertDocumentNode)
        val tagMatches = tagRegex.findAll(text).toList()
        val sb = StringBuilder()
        var lastIndex = 0

        for (match in tagMatches) {
            // Append text before the match
            sb.append(text, lastIndex, match.range.first)

            // Suspend call: Find or Insert Tag Node
            val tagName = match.groupValues[1]
            val tagId = repository.insertDocumentNode(TagNode(name = tagName))

            // Queue Edge Creation (Block -> Tag)
            edgeActions.add { blockId ->
                repository.insertDocumentEdge(StandardSchemas.EDGE_TAGGED, blockId, tagId)
            }

            // Append replacement template
            sb.append("{{tag:$tagId}}")

            lastIndex = match.range.last + 1
        }
        // Append remaining text
        if (lastIndex < text.length) {
            sb.append(text, lastIndex, text.length)
        }

        val processedText = sb.toString()

        // --- 2. Handle WikiLinks ---
        // (Placeholder implementation: In the future, resolve target doc ID here)
        // For now, we leave them as text or process similarly if we had a resolver.

        return ExtractionResult(processedText, edgeActions)
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

            MarkdownElementTypes.ORDERED_LIST -> null // Skip container, process children
            MarkdownElementTypes.UNORDERED_LIST -> null // Skip container

            MarkdownElementTypes.LIST_ITEM -> {
                if (text.trim().startsWith("- [ ]") || text.trim().startsWith("- [x]")) {
                    val isChecked = text.contains("[x]")
                    val content = text.replace(Regex("^- \\[[x ]\\]"), "").trim()
                    TaskListItemNode(content, isChecked, "- [ ]")
                } else if (text.trim().matches(Regex("^\\d+\\."))) {
                    val num = text.trim().substringBefore(".").toIntOrNull() ?: 1
                    val content = text.replace(Regex("^\\d+\\."), "").trim()
                    OrderedListItemNode(content, num)
                } else {
                    val content = text.removePrefix("-").removePrefix("*").trim()
                    UnorderedListItemNode(content, "-")
                }
            }

            GFMElementTypes.TABLE -> {
                // Placeholder for table parsing
                TableNode("[]", "[]")
            }

            else -> null
        }
    }

    private fun extractHeaderText(raw: String): String = raw.trimStart('#', ' ').trim()
}

// Simple Stack implementation
class Stack<T> {
    private val elements = ArrayDeque<T>()
    fun push(item: T) = elements.addLast(item)
    fun pop(): T = elements.removeLast()
    fun peek(): T = elements.last()
    fun isNotEmpty() = elements.isNotEmpty()
    fun isEmpty() = elements.isEmpty()
}