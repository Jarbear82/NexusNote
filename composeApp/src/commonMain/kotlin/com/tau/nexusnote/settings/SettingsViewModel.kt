package com.tau.nexusnote.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tau.nexusnote.codex.graph.fcose.LayoutConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the state for the Settings screen.
 */
class SettingsViewModel(
    val settingsFlow: StateFlow<SettingsData>,
    private val onUpdateSettings: (SettingsData) -> Unit
) {

    // --- Theme ---
    fun onThemeModeChange(mode: ThemeMode) {
        val newSettings = settingsFlow.value.copy(
            theme = settingsFlow.value.theme.copy(themeMode = mode)
        )
        onUpdateSettings(newSettings)
    }

    fun onResetTheme() {
        val newSettings = settingsFlow.value.copy(theme = ThemeSettings.Default)
        onUpdateSettings(newSettings)
    }

    fun onAccentColorChange(color: Color) {
        val newSettings = settingsFlow.value.copy(
            theme = settingsFlow.value.theme.copy(accentColor = color.toArgb().toLong())
        )
        onUpdateSettings(newSettings)
    }

    fun onCustomBackgroundColorChange(color: Color) {
        val newSettings = settingsFlow.value.copy(
            theme = settingsFlow.value.theme.copy(customBackgroundColor = color.toArgb().toLong())
        )
        onUpdateSettings(newSettings)
    }

    // --- Graph Physics (Updated for LayoutConfig) ---
    // Note: LayoutConfig uses Double, Sliders use Float. Conversions required.

    fun onGravityChange(value: Float) {
        val newSettings = settingsFlow.value.copy(
            graphPhysics = settingsFlow.value.graphPhysics.copy(
                config = settingsFlow.value.graphPhysics.config.copy(gravityConstant = value.toDouble())
            )
        )
        onUpdateSettings(newSettings)
    }

    fun onRepulsionChange(value: Float) {
        val newSettings = settingsFlow.value.copy(
            graphPhysics = settingsFlow.value.graphPhysics.copy(
                config = settingsFlow.value.graphPhysics.config.copy(repulsionConstant = value.toDouble())
            )
        )
        onUpdateSettings(newSettings)
    }

    fun onIdealEdgeLengthChange(value: Float) { // Replaces "Spring" length/stiffness concept roughly
        val newSettings = settingsFlow.value.copy(
            graphPhysics = settingsFlow.value.graphPhysics.copy(
                config = settingsFlow.value.graphPhysics.config.copy(idealEdgeLength = value.toDouble())
            )
        )
        onUpdateSettings(newSettings)
    }

    fun onCoolingFactorChange(value: Float) { // Replaces Damping
        val newSettings = settingsFlow.value.copy(
            graphPhysics = settingsFlow.value.graphPhysics.copy(
                config = settingsFlow.value.graphPhysics.config.copy(coolingFactor = value.toDouble())
            )
        )
        onUpdateSettings(newSettings)
    }

    fun onResetPhysics() {
        val newSettings = settingsFlow.value.copy(graphPhysics = GraphPhysicsSettings.Default)
        onUpdateSettings(newSettings)
    }

    // --- Graph Rendering ---
    fun onStartSimulationOnLoadChange(enabled: Boolean) {
        val newSettings = settingsFlow.value.copy(
            graphRendering = settingsFlow.value.graphRendering.copy(startSimulationOnLoad = enabled)
        )
        onUpdateSettings(newSettings)
    }

    fun onShowNodeLabelsChange(enabled: Boolean) {
        val newSettings = settingsFlow.value.copy(
            graphRendering = settingsFlow.value.graphRendering.copy(showNodeLabels = enabled)
        )
        onUpdateSettings(newSettings)
    }

    fun onShowEdgeLabelsChange(enabled: Boolean) {
        val newSettings = settingsFlow.value.copy(
            graphRendering = settingsFlow.value.graphRendering.copy(showEdgeLabels = enabled)
        )
        onUpdateSettings(newSettings)
    }

    fun onShowCrosshairsChange(enabled: Boolean) {
        val newSettings = settingsFlow.value.copy(
            graphRendering = settingsFlow.value.graphRendering.copy(showCrosshairs = enabled)
        )
        onUpdateSettings(newSettings)
    }

    // --- Graph Interaction ---
    fun onZoomSensitivityChange(value: Float) {
        val newSettings = settingsFlow.value.copy(
            graphInteraction = settingsFlow.value.graphInteraction.copy(zoomSensitivity = value)
        )
        onUpdateSettings(newSettings)
    }

    fun onNodeBaseRadiusChange(value: Float) {
        val newSettings = settingsFlow.value.copy(
            graphInteraction = settingsFlow.value.graphInteraction.copy(nodeBaseRadius = value)
        )
        onUpdateSettings(newSettings)
    }

    fun onNodeRadiusEdgeFactorChange(value: Float) {
        val newSettings = settingsFlow.value.copy(
            graphInteraction = settingsFlow.value.graphInteraction.copy(nodeRadiusEdgeFactor = value)
        )
        onUpdateSettings(newSettings)
    }

    // --- Data ---
    fun onChangeDefaultDirectory() {
        // Handled by MainViewModel logic usually
    }

    fun onAutoLoadLastCodexChange(enabled: Boolean) {
        val newSettings = settingsFlow.value.copy(
            data = settingsFlow.value.data.copy(autoLoadLastCodex = enabled)
        )
        onUpdateSettings(newSettings)
    }

    fun onAutoRefreshCodexChange(enabled: Boolean) {
        val newSettings = settingsFlow.value.copy(
            data = settingsFlow.value.data.copy(autoRefreshCodex = enabled)
        )
        onUpdateSettings(newSettings)
    }

    fun onRefreshIntervalChange(value: Float) {
        val newSettings = settingsFlow.value.copy(
            data = settingsFlow.value.data.copy(refreshInterval = value)
        )
        onUpdateSettings(newSettings)
    }

    // --- General ---
    fun onStartupScreenChange(screen: String) {
        val newSettings = settingsFlow.value.copy(
            general = settingsFlow.value.general.copy(startupScreen = screen)
        )
        onUpdateSettings(newSettings)
    }

    fun onDefaultCodexViewChange(view: String) {
        val newSettings = settingsFlow.value.copy(
            general = settingsFlow.value.general.copy(defaultCodexView = view)
        )
        onUpdateSettings(newSettings)
    }

    fun onConfirmNodeEdgeDeletionChange(enabled: Boolean) {
        val newSettings = settingsFlow.value.copy(
            general = settingsFlow.value.general.copy(confirmNodeEdgeDeletion = enabled)
        )
        onUpdateSettings(newSettings)
    }

    fun onConfirmSchemaDeletionChange(enabled: Boolean) {
        val newSettings = settingsFlow.value.copy(
            general = settingsFlow.value.general.copy(confirmSchemaDeletion = enabled)
        )
        onUpdateSettings(newSettings)
    }
}