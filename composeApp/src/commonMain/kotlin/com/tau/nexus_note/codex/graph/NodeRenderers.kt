package com.tau.nexus_note.codex.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tau.nexus_note.datamodels.ColorInfo
import com.tau.nexus_note.datamodels.NodeStyle
import com.tau.nexus_note.ui.components.Icon
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import java.io.File

// --- Phase 1: Stable Data Contract ---
/**
 * Holds only the visual properties required to render a node's content.
 * Does NOT include physics state (pos, vel) to prevent recomposition on movement.
 */
@Stable
data class StableNodeProps(
    val id: Long,
    val label: String,
    val displayProperty: String,
    val style: NodeStyle,
    val colorInfo: ColorInfo,
    val width: Float,
    val height: Float,
    val properties: Map<String, String>,
    val backgroundImagePath: String? = null
)

// --- Phase 2: Stable Content Wrapper ---
@Composable
fun StableNodeContent(
    props: StableNodeProps,
    modifier: Modifier = Modifier
) {
    when (props.style) {
        NodeStyle.TITLE, NodeStyle.DOCUMENT -> TitleRenderer(props, modifier)
        NodeStyle.HEADING, NodeStyle.SECTION -> HeadingRenderer(props, modifier)
        NodeStyle.SHORT_TEXT, NodeStyle.GENERIC -> ShortTextRenderer(props, modifier)
        NodeStyle.LONG_TEXT, NodeStyle.BLOCK -> LongTextRenderer(props, modifier)
        NodeStyle.CODE_BLOCK -> CodeBlockRenderer(props, modifier)
        NodeStyle.MAP -> MapRenderer(props, modifier)
        NodeStyle.SET -> SetRenderer(props, modifier)
        NodeStyle.UNORDERED_LIST, NodeStyle.LIST -> UnorderedListRenderer(props, modifier)
        NodeStyle.ORDERED_LIST -> OrderedListRenderer(props, modifier)
        NodeStyle.TAG -> TagRenderer(props, modifier)
        NodeStyle.TABLE -> TableRenderer(props, modifier)
        NodeStyle.IMAGE -> ImageRenderer(props, modifier)
        NodeStyle.ATTACHMENT -> ShortTextRenderer(props, modifier) // Fallback
    }
}

// --- Renderers (Updated to use StableNodeProps) ---

