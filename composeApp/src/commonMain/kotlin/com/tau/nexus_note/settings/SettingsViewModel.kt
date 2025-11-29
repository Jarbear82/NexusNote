package com.tau.nexus_note.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.StateFlow

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

    fun onDensityModeChange(mode: DensityMode) {
        val newSettings = settingsFlow.value.copy(
            theme = settingsFlow.value.theme.copy(densityMode = mode)
        )
        onUpdateSettings(newSettings)
    }

    fun onUseRoundedCornersChange(enabled: Boolean) {
        val newSettings = settingsFlow.value.copy(
            theme = settingsFlow.value.theme.copy(useRoundedCorners = enabled)
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

    // --- Graph Physics ---
    fun onGravityChange(value: Float) {
        val newSettings = settingsFlow.value.copy(
            graphPhysics = settingsFlow.value.graphPhysics.copy(
                options = settingsFlow.value.graphPhysics.options.copy(gravity = value)
            )
        )
        onUpdateSettings(newSettings)
    }

    fun onRepulsionChange(value: Float) {
        val newSettings = settingsFlow.value.copy(
            graphPhysics = settingsFlow.value.graphPhysics.copy(
                options = settingsFlow.value.graphPhysics.options.copy(repulsion = value)
            )
        )
        onUpdateSettings(newSettings)
    }

    fun onSpringChange(value: Float) {
        val newSettings = settingsFlow.value.copy(
            graphPhysics = settingsFlow.value.graphPhysics.copy(
                options = settingsFlow.value.graphPhysics.options.copy(spring = value)
            )
        )
        onUpdateSettings(newSettings)
    }

    fun onDampingChange(value: Float) {
        val newSettings = settingsFlow.value.copy(
            graphPhysics = settingsFlow.value.graphPhysics.copy(
                options = settingsFlow.value.graphPhysics.options.copy(damping = value)
            )
        )
        onUpdateSettings(newSettings)
    }

    fun onBarnesHutThetaChange(value: Float) {
        val newSettings = settingsFlow.value.copy(
            graphPhysics = settingsFlow.value.graphPhysics.copy(
                options = settingsFlow.value.graphPhysics.options.copy(barnesHutTheta = value)
            )
        )
        onUpdateSettings(newSettings)
    }

    fun onToleranceChange(value: Float) {
        val newSettings = settingsFlow.value.copy(
            graphPhysics = settingsFlow.value.graphPhysics.copy(
                options = settingsFlow.value.graphPhysics.options.copy(tolerance = value)
            )
        )
        onUpdateSettings(newSettings)
    }

    // New: Change layout mode default
    fun onDefaultLayoutModeChange(mode: GraphLayoutMode) {
        val newSettings = settingsFlow.value.copy(
            graphPhysics = settingsFlow.value.graphPhysics.copy(layoutMode = mode)
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

    fun onLodThresholdChange(value: Float) {
        val newSettings = settingsFlow.value.copy(
            graphRendering = settingsFlow.value.graphRendering.copy(lodThreshold = value)
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
        val newOptions = settingsFlow.value.graphPhysics.options.copy(nodeBaseRadius = value)
        val newSettings = settingsFlow.value.copy(
            graphPhysics = settingsFlow.value.graphPhysics.copy(options = newOptions),
            graphInteraction = settingsFlow.value.graphInteraction.copy(nodeBaseRadius = value)
        )
        onUpdateSettings(newSettings)
    }

    fun onNodeRadiusEdgeFactorChange(value: Float) {
        val newOptions = settingsFlow.value.graphPhysics.options.copy(nodeRadiusEdgeFactor = value)
        val newSettings = settingsFlow.value.copy(
            graphPhysics = settingsFlow.value.graphPhysics.copy(options = newOptions),
            graphInteraction = settingsFlow.value.graphInteraction.copy(nodeRadiusEdgeFactor = value)
        )
        onUpdateSettings(newSettings)
    }

    // --- Data ---
    fun onChangeDefaultDirectory() {
        println("Directory change requested")
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