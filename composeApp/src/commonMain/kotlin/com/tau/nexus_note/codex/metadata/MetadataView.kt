package com.tau.nexus_note.codex.metadata

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.CodexRepository
import com.tau.nexus_note.datamodels.EdgeDisplayItem
import com.tau.nexus_note.datamodels.NodeDisplayItem
import com.tau.nexus_note.ui.components.CodexSectionHeader
import com.tau.nexus_note.ui.components.display.SmartPropertyRenderer
import com.tau.nexus_note.ui.theme.LocalDensityTokens

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
    repository: CodexRepository
) {
    // UPDATED: Get the media path directly
    val mediaRootPath = repository.mediaDirectoryPath
    val density = LocalDensityTokens.current

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp).verticalScroll(rememberScrollState())) {

        // --- Selection Details ---
        CodexSectionHeader("Selection Details")

        if (primarySelectedItem is NodeDisplayItem) {
            val nodeEditState = repository.getNodeEditState(primarySelectedItem.id)
            if (nodeEditState != null) {
                Text(primarySelectedItem.label, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontSize = density.titleFontSize)
                Spacer(Modifier.height(8.dp))

                nodeEditState.schema.properties.forEach { prop ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(prop.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, fontSize = density.bodyFontSize)
                        val value = nodeEditState.properties[prop.name] ?: ""
                        SmartPropertyRenderer(prop, value, mediaRootPath)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }

                Spacer(Modifier.height(16.dp))
                Button(onClick = { onEditNodeClick(primarySelectedItem) }, modifier = Modifier.height(density.buttonHeight)) { Text("Edit Node") }
            }
        } else if (primarySelectedItem is EdgeDisplayItem) {
            val edgeEditState = repository.getEdgeEditState(primarySelectedItem)
            if (edgeEditState != null) {
                Text(primarySelectedItem.label, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontSize = density.titleFontSize)
                Text("${primarySelectedItem.src.displayProperty} -> ${primarySelectedItem.dst.displayProperty}", fontSize = density.bodyFontSize)
                Spacer(Modifier.height(8.dp))

                edgeEditState.schema.properties.forEach { prop ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(prop.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, fontSize = density.bodyFontSize)
                        val value = edgeEditState.properties[prop.name] ?: ""
                        SmartPropertyRenderer(prop, value, mediaRootPath)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }

                Spacer(Modifier.height(16.dp))
                Button(onClick = { onEditEdgeClick(primarySelectedItem) }, modifier = Modifier.height(density.buttonHeight)) { Text("Edit Edge") }
            }
        } else {
            Text("No item selected.", style = MaterialTheme.typography.bodyMedium, fontSize = density.bodyFontSize)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- Data Refresh ---
        CodexSectionHeader("Data Management")

        Button(onClick = onListAllClick, modifier = Modifier.fillMaxWidth().height(density.buttonHeight)) { Text("Refresh All Data") }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onListNodesClick, modifier = Modifier.weight(1f).height(density.buttonHeight)) { Text("Refresh Graph Nodes") }
            Button(onClick = onListEdgesClick, modifier = Modifier.weight(1f).height(density.buttonHeight)) { Text("Refresh Graph Edges") }
        }
    }
}