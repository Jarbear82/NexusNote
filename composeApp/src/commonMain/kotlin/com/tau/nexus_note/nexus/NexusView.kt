package com.tau.nexus_note.nexus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.utils.DirectoryPicker
import com.tau.nexus_note.MainViewModel
import com.tau.nexus_note.ui.components.CodexAlertDialog
import com.tau.nexus_note.ui.components.CodexListItem
import com.tau.nexus_note.ui.theme.LocalDensityTokens
import com.tau.nexus_note.utils.labelToColor
import com.tau.nexus_note.utils.FilePicker

@Composable
fun NexusView(viewModel: MainViewModel) {
    val codices by viewModel.codices.collectAsState()
    val baseDirectory by viewModel.codexBaseDirectory.collectAsState()
    val showBaseDirPicker by viewModel.showBaseDirPicker.collectAsState()
    val showImportPicker by viewModel.showImportFilePicker.collectAsState()
    val showImportDirPicker by viewModel.showImportDirPicker.collectAsState()

    val newCodexName by viewModel.newCodexName.collectAsState()
    val codexNameError by viewModel.codexNameError.collectAsState()
    val codexToDelete by viewModel.codexToDelete.collectAsState()

    // Density Awareness
    val density = LocalDensityTokens.current

    // --- Dialogs (File Pickers) ---
    FilePicker(
        show = showImportPicker,
        fileExtensions = listOf("md", "markdown", "txt"),
        allowMultiple = true,
        onResult = { viewModel.onImportFilesSelected(it) }
    )

    DirectoryPicker(
        show = showBaseDirPicker,
        title = "Select Codex Storage Directory",
        initialDirectory = baseDirectory,
        onResult = { viewModel.onBaseDirectorySelected(it) }
    )

    DirectoryPicker(
        show = showImportDirPicker,
        title = "Select Vault/Folder to Import",
        initialDirectory = baseDirectory,
        onResult = { viewModel.onImportFolderSelected(it) }
    )

    codexToDelete?.let { item ->
        CodexAlertDialog(
            title = "Delete Codex?",
            text = "Are you sure you want to permanently delete the codex file '${item.name}'? This will also delete its associated media folder.\n\nThis action cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.confirmDeleteCodex() },
            onDismiss = { viewModel.cancelDeleteCodex() },
            isDestructive = true
        )
    }

    // --- Main UI ---

    Column(
        modifier = Modifier.fillMaxSize().padding(density.contentPadding),
    ) {
        // --- Database List Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Existing Codices in: $baseDirectory",
                style = MaterialTheme.typography.titleMedium,
                fontSize = density.titleFontSize
            )
            OutlinedButton(
                onClick = { viewModel.onChangeBaseDirectoryClicked() },
                modifier = Modifier.height(density.buttonHeight)
            ) {
                Text("Change")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // --- List ---
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (codices.isEmpty()) {
                item {
                    Text(
                        "No codices found in this directory.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = density.bodyFontSize
                    )
                }
            }
            items(codices) { graph ->
                val colorInfo = labelToColor(graph.path)
                CodexListItem(
                    headline = graph.name,
                    supportingText = graph.path,
                    colorSeed = graph.path,
                    onClick = { viewModel.openCodex(graph) },
                    leadingContent = {
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = "Codex Icon",
                            tint = colorInfo.composeFontColor
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.requestDeleteCodex(graph) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Codex",
                                tint = colorInfo.composeFontColor,
                                modifier = Modifier.size(density.iconSize)
                            )
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Create Codex Section ---

        Text(
            "Create New Codex:",
            style = MaterialTheme.typography.titleMedium,
            fontSize = density.titleFontSize,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = newCodexName,
            onValueChange = { viewModel.validateCodexName(it) },
            label = { Text("Codex Name") },
            singleLine = true,
            placeholder = { Text("MyDatabase") },
            suffix = { Text(".sqlite") },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = density.bodyFontSize),
            isError = codexNameError != null,
            supportingText = {
                if (codexNameError != null) {
                    Text(codexNameError!!)
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // --- Actions ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { viewModel.onCodexNameConfirmed() },
                enabled = newCodexName.isNotBlank() && codexNameError == null,
                modifier = Modifier.height(density.buttonHeight)
            ) {
                Text("Create and Open")
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = { viewModel.onImportDocumentsClicked() },
                modifier = Modifier.height(density.buttonHeight)
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(density.iconSize))
                Spacer(modifier = Modifier.width(4.dp))
                Text("File")
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = { viewModel.onImportFolderClicked() },
                modifier = Modifier.height(density.buttonHeight)
            ) {
                Icon(Icons.Default.DriveFolderUpload, contentDescription = null, modifier = Modifier.size(density.iconSize))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Folder")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { viewModel.openInMemoryTerminal() },
                modifier = Modifier.height(density.buttonHeight)
            ) {
                Text("In-Memory Codex")
            }
        }
    }
}