package com.tau.nexusnote.codex.metadata

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.datamodels.EdgeDisplayItem
import com.tau.nexusnote.datamodels.NodeDisplayItem
import com.tau.nexusnote.ui.components.CodexSectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataView(
    nodes: List<NodeDisplayItem>, // This is now the paginated list
    edges: List<EdgeDisplayItem>, // This is now the paginated list
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
    onListEdgesClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {

        // --- Selection Details ---
        CodexSectionHeader("Selection Details")

        val primaryNode = primarySelectedItem as? NodeDisplayItem
        val secondaryNode = secondarySelectedItem as? NodeDisplayItem

        if (primaryNode != null && secondaryNode != null) {
            Text(
                "Source: ${primaryNode.label} : ${primaryNode.displayProperty}\n" +
                        "Destination: ${secondaryNode.label} : ${secondaryNode.displayProperty}",
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (primaryNode != null) {
            Text(
                "Selected Node:\n" +
                        "${primaryNode.label} : ${primaryNode.displayProperty}",
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (primarySelectedItem != null) {
            Text(
                "Selected Item: $primarySelectedItem",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text("No item selected.", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Data Refresh ---
        CodexSectionHeader("Data Management")

        Button(
            onClick = onListAllClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh All Data")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onListNodesClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("Refresh Graph Nodes")
            }
            Button(
                onClick = onListEdgesClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("Refresh Graph Edges")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

    }
}