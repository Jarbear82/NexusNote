package com.tau.nexus_note.doc_parser

import com.tau.nexus_note.CodexRepository
import com.tau.nexus_note.utils.writeTextFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MarkdownExporter(private val repository: CodexRepository) {

    suspend fun export(targetDirectory: String) = withContext(Dispatchers.IO) {
        val rootNodes = repository.findRootDocuments()
        val orphanedNodes = repository.findOrphanedNodes()

        // 1. Export Root Documents
        rootNodes.forEach { root ->
            val markdown = buildMarkdownForNode(root.id)
            val safeName = root.displayProperty.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val filename = "$safeName.md"
            // We assume a simple flat structure for now in the target directory
            // or we could recreate folder structure if "filepath" property is valid relative path.
            // For now, flat export to target directory.
            writeTextFile("$targetDirectory/$filename", markdown)
        }

        // 2. Export Orphans
        if (orphanedNodes.isNotEmpty()) {
            val orphanMd = StringBuilder("# Unsorted Nodes\n\n")
            orphanedNodes.forEach { node ->
                orphanMd.append("## ${node.displayProperty}\n")
                orphanMd.append(buildMarkdownForNode(node.id)).append("\n\n---\n\n")
            }
            writeTextFile("$targetDirectory/Unsorted.md", orphanMd.toString())
        }
    }

    private suspend fun buildMarkdownForNode(nodeId: Long, level: Int = 1): String {
        // Fixed: Use the alias added to Repository
        val nodeEditState = repository.getNodeById(nodeId) ?: return ""
        val node = nodeEditState.schema
        val props = nodeEditState.properties

        val children = repository.getChildrenSorted(nodeId)
        val sb = StringBuilder()

        // Render Self based on Schema Type
        when (node.name) {
            StandardSchemas.DOC_NODE_DOCUMENT -> {
                // Document node itself might have frontmatter, but usually just contains children
                // If it has "content" (preamble), render it.
                val content = props[StandardSchemas.PROP_CONTENT]
                if (!content.isNullOrBlank()) {
                    sb.append(resolveTemplates(content)).append("\n\n")
                }
            }
            StandardSchemas.DOC_NODE_SECTION -> {
                // Ensure level corresponds to markdown headers (#, ##, ###)
                // Use the level passed from recursion or from property if available
                val propLevel = props[StandardSchemas.PROP_LEVEL]?.toIntOrNull() ?: level
                val title = props[StandardSchemas.PROP_TITLE] ?: "Untitled"
                sb.append("${"#".repeat(propLevel)} $title\n\n")
            }
            StandardSchemas.DOC_NODE_BLOCK -> {
                val content = resolveTemplates(props[StandardSchemas.PROP_CONTENT] ?: "")
                sb.append("$content\n\n")
            }
            StandardSchemas.DOC_NODE_CODE_BLOCK -> {
                val content = props[StandardSchemas.PROP_CONTENT] ?: ""
                val lang = props[StandardSchemas.PROP_LANGUAGE] ?: ""
                sb.append("```$lang\n$content\n```\n\n")
            }
            StandardSchemas.DOC_NODE_ORDERED_ITEM -> {
                val content = resolveTemplates(props[StandardSchemas.PROP_CONTENT] ?: "")
                val num = props[StandardSchemas.PROP_NUMBER] ?: "1"
                sb.append("$num. $content\n")
            }
            StandardSchemas.DOC_NODE_UNORDERED_ITEM -> {
                val content = resolveTemplates(props[StandardSchemas.PROP_CONTENT] ?: "")
                val bullet = props[StandardSchemas.PROP_BULLET_CHAR] ?: "-"
                sb.append("$bullet $content\n")
            }
            StandardSchemas.DOC_NODE_TASK_ITEM -> {
                val content = resolveTemplates(props[StandardSchemas.PROP_CONTENT] ?: "")
                // Fixed: Safe nullable boolean check
                val checked = props[StandardSchemas.PROP_IS_CHECKED]?.toBoolean() ?: false
                val marker = if (checked) "[x]" else "[ ]"
                sb.append("- $marker $content\n")
            }
            // Add other types like Callouts, Tables as needed
            else -> {
                // Fallback for generic nodes: Render their display property as header?
                // Or just ignore if purely structural.
                val content = props["content"]
                if(content != null) sb.append("$content\n\n")
            }
        }

        // Recurse Children
        children.forEach { child ->
            // Determine next level. If current is Document, children start at 1?
            // Usually top level sections in doc are Level 1 or 2.
            val nextLevel = if (node.name == StandardSchemas.DOC_NODE_DOCUMENT) 1 else level + 1
            sb.append(buildMarkdownForNode(child.id, nextLevel))
        }

        return sb.toString()
    }

    private suspend fun resolveTemplates(content: String): String {
        // Replace {{tag:123}} with #tagName
        var text = content

        // Tags
        val tagRegex = Regex("\\{\\{tag:(\\d+)\\}\\}")

        // To properly support async replacement:
        return replaceAsync(text, tagRegex) { match ->
            val id = match.groupValues[1].toLong()
            val node = repository.getNodeById(id)
            if (node != null) {
                // Resolved: explicit type parameter on let not strictly needed but logic fixed by getNodeById existence
                val label = node.schema.properties.find{it.isDisplayProperty}?.let{ p -> node.properties[p.name]} ?: "tag"
                "#$label"
            } else match.value
        }
    }

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
}