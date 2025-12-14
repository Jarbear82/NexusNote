package com.tau.nexusnote.nexus

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.tau.nexusnote.datamodels.CodexItem

@Composable
fun DeleteCodexDialog(
    item: CodexItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = "Delete Codex?"
    val text = "Are you sure you want to permanently delete the codex file '${item.name}'? " +
            "This will also delete its associated media folder.\n\n" +
            "This action cannot be undone."

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}