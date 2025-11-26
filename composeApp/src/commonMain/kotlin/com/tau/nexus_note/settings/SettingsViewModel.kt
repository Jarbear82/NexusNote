package com.tau.nexus_note.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the state for the Settings screen.
 * It receives the master StateFlow from MainViewModel (which is fed by the SettingsRepository)
 * and provides event handlers to update it via a lambda.
 */
class SettingsViewModel(
    /**
     * A flow that emits the current, persisted settings.
     * The View should collect this.
     */
    val settingsFlow: StateFlow<SettingsData>,

    /**
     * A lambda function to call when settings need to be updated.
     * This will trigger the SettingsRepository to save the new data.
     */
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
        // Now just resets to the simple default
        val newSettings = settingsFlow.value.copy(theme = ThemeSettings.Default)
        onUpdateSettings(newSettings)
    }

    // --- ADDED: New simplified color handlers ---

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
        // This would trigger navigation via the MainViewModel
        // For now, we just log it
        println("Directory change requested")
        // NOTE: This action is handled by MainViewModel,
        // so this function can be modified to call a lambda from MainViewModel
        // if you want to trigger the directory picker.
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