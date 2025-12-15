package com.tau.nexusnote.codex.graph

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.datamodels.GraphNode
import com.tau.nexusnote.datamodels.NodeContent
import com.tau.nexusnote.datamodels.TaskItem
import com.tau.nexusnote.utils.toPascalCase
import kotlin.math.min

/**
 * Extension functions for drawing specific NodeContent types directly on the Graph Canvas.
 * This decouples specific rendering logic from the main GraphView.
 */

// --- Constants ---
private const val PADDING = 16f
private const val HEADER_HEIGHT = 24f
private const val LINE_HEIGHT = 20f

@OptIn(ExperimentalTextApi::class)
fun DrawScope.drawTitleNode(
    node: GraphNode,
    content: NodeContent.TextContent,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    backgroundColor: Color,
    borderColor: Color
) {
    val titleStyle = style.copy(
        fontSize = style.fontSize * 1.5,
        fontWeight = FontWeight.Bold
    )
    drawStandardBox(node, backgroundColor, borderColor)
    drawWrappedText(node, content.text.toPascalCase(), textMeasurer, titleStyle, center = true)
}

@OptIn(ExperimentalTextApi::class)
fun DrawScope.drawHeadingNode(
    node: GraphNode,
    content: NodeContent.TextContent,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    backgroundColor: Color,
    borderColor: Color
) {
    val headingStyle = style.copy(
        fontSize = style.fontSize * 1.2,
        fontWeight = FontWeight.Bold
    )
    drawStandardBox(node, backgroundColor, borderColor)
    drawWrappedText(node, content.text, textMeasurer, headingStyle, center = true)
}

@OptIn(ExperimentalTextApi::class)
fun DrawScope.drawTextNode(
    node: GraphNode,
    content: NodeContent.TextContent,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    backgroundColor: Color,
    borderColor: Color,
    isShort: Boolean = false
) {
    drawStandardBox(node, backgroundColor, borderColor)
    val displayText = if (isShort) content.text.take(140) else content.text
    drawWrappedText(node, displayText, textMeasurer, style)
}

@OptIn(ExperimentalTextApi::class)
fun DrawScope.drawCodeNode(
    node: GraphNode,
    content: NodeContent.CodeContent,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    backgroundColor: Color, // Usually darker for code
    borderColor: Color
) {
    val topLeft = node.pos - Offset(node.width / 2, node.height / 2)
    val size = Size(node.width, node.height)

    // Background
    drawRoundRect(
        color = backgroundColor,
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(8f, 8f)
    )

    // Header Bar
    val headerHeight = 24f
    drawRoundRect(
        color = borderColor.copy(alpha = 0.5f),
        topLeft = topLeft,
        size = Size(node.width, headerHeight),
        cornerRadius = CornerRadius(8f, 8f) // Clip bottom in real clipping, simplified here
    )
    // Draw straight rect to cover bottom corners of header to make them square
    drawRect(
        color = borderColor.copy(alpha = 0.5f),
        topLeft = topLeft + Offset(0f, headerHeight - 5f),
        size = Size(node.width, 5f)
    )

    // Border
    drawRoundRect(
        color = borderColor,
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = 2f)
    )

    // Header Text (Filename + Language)
    val headerText = (content.filename ?: "") + " [${content.language}]"
    val headerStyle = style.copy(fontSize = style.fontSize * 0.8, color = Color.White)
    val headerLayout = textMeasurer.measure(
        text = AnnotatedString(headerText),
        style = headerStyle
    )
    drawText(
        textLayoutResult = headerLayout,
        topLeft = topLeft + Offset(8f, 4f)
    )

    // Code Content
    val codeStyle = style.copy(fontFamily = FontFamily.Monospace, color = Color(0xFFA9B7C6))
    val codeLayout = textMeasurer.measure(
        text = AnnotatedString(content.code),
        style = codeStyle,
        constraints = Constraints(maxWidth = (node.width - 16).toInt())
    )
    drawText(
        textLayoutResult = codeLayout,
        topLeft = topLeft + Offset(8f, headerHeight + 8f)
    )
}

