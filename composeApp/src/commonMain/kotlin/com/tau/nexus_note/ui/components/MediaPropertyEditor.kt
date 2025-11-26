package com.tau.nexus_note.ui.components.editors

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.utils.FilePicker
import com.tau.nexus_note.utils.copyFileToMediaDir

@Composable
fun MediaPropertyEditor(
    label: String,
    currentValue: String,
    codexPath: String,
    onValueChange: (String) -> Unit,
    extensions: List<String>,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    FilePicker(
        show = showPicker,
        fileExtensions = extensions,
        allowMultiple = false,
        onResult = { paths ->
            showPicker = false
            if (paths.isNotEmpty()) {
                val sourcePath = paths.first()
                if (codexPath.isNotBlank()) {
                    try {
                        val relativePath = copyFileToMediaDir(sourcePath, codexPath)
                        onValueChange(relativePath)
                    } catch (e: Exception) {
                        println("Error copying file: $e")
                    }
                }
            }
        }
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = currentValue,
                onValueChange = {}, // Read only
                label = { Text(label) },
                modifier = Modifier.weight(1f),
                readOnly = true,
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { showPicker = true },
                enabled = codexPath.isNotBlank() && codexPath != ":memory:"
            ) {
                Icon(Icons.Default.UploadFile, null)
                Spacer(Modifier.width(4.dp))
                Text("Upload")
            }
        }
        if (codexPath == ":memory:") {
            Text(
                "Media upload unavailable in memory mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}