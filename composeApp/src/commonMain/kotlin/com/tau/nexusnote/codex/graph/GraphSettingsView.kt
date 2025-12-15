package com.tau.nexusnote.codex.graph

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tau.nexusnote.codex.graph.fcose.LayoutConfig
import com.tau.nexusnote.ui.components.CodexDropdown
import kotlin.math.roundToInt

enum class ConstraintUiType(val label: String) {
    ALIGN_VERTICAL("Align Vertical"),
    ALIGN_HORIZONTAL("Align Horizontal"),
    RELATIVE_LR("Relative Left->Right"),
    RELATIVE_TB("Relative Top->Bottom")
}

@Composable
fun GraphSettingsView(
    options: LayoutConfig,
    onDetangleClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GraphViewmodel? = null,
    primarySelectedId: Long? = null,
    secondarySelectedId: Long? = null
) {
    Card(
        modifier = modifier
            .width(320.dp)
            .heightIn(max = 600.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // --- Pipeline Controls ---
            Text("Pipeline Control Panel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (viewModel != null) {
                PipelineRow("1. Reset", "Randomize", viewModel::runRandomize)
                PipelineRow("2. Global", "Draft", viewModel::runDraft)
                PipelineRow("3. Align", "Transform", viewModel::runTransform)
                PipelineRow("4. Snap", "Enforce", viewModel::runEnforce)
                PipelineRow("5. Settle", "Polish", { viewModel.runPolishing(burst = false) })
            } else {
                Text("ViewModel not linked.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))

            // --- Constraint Controls ---
            Text("Constraint Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val selectedIds = listOfNotNull(primarySelectedId, secondarySelectedId)
            Text(
                "Selected Nodes: ${selectedIds.size}",
                style = MaterialTheme.typography.bodySmall,
                color = if(selectedIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (selectedIds.isNotEmpty()) {
                var selectedConstraint by remember { mutableStateOf(ConstraintUiType.ALIGN_VERTICAL) }

                Spacer(Modifier.height(8.dp))

                CodexDropdown(
                    label = "Constraint Type",
                    options = ConstraintUiType.entries,
                    selectedOption = selectedConstraint,
                    onOptionSelected = { selectedConstraint = it },
                    displayTransform = { it.label }
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        viewModel?.addConstraint(selectedConstraint, selectedIds)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = viewModel != null
                ) {
                    Text("Add Constraint")
                }
            } else {
                Text(
                    "Select nodes in the graph to add constraints.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // --- Layout Config (Live Tweaks) ---
            Text("Layout Config (Read-Only)", style = MaterialTheme.typography.titleMedium)
            Text("Adjust in Settings > Graph", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Just read-only display here since changing requires ViewModel plumbing back to SettingsViewModel
            // or we could add direct setters to GraphViewModel if we want transient editing.
            // For now, we display current values.
            ConfigRow("Gravity", options.gravityConstant)
            ConfigRow("Repulsion", options.repulsionConstant)
            ConfigRow("Ideal Edge", options.idealEdgeLength)
            ConfigRow("Cooling", options.coolingFactor)
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(String.format("%.2f", value), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PipelineRow(label: String, buttonText: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Button(
            onClick = onClick,
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text(buttonText, fontSize = 12.sp)
        }
    }
}