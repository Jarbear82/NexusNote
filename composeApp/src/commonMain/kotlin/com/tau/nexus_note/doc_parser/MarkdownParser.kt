package com.tau.nexus_note.doc_parser

import com.tau.nexus_note.CodexRepository
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser as IntelliJMarkdownParser

class MarkdownParser(private val repository: CodexRepository) : DocumentParser {

    override val supportedExtensions = listOf("md", "markdown")

    private val flavour = GFMFlavourDescriptor()
    // 2. Use the Aliased Class
    private val parser = IntelliJMarkdownParser(flavour)

    // Regex for Obsidian features
    private val wikiLinkRegex = Regex("\\[\\[(.*?)(?:\\|(.*?))?\\]\\]")
    private val embedRegex = Regex("!\\[\\[(.*?)\\]\\]")
    private val frontmatterRegex = Regex("^---\\s*\\n([\\s\\S]*?)\\n---\\s*\\n")
    private val calloutRegex = Regex("^\\[!(\\w+)\\][-+]? ?(.*)")

    override suspend fun parse(
        fileUri: String,
        content: String,
        repository: CodexRepository
    ): Result<Unit> {
        return try {
            // 1. Extract Frontmatter (Metadata)
            var bodyContent = content
            var frontmatterJson = "{}"

            frontmatterRegex.find(content)?.let { match ->
                val yamlContent = match.groupValues[1]
                // In a real app, parse YAML to JSON here.
                // For now, we store the raw YAML string.
                frontmatterJson = "{ \"raw\": \"$yamlContent\" }"
                bodyContent = content.substring(match.range.last + 1)
            }

            // 2. Create Root Document Node
            val fileName = fileUri.substringAfterLast('/').substringBeforeLast('.')
            val extension = fileUri.substringAfterLast('.', "")

            val rootNode = DocRootNode(
                uri = fileUri,
                name = fileName,
                extension = extension,
                frontmatterJson = frontmatterJson
            )

            // TODO: Insert Root Node into DB and get its ID
            val rootId = repository.insertDocumentNode(rootNode)

            // 3. Parse AST (GFM Base)
            val parsedTree = parser.buildMarkdownTreeFromString(bodyContent)

            // 4. Traverse and Build Graph
            val context = ParsingContext(
                rawText = bodyContent,
                parentId = rootId,
                previousBlockId = null
            )

            walkTree(parsedTree, context)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun walkTree(node: ASTNode, ctx: ParsingContext) {
        // Skip the top-level MARKDOWN_FILE node itself, just process children
        if (node.type == MarkdownElementTypes.MARKDOWN_FILE) {
            node.children.forEach { walkTree(it, ctx) }
            return
        }

        // 1. Identify the DocumentNode type
        val docNode = mapAstToDocumentNode(node, ctx.rawText)

        // 2. Handle Hierarchy & Sections
        if (docNode != null) {
            // Logic to handle Section nesting based on Headers
            if (docNode is SectionNode) {
                // Pop stack until we find a parent with lower level
                while (ctx.sectionStack.isNotEmpty() && ctx.sectionStack.peek().level >= docNode.level) {
                    ctx.sectionStack.pop()
                }

                // INSERT SECTION
                val nodeId = repository.insertDocumentNode(docNode)

                // LINK TO PARENT
                // If stack is empty, parent is Root. Else parent is top of stack.
                // FIX: Handle nullable dbId safely
                val stackTop = if (ctx.sectionStack.isNotEmpty()) ctx.sectionStack.peek() else null
                val parentId = stackTop?.dbId ?: ctx.parentId

                repository.insertDocumentEdge(StandardSchemas.EDGE_CONTAINS, parentId, nodeId)

                // LINK READING ORDER
                if (ctx.previousBlockId != null) {
                    repository.insertDocumentEdge(StandardSchemas.EDGE_NEXT, ctx.previousBlockId!!, nodeId)
                }

                // Push with ID
                // FIX: 'copy' works here because docNode is smart-cast to SectionNode
                ctx.sectionStack.push(docNode.copy(dbId = nodeId))
                ctx.previousBlockId = nodeId

            } else {
                // INSERT BLOCK (Paragraphs, Lists, etc.)
                val nodeId = repository.insertDocumentNode(docNode)

                // Link to Parent
                val stackTop = if (ctx.sectionStack.isNotEmpty()) ctx.sectionStack.peek() else null
                val parentId = stackTop?.dbId ?: ctx.parentId

                repository.insertDocumentEdge(StandardSchemas.EDGE_CONTAINS, parentId, nodeId)

                // Link Reading Order
                if (ctx.previousBlockId != null) {
                    repository.insertDocumentEdge(StandardSchemas.EDGE_NEXT, ctx.previousBlockId!!, nodeId)
                }
                ctx.previousBlockId = nodeId

                // 3. Process Inlines (Links/Embeds) for Leaf Nodes
                if (docNode is ParagraphNode) {
                    extractInlineLinks(docNode.content)
                } else if (docNode is ListItemNode) {
                    extractInlineLinks(docNode.content)
                }
            }
        }

        // 4. Handle Children (Recursion)
        if (docNode is CalloutNode || docNode is QuoteNode || docNode is ListNode || docNode is TableNode) {
            // For container blocks, we dive deeper
            val childCtx = ctx.copy(parentId = 0L /* placeholder, logic mostly relies on stack/previousId */)
            node.children.forEach { child ->
                // Filter out generic tokens like newlines/formatting chars for cleaner graph
                if (isStructuralNode(child)) {
                    walkTree(child, childCtx)
                }
            }
        }
    }

    private fun mapAstToDocumentNode(node: ASTNode, rawText: String): DocumentNode? {
        val text = node.getTextInNode(rawText).toString()

        return when (node.type) {
            // --- HEADERS / SECTIONS ---
            MarkdownElementTypes.ATX_1 -> SectionNode(extractHeaderText(text), 1)
            MarkdownElementTypes.ATX_2 -> SectionNode(extractHeaderText(text), 2)
            MarkdownElementTypes.ATX_3 -> SectionNode(extractHeaderText(text), 3)
            MarkdownElementTypes.ATX_4 -> SectionNode(extractHeaderText(text), 4)
            MarkdownElementTypes.ATX_5 -> SectionNode(extractHeaderText(text), 5)
            MarkdownElementTypes.ATX_6 -> SectionNode(extractHeaderText(text), 6)

            // --- BLOCKS ---
            MarkdownElementTypes.PARAGRAPH -> ParagraphNode(text.trim())

            MarkdownElementTypes.CODE_FENCE,
            MarkdownElementTypes.CODE_BLOCK -> {
                val lang = text.lines().firstOrNull()?.trim()?.removePrefix("```") ?: ""
                val content = text.removePrefix("```$lang").removeSuffix("```").trim()
                CodeBlockNode(content, lang)
            }

            MarkdownElementTypes.BLOCK_QUOTE -> {
                val firstLine = text.lines().firstOrNull()?.trim() ?: ""
                val cleanLine = firstLine.removePrefix(">").trim()

                val calloutMatch = calloutRegex.find(cleanLine)
                if (calloutMatch != null) {
                    val (type, title) = calloutMatch.destructured
                    CalloutNode(type, title.ifBlank { type.replaceFirstChar { it.uppercase() } })
                } else {
                    QuoteNode(text)
                }
            }

            // --- LISTS ---
            MarkdownElementTypes.UNORDERED_LIST -> ListNode("bullet", true)
            MarkdownElementTypes.ORDERED_LIST -> ListNode("ordered", true)

            MarkdownElementTypes.LIST_ITEM -> {
                val isTask = text.contains(Regex("^\\[[ xX]\\]"))
                val isComplete = text.contains(Regex("^\\[[xX]\\]"))
                val cleanContent = text.replace(Regex("^([-*+]|\\d+\\.)\\s+(\\[[ xX]\\]\\s+)?"), "")

                ListItemNode(cleanContent, isTask, isComplete)
            }

            // --- TABLES ---
            GFMElementTypes.TABLE -> TableNode("[]")
            GFMElementTypes.ROW -> null

            // --- OTHERS ---
            MarkdownElementTypes.HTML_BLOCK -> HTMLBlockNode(text)
            GFMTokenTypes.TILDE -> ThematicBreakNode("---")

            else -> null
        }
    }

    private fun extractInlineLinks(content: String /*, sourceNodeId: Long */) {
        // 1. Obsidian WikiLinks [[Link]]
        wikiLinkRegex.findAll(content).forEach { match ->
            val linkTarget = match.groupValues[1]
            val alias = match.groupValues[2]
            // repository.createEdge(StandardSchemas.EDGE_LINKS_TO, sourceNodeId, linkTarget)
        }

        // 2. Obsidian Embeds ![[Image.png]]
        embedRegex.findAll(content).forEach { match ->
            val target = match.groupValues[1]
            // repository.createEdge(StandardSchemas.EDGE_EMBEDS, sourceNodeId, target)
        }
    }

    private fun extractHeaderText(raw: String): String = raw.trimStart('#', ' ').trim()

    private fun isStructuralNode(node: ASTNode): Boolean {
        return node.type != GFMTokenTypes.CHECK_BOX &&
                node.type != MarkdownTokenTypes.EOL
    }

    private data class ParsingContext(
        val rawText: String,
        var parentId: Long,
        var previousBlockId: Long?,
        val sectionStack: Stack<SectionNode> = Stack()
    )
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