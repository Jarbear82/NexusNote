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
import com.tau.nexus_note.ui.components.Icon
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import java.io.File

// --- 1. Title Renderer ---
@Composable
fun TitleRenderer(node: TitleGraphNode, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(node.width.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(
                text = node.title,
                color = node.colorInfo.composeFontColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// --- 2. Heading Renderer ---
@Composable
fun HeadingRenderer(node: HeadingGraphNode, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(node.width.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Text(
                text = node.text,
                color = node.colorInfo.composeFontColor,
                fontSize = (22 - (node.level * 2)).coerceAtLeast(14).sp,
                fontWeight = FontWeight.Bold,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// --- 3. Short Text Renderer ---
@Composable
fun ShortTextRenderer(node: ShortTextGraphNode, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.defaultMinSize(minWidth = 120.dp),
        shape = RoundedCornerShape(50),
        color = node.colorInfo.composeColor,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha=0.3f))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = node.text,
                color = node.colorInfo.composeFontColor,
                fontSize = 14.sp,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// --- 4. Long Text Renderer ---
@Composable
fun LongTextRenderer(node: LongTextGraphNode, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(node.width.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = node.content,
                color = node.colorInfo.composeFontColor,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// --- 5. Code Block Renderer ---
@Composable
fun CodeBlockRenderer(node: CodeBlockGraphNode, modifier: Modifier = Modifier) {
    val highlights = remember(node.code, node.language) {
        Highlights.Builder()
            .code(node.code)
            .theme(SyntaxThemes.darcula())
            .language(SyntaxLanguage.getByName(node.language) ?: SyntaxLanguage.DEFAULT)
            .build()
    }

    fun Highlights.toAnnotatedString(): AnnotatedString {
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
                Text(node.language.uppercase(), color = Color(0xFFA9B7C6), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Box(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = node.code,
                    color = Color(0xFFA9B7C6),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// --- 6. Map Renderer ---
@Composable
fun MapRenderer(node: MapGraphNode, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(node.width.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(node.label, fontWeight = FontWeight.Bold, fontSize=12.sp, color = node.colorInfo.composeFontColor.copy(alpha=0.6f))
            HorizontalDivider(modifier=Modifier.padding(vertical=4.dp))
            node.data.entries.forEach { (k, v) ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("$k: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = node.colorInfo.composeFontColor)
                    Text(v, fontSize = 12.sp, color = node.colorInfo.composeFontColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// --- 7. Set Renderer (Chips) ---
@Composable
fun SetRenderer(node: SetGraphNode, modifier: Modifier = Modifier) {
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
                node.items.forEach { item ->
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

// --- 8. Unordered List Renderer ---
@Composable
fun UnorderedListRenderer(node: UnorderedListGraphNode, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(node.width.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            node.items.forEach { item ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 2.dp)) {
                    Text("•", fontWeight = FontWeight.Bold, color = node.colorInfo.composeFontColor, modifier = Modifier.padding(end=6.dp))
                    Text(item, fontSize = 14.sp, color = node.colorInfo.composeFontColor)
                }
            }
        }
    }
}

// --- 9. Ordered List Renderer ---
@Composable
fun OrderedListRenderer(node: OrderedListGraphNode, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(node.width.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            node.items.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 2.dp)) {
                    Text("${index + 1}.", fontWeight = FontWeight.Bold, color = node.colorInfo.composeFontColor, modifier = Modifier.width(20.dp))
                    Text(item, fontSize = 14.sp, color = node.colorInfo.composeFontColor)
                }
            }
        }
    }
}

// --- 10. Tag Renderer ---
@Composable
fun TagRenderer(node: TagGraphNode, modifier: Modifier = Modifier) {
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
            Text(node.name, fontWeight = FontWeight.Medium, color = node.colorInfo.composeFontColor)
        }
    }
}

// --- 11. Table Renderer ---
@Composable
fun TableRenderer(node: TableGraphNode, modifier: Modifier = Modifier) {
    val fontColor = node.colorInfo.composeFontColor
    val borderColor = fontColor.copy(alpha = 0.2f)

    Card(
        modifier = modifier.width(node.width.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (!node.caption.isNullOrBlank()) {
                Text(
                    node.caption,
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
                    if (node.headers.isNotEmpty()) {
                        node.headers.forEach { header ->
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
                node.rows.forEachIndexed { index, row ->
                    HorizontalDivider(color = borderColor, thickness = 0.5.dp)
                    Row(modifier = Modifier.padding(4.dp)) {
                        if (node.headers.isNotEmpty()) {
                            node.headers.forEach { header ->
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

// --- 12. Image Renderer (New) ---
@Composable
fun ImageRenderer(node: ImageGraphNode, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(node.width.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            KamelImage(
                resource = { asyncPainterResource(data = File(node.uri).toURI().toString()) },
                contentDescription = node.altText,
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
                    text = node.altText.ifBlank { "Image" },
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

// --- 13. Legacy List Renderer ---
@Composable
fun ListRenderer(node: ListGraphNode, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(node.width.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            node.items.forEach { item ->
                Text("• $item", fontSize = 14.sp, color = node.colorInfo.composeFontColor)
            }
        }
    }
}