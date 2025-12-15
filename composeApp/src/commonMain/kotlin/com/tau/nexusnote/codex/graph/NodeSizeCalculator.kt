package com.tau.nexusnote.codex.graph

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tau.nexusnote.datamodels.NodeContent
import com.tau.nexusnote.datamodels.SchemaConfig
import kotlin.math.max

/**
 * Service responsible for calculating the visual dimensions of a node based on its content.
 * This allows the physics engine to run with accurate bounding boxes.
 */
@OptIn(ExperimentalTextApi::class)
class NodeSizeCalculator(
    private val textMeasurer: TextMeasurer,
    private val density: Density
) {
    // --- Standard Typography Styles (Matching App Typography roughly) ---
    private val titleStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    )
    private val headingStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    )
    private val bodyStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp
    )
    private val codeStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp
    )

    // --- Constraints ---
    private val minNodeWidth = 50.dp
    private val maxNodeWidth = 300.dp // Max width for text blocks before wrapping
    private val padding = 16.dp

    fun measure(content: NodeContent, config: SchemaConfig?): Size {
        return when (content) {
            is NodeContent.TextContent -> measureText(content, config)
            is NodeContent.CodeContent -> measureCode(content, config)
            is NodeContent.TableContent -> measureTable(content, config)
            is NodeContent.ImageContent -> measureImage(content)
            is NodeContent.MapContent -> measureMap() // Default size for standard nodes
            is NodeContent.ListContent -> measureList(content.items)
            is NodeContent.TaskListContent -> measureList(content.items.map { it.text })
            is NodeContent.SetContent -> measureList(content.items.toList())
            is NodeContent.DateTimestampContent -> Size(120f, 60f)
            is NodeContent.TagContent -> Size(100f, 40f)
        }
    }

    private fun measureText(content: NodeContent.TextContent, config: SchemaConfig?): Size {
        val style = when (config) {
            is SchemaConfig.TitleConfig -> titleStyle
            is SchemaConfig.HeadingConfig -> headingStyle
            else -> bodyStyle
        }

        // Apply width constraints for wrapping
        val maxWidthPx = with(density) { maxNodeWidth.toPx() }.toInt()
        val minWidthPx = with(density) { minNodeWidth.toPx() }.toInt()

        val result = textMeasurer.measure(
            text = AnnotatedString(content.text),
            style = style,
            constraints = Constraints(maxWidth = maxWidthPx)
        )

        val width = max(minWidthPx, result.size.width) + with(density) { padding.toPx() * 2 }
        val height = result.size.height + with(density) { padding.toPx() * 2 }

        return Size(width.toFloat(), height.toFloat())
    }

    private fun measureCode(content: NodeContent.CodeContent, config: SchemaConfig?): Size {
        val showFilename = (config as? SchemaConfig.CodeBlockConfig)?.showFilename ?: true
        val maxWidthPx = with(density) { 400.dp.toPx() }.toInt() // Code blocks can be wider

        // Measure Code Content
        val codeResult = textMeasurer.measure(
            text = AnnotatedString(content.code),
            style = codeStyle,
            constraints = Constraints(maxWidth = maxWidthPx)
        )

        var height = codeResult.size.height.toFloat()
        var width = codeResult.size.width.toFloat()

        // Add Header Height if filename is shown
        if (showFilename || content.filename != null) {
            val headerHeight = with(density) { 24.dp.toPx() }
            height += headerHeight
        }

        // Add Padding
        val paddingPx = with(density) { padding.toPx() }
        return Size(width + paddingPx * 2, height + paddingPx * 2)
    }

    private fun measureTable(content: NodeContent.TableContent, config: SchemaConfig?): Size {
        val colCount = max(1, content.headers.size)
        val rowCount = content.rows.size + 1 // +1 for header

        // Estimate based on fixed cell size for performance
        // Real implementation might measure each cell, but that's expensive.
        val estColWidth = with(density) { 100.dp.toPx() }
        val estRowHeight = with(density) { 30.dp.toPx() }

        val width = colCount * estColWidth
        val height = rowCount * estRowHeight

        return Size(width, height)
    }

    private fun measureImage(content: NodeContent.ImageContent): Size {
        // Return thumbnail size + label space
        val thumbSize = with(density) { 100.dp.toPx() }
        val labelHeight = if (content.caption != null) with(density) { 20.dp.toPx() } else 0f
        return Size(thumbSize, thumbSize + labelHeight)
    }

    private fun measureMap(): Size {
        // Default circular node size
        val size = with(density) { 50.dp.toPx() }
        return Size(size, size)
    }

    private fun measureList(items: List<String>): Size {
        val maxWidthPx = with(density) { maxNodeWidth.toPx() }.toInt()
        var maxWidth = 0
        var totalHeight = 0

        // Measure first few items to estimate or calculate bounds
        items.take(10).forEach { item ->
            // Use named argument for constraints to fix ambiguity
            val res = textMeasurer.measure(
                text = AnnotatedString(item),
                style = bodyStyle,
                constraints = Constraints(maxWidth = maxWidthPx)
            )
            if (res.size.width > maxWidth) maxWidth = res.size.width
            totalHeight += res.size.height
        }
        // Add ellipsis estimate if cropped
        if (items.size > 10) totalHeight += with(density) { 20.dp.toPx() }.toInt()

        val paddingPx = with(density) { padding.toPx() }
        return Size((maxWidth + paddingPx * 2).toFloat(), (totalHeight + paddingPx * 2).toFloat())
    }
}