package com.tau.nexus_note.codex.graph

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.ui.theme.LocalDensityTokens
import kotlin.math.roundToInt

/**
 * Defines the available static layout algorithms.
 */
enum class DetangleAlgorithm(val description: String) {
    FRUCHTERMAN_REINGOLD("Fruchterman-Reingold - General Purpose"),
    KAMADA_KAWAI("Kamada-Kawai - High-Quality (Small)"),
    CIRCULAR("Circular - Fastest (Cycles)"),
    HIERARCHICAL("Hierarchical - Directed Graphs (Not Implemented)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetangleSettingsDialog(
    onDismiss: () -> Unit,
    onDetangle: (DetangleAlgorithm, Map<String, Any>) -> Unit
) {
    // --- General State ---
    var selectedAlgorithm by remember { mutableStateOf(DetangleAlgorithm.FRUCHTERMAN_REINGOLD) }
    var expanded by remember { mutableStateOf(false) }

    // --- FR Parameters ---
    var frIterations by remember { mutableStateOf(500f) }
    var frArea by remember { mutableStateOf(1.0f) }
    var frGravity by remember { mutableStateOf(0.1f) }

    // --- KK Parameters ---
    var kkEpsilon by remember { mutableStateOf(1.0f) }
    var kkSpringConstant by remember { mutableStateOf(0.8f) }

    val density = LocalDensityTokens.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Detangle Settings", fontSize = density.titleFontSize) },
        text = {
            Column {
                // --- Algorithm Selection ---
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedAlgorithm.description,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Algorithm") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = density.bodyFontSize)
                    )
                    ExposedDropdownMenu(expanded, { expanded = false }) {
                        DetangleAlgorithm.entries.forEach { alg ->
                            DropdownMenuItem(
                                text = { Text(alg.description, fontSize = density.bodyFontSize) },
                                onClick = {
                                    selectedAlgorithm = alg
                                    expanded = false
                                },
                                enabled = alg != DetangleAlgorithm.HIERARCHICAL // Disable unavailable
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // --- Tunable Parameters ---
                when (selectedAlgorithm) {
                    DetangleAlgorithm.FRUCHTERMAN_REINGOLD -> {
                        Text("Iterations: ${frIterations.toInt()}", fontSize = density.bodyFontSize)
                        Slider(
                            value = frIterations,
                            onValueChange = { frIterations = it },
                            valueRange = 100f..2000f
                        )
                        Text("Area: ${String.format("%.1f", frArea)}", fontSize = density.bodyFontSize)
                        Slider(
                            value = frArea,
                            onValueChange = { frArea = it },
                            valueRange = 0.1f..5.0f
                        )
                        Text("Gravity: ${String.format("%.2f", frGravity)}", fontSize = density.bodyFontSize)
                        Slider(
                            value = frGravity,
                            onValueChange = { frGravity = it },
                            valueRange = 0.0f..1.0f
                        )
                    }

                    DetangleAlgorithm.KAMADA_KAWAI -> {
                        Text("Epsilon (Tolerance): ${String.format("%.1f", kkEpsilon)}", fontSize = density.bodyFontSize)
                        Slider(
                            value = kkEpsilon,
                            onValueChange = { kkEpsilon = it },
                            valueRange = 0.1f..10.0f
                        )
                        Text("Spring Constant: ${String.format("%.1f", kkSpringConstant)}", fontSize = density.bodyFontSize)
                        Slider(
                            value = kkSpringConstant,
                            onValueChange = { kkSpringConstant = it },
                            valueRange = 0.1f..5.0f
                        )
                    }

                    DetangleAlgorithm.CIRCULAR -> {
                        Text("Parameters for Circular Layout (Not Implemented)", fontSize = density.bodyFontSize)
                    }

                    DetangleAlgorithm.HIERARCHICAL -> {
                        Text("Parameters for Hierarchical Layout (Not Implemented)", fontSize = density.bodyFontSize)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val params = when (selectedAlgorithm) {
                        DetangleAlgorithm.FRUCHTERMAN_REINGOLD -> mapOf(
                            "iterations" to frIterations.toInt(),
                            "area" to frArea,
                            "gravity" to frGravity
                        )
                        else -> emptyMap()
                    }
                    onDetangle(selectedAlgorithm, params)
                },
                modifier = Modifier.height(density.buttonHeight)
            ) { Text("Detangle") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(density.buttonHeight)) { Text("Cancel") }
        }
    )
}