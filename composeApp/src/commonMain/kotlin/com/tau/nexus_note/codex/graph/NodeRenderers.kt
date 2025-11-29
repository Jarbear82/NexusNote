package com.tau.nexus_note.codex.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import java.io.File

// --- 1. Heading Renderer (Documents, Sections) ---
@Composable
fun HeadingRenderer(node: HeadingGraphNode, modifier: Modifier = Modifier) {
    val isExpanded = node.isExpanded
    val fontSize = if (node.level == 1) 24.sp else 18.sp

    Card(
        modifier = modifier.width(if(isExpanded) node.width.dp else 200.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if(node.level == 1) 8.dp else 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(
                text = node.title,
                color = node.colorInfo.composeFontColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                maxLines = if(isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// --- 2. Short Text Renderer (Tags, Attachments) ---
@Composable
fun ShortTextRenderer(node: ShortTextGraphNode, modifier: Modifier = Modifier) {
    val isExpanded = node.isExpanded

    if (node.backgroundImagePath != null && File(node.backgroundImagePath).exists()) {
        Card(
            modifier = modifier.size(if(isExpanded) 200.dp else 80.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            KamelImage(
                resource = { asyncPainterResource(data = File(node.backgroundImagePath).toURI().toString()) },
                contentDescription = node.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    Surface(
        modifier = modifier.defaultMinSize(minWidth = 100.dp),
        shape = RoundedCornerShape(50),
        color = node.colorInfo.composeColor,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha=0.3f))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = node.text,
                color = node.colorInfo.composeFontColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// --- 3. Long Text Renderer (Blocks, Content) ---
@Composable
fun LongTextRenderer(node: LongTextGraphNode, modifier: Modifier = Modifier) {
    val isExpanded = node.isExpanded

    Card(
        modifier = modifier.width(if(isExpanded) node.width.dp else 250.dp),
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
                maxLines = if(isExpanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// --- 4. Map Renderer (Key-Value Tables) ---
@Composable
fun MapRenderer(node: MapGraphNode, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val fontColor = node.colorInfo.composeFontColor

    Card(
        modifier = modifier.width(280.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = node.label.uppercase(),
                    color = fontColor.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (node.title != null) {
                Text(
                    text = node.title,
                    color = fontColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = fontColor.copy(alpha = 0.2f))

            if (node.data.isNotEmpty()) {
                val propertyLimit = 3
                val shouldCollapse = node.data.size > propertyLimit
                val propertiesToShow = if (expanded || !shouldCollapse) node.data.entries.toList() else node.data.entries.take(propertyLimit).toList()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    propertiesToShow.forEach { (key, value) ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("$key: ", color = fontColor.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(value, color = fontColor, fontSize = 12.sp, maxLines = if (expanded) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                if (shouldCollapse) {
                    Text(
                        text = if (expanded) "Show Less" else "Show More...",
                        color = fontColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { expanded = !expanded }.padding(top = 8.dp).align(Alignment.End)
                    )
                }
            }
        }
    }
}

// --- 5. Code Renderer ---
@Composable
fun CodeRenderer(node: CodeGraphNode, modifier: Modifier = Modifier) {
    val isExpanded = node.isExpanded

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
        modifier = modifier.width(if (isExpanded) node.width.dp else 300.dp),
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
                if (node.filename.isNotBlank()) Text(node.filename, color = Color.Gray, fontSize = 10.sp)
            }

            Box(modifier = Modifier.padding(12.dp)) {
                if (isExpanded) {
                    Text(
                        text = highlights.toAnnotatedString(),
                        color = Color(0xFFA9B7C6),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                } else {
                    Text(
                        text = node.code.lines().take(3).joinToString("\n"),
                        color = Color(0xFFA9B7C6),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

// --- 6. List Renderer ---
@Composable
fun ListRenderer(node: ListGraphNode, modifier: Modifier = Modifier) {
    val isExpanded = node.isExpanded

    Card(
        modifier = modifier.width(if(isExpanded) node.width.dp else 220.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (node.title != null) {
                Text(node.title, fontWeight = FontWeight.Bold, color = node.colorInfo.composeFontColor)
                HorizontalDivider(color = node.colorInfo.composeFontColor.copy(alpha=0.5f), modifier = Modifier.padding(vertical=4.dp))
            }

            if (isExpanded) {
                node.items.forEach { item ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 4.dp)) {
                        Text("•", fontWeight = FontWeight.Bold, color = node.colorInfo.composeFontColor, modifier = Modifier.width(12.dp))
                        Text(item, fontSize = 12.sp, color = node.colorInfo.composeFontColor)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("•", fontWeight = FontWeight.Bold, color = node.colorInfo.composeFontColor)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = node.items.firstOrNull() ?: "Empty List",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        color = node.colorInfo.composeFontColor
                    )
                }
                if (node.items.size > 1) {
                    Text(
                        text = "+ ${node.items.size - 1} more...",
                        fontSize = 10.sp,
                        color = node.colorInfo.composeFontColor.copy(alpha=0.7f),
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }
    }
}

// --- 7. Table Renderer ---
@Composable
fun TableRenderer(node: TableGraphNode, modifier: Modifier = Modifier) {
    val isExpanded = node.isExpanded
    val fontColor = node.colorInfo.composeFontColor
    val borderColor = fontColor.copy(alpha = 0.2f)

    Card(
        modifier = modifier.width(if (isExpanded) node.width.dp else 300.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Caption / Title
            if (!node.caption.isNullOrBlank()) {
                Text(
                    text = node.caption,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = fontColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Table Content
            Column(modifier = Modifier.border(1.dp, borderColor, RoundedCornerShape(4.dp))) {
                // Header Row
                Row(modifier = Modifier.background(borderColor).padding(4.dp)) {
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
                }

                if (isExpanded) {
                    // Data Rows
                    node.rows.forEachIndexed { index, row ->
                        HorizontalDivider(color = borderColor, thickness = 0.5.dp)
                        Row(modifier = Modifier.padding(4.dp)) {
                            node.headers.forEach { header ->
                                Text(
                                    text = row[header] ?: "",
                                    fontSize = 12.sp,
                                    color = fontColor,
                                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else {
                    // Collapsed Hint
                    if (node.rows.isNotEmpty()) {
                        HorizontalDivider(color = borderColor, thickness = 0.5.dp)
                        Text(
                            text = "${node.rows.size} rows hidden...",
                            fontSize = 10.sp,
                            color = fontColor.copy(alpha = 0.7f),
                            modifier = Modifier.padding(8.dp).align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}

// --- 8. Cluster Node Renderer ---
@Composable
fun ClusterNodeView(node: ClusterNode, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(node.radius.dp * 2)
            .shadow(8.dp, CircleShape)
            .background(node.colorInfo.composeColor, CircleShape)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${node.childCount}",
                color = node.colorInfo.composeFontColor,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = node.label,
                color = node.colorInfo.composeFontColor.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}