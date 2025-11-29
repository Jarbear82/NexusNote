package com.tau.nexus_note.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import com.tau.nexus_note.codex.graph.physics.PhysicsOptions
import kotlinx.serialization.Serializable

enum class SettingsCategory(
    val title: String,
    val icon: ImageVector
) {
    APPEARANCE("Appearance", Icons.Default.Palette),
    GRAPH("Graph", Icons.Default.Hub),
    DATA("Data", Icons.Default.Storage),
    GENERAL("General", Icons.Default.Settings),
    ABOUT("About", Icons.Default.Info)
}

@Serializable
enum class ThemeMode {
    LIGHT, DARK, SYSTEM, CUSTOM
}

@Serializable
enum class DensityMode {
    COMPACT, COMFORTABLE, LARGE
}

/**
 * Defines the operational mode of the Graph View.
 */
@Serializable
enum class GraphLayoutMode(val displayName: String) {
    CONTINUOUS("Continuous (Simulation)"),
    COMPUTED("Computed (Static)"),
    HIERARCHICAL("Hierarchical (Tree)")
}

/**
 * Defines the flow direction for Hierarchical layouts.
 */
@Serializable
enum class LayoutDirection(val displayName: String) {
    TOP_BOTTOM("Top -> Bottom"),
    BOTTOM_TOP("Bottom -> Top"),
    LEFT_RIGHT("Left -> Right"),
    RIGHT_LEFT("Right -> Left")
}

@Serializable
data class ThemeSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val densityMode: DensityMode = DensityMode.COMFORTABLE,
    val useRoundedCorners: Boolean = false,
    val accentColor: Long = 0xFF33C3FF,
    val customBackgroundColor: Long = 0xFF121212
) {
    companion object {
        val Default = ThemeSettings()
    }
}

@Serializable
data class GraphPhysicsSettings(
    val layoutMode: GraphLayoutMode = GraphLayoutMode.CONTINUOUS,
    val hierarchicalDirection: LayoutDirection = LayoutDirection.LEFT_RIGHT, // Added Direction
    val options: PhysicsOptions = PhysicsOptions(
        gravity = 0.5f,
        repulsion = 2000f,
        spring = 0.1f,
        damping = 0.9f,
        nodeBaseRadius = 15f,
        nodeRadiusEdgeFactor = 2.0f,
        minDistance = 2.0f,
        barnesHutTheta = 1.2f,
        tolerance = 1.0f
    )
) {
    companion object {
        val Default = GraphPhysicsSettings()
    }
}

@Serializable
data class GraphRenderingSettings(
    val showNodeLabels: Boolean = true,
    val showEdgeLabels: Boolean = true,
    val showCrosshairs: Boolean = true,
    val startSimulationOnLoad: Boolean = true,
    val lodThreshold: Float = 0.001f // UPDATED: Lowered threshold so nodes stay detailed longer
) {
    companion object {
        val Default = GraphRenderingSettings()
    }
}

@Serializable
data class GraphInteractionSettings(
    val zoomSensitivity: Float = 1.0f,
    val nodeBaseRadius: Float = 15f,
    val nodeRadiusEdgeFactor: Float = 2.0f,
    val snapToGrid: Boolean = false
) {
    companion object {
        val Default = GraphInteractionSettings()
    }
}

@Serializable
data class DataSettings(
    val defaultCodexDirectory: String = com.tau.nexus_note.utils.getHomeDirectoryPath(),
    val autoLoadLastCodex: Boolean = false,
    val autoRefreshCodex: Boolean = false,
    val refreshInterval: Float = 60f
) {
    companion object {
        val Default = DataSettings()
    }
}

@Serializable
data class GeneralSettings(
    val startupScreen: String = "Nexus",
    val defaultCodexView: String = "Graph",
    val confirmNodeEdgeDeletion: Boolean = true,
    val confirmSchemaDeletion: Boolean = true,
    val defaultMarkdownFlavor: String = "Obsidian"
) {
    companion object {
        val Default = GeneralSettings()
    }
}

@Serializable
data class SettingsData(
    val theme: ThemeSettings = ThemeSettings.Default,
    val graphPhysics: GraphPhysicsSettings = GraphPhysicsSettings.Default,
    val graphRendering: GraphRenderingSettings = GraphRenderingSettings.Default,
    val graphInteraction: GraphInteractionSettings = GraphInteractionSettings.Default,
    val data: DataSettings = DataSettings.Default,
    val general: GeneralSettings = GeneralSettings.Default
) {
    companion object {
        val Default = SettingsData()
    }
}