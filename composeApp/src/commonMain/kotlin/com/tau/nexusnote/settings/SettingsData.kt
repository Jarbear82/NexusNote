package com.tau.nexusnote.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import com.tau.nexusnote.codex.graph.physics.PhysicsOptions
import com.tau.nexusnote.utils.getHomeDirectoryPath
import kotlinx.serialization.Serializable

// NEW: Enum to represent the settings categories for the UI
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

/**

Enumeration for the possible theme modes.
 */
@Serializable
enum class ThemeMode {
    LIGHT, DARK, SYSTEM, CUSTOM
}

/**

Holds all settings related to a custom color theme.

All colors are stored as Longs (ARGB). "On" colors are derived automatically.
 */
@Serializable
data class ThemeSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: Long = 0xFF33C3FF, // A nice default blue accent
    val customBackgroundColor: Long = 0xFF121212 // Default custom background (dark)
) {
    companion object {
        val Default = ThemeSettings()
    }
}

/**

Holds settings for default graph physics.

This re-uses the PhysicsOptions data class.
 */
@Serializable
data class GraphPhysicsSettings(
    val options: PhysicsOptions = PhysicsOptions(
        gravity = 0.25f,
        repulsion = 7000f,
        spring = 0.1f,
        damping = 0.8f,
        nodeBaseRadius = 15f,
        nodeRadiusEdgeFactor = 1.0f,
        minDistance = 10.0f,
        barnesHutTheta = 0.8f,
        tolerance = 1.0f
    )
) {
    companion object {
        val Default = GraphPhysicsSettings()
    }
}

/**

Holds settings for graph rendering.
 */
@Serializable
data class GraphRenderingSettings(
    val startSimulationOnLoad: Boolean = true,
    val showNodeLabels: Boolean = true,
    val showEdgeLabels: Boolean = true,
    val showCrosshairs: Boolean = true,
    val showAttributesAsNodes: Boolean = false
) {
    companion object {
        val Default = GraphRenderingSettings()
    }
}

/**

Holds settings for graph interaction.
 */
@Serializable
data class GraphInteractionSettings(
    val zoomSensitivity: Float = 1.0f,
    val nodeBaseRadius: Float = 15f,
    val nodeRadiusEdgeFactor: Float = 2.0f
) {
    companion object {
        val Default = GraphInteractionSettings()
    }
}

/**

Holds settings for data and codex file management.
 */
@Serializable
data class DataSettings(
    val defaultCodexDirectory: String = getHomeDirectoryPath(),
    val autoLoadLastCodex: Boolean = false,
    val autoRefreshCodex: Boolean = false,
    val refreshInterval: Float = 60f // in seconds
) {
    companion object {
        val Default = DataSettings()
    }
}

/**

Holds settings for general application behavior settings.
 */
@Serializable
data class GeneralSettings(
    val startupScreen: String = "Nexus", // "Nexus" or "Last Codex"
    val defaultCodexView: String = "Graph", // "Graph" or "List"
    val confirmNodeEdgeDeletion: Boolean = true,
    val confirmSchemaDeletion: Boolean = true,
    val defaultMarkdownFlavor: String = "Obsidian"
) {
    companion object {
        val Default = GeneralSettings()
    }
}

/**

Root data class holding all application settings.
*/
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
/*
 * The master default settings for the entire application.
*/
val Default = SettingsData()
}
}