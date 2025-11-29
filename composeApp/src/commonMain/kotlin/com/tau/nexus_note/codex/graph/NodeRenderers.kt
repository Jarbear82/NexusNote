package com.tau.nexus_note.codex.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tau.nexus_note.ui.components.Icon
import com.tau.nexus_note.ui.components.IconButton
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import java.io.File

// --- Helper: Convert SnipMe Highlights to Compose AnnotatedString ---
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

// --- 1. Section Node (The Blue Header) ---
@Composable
fun SectionNodeView(node: SectionGraphNode, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(280.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1976D2)), // Blue
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = node.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = "Level ${node.level}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}

// --- 2. Document Node (The Root) ---
@Composable
fun DocumentNodeView(node: DocumentGraphNode, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(300.dp),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)), // Dark Grey
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "DOCUMENT",
                color = Color(0xFF90CAF9),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = node.label, // Name
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

// --- 3. Block Node (The Content) ---
@Composable
fun BlockNodeView(node: BlockGraphNode, modifier: Modifier = Modifier) {
    val isExpanded = node.isExpanded

    Card(
        modifier = modifier.width(if(isExpanded) node.width.dp else 250.dp), // Use physics width if expanded
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEEEEE)), // Light Gray
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = node.content,
                color = Color.Black,
                fontSize = 14.sp,
                maxLines = if(isExpanded) Int.MAX_VALUE else 5,
                lineHeight = 20.sp,
                overflow = TextOverflow.Ellipsis
            )
            if (!isExpanded && node.content.length > 100) {
                Text("...", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

// --- 4. Tag Node (The Pill) ---
@Composable
fun TagNodeView(node: TagGraphNode, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFFE8F5E9), CircleShape) // Light Green
            .border(1.dp, Color(0xFF43A047), CircleShape)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "#${node.name}",
            color = Color(0xFF2E7D32),
            fontWeight = FontWeight.Bold
        )
    }
}

// --- 5. Image / Attachment Node ---
@Composable
fun AttachmentNodeView(node: AttachmentGraphNode, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(200.dp).height(150.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        if (node.resolvedPath.isNotBlank()) {
            val file = File(node.resolvedPath)
            if (file.exists()) {
                KamelImage(
                    resource = { asyncPainterResource(data = file.toURI().toString()) },
                    contentDescription = node.filename,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onLoading = {
                        Box(Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
                            Text("Loading...", color = Color.White, fontSize = 10.sp)
                        }
                    },
                    onFailure = {
                        Box(Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                            Text("Error", color = Color.White)
                        }
                    }
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color.Gray), contentAlignment = Alignment.Center) {
                    Text("File Missing", color = Color.White)
                }
            }
        } else {
            Box(Modifier.fillMaxSize().background(Color.LightGray), contentAlignment = Alignment.Center) {
                Text(node.filename)
            }
        }
    }
}

