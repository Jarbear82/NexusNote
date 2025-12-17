package com.tau.nexusnote.nexus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
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
import com.tau.nexusnote.datamodels.CodexItem
import com.tau.nexusnote.utils.DirectoryPicker
import com.tau.nexusnote.MainViewModel
import com.tau.nexusnote.ui.components.CodexAlertDialog
import com.tau.nexusnote.ui.components.CodexListItem
import com.tau.nexusnote.utils.labelToColor

@Composable
fun NexusView(viewModel: MainViewModel) {
    val codices by viewModel.codices.collectAsState()
    val baseDirectory by viewModel.codexBaseDirectory.collectAsState()
    val showBaseDirPicker by viewModel.showBaseDirPicker.collectAsState()

    // --- State for inline creation ---
    val newCodexName by viewModel.newCodexName.collectAsState()
    val codexNameError by viewModel.codexNameError.collectAsState()
    val codexToDelete by viewModel.codexToDelete.collectAsState()

    // --- Dialogs ---

    DirectoryPicker(
        show = showBaseDirPicker,
        title = "Select Codex Storage Directory",
        initialDirectory = baseDirectory,
        onResult = { viewModel.onBaseDirectorySelected(it) }
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
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        // --- Database List ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Existing Codices in: $baseDirectory", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { viewModel.onChangeBaseDirectoryClicked() }) {
                Text("Change")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (codices.isEmpty()) {
                item {
                    Text(
                        "No codices found in this directory.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
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
                                tint = colorInfo.composeFontColor
                            )
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Create Codex ---

        Text(
            "Create New Codex:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        OutlinedTextField(
            value = newCodexName,
            onValueChange = { viewModel.validateCodexName(it) },
            label = { Text("Codex Name") },
            singleLine = true,
            placeholder = { Text("MyDatabase") },
            suffix = { Text(".sqlite") },
            isError = codexNameError != null,
            supportingText = {
                if (codexNameError != null) {
                    Text(codexNameError!!)
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth()
        )

        // --- Actions ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { viewModel.onCodexNameConfirmed() },
                enabled = newCodexName.isNotBlank() && codexNameError == null,
            ) {
                Text("Create and Open")
            }
            Button(
                onClick = { viewModel.openInMemoryTerminal() },
            ) {
                Text("Open In-Memory Terminal")
            }
        }
    }
}