// 1. Title Renderer
@Composable
fun TitleRenderer(node: StableNodeProps, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(node.width.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(
                text = node.displayProperty, // Was node.title
                color = node.colorInfo.composeFontColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// 2. Heading Renderer
@Composable
fun HeadingRenderer(node: StableNodeProps, modifier: Modifier = Modifier) {
    // Extract level from properties or default to 1
    val level = node.properties["level"]?.toIntOrNull() ?: 1

    Card(
        modifier = modifier.width(node.width.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Text(
                text = node.displayProperty,
                color = node.colorInfo.composeFontColor,
                fontSize = (22 - (level * 2)).coerceAtLeast(14).sp,
                fontWeight = FontWeight.Bold,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// 3. Short Text Renderer
@Composable
fun ShortTextRenderer(node: StableNodeProps, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.defaultMinSize(minWidth = 120.dp),
        shape = RoundedCornerShape(50),
        color = node.colorInfo.composeColor,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha=0.3f))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = node.displayProperty,
                color = node.colorInfo.composeFontColor,
                fontSize = 14.sp,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// 4. Long Text Renderer
@Composable
fun LongTextRenderer(node: StableNodeProps, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(node.width.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = node.properties["content"] ?: node.displayProperty,
                color = node.colorInfo.composeFontColor,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// 5. Code Block Renderer (Optimized)
@Composable
fun CodeBlockRenderer(node: StableNodeProps, modifier: Modifier = Modifier) {
    val code = node.properties["content"] ?: ""
    val language = node.properties["language"] ?: "text"

    // Memoize the expensive syntax highlighting operation
    val highlightedText = remember(code, language) {
        Highlights.Builder()
            .code(code)
            .theme(SyntaxThemes.darcula())
            .language(SyntaxLanguage.getByName(language) ?: SyntaxLanguage.DEFAULT)
            .build()
            .toAnnotatedString()
    }

    Card(
        modifier = modifier.width(node.width.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B2B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF3C3F41)).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(language.uppercase(), color = Color(0xFFA9B7C6), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Box(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = highlightedText,
                    color = Color(0xFFA9B7C6),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

private fun Highlights.toAnnotatedString(): AnnotatedString {
    return buildAnnotatedString {
        append(getCode())
        getHighlights().forEach { highlight ->
            val style = when (highlight) {
                is ColorHighlight -> SpanStyle(color = Color(highlight.rgb.toLong() and 0xFFFFFFFFL))
                is BoldHighlight -> SpanStyle(fontWeight = FontWeight.Bold)
            }
            addStyle(style, highlight.location.start, highlight.location.end)
        }
    }
}

// 6. Map Renderer
@Composable
fun MapRenderer(node: StableNodeProps, modifier: Modifier = Modifier) {
    val data = remember(node.properties["data"]) {
        com.tau.nexus_note.utils.PropertySerialization.deserializeMap(node.properties["data"] ?: "{}")
    }

    Card(
        modifier = modifier.width(node.width.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(node.label, fontWeight = FontWeight.Bold, fontSize=12.sp, color = node.colorInfo.composeFontColor.copy(alpha=0.6f))
            HorizontalDivider(modifier=Modifier.padding(vertical=4.dp))
            data.entries.forEach { (k, v) ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("$k: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = node.colorInfo.composeFontColor)
                    Text(v, fontSize = 12.sp, color = node.colorInfo.composeFontColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// 7. Set Renderer (Chips)
@Composable
fun SetRenderer(node: StableNodeProps, modifier: Modifier = Modifier) {
    val items = remember(node.properties["items"]) {
        com.tau.nexus_note.utils.PropertySerialization.deserializeList(node.properties["items"] ?: "[]")
    }

    Card(
        modifier = modifier.width(node.width.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("SET (Unique)", fontWeight = FontWeight.Bold, fontSize=10.sp, color=node.colorInfo.composeFontColor.copy(alpha=0.6f))
            Spacer(Modifier.height(4.dp))
            OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items.forEach { item ->
                    Surface(
                        color = Color.Black.copy(alpha=0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(item, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, color = node.colorInfo.composeFontColor)
                    }
                }
            }
        }
    }
}

// 8. Unordered List Renderer
@Composable
fun UnorderedListRenderer(node: StableNodeProps, modifier: Modifier = Modifier) {
    val items = remember(node.properties["items"]) {
        com.tau.nexus_note.utils.PropertySerialization.deserializeList(node.properties["items"] ?: "[]")
    }

    Card(
        modifier = modifier.width(node.width.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            items.forEach { item ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 2.dp)) {
                    Text("•", fontWeight = FontWeight.Bold, color = node.colorInfo.composeFontColor, modifier = Modifier.padding(end=6.dp))
                    Text(item, fontSize = 14.sp, color = node.colorInfo.composeFontColor)
                }
            }
        }
    }
}

// 9. Ordered List Renderer
@Composable
fun OrderedListRenderer(node: StableNodeProps, modifier: Modifier = Modifier) {
    val items = remember(node.properties["items"]) {
        com.tau.nexus_note.utils.PropertySerialization.deserializeList(node.properties["items"] ?: "[]")
    }

    Card(
        modifier = modifier.width(node.width.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            items.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 2.dp)) {
                    Text("${index + 1}.", fontWeight = FontWeight.Bold, color = node.colorInfo.composeFontColor, modifier = Modifier.width(20.dp))
                    Text(item, fontSize = 14.sp, color = node.colorInfo.composeFontColor)
                }
            }
        }
    }
}

// 10. Tag Renderer
@Composable
fun TagRenderer(node: StableNodeProps, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = node.colorInfo.composeColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, node.colorInfo.composeFontColor.copy(alpha=0.2f)),
        shadowElevation = 2.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("#", fontWeight = FontWeight.Bold, color = node.colorInfo.composeFontColor.copy(alpha=0.6f))
            Spacer(Modifier.width(2.dp))
            Text(node.displayProperty, fontWeight = FontWeight.Medium, color = node.colorInfo.composeFontColor)
        }
    }
}

// 11. Table Renderer
@Composable
fun TableRenderer(node: StableNodeProps, modifier: Modifier = Modifier) {
    val fontColor = node.colorInfo.composeFontColor
    val borderColor = fontColor.copy(alpha = 0.2f)

    val headers = remember(node.properties["headers"]) {
        com.tau.nexus_note.utils.PropertySerialization.deserializeList(node.properties["headers"] ?: "[]")
    }
    val rows = remember(node.properties["data"]) {
        com.tau.nexus_note.utils.PropertySerialization.deserializeListOfMaps(node.properties["data"] ?: "[]")
    }
    val caption = node.properties["caption"]

    Card(
        modifier = modifier.width(node.width.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (!caption.isNullOrBlank()) {
                Text(
                    caption,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = fontColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Table Container
            Column(modifier = Modifier.border(1.dp, borderColor, RoundedCornerShape(4.dp))) {
                // Headers
                Row(modifier = Modifier.background(borderColor.copy(alpha = 0.1f)).padding(4.dp)) {
                    if (headers.isNotEmpty()) {
                        headers.forEach { header ->
                            Text(
                                text = header,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = fontColor,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        // Empty header fallback
                        Text("Empty Table", fontSize=12.sp, color=fontColor, modifier=Modifier.padding(4.dp))
                    }
                }

                // Rows
                rows.forEachIndexed { index, row ->
                    HorizontalDivider(color = borderColor, thickness = 0.5.dp)
                    Row(modifier = Modifier.padding(4.dp)) {
                        if (headers.isNotEmpty()) {
                            headers.forEach { header ->
                                Text(
                                    text = row[header] ?: "",
                                    fontSize = 12.sp,
                                    color = fontColor,
                                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                    maxLines = Int.MAX_VALUE,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            // Fallback if no headers but data exists
                            Text(
                                text = row.values.joinToString(", "),
                                fontSize = 12.sp,
                                color = fontColor,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// 12. Image Renderer
@Composable
fun ImageRenderer(node: StableNodeProps, modifier: Modifier = Modifier) {
    // Note: URI logic moved here
    val uri = node.properties["filepath"] ?: "" // assuming 'filepath' was mapped or passed correctly, or we can use backgroundImagePath if it fits logic
    // Actually, GraphNode usually has specialized fields. StableNodeProps puts everything in 'properties'.
    // In GraphViewModel.createGraphNode, we see ImageGraphNode gets 'uri' from absolute path.
    // We should ensure StableNodeProps receives the full URI.
    // In the wrapper below, we'll map ImageGraphNode.uri to properties["uri"].

    val imageUri = node.properties["uri"] ?: ""
    val altText = node.properties["altText"] ?: node.displayProperty

    Card(
        modifier = modifier.width(node.width.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            KamelImage(
                resource = { asyncPainterResource(data = File(imageUri).toURI().toString()) },
                contentDescription = altText,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(node.height.dp) // Use full height
                    .background(Color.Black.copy(alpha = 0.1f)),
                contentScale = ContentScale.Crop,
                onFailure = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.BrokenImage, "Error", tint = Color.Red.copy(alpha = 0.5f))
                    }
                }
            )

            Box(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = altText.ifBlank { "Image" },
                    color = node.colorInfo.composeFontColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}