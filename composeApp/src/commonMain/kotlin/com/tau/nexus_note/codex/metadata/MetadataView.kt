package com.tau.nexus_note.codex.metadata

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.CodexRepository
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.ui.components.CodexSectionHeader
import com.tau.nexus_note.ui.components.display.SmartPropertyRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataView(
    nodes: List<NodeDisplayItem>,
    edges: List<EdgeDisplayItem>,
    primarySelectedItem: Any?,
    secondarySelectedItem: Any?,
    onNodeClick: (NodeDisplayItem) -> Unit,
    onEdgeClick: (EdgeDisplayItem) -> Unit,
    onEditNodeClick: (NodeDisplayItem) -> Unit,
    onEditEdgeClick: (EdgeDisplayItem) -> Unit,
    onDeleteNodeClick: (NodeDisplayItem) -> Unit,
    onDeleteEdgeClick: (EdgeDisplayItem) -> Unit,
    onAddNodeClick: () -> Unit,
    onAddEdgeClick: () -> Unit,
    onListAllClick: () -> Unit,
    onListNodesClick: () -> Unit,
    onListEdgesClick: () -> Unit,
    // Pass Repository to fetch full details
    repository: CodexRepository
) {
    val codexPath = repository.dbPath
    // We need to fetch the full details (properties) of the selected item.
    // DisplayItems only have label/displayProperty.
    // In a real app, you might query this on selection.
    // For now, we will assume we can get it via repository helper or pass it in.
    // Let's rely on EditCreateViewModel pattern or just fetch here.

    // Simplification: We will read the EditState from repo to get properties for display.
    // This assumes repository has a synchronous way or we launch effect.
    // For immediate UI update, let's just use what we have, but we need properties.
    // Since DisplayItems don't have the Map, we need to fetch.

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp).verticalScroll(rememberScrollState())) {

        // --- Selection Details ---
        CodexSectionHeader("Selection Details")

        if (primarySelectedItem is NodeDisplayItem) {
            val nodeEditState = repository.getNodeEditState(primarySelectedItem.id)
            if (nodeEditState != null) {
                Text(primarySelectedItem.label, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))

                nodeEditState.schema.properties.forEach { prop ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(prop.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        val value = nodeEditState.properties[prop.name] ?: ""
                        SmartPropertyRenderer(prop, value, codexPath)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }

                Spacer(Modifier.height(16.dp))
                Button(onClick = { onEditNodeClick(primarySelectedItem) }) { Text("Edit Node") }
            }
        } else if (primarySelectedItem is EdgeDisplayItem) {
            val edgeEditState = repository.getEdgeEditState(primarySelectedItem)
            if (edgeEditState != null) {
                Text(primarySelectedItem.label, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text("${primarySelectedItem.src.displayProperty} -> ${primarySelectedItem.dst.displayProperty}")
                Spacer(Modifier.height(8.dp))

                edgeEditState.schema.properties.forEach { prop ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(prop.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        val value = edgeEditState.properties[prop.name] ?: ""
                        SmartPropertyRenderer(prop, value, codexPath)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }

                Spacer(Modifier.height(16.dp))
                Button(onClick = { onEditEdgeClick(primarySelectedItem) }) { Text("Edit Edge") }
            }
        } else {
            Text("No item selected.", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- Data Refresh ---
        CodexSectionHeader("Data Management")

        Button(onClick = onListAllClick, modifier = Modifier.fillMaxWidth()) { Text("Refresh All Data") }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onListNodesClick, modifier = Modifier.weight(1f)) { Text("Refresh Graph Nodes") }
            Button(onClick = onListEdgesClick, modifier = Modifier.weight(1f)) { Text("Refresh Graph Edges") }
        }
    }
}