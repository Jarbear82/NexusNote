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
import androidx.compose.ui.unit.Constraints
import com.tau.nexusnote.datamodels.GraphNode
import com.tau.nexusnote.datamodels.NodeContent
import com.tau.nexusnote.datamodels.SchemaConfig
import com.tau.nexusnote.utils.toPascalCase
import kotlin.math.min

/**
 * Extension functions for drawing specific NodeContent types directly on the Graph Canvas.
 * Decouples specific rendering logic from the main GraphView.
 * Driven by SchemaConfig for styling.
 */

// --- Constants ---
private const val PADDING = 16f

@OptIn(ExperimentalTextApi::class)
fun DrawScope.drawTextNode(
    node: GraphNode,
    content: NodeContent.TextContent,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    backgroundColor: Color,
    borderColor: Color,
    config: SchemaConfig?
) {
    // Determine Style based on Config
    when (config) {
        is SchemaConfig.TextConfig.Heading -> {
            val headingStyle = style.copy(
                fontSize = style.fontSize * 1.5,
                fontWeight = FontWeight.Bold
            )
            drawStandardBox(node, backgroundColor, borderColor)
            drawWrappedText(node, content.value, textMeasurer, headingStyle, center = true)
        }
        is SchemaConfig.TextConfig.Title -> {
            val titleStyle = style.copy(
                fontSize = style.fontSize * 2.0,
                fontWeight = FontWeight.Black
            )
            // Titles might have casing rules
            val text = if (config.casing == "TitleCase") content.value.toPascalCase() else content.value
            drawStandardBox(node, backgroundColor, borderColor)
            drawWrappedText(node, text, textMeasurer, titleStyle, center = true)
        }
        is SchemaConfig.TextConfig.Tag -> {
            val tagStyle = style.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            val pillColor = node.colorInfo.composeColor // Use node color as background for tags
            // Draw Pill Shape
            val topLeft = node.pos - Offset(node.width / 2, node.height / 2)
            drawRoundRect(
                color = pillColor,
                topLeft = topLeft,
                size = Size(node.width, node.height),
                cornerRadius = CornerRadius(node.height / 2, node.height / 2)
            )
            drawWrappedText(node, "#${content.value}", textMeasurer, tagStyle, center = true)
        }
        else -> {
            // Default / PlainText (Primitive fallback)
            drawStandardBox(node, backgroundColor, borderColor)
            val displayText = content.value.take(140)
            drawWrappedText(node, displayText, textMeasurer, style)
        }
    }
}

@OptIn(ExperimentalTextApi::class)
fun DrawScope.drawCodeNode(
    node: GraphNode,
    content: NodeContent.CodeContent,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    backgroundColor: Color,
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

    // Check config for showing filename, default true for primitive
    val showFilename = (node.config as? SchemaConfig.CodeConfig)?.showFilename ?: true

    // Header Bar
    val headerHeight = 24f
    drawRoundRect(
        color = borderColor.copy(alpha = 0.5f),
        topLeft = topLeft,
        size = Size(node.width, headerHeight),
        cornerRadius = CornerRadius(8f, 8f)
    )
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

    // Header Text
    var headerText = "[${content.language}]"
    if (showFilename && !content.filename.isNullOrBlank()) {
        headerText = "${content.filename} $headerText"
    }

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

    // Check config for header row visibility, default true
    val showHeaders = (node.config as? SchemaConfig.TableConfig)?.showColumnHeaders ?: true

    // If headers are hidden, treat the first row of data as the first visual row
    val rowsToRender = if (showHeaders) listOf(content.headers) + content.rows else content.rows

    val rowHeight = 30f
    val colWidth = node.width / content.headers.size.coerceAtLeast(1)

    rowsToRender.take(min(rowsToRender.size, (node.height / rowHeight).toInt())).forEachIndexed { rowIndex, row ->
        val y = topLeft.y + (rowIndex * rowHeight) + PADDING

        if (rowIndex > 0) {
            drawLine(
                color = borderColor.copy(alpha = 0.3f),
                start = Offset(topLeft.x, y),
                end = Offset(topLeft.x + node.width, y)
            )
        }

        row.forEachIndexed { colIndex, text ->
            val x = topLeft.x + (colIndex * colWidth) + 8f
            // Bold the header row if we are showing headers and this is the first row
            val cellStyle = if(showHeaders && rowIndex == 0) style.copy(fontWeight = FontWeight.Bold) else style

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
fun DrawScope.drawListNode(
    node: GraphNode,
    content: NodeContent.ListContent,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    backgroundColor: Color,
    borderColor: Color,
    config: SchemaConfig?
) {
    drawStandardBox(node, backgroundColor, borderColor)
    val topLeft = node.pos - Offset(node.width / 2, node.height / 2)
    val startY = topLeft.y + PADDING
    val startX = topLeft.x + PADDING

    content.items.take(10).forEachIndexed { index, item ->
        val y = startY + (index * 24f)

        // Determine indicator
        val isTask = config is SchemaConfig.ListConfig.Task
        val isOrdered = config is SchemaConfig.ListConfig.Ordered

        var textOffset = 20f

        if (isTask) {
            // Draw Checkbox
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
        } else if (isOrdered) {
            // Draw Number
            val numStr = "${index + 1}."
            val numLayout = textMeasurer.measure(AnnotatedString(numStr), style)
            drawText(numLayout, topLeft = Offset(startX, y))
            textOffset = numLayout.size.width + 8f
        } else {
            // Draw Bullet (Default for Primitives)
            drawCircle(
                color = borderColor,
                radius = 3f,
                center = Offset(startX + 6f, y + 10f)
            )
        }

        // Draw Text
        val textLayout = textMeasurer.measure(
            text = AnnotatedString(item.text),
            style = style,
            constraints = Constraints(maxWidth = (node.width - PADDING * 2 - textOffset).toInt())
        )
        drawText(
            textLayoutResult = textLayout,
            topLeft = Offset(startX + textOffset, y)
        )
    }
}

@OptIn(ExperimentalTextApi::class)
fun DrawScope.drawImageNode(
    node: GraphNode,
    content: NodeContent.MediaContent,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    backgroundColor: Color,
    borderColor: Color
) {
    val topLeft = node.pos - Offset(node.width / 2, node.height / 2)
    val size = Size(node.width, node.height)

    drawRect(
        color = Color.DarkGray,
        topLeft = topLeft,
        size = size
    )
    drawRect(
        color = borderColor,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = 4f)
    )

    // Placeholder Icon logic
    val center = node.pos
    val iconSize = min(node.width, node.height) * 0.3f

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