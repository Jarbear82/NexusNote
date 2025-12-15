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
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.datamodels.GraphNode
import com.tau.nexusnote.datamodels.NodeContent
import com.tau.nexusnote.datamodels.SchemaConfig
import com.tau.nexusnote.utils.toAlphaIndex
import com.tau.nexusnote.utils.toPascalCase
import com.tau.nexusnote.utils.toRomanIndex
import kotlin.math.max
import kotlin.math.min

/**
 * Extension functions for drawing specific NodeContent types directly on the Graph Canvas.
 * Decouples specific rendering logic from the main GraphView.
 * Driven by SchemaConfig for styling.
 * Updated: Now uses density-aware sizing for all elements.
 */

@OptIn(ExperimentalTextApi::class)
fun DrawScope.drawTextNode(
    node: GraphNode,
    content: NodeContent.TextContent,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    backgroundColor: Color,
    borderColor: Color,
    config: SchemaConfig?,
    padding: Float
) {
    // Determine Style based on Config
    when (config) {
        is SchemaConfig.TextConfig.Heading -> {
            val headingStyle = style.copy(
                fontSize = style.fontSize * 1.5,
                fontWeight = FontWeight.Bold
            )
            drawStandardBox(node, backgroundColor, borderColor)
            drawWrappedText(node, content.value, textMeasurer, headingStyle, padding, center = true)
        }
        is SchemaConfig.TextConfig.Title -> {
            val titleStyle = style.copy(
                fontSize = style.fontSize * 2.0,
                fontWeight = FontWeight.Black
            )
            // Titles might have casing rules
            val text = if (config.casing == "TitleCase") content.value.toPascalCase() else content.value
            drawStandardBox(node, backgroundColor, borderColor)
            drawWrappedText(node, text, textMeasurer, titleStyle, padding, center = true)
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
            drawWrappedText(node, "#${content.value}", textMeasurer, tagStyle, padding, center = true)
        }
        else -> {
            // Default / PlainText (Primitive fallback)
            drawStandardBox(node, backgroundColor, borderColor)
            val displayText = content.value.take(140)
            drawWrappedText(node, displayText, textMeasurer, style, padding)
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
    borderColor: Color,
    padding: Float
) {
    val density = drawContext.density
    val topLeft = node.pos - Offset(node.width / 2, node.height / 2)
    val size = Size(node.width, node.height)

    val cornerRadius = with(density) { 8.dp.toPx() }
    val strokeWidth = with(density) { 2.dp.toPx() }

    // Background
    drawRoundRect(
        color = backgroundColor,
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
    )

    // Check config for showing filename, default true for primitive
    val showFilename = (node.config as? SchemaConfig.CodeConfig)?.showFilename ?: true

    // Header Bar
    val headerHeight = with(density) { 36.dp.toPx() }
    drawRoundRect(
        color = borderColor.copy(alpha = 0.5f),
        topLeft = topLeft,
        size = Size(node.width, headerHeight),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
    )
    drawRect(
        color = borderColor.copy(alpha = 0.5f),
        topLeft = topLeft + Offset(0f, headerHeight - with(density){5.dp.toPx()}),
        size = Size(node.width, with(density){5.dp.toPx()})
    )

    // Border
    drawRoundRect(
        color = borderColor,
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        style = Stroke(width = strokeWidth)
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
        topLeft = topLeft + Offset(padding, with(density){4.dp.toPx()})
    )

    // Code Content
    val codeStyle = style.copy(fontFamily = FontFamily.Monospace, color = Color(0xFFA9B7C6))
    val codeLayout = textMeasurer.measure(
        text = AnnotatedString(content.code),
        style = codeStyle,
        constraints = Constraints(maxWidth = (node.width - padding * 2).toInt())
    )
    drawText(
        textLayoutResult = codeLayout,
        topLeft = topLeft + Offset(padding, headerHeight + padding/2)
    )
}

@OptIn(ExperimentalTextApi::class)
fun DrawScope.drawTableNode(
    node: GraphNode,
    content: NodeContent.TableContent,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    backgroundColor: Color,
    borderColor: Color,
    padding: Float
) {
    val density = drawContext.density
    val topLeft = node.pos - Offset(node.width / 2, node.height / 2)
    drawStandardBox(node, backgroundColor, borderColor)

    val showColHeaders = (node.config as? SchemaConfig.TableConfig)?.showColumnHeaders ?: true
    val rowHeaderType = (node.config as? SchemaConfig.TableConfig)?.rowHeaderType ?: "None"
    val hasRowHeaders = rowHeaderType != "None"

    // Calculate dimensions
    val rowHeight = with(density) { 30.dp.toPx() }

    // Determine number of visible columns (Data columns + 1 if row header exists)
    val dataColCount = max(1, content.headers.size)
    val visualColCount = if (hasRowHeaders) dataColCount + 1 else dataColCount
    val colWidth = node.width / visualColCount

    // Prepare rows including headers if needed
    val rowsToRender = if (showColHeaders) listOf(content.headers) + content.rows else content.rows

    rowsToRender.take(min(rowsToRender.size, (node.height / rowHeight).toInt())).forEachIndexed { rowIndex, row ->
        val y = topLeft.y + (rowIndex * rowHeight) + padding

        // Draw Divider
        if (rowIndex > 0) {
            drawLine(
                color = borderColor.copy(alpha = 0.3f),
                start = Offset(topLeft.x, y),
                end = Offset(topLeft.x + node.width, y)
            )
        }

        // Draw Row Header (Column 0)
        if (hasRowHeaders) {
            // Determine header text
            val headerText = if (showColHeaders && rowIndex == 0) {
                "#" // Corner cell
            } else {
                val dataIndex = if (showColHeaders) rowIndex else rowIndex + 1
                if (rowHeaderType == "Numeric") "$dataIndex"
                else "${('A'.code + (dataIndex - 1) % 26).toChar()}" // Simple Alpha
            }

            val measured = textMeasurer.measure(
                text = AnnotatedString(headerText),
                style = style.copy(fontWeight = FontWeight.Bold, color = style.color.copy(alpha = 0.7f)),
                constraints = Constraints(maxWidth = (colWidth - padding).toInt())
            )
            drawText(measured, topLeft = Offset(topLeft.x + padding/2, y + with(density){5.dp.toPx()}))

            // Draw Vertical Separator
            drawLine(
                color = borderColor.copy(alpha = 0.3f),
                start = Offset(topLeft.x + colWidth, topLeft.y),
                end = Offset(topLeft.x + colWidth, topLeft.y + node.height)
            )
        }

        // Draw Data Cells
        row.forEachIndexed { colIndex, text ->
            // Shift x based on whether we have a row header
            val visualColIndex = if (hasRowHeaders) colIndex + 1 else colIndex
            val x = topLeft.x + (visualColIndex * colWidth) + padding/2

            val cellStyle = if(showColHeaders && rowIndex == 0) style.copy(fontWeight = FontWeight.Bold) else style

            val measured = textMeasurer.measure(
                text = AnnotatedString(text),
                style = cellStyle,
                maxLines = 1,
                constraints = Constraints(maxWidth = (colWidth - padding).toInt())
            )
            drawText(measured, topLeft = Offset(x, y + with(density){5.dp.toPx()}))
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
    config: SchemaConfig?,
    padding: Float,
    gap: Float
) {
    val density = drawContext.density
    drawStandardBox(node, backgroundColor, borderColor)
    val topLeft = node.pos - Offset(node.width / 2, node.height / 2)
    val startX = topLeft.x + padding

    // Start Y accumulation
    var currentY = topLeft.y + padding

    content.items.take(10).forEachIndexed { index, item ->
        val isTask = config is SchemaConfig.ListConfig.Task
        val isOrdered = config is SchemaConfig.ListConfig.Ordered
        val isUnordered = config is SchemaConfig.ListConfig.Unordered

        var textOffset = with(density) { 20.dp.toPx() }

        if (isTask) {
            // Draw Checkbox
            val boxSize = with(density) { 12.dp.toPx() }
            val checkSize = with(density) { 8.dp.toPx() }

            val checkboxRect = Rect(startX, currentY + 2f, startX + boxSize, currentY + boxSize + 2f)
            drawRect(
                color = borderColor,
                topLeft = checkboxRect.topLeft,
                size = Size(boxSize, boxSize),
                style = Stroke(width = with(density){1.dp.toPx()})
            )
            if (item.isCompleted) {
                drawRect(
                    color = borderColor,
                    topLeft = checkboxRect.topLeft + Offset((boxSize-checkSize)/2, (boxSize-checkSize)/2),
                    size = Size(checkSize, checkSize)
                )
            }
        } else if (isOrdered) {
            val orderedConfig = config as SchemaConfig.ListConfig.Ordered
            // Draw Number/Alpha/Roman
            val indicator = when(orderedConfig.indicatorType) {
                "Numeric" -> "${index + 1}."
                "AlphaUpper" -> "${index.toAlphaIndex(true)}."
                "AlphaLower" -> "${index.toAlphaIndex(false)}."
                "RomanUpper" -> "${index.toRomanIndex(true)}."
                "RomanLower" -> "${index.toRomanIndex(false)}."
                else -> "${index + 1}."
            }
            val numLayout = textMeasurer.measure(AnnotatedString(indicator), style)
            drawText(numLayout, topLeft = Offset(startX, currentY))
            textOffset = numLayout.size.width + padding/2
        } else if (isUnordered) {
            // Draw configured symbol
            val symbol = (config as SchemaConfig.ListConfig.Unordered).indicatorSymbol
            val symLayout = textMeasurer.measure(AnnotatedString(symbol), style)
            drawText(symLayout, topLeft = Offset(startX, currentY))
            textOffset = symLayout.size.width + padding/2
        } else {
            // Primitive Fallback (Bullet)
            drawCircle(
                color = borderColor,
                radius = with(density) { 3.dp.toPx() },
                center = Offset(startX + with(density){6.dp.toPx()}, currentY + with(density){8.dp.toPx()})
            )
        }

        // Draw Text
        val textLayout = textMeasurer.measure(
            text = AnnotatedString(item.text),
            style = style,
            constraints = Constraints(maxWidth = (node.width - padding * 2 - textOffset).toInt())
        )
        drawText(
            textLayoutResult = textLayout,
            topLeft = Offset(startX + textOffset, currentY)
        )

        // Advance Y based on real text height plus gap
        currentY += textLayout.size.height + gap
    }
}

@OptIn(ExperimentalTextApi::class)
fun DrawScope.drawImageNode(
    node: GraphNode,
    content: NodeContent.MediaContent,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    backgroundColor: Color,
    borderColor: Color,
    padding: Float
) {
    val density = drawContext.density
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
        style = Stroke(width = with(density) { 4.dp.toPx() })
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
                node.height - labelLayout.size.height - padding/4
            )
        )
    }
}

// --- Helpers ---

private fun DrawScope.drawStandardBox(node: GraphNode, bgColor: Color, borderColor: Color) {
    val density = drawContext.density
    val cornerRadius = with(density) { 8.dp.toPx() }
    val strokeWidth = with(density) { 2.dp.toPx() }
    val topLeft = node.pos - Offset(node.width / 2, node.height / 2)

    drawRoundRect(
        color = bgColor,
        topLeft = topLeft,
        size = Size(node.width, node.height),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
    )
    drawRoundRect(
        color = borderColor,
        topLeft = topLeft,
        size = Size(node.width, node.height),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        style = Stroke(width = strokeWidth)
    )
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawWrappedText(
    node: GraphNode,
    text: String,
    measurer: TextMeasurer,
    style: TextStyle,
    padding: Float,
    center: Boolean = false
) {
    val maxWidth = (node.width - padding * 2).toInt()
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
        Offset(padding, padding)
    }

    drawText(
        textLayoutResult = layoutResult,
        topLeft = topLeft + textOffset
    )
}