package com.tau.nexus_note.codex.graph

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.codex.graph.physics.PhysicsOptions
import com.tau.nexus_note.settings.GraphLayoutMode
import com.tau.nexus_note.settings.LayoutDirection
import com.tau.nexus_note.ui.components.CodexDropdown
import com.tau.nexus_note.ui.components.Icon
import com.tau.nexus_note.ui.components.IconButton
import com.tau.nexus_note.ui.theme.LocalDensityTokens

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
    // Phase 1: LOD
    lodThreshold: Float,
    onLodThresholdChange: (Float) -> Unit,
    // Phase 4
    isEditMode: Boolean,
    onToggleEditMode: () -> Unit,
    isSelectionMode: Boolean,
    onToggleSelectionMode: () -> Unit,
    onFitToScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensityTokens.current
    val scrollState = rememberScrollState()

    Card(
        modifier = modifier
            .width(320.dp)
            .heightIn(max = 600.dp), // Prevent overflow on small screens
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
    ) {
        Column(
            modifier = Modifier
                .padding(density.contentPadding)
                .verticalScroll(scrollState) // Enable scrolling
        ) {
            Text("Graph Tools", style = MaterialTheme.typography.titleMedium, fontSize = density.titleFontSize)
            Spacer(Modifier.height(16.dp))

            // --- Interaction Mode ---
            Text("Interaction", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = onToggleSelectionMode, colors = IconButtonDefaults.iconButtonColors(contentColor = if(isSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)) {
                    Icon(if(isSelectionMode) Icons.Default.SelectAll else Icons.Default.PanTool, "Selection Mode")
                }
                IconButton(onClick = onToggleEditMode, colors = IconButtonDefaults.iconButtonColors(contentColor = if(isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)) {
                    Icon(Icons.Default.Edit, "Edit Mode")
                }
                IconButton(onClick = onFitToScreen) {
                    Icon(Icons.Default.CenterFocusStrong, "Fit to Screen")
                }
            }
            if (isEditMode) {
                Text(
                    "Tap empty space to add Node.\nTap two nodes to link.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // --- Layout Strategy ---
            Text("Layout Mode", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            CodexDropdown(
                label = "Select Mode",
                options = GraphLayoutMode.entries,
                selectedOption = layoutMode,
                onOptionSelected = onLayoutModeChange,
                displayTransform = { it.displayName }
            )
            Spacer(Modifier.height(12.dp))

            // Contextual Controls based on Mode
            when (layoutMode) {
                GraphLayoutMode.CONTINUOUS -> {
                    // Simplified: Removed granular physics sliders (Gravity, Repulsion) to reduce clutter.
                    // Users can access deep physics tuning in the main Settings screen.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Snap Nodes on Release", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = snapEnabled, onCheckedChange = onSnapToggle, modifier = Modifier.scale(0.8f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onTriggerLayout, modifier = Modifier.fillMaxWidth()) {
                        Text("Detangle Settings")
                    }
                }
                GraphLayoutMode.COMPUTED -> {
                    Button(onClick = onTriggerLayout, modifier = Modifier.fillMaxWidth()) {
                        Text("Run Detangle Algorithm")
                    }
                    Text(
                        "Static layout. Drag nodes to adjust manually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                GraphLayoutMode.HIERARCHICAL -> {
                    CodexDropdown(
                        label = "Tree Direction",
                        options = LayoutDirection.entries,
                        selectedOption = layoutDirection,
                        onOptionSelected = onLayoutDirectionChange,
                        displayTransform = { it.displayName },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Button(onClick = onTriggerLayout, modifier = Modifier.fillMaxWidth()) {
                        Text("Apply Tree Layout")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // --- View Options ---
            Text("View Options", style = MaterialTheme.typography.labelMedium)
            SettingSlider("LOD Zoom Threshold", lodThreshold, onLodThresholdChange, 0.1f..1.0f)
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

private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)