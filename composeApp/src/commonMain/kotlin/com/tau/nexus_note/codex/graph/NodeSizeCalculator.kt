package com.tau.nexus_note.codex.graph

import androidx.compose.ui.geometry.Size
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.datamodels.NodeStyle
import com.tau.nexus_note.doc_parser.StandardSchemas
import com.tau.nexus_note.utils.PropertySerialization
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Holds pre-calculated pixel values based on current screen density.
 */
data class SizingContext(
    val charWidthPx: Float,
    val lineHeightPx: Float,
    val paddingPx: Float,
    val titleScale: Float = 1.7f,
    val headingScale: Float = 1.4f
)

object NodeSizeCalculator {

    fun calculate(node: NodeDisplayItem, ctx: SizingContext): Size {
        // Always Expanded State
        return when (node.style) {
            NodeStyle.TITLE -> calculateSquarifiedText(node.displayProperty, ctx.titleScale, ctx)
            NodeStyle.HEADING -> calculateSquarifiedText(node.displayProperty, ctx.headingScale, ctx)

            NodeStyle.LONG_TEXT, NodeStyle.BLOCK, NodeStyle.SHORT_TEXT,
            NodeStyle.DOCUMENT, NodeStyle.SECTION, NodeStyle.ATTACHMENT -> {
                val content = node.properties[StandardSchemas.PROP_CONTENT] ?: node.displayProperty
                calculateSquarifiedText(content, 1.0f, ctx)
            }

            NodeStyle.CODE_BLOCK -> {
                val code = node.properties[StandardSchemas.PROP_CONTENT] ?: ""
                calculateCodeBlock(code, ctx)
            }

            NodeStyle.LIST, NodeStyle.SET, NodeStyle.UNORDERED_LIST, NodeStyle.ORDERED_LIST -> {
                val json = node.properties[StandardSchemas.PROP_LIST_ITEMS] ?: "[]"
                calculateList(json, ctx)
            }

            NodeStyle.MAP -> {
                val json = node.properties[StandardSchemas.PROP_MAP_DATA] ?: "{}"
                calculateMap(json, ctx)
            }

            NodeStyle.TABLE -> {
                val headers = node.properties[StandardSchemas.PROP_HEADERS] ?: "[]"
                val data = node.properties[StandardSchemas.PROP_DATA] ?: "[]"
                calculateTable(headers, data, ctx)
            }

            NodeStyle.IMAGE -> {
                val w = node.properties[StandardSchemas.PROP_IMG_WIDTH]
                val h = node.properties[StandardSchemas.PROP_IMG_HEIGHT]
                calculateImage(w, h, ctx)
            }

            else -> Size(250f, 150f)
        }
    }

    private fun calculateSquarifiedText(text: String, fontScale: Float, ctx: SizingContext): Size {
        if (text.isBlank()) return Size(100f, 60f)

        val cw = ctx.charWidthPx * fontScale
        val lh = ctx.lineHeightPx * fontScale

        val len = text.length
        // Target roughly square aspect ratio
        val targetCharsPerLine = sqrt(len.toFloat() * 2.2f).toInt().coerceAtLeast(15)

        var maxLineLength = 0
        var currentLineLength = 0
        var lineCount = 1

        val words = text.split(Regex("\\s+"))
        for (word in words) {
            val wordLen = word.length
            // Wrap logic
            if (currentLineLength + wordLen > targetCharsPerLine && currentLineLength > 0) {
                lineCount++
                maxLineLength = max(maxLineLength, currentLineLength)
                currentLineLength = wordLen + 1
            } else {
                currentLineLength += wordLen + 1
            }
        }
        maxLineLength = max(maxLineLength, currentLineLength)

        return Size(
            (maxLineLength * cw) + ctx.paddingPx,
            (lineCount * lh) + ctx.paddingPx
        )
    }

    private fun calculateCodeBlock(code: String, ctx: SizingContext): Size {
        val lines = code.lines()
        val maxLen = lines.maxOfOrNull { it.length } ?: 15
        val lineCount = lines.size.coerceIn(2, 50)

        val width = (maxLen * ctx.charWidthPx) + ctx.paddingPx + 20f
        val height = (lineCount * ctx.lineHeightPx) + ctx.paddingPx + 30f // Header buffer

        return Size(width.coerceAtLeast(200f), height)
    }

    private fun calculateList(json: String, ctx: SizingContext): Size {
        val items = PropertySerialization.deserializeList(json)
        if (items.isEmpty()) return Size(200f, 60f)

        val maxAllowedWidth = 30 // Constraint from user

        // Calculate max width used (clamped to 30)
        val actualMaxLen = items.maxOfOrNull { it.length } ?: 10
        val effectiveWidthChars = actualMaxLen.coerceAtMost(maxAllowedWidth)

        var totalLines = 0
        items.forEach { item ->
            // Wrap within the 30 char limit
            val linesForItem = ceil(item.length.toFloat() / maxAllowedWidth).toInt().coerceAtLeast(1)
            totalLines += linesForItem
        }

        val width = (effectiveWidthChars * ctx.charWidthPx) + ctx.paddingPx + 20f // +Bullets
        val height = (totalLines * ctx.lineHeightPx) + ctx.paddingPx

        return Size(width, height)
    }

    private fun calculateTable(headersJson: String, dataJson: String, ctx: SizingContext): Size {
        val headers = PropertySerialization.deserializeList(headersJson)
        val rows = PropertySerialization.deserializeListOfMaps(dataJson)

        val colCount = headers.size.coerceAtLeast(1)
        val rowCount = rows.size.coerceAtMost(10) // Render limit for graph view

        val maxColWidth = 20 // Constraint from user

        // 1. Calculate Width
        // Simplified heuristic: assume columns fill up to maxColWidth if data exists
        // A more accurate approach would involve scanning data, but for graph layout
        // assuming standard width per column is often sufficient and faster.
        // Let's implement the requested heuristic: find max width per column.

        var totalTableWidthChars = 0
        val colWidths = IntArray(colCount)

        for (i in 0 until colCount) {
            val headerKey = if (i < headers.size) headers[i] else ""
            var maxLen = headerKey.length

            // Scan rows (limit to 10 for performance)
            rows.take(10).forEach { row ->
                val cellContent = row[headerKey] ?: row.values.elementAtOrNull(i) ?: ""
                maxLen = max(maxLen, cellContent.length)
            }

            // Apply cap
            colWidths[i] = maxLen.coerceAtMost(maxColWidth)
            totalTableWidthChars += colWidths[i]
        }

        // 2. Calculate Height
        var totalRowLines = 1 // Header
        rows.take(10).forEach { row ->
            var maxLinesInRow = 1
            for (i in 0 until colCount) {
                val headerKey = if (i < headers.size) headers[i] else ""
                val cellContent = row[headerKey] ?: row.values.elementAtOrNull(i) ?: ""
                // Wrap logic for cell
                val lines = ceil(cellContent.length.toFloat() / maxColWidth).toInt().coerceAtLeast(1)
                maxLinesInRow = max(maxLinesInRow, lines)
            }
            totalRowLines += maxLinesInRow
        }

        val width = (totalTableWidthChars * ctx.charWidthPx) + ctx.paddingPx + (colCount * 10f) // Spacing
        val height = (totalRowLines * (ctx.lineHeightPx + 5f)) + ctx.paddingPx

        return Size(width.coerceAtLeast(200f), height)
    }

    private fun calculateMap(json: String, ctx: SizingContext): Size {
        val map = PropertySerialization.deserializeMap(json)
        val count = map.size.coerceAtMost(10)
        // Key + Value width approximation (e.g. 35 chars)
        val width = (35 * ctx.charWidthPx) + ctx.paddingPx
        val height = (count * ctx.lineHeightPx) + ctx.paddingPx + 20f
        return Size(width, height)
    }

    private fun calculateImage(wStr: String?, hStr: String?, ctx: SizingContext): Size {
        val w = wStr?.toIntOrNull() ?: 0
        val h = hStr?.toIntOrNull() ?: 0

        // Fallback size
        if (w <= 0 || h <= 0) return Size(300f, 225f)

        val aspectRatio = w.toFloat() / h.toFloat()

        // Constrain to a reasonable graph node width, e.g., 350px
        val targetW = 350f
        val targetH = targetW / aspectRatio

        return Size(targetW, targetH + 40f) // +40 for caption/border
    }
}