package com.tau.nexusnote.codex.graph

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tau.nexusnote.codex.graph.fcose.LayoutConfig
import com.tau.nexusnote.codex.graph.physics.PhysicsOptions
import com.tau.nexusnote.ui.components.CodexDropdown
import com.tau.nexusnote.ui.components.CodexSectionHeader
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun GraphSettingsView(
    options: PhysicsOptions,
    onDetangleClick: () -> Unit, // Kept for compatibility
    modifier: Modifier = Modifier,
    viewModel: GraphViewmodel? = null,
    primarySelectedId: Long? = null,
    secondarySelectedId: Long? = null
) {
    if (viewModel == null) {
        Text("No ViewModel linked", modifier = modifier.padding(16.dp))
        return
    }

    // --- State Observables ---
    val layoutConfig by viewModel.layoutConfig.collectAsState()
    val simulationEnabled by viewModel.simulationEnabled.collectAsState()
    val isSimulationPaused by viewModel.isSimulationPaused.collectAsState()
    val renderingSettings by viewModel.renderingSettings.collectAsState()
    val scope = rememberCoroutineScope()

    // --- Local UI State ---
    var selectedAlgorithm by remember { mutableStateOf(DetangleAlgorithm.FCOSE) }
    var isFcoseManual by remember { mutableStateOf(false) }

    // --- Local Params for Non-fCoSE Algorithms ---
    var frIterations by remember { mutableStateOf(500f) }
    var frGravity by remember { mutableStateOf(0.1f) }
    var kkSpringConstant by remember { mutableStateOf(0.8f) }
    var kkIterations by remember { mutableStateOf(100f) }

    Card(
        modifier = modifier
            .width(360.dp)
            .heightIn(max = 800.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // =================================================================
            // 0. VISUAL SETTINGS
            // =================================================================
            CodexSectionHeader("Visuals")

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show Attributes as Nodes", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = renderingSettings.showAttributesAsNodes,
                    onCheckedChange = { viewModel.updateRenderingSettings(renderingSettings.copy(showAttributesAsNodes = it)) }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // =================================================================
            // 1. HEADER & SELECTION
            // =================================================================
            CodexSectionHeader("Layout & Physics")

            Text(
                "Detangle Algorithm",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            CodexDropdown(
                label = "Algorithm",
                options = DetangleAlgorithm.entries.filter {
                    it != DetangleAlgorithm.HIERARCHICAL && it != DetangleAlgorithm.CIRCULAR
                },
                selectedOption = selectedAlgorithm,
                onOptionSelected = { selectedAlgorithm = it },
                displayTransform = { it.description.split(" - ").first() }
            )

            Spacer(Modifier.height(16.dp))

            // =================================================================
            // 2. DYNAMIC CONTENT AREA
            // =================================================================

            AnimatedVisibility(visible = selectedAlgorithm == DetangleAlgorithm.FCOSE) {
                Column {
                    // --- Mode Toggle ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        SegmentedButton(
                            selected = !isFcoseManual,
                            onClick = { isFcoseManual = false },
                            label = "Automatic"
                        )
                        Spacer(Modifier.width(8.dp))
                        SegmentedButton(
                            selected = isFcoseManual,
                            onClick = { isFcoseManual = true },
                            label = "Manual"
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // --- Action Buttons ---
                    if (!isFcoseManual) {
                        Button(
                            onClick = { scope.launch { viewModel.runFullFcosePipeline() } },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text("Run Full Detangle", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Manual Step Grid
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionButton("Randomize", Modifier.weight(1f)) { viewModel.runRandomize() }
                                ActionButton("Draft", Modifier.weight(1f)) { viewModel.runDraft() }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionButton("Transform", Modifier.weight(1f)) { viewModel.runTransform() }
                                ActionButton("Enforce", Modifier.weight(1f)) { viewModel.runEnforce() }
                            }
                            ActionButton("Polish (Settle)", Modifier.fillMaxWidth()) { viewModel.runPolishing() }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // --- fCoSE Parameters ---
                    Text("Parameters", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)

                    PhysicsSlider(
                        "Ideal Edge Length",
                        layoutConfig.idealEdgeLength.toFloat(),
                        10f..200f
                    ) { viewModel.updateLayoutConfig(layoutConfig.copy(idealEdgeLength = it.toDouble())) }

                    PhysicsSlider(
                        "Repulsion Strength",
                        layoutConfig.repulsionConstant.toFloat(),
                        1000f..10000f
                    ) { viewModel.updateLayoutConfig(layoutConfig.copy(repulsionConstant = it.toDouble())) }

                    PhysicsSlider(
                        "Gravity",
                        layoutConfig.gravityConstant.toFloat(),
                        0f..1f
                    ) { viewModel.updateLayoutConfig(layoutConfig.copy(gravityConstant = it.toDouble())) }

                    PhysicsSlider(
                        "Compound Gravity",
                        layoutConfig.compoundGravityConstant.toFloat(),
                        0f..5f
                    ) { viewModel.updateLayoutConfig(layoutConfig.copy(compoundGravityConstant = it.toDouble())) }
                }
            }

            AnimatedVisibility(visible = selectedAlgorithm != DetangleAlgorithm.FCOSE) {
                Column {
                    Button(
                        onClick = {
                            val params = when(selectedAlgorithm) {
                                DetangleAlgorithm.FRUCHTERMAN_REINGOLD -> mapOf(
                                    "iterations" to frIterations.toInt(),
                                    "gravity" to frGravity
                                )
                                DetangleAlgorithm.KAMADA_KAWAI -> mapOf(
                                    "springConstant" to kkSpringConstant,
                                    "iterations" to kkIterations.toInt()
                                )
                                else -> emptyMap()
                            }
                            scope.launch { viewModel.startDetangle(selectedAlgorithm, params) }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("Detangle Graph", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Parameters", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)

                    if (selectedAlgorithm == DetangleAlgorithm.FRUCHTERMAN_REINGOLD) {
                        PhysicsSlider("Iterations", frIterations, 100f..2000f) { frIterations = it }
                        PhysicsSlider("Gravity", frGravity, 0f..1f) { frGravity = it }
                    } else if (selectedAlgorithm == DetangleAlgorithm.KAMADA_KAWAI) {
                        PhysicsSlider("Spring Constant", kkSpringConstant, 0.1f..5f) { kkSpringConstant = it }
                        PhysicsSlider("Iterations", kkIterations, 10f..500f) { kkIterations = it }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // =================================================================
            // 3. PHYSICS SIMULATION SETTINGS
            // =================================================================
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Physics Simulation", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (simulationEnabled) {
                    Badge(containerColor = if (isSimulationPaused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary) {
                        Text(if (isSimulationPaused) "Sleeping" else "Active")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Pause / Resume Toggle
            Button(
                onClick = {
                    if (simulationEnabled) viewModel.stopSimulation() else viewModel.startSimulation()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (simulationEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(if (simulationEnabled) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (simulationEnabled) "Pause Simulation" else "Resume Simulation")
            }

            Spacer(Modifier.height(12.dp))

            PhysicsSlider(
                "Sim. Gravity",
                options.gravity,
                0f..2f
            ) { viewModel.updatePhysicsOptions(options.copy(gravity = it)) }

            PhysicsSlider(
                "Sim. Drag (Damping)",
                options.damping,
                0.1f..1.0f
            ) { viewModel.updatePhysicsOptions(options.copy(damping = it)) }

            PhysicsSlider(
                "Sim. Speed (Tolerance)",
                options.tolerance,
                0.1f..5.0f
            ) { viewModel.updatePhysicsOptions(options.copy(tolerance = it)) }
        }
    }
}

// --- Helpers ---

@Composable
private fun SegmentedButton(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    OutlinedButton(
        onClick = onClick,
        colors = if (selected) ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) else ButtonDefaults.outlinedButtonColors()
    ) {
        Text(label)
    }
}

@Composable
private fun ActionButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun PhysicsSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                text = String.format("%.2f", value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.height(20.dp)
        )
    }
}