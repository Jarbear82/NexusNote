package com.tau.nexus_note.codex.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import java.io.File

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
    Card(
        modifier = modifier.width(250.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEEEEE)), // Light Gray
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = node.content,
                color = Color.Black,
                fontSize = 14.sp,
                maxLines = 5,
                lineHeight = 20.sp
            )
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
                    modifier = Modifier.fillMaxSize()
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
    Card(
        modifier = modifier.width(300.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B2B)) // Dark IDE theme
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF3C3F41))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(node.language, color = Color.Gray, fontSize = 12.sp)
                Text(node.caption, color = Color.Gray, fontSize = 12.sp)
            }
            // Code
            Text(
                text = node.code,
                color = Color(0xFFA9B7C6), // Darcula text
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

// --- 7. Table Node ---
@Composable
fun TableNodeView(node: TableGraphNode, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(280.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Table Data", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            // Minimal representation
            node.headers.take(3).forEach { header ->
                Text("- $header", fontSize = 12.sp)
            }
            if (node.headers.size > 3) Text("...", fontSize = 10.sp)
        }
    }
}

// --- 8. Default Fallback ---
@Composable
fun DefaultNodeView(node: GenericGraphNode, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(node.radius.dp * 2)
            .background(node.colorInfo.composeColor, CircleShape)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Just a simple circle
    }
}