package com.tau.nexus_note.codex.graph

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.codex.graph.physics.PhysicsOptions
import com.tau.nexus_note.codex.graph.physics.SolverType
import com.tau.nexus_note.settings.GraphLayoutMode
import com.tau.nexus_note.settings.LayoutDirection
import com.tau.nexus_note.ui.components.CodexDropdown
import com.tau.nexus_note.ui.theme.LocalDensityTokens
import kotlin.math.roundToInt

@Composable
fun GraphSettingsView(
    layoutMode: GraphLayoutMode,
    onLayoutModeChange: (GraphLayoutMode) -> Unit,
    layoutDirection: LayoutDirection,
    onLayoutDirectionChange: (LayoutDirection) -> Unit,
    physicsOptions: PhysicsOptions,
    onPhysicsOptionChange: (PhysicsOptions) -> Unit,
    onTriggerLayout: () -> Unit,
    snapEnabled: Boolean,
    onSnapToggle: (Boolean) -> Unit,
    // Clustering Callbacks
    onClusterOutliers: () -> Unit,
    onClusterHubs: () -> Unit,
    onClearClustering: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensityTokens.current

    Card(
        modifier = modifier.width(320.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
    ) {
        Column(modifier = Modifier.padding(density.contentPadding)) {
            Text("Graph Settings", style = MaterialTheme.typography.titleMedium, fontSize = density.titleFontSize)
            Spacer(Modifier.height(16.dp))

            // --- Layout Mode Selector ---
            CodexDropdown(
                label = "Layout Mode",
                options = GraphLayoutMode.entries,
                selectedOption = layoutMode,
                onOptionSelected = onLayoutModeChange,
                displayTransform = { it.displayName }
            )
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Snap Back on Drag", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(checked = snapEnabled, onCheckedChange = onSnapToggle, modifier = Modifier.scale(0.8f))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // --- Algorithmic Clustering ---
            Text("Clustering", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onClusterOutliers, modifier = Modifier.weight(1f)) {
                    Text("Outliers", fontSize = density.bodyFontSize)
                }
                Button(onClick = onClusterHubs, modifier = Modifier.weight(1f)) {
                    Text("Hubs", fontSize = density.bodyFontSize)
                }
            }
            Button(
                onClick = onClearClustering,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors()
            ) { Text("Clear Clusters") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // --- Contextual Controls ---
            when (layoutMode) {
                GraphLayoutMode.CONTINUOUS -> {
                    Text("Physics Simulation", style = MaterialTheme.typography.labelMedium)

                    CodexDropdown(
                        label = "Solver Strategy",
                        options = SolverType.entries,
                        selectedOption = physicsOptions.solver,
                        onOptionSelected = { onPhysicsOptionChange(physicsOptions.copy(solver = it)) },
                        displayTransform = { it.displayName },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    SettingSlider("Gravity", physicsOptions.gravity, { onPhysicsOptionChange(physicsOptions.copy(gravity = it)) }, 0f..2f)
                    SettingSlider("Repulsion", physicsOptions.repulsion, { onPhysicsOptionChange(physicsOptions.copy(repulsion = it)) }, 0f..5000f)
                    SettingSlider("Spring", physicsOptions.spring, { onPhysicsOptionChange(physicsOptions.copy(spring = it)) }, 0.01f..0.5f)
                    Button(onClick = onTriggerLayout, modifier = Modifier.fillMaxWidth()) { Text("Detangle (Restart)") }
                }
                GraphLayoutMode.COMPUTED -> {
                    Text("Static Algorithms", style = MaterialTheme.typography.labelMedium)
                    Button(onClick = onTriggerLayout, modifier = Modifier.fillMaxWidth()) { Text("Re-run Detangle") }
                    Text(
                        "Nodes are frozen. Drag to move manually, or enable 'Snap Back' to let physics settle neighbors.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GraphLayoutMode.HIERARCHICAL -> {
                    Text("Tree Layout", style = MaterialTheme.typography.labelMedium)

                    CodexDropdown(
                        label = "Direction",
                        options = LayoutDirection.entries,
                        selectedOption = layoutDirection,
                        onOptionSelected = onLayoutDirectionChange,
                        displayTransform = { it.displayName },
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Button(onClick = onTriggerLayout, modifier = Modifier.fillMaxWidth()) { Text("Apply Layout") }
                }
            }
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>
) {
    val density = LocalDensityTokens.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = density.bodyFontSize)
            Text(
                text = String.format("%.2f", value),
                fontSize = density.bodyFontSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().height(24.dp)
        )
        Spacer(Modifier.height(8.dp))
    }
}

private fun Modifier.scale(scale: Float): Modifier = this