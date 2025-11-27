package com.tau.nexus_note.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrokenImage
import com.tau.nexus_note.ui.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import java.io.File

@Composable
fun ImagePreview(relativePath: String, mediaRootPath: String) {
    if (relativePath.isBlank()) return

    // Calculate absolute path using the explicit media directory root
    val mediaFile = File(mediaRootPath, relativePath)

    if (mediaFile.exists()) {
        KamelImage(
            resource = { asyncPainterResource(data = mediaFile.toURI().toString()) },
            contentDescription = "Image: ${mediaFile.name}",
            modifier = Modifier
                .height(200.dp)
                .fillMaxWidth()
                .background(Color.LightGray),
            contentScale = ContentScale.Fit,
            onFailure = { exception ->
                println("ERROR: Kamel failed to load image: $exception")
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.BrokenImage, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Text("Error loading image", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BrokenImage, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text("Image not found ($relativePath)", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun AudioPreview(relativePath: String, mediaRootPath: String) {
    if (relativePath.isBlank()) return

    val mediaFile = File(mediaRootPath, relativePath)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Audiotrack, null)
        Spacer(Modifier.width(8.dp))
        Column {
            Text("Audio File", style = MaterialTheme.typography.labelSmall)
            Text(
                if (mediaFile.exists()) mediaFile.name else "File not found ($relativePath)",
                style = MaterialTheme.typography.bodyMedium,
                color = if (mediaFile.exists()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
            )
        }
    }
}