@OptIn(ExperimentalTextApi::class)
fun DrawScope.drawTableNode(
    node: GraphNode,
    content: NodeContent.TableContent,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    backgroundColor: Color,
    borderColor: Color
) {
    val topLeft = node.pos - Offset(node.width / 2, node.height / 2)
    drawStandardBox(node, backgroundColor, borderColor)

    // Simple grid rendering
    val rows = listOf(content.headers) + content.rows
    val rowHeight = 30f
    val colWidth = node.width / content.headers.size.coerceAtLeast(1)

    rows.take(min(rows.size, (node.height / rowHeight).toInt())).forEachIndexed { rowIndex, row ->
        val y = topLeft.y + (rowIndex * rowHeight) + PADDING

        // Draw horizontal line
        if (rowIndex > 0) {
            drawLine(
                color = borderColor.copy(alpha = 0.3f),
                start = Offset(topLeft.x, y),
                end = Offset(topLeft.x + node.width, y)
            )
        }

        // Draw Cell Text
        row.forEachIndexed { colIndex, text ->
            val x = topLeft.x + (colIndex * colWidth) + 8f
            val cellStyle = if(rowIndex == 0) style.copy(fontWeight = FontWeight.Bold) else style

            // Clip text
            val measured = textMeasurer.measure(
                text = AnnotatedString(text),
                style = cellStyle,
                maxLines = 1,
                constraints = Constraints(maxWidth = (colWidth - 16).toInt())
            )

            drawText(
                textLayoutResult = measured,
                topLeft = Offset(x, y + 5f)
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
fun DrawScope.drawTaskListNode(
    node: GraphNode,
    content: NodeContent.TaskListContent,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    backgroundColor: Color,
    borderColor: Color
) {
    drawStandardBox(node, backgroundColor, borderColor)
    val topLeft = node.pos - Offset(node.width / 2, node.height / 2)
    val startY = topLeft.y + PADDING
    val startX = topLeft.x + PADDING

    content.items.take(10).forEachIndexed { index, item ->
        val y = startY + (index * 24f)

        // Checkbox
        val checkboxRect = Rect(startX, y + 4f, startX + 12f, y + 16f)
        drawRect(
            color = borderColor,
            topLeft = checkboxRect.topLeft,
            size = Size(12f, 12f),
            style = Stroke(width = 1f)
        )
        if (item.isCompleted) {
            drawRect(
                color = borderColor,
                topLeft = checkboxRect.topLeft + Offset(2f, 2f),
                size = Size(8f, 8f)
            )
        }

        // Text
        val textLayout = textMeasurer.measure(
            text = AnnotatedString(item.text),
            style = style,
            constraints = Constraints(maxWidth = (node.width - PADDING * 2 - 20f).toInt())
        )
        drawText(
            textLayoutResult = textLayout,
            topLeft = Offset(startX + 20f, y)
        )
    }
}

@OptIn(ExperimentalTextApi::class)
fun DrawScope.drawTagNode(
    node: GraphNode,
    content: NodeContent.TagContent,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    backgroundColor: Color, // Often the tag color
    borderColor: Color
) {
    val topLeft = node.pos - Offset(node.width / 2, node.height / 2)

    // Draw Pill Shape
    drawRoundRect(
        color = backgroundColor,
        topLeft = topLeft,
        size = Size(node.width, node.height),
        cornerRadius = CornerRadius(node.height / 2, node.height / 2)
    )

    // Text
    val tagText = "#${content.name}"
    drawWrappedText(node, tagText, textMeasurer, style.copy(color = Color.White), center = true)
}

@OptIn(ExperimentalTextApi::class)
fun DrawScope.drawImageNode(
    node: GraphNode,
    content: NodeContent.ImageContent,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    backgroundColor: Color,
    borderColor: Color
) {
    val topLeft = node.pos - Offset(node.width / 2, node.height / 2)
    val size = Size(node.width, node.height)

    // Image Frame
    drawRect(
        color = Color.DarkGray, // Placeholder background
        topLeft = topLeft,
        size = size
    )
    drawRect(
        color = borderColor,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = 4f)
    )

    // Placeholder Icon logic (simplified geometric representation)
    val center = node.pos
    val iconSize = min(node.width, node.height) * 0.3f

    // Draw "Mountain" shape for image placeholder
    drawLine(
        color = Color.LightGray,
        start = center - Offset(iconSize, -iconSize/2),
        end = center - Offset(0f, iconSize),
        strokeWidth = 2f
    )
    drawLine(
        color = Color.LightGray,
        start = center - Offset(0f, iconSize),
        end = center + Offset(iconSize, iconSize/2),
        strokeWidth = 2f
    )

    // Filename Label (Bottom overlay)
    if (!content.caption.isNullOrBlank()) {
        val labelStyle = style.copy(color = Color.White, background = Color.Black.copy(alpha = 0.6f))
        val labelLayout = textMeasurer.measure(AnnotatedString(content.caption), labelStyle)

        drawText(
            textLayoutResult = labelLayout,
            topLeft = topLeft + Offset(
                (node.width - labelLayout.size.width) / 2,
                node.height - labelLayout.size.height - 4f
            )
        )
    }
}

// --- Helpers ---

private fun DrawScope.drawStandardBox(node: GraphNode, bgColor: Color, borderColor: Color) {
    val topLeft = node.pos - Offset(node.width / 2, node.height / 2)
    drawRoundRect(
        color = bgColor,
        topLeft = topLeft,
        size = Size(node.width, node.height),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawRoundRect(
        color = borderColor,
        topLeft = topLeft,
        size = Size(node.width, node.height),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = 2f)
    )
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawWrappedText(
    node: GraphNode,
    text: String,
    measurer: TextMeasurer,
    style: TextStyle,
    center: Boolean = false
) {
    val maxWidth = (node.width - PADDING * 2).toInt()
    val layoutResult = measurer.measure(
        text = AnnotatedString(text),
        style = style,
        constraints = Constraints(maxWidth = maxWidth)
    )

    val topLeft = node.pos - Offset(node.width / 2, node.height / 2)
    val textOffset = if (center) {
        Offset(
            (node.width - layoutResult.size.width) / 2,
            (node.height - layoutResult.size.height) / 2
        )
    } else {
        Offset(PADDING, PADDING)
    }

    drawText(
        textLayoutResult = layoutResult,
        topLeft = topLeft + textOffset
    )
}