// --- 6. Code Block ---
@Composable
fun CodeBlockView(node: CodeBlockGraphNode, modifier: Modifier = Modifier) {
    val isExpanded = node.isExpanded

    // Highlight Logic using SnipMe Highlights
    val highlights = remember(node.code, node.language) {
        Highlights.Builder()
            .code(node.code)
            .theme(SyntaxThemes.darcula())
            .language(SyntaxLanguage.getByName(node.language) ?: SyntaxLanguage.DEFAULT)
            .build()
    }

    Column(modifier = modifier.width(if (isExpanded) node.width.dp else 300.dp)) {
        // Main Card (Code Content)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B2B)), // Dark IDE theme
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF3C3F41))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        node.language.uppercase(),
                        color = Color(0xFFA9B7C6),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (node.filename.isNotBlank()) {
                        Text(node.filename, color = Color.Gray, fontSize = 10.sp)
                    }
                }

                // Content Body
                Box(modifier = Modifier.padding(12.dp)) {
                    if (isExpanded) {
                        // Use AnnotatedString for highlighting - ACTUAL USAGE HERE
                        Text(
                            text = highlights.toAnnotatedString(),
                            color = Color(0xFFA9B7C6),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    } else {
                        // Collapsed: First few lines + ellipsis
                        Column {
                            val lines = node.code.lines().take(3)
                            Text(
                                text = lines.joinToString("\n"),
                                color = Color(0xFFA9B7C6),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            if (node.code.lines().size > 3) {
                                Text("...", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Caption Footer (Photo-caption style)
        if (node.caption.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, start = 4.dp, end = 4.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray),
                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp),
                shadowElevation = 2.dp
            ) {
                Text(
                    text = node.caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black,
                    modifier = Modifier.padding(8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

// --- 7. Table Node ---
@Composable
fun TableNodeView(node: TableGraphNode, modifier: Modifier = Modifier) {
    val isExpanded = node.isExpanded

    Card(
        modifier = modifier.width(if(isExpanded) node.width.dp else 280.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Show ALL headers, letting them share space
                node.headers.forEach { h ->
                    Text(
                        text = h,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = Color.LightGray)

            if (isExpanded) {
                // Expanded: Render Data Rows
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    node.data.forEachIndexed { index, row ->
                        Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                            node.headers.forEach { h ->
                                Text(
                                    text = row[h] ?: "",
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f).padding(end = 4.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (index < node.data.lastIndex) {
                            HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
                        }
                    }
                }
            } else {
                // Collapsed: Visual cue
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${node.data.size} rows hidden",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}

// --- 8. List Node View ---
@Composable
fun ListNodeView(node: ListGraphNode, modifier: Modifier = Modifier) {
    val isExpanded = node.isExpanded
    val type = node.listType // "ordered", "unordered", "task"

    Card(
        modifier = modifier.width(if(isExpanded) node.width.dp else 220.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)), // Light Yellow
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFBC02D)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (isExpanded) {
                // Render Full List
                node.items.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 4.dp)) {
                        when (type) {
                            "ordered" -> Text("${index + 1}.", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                            "task" -> Icon(Icons.Default.CheckBoxOutlineBlank, null, modifier = Modifier.size(16.dp))
                            else -> Text("•", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(16.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(item, fontSize = 12.sp)
                    }
                }
            } else {
                // Render Collapsed: First Item + Cue
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (type) {
                        "ordered" -> Text("1.", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        "task" -> Icon(Icons.Default.CheckBoxOutlineBlank, null, modifier = Modifier.size(14.dp))
                        else -> Text("•", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = node.items.firstOrNull() ?: "Empty List",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp
                    )
                }
                if (node.items.size > 1) {
                    Text(
                        text = "+ ${node.items.size - 1} more items...",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 20.dp, top = 2.dp)
                    )
                }
            }
        }
    }
}

// --- 9. Cluster Node (New) ---
@Composable
fun ClusterNodeView(node: ClusterNode, modifier: Modifier = Modifier) {
    val bgColor = node.colorInfo.composeColor

    Box(
        modifier = modifier
            .size(80.dp) // Base size, physics radius might be larger
            .shadow(8.dp, CircleShape)
            .background(bgColor, CircleShape)
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

// --- 10. Default Fallback ---
@Composable
fun DefaultNodeView(node: GenericGraphNode, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    // Ensure text is legible against the card background
    val fontColor = node.colorInfo.composeFontColor

    Card(
        modifier = modifier.width(280.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = node.colorInfo.composeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 1. Small text in top corner for Schema Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = node.label.uppercase(),
                    color = fontColor.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Main Title (Display Property)
            Text(
                text = node.displayProperty,
                color = fontColor,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = fontColor.copy(alpha = 0.2f)
            )

            // 3. Properties List (Ellipsed > 3)
            if (node.displayProperties.isNotEmpty()) {
                val propertyLimit = 3
                val shouldCollapse = node.displayProperties.size > propertyLimit
                val propertiesToShow = if (expanded || !shouldCollapse) {
                    node.displayProperties.entries.toList()
                } else {
                    node.displayProperties.entries.take(propertyLimit).toList()
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    propertiesToShow.forEach { (key, value) ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "$key: ",
                                color = fontColor.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = value,
                                color = fontColor,
                                fontSize = 12.sp,
                                maxLines = if (expanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (shouldCollapse) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (expanded) "Show Less" else "Show More...",
                        color = fontColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .clickable { expanded = !expanded }
                            .padding(4.dp)
                            .align(Alignment.End)
                    )
                }
            }
        }
    }
}