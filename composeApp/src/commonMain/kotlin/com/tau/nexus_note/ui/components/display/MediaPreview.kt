package com.tau.nexus_note.ui.components.display

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import com.tau.nexus_note.ui.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun ImagePreview(relativePath: String, codexPath: String) {
    if (relativePath.isBlank()) return

    // Calculate absolute path
    val dbFile = File(codexPath)
    val mediaFile = File(dbFile.parent, relativePath)

    if (mediaFile.exists()) {
        // NOTE: In a real Compose Desktop app, you'd use `rememberImagePainter` or similar.
        // Since we don't have coil/kamell dependency, we show a placeholder UI
        // that represents where the image would be.
        Box(
            modifier = Modifier
                .height(200.dp)
                .fillMaxWidth()
                .background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Image, null, modifier = Modifier.size(48.dp))
                Text("Image: ${mediaFile.name}", style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BrokenImage, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text("Image not found: $relativePath", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun AudioPreview(relativePath: String, codexPath: String) {
    if (relativePath.isBlank()) return

    val dbFile = File(codexPath)
    val mediaFile = File(dbFile.parent, relativePath)

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