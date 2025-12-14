package com.tau.nexusnote.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.ui.components.CodexSectionHeader
import com.tau.nexusnote.utils.hexToColor
import kotlin.math.roundToInt

@Composable
fun SettingsView(
    viewModel: SettingsViewModel
) {

    val settings by viewModel.settingsFlow.collectAsState()
    var selectedCategory by remember { mutableStateOf(SettingsCategory.APPEARANCE) }

    Row(modifier = Modifier.fillMaxSize()) {
        // --- Navigation Rail ---
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Spacer(Modifier.height(8.dp))
            SettingsCategory.entries.forEach { category ->
                NavigationRailItem(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    icon = { Icon(category.icon, contentDescription = category.title) },
                    label = { Text(category.title) },
                    alwaysShowLabel = true
                )
            }
        }

        // --- Content Area ---
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            when (selectedCategory) {
                SettingsCategory.APPEARANCE -> {
                    item {
                        CodexSectionHeader("Appearance & Theme")
                        ThemeSettingsSection(settings.theme, viewModel)
                    }
                }
                SettingsCategory.GRAPH -> {
                    item {
                        CodexSectionHeader("Graph View")
                        GraphSettingsSection(settings, viewModel)
                    }
                }
                SettingsCategory.DATA -> {
                    item {
                        CodexSectionHeader("Data & Codex")
                        DataSettingsSection(settings.data, viewModel)
                    }
                }
                SettingsCategory.GENERAL -> {
                    item {
                        CodexSectionHeader("General")
                        GeneralSettingsSection(settings.general, viewModel)
                    }
                }
                SettingsCategory.ABOUT -> {
                    item {
                        CodexSectionHeader("About")
                        AboutSection()
                    }
                }
            }
        }
    }


}

// --- Appearance Section ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSettingsSection(
    theme: ThemeSettings,
    viewModel: SettingsViewModel
) {
// --- Theme Mode Dropdown ---
    SettingDropdown(
        label = "Theme Mode",
        selected = theme.themeMode.name,
        options = ThemeMode.entries.map { it.name },
        onSelected = { viewModel.onThemeModeChange(ThemeMode.valueOf(it)) }
    )

// --- Single Accent Color Picker (always visible) ---
    ColorSettingItem(
        label = "Accent Color",
        color = Color(theme.accentColor),
        onColorChange = { viewModel.onAccentColorChange(it) }
    )

// --- Conditional visibility for Custom Background ---
    AnimatedVisibility(visible = theme.themeMode == ThemeMode.CUSTOM) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Custom Background", style = MaterialTheme.typography.titleMedium)
            ColorSettingItem(
                label = "Background",
                color = Color(theme.customBackgroundColor),
                onColorChange = { viewModel.onCustomBackgroundColorChange(it) }
            )
        }
    }

// --- Reset Button ---
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = viewModel::onResetTheme,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Reset Theme to Defaults")
    }


}

// --- Graph View Section ---
@Composable
private fun GraphSettingsSection(
    settings: SettingsData,
    viewModel: SettingsViewModel
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Physics", "Rendering", "Interaction")

    PrimaryTabRow(selectedTabIndex = selectedTab) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedTab == index,
                onClick = { selectedTab = index },
                text = { Text(title) }
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    when (selectedTab) {
        0 -> GraphPhysicsSubSection(settings.graphPhysics, viewModel)
        1 -> GraphRenderingSubSection(settings.graphRendering, viewModel)
        2 -> GraphInteractionSubSection(settings.graphInteraction, viewModel)
    }


}

@Composable
private fun GraphPhysicsSubSection(
    physics: GraphPhysicsSettings,
    viewModel: SettingsViewModel
) {
// Recommended Defaults Text
    InfoCard(
        "Recommended Defaults: Gravity: 0.5, Repulsion: 2000, Spring: 0.1, Damping: 0.9, Barnes-Hut: 1.2, Tolerance: 1.0"
    )

    SettingSlider(
        label = "Gravity",
        value = physics.options.gravity,
        onValueChange = viewModel::onGravityChange,
        range = 0f..2f
    )
    SettingSlider(
        label = "Repulsion",
        value = physics.options.repulsion,
        onValueChange = viewModel::onRepulsionChange,
        range = 0f..10000f
    )
    SettingSlider(
        label = "Spring Stiffness",
        value = physics.options.spring,
        onValueChange = viewModel::onSpringChange,
        range = 0.01f..1f
    )
    SettingSlider(
        label = "Damping",
        value = physics.options.damping,
        onValueChange = viewModel::onDampingChange,
        range = 0.5f..1f
    )
    SettingSlider(
        label = "Barnes-Hut Theta",
        value = physics.options.barnesHutTheta,
        onValueChange = viewModel::onBarnesHutThetaChange,
        range = 0.1f..3f
    )
    SettingSlider(
        label = "Tolerance (Speed)",
        value = physics.options.tolerance,
        onValueChange = viewModel::onToleranceChange,
        range = 0.1f..10f
    )

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = viewModel::onResetPhysics,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Reset Physics to Defaults")
    }


}

@Composable
private fun GraphRenderingSubSection(
    rendering: GraphRenderingSettings,
    viewModel: SettingsViewModel
) {
    SettingToggle(
        label = "Start Simulation on Load",
        checked = rendering.startSimulationOnLoad,
        onCheckedChange = viewModel::onStartSimulationOnLoadChange
    )
    SettingToggle(
        label = "Show Node Labels by Default",
        checked = rendering.showNodeLabels,
        onCheckedChange = viewModel::onShowNodeLabelsChange
    )
    SettingToggle(
        label = "Show Edge Labels by Default",
        checked = rendering.showEdgeLabels,
        onCheckedChange = viewModel::onShowEdgeLabelsChange
    )
    SettingToggle(
        label = "Show Center Crosshairs",
        checked = rendering.showCrosshairs,
        onCheckedChange = viewModel::onShowCrosshairsChange
    )
}

@Composable
private fun GraphInteractionSubSection(
    interaction: GraphInteractionSettings,
    viewModel: SettingsViewModel
) {
    SettingSlider(
        label = "Scroll Zoom Sensitivity",
        value = interaction.zoomSensitivity,
        onValueChange = viewModel::onZoomSensitivityChange,
        range = 0.5f..2.0f
    )
    SettingSlider(
        label = "Node Base Radius",
        value = interaction.nodeBaseRadius,
        onValueChange = viewModel::onNodeBaseRadiusChange,
        range = 5f..50f
    )
    SettingSlider(
        label = "Node Radius Edge Factor",
        value = interaction.nodeRadiusEdgeFactor,
        onValueChange = viewModel::onNodeRadiusEdgeFactorChange,
        range = 0.5f..5.0f
    )
}

// --- Data & Codex Section ---
@Composable
private fun DataSettingsSection(
    data: DataSettings,
    viewModel: SettingsViewModel
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Default Codex Directory", style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = data.defaultCodexDirectory,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .padding(8.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = viewModel::onChangeDefaultDirectory) {
                Text("Change")
            }
        }
    }

    SettingToggle(
        label = "Auto-load Last Codex on Startup",
        checked = data.autoLoadLastCodex,
        onCheckedChange = viewModel::onAutoLoadLastCodexChange
    )
    SettingToggle(
        label = "Auto-Refresh Codex Data",
        checked = data.autoRefreshCodex,
        onCheckedChange = viewModel::onAutoRefreshCodexChange
    )
// Add other data settings here


}

// --- General Section ---
@Composable
private fun GeneralSettingsSection(
    general: GeneralSettings,
    viewModel: SettingsViewModel
) {
    SettingDropdown(
        label = "Startup Screen",
        selected = general.startupScreen,
        options = listOf("Nexus", "Last Codex"),
        onSelected = viewModel::onStartupScreenChange
    )
    SettingDropdown(
        label = "Default Codex View",
        selected = general.defaultCodexView,
        options = listOf("Graph", "List"),
        onSelected = viewModel::onDefaultCodexViewChange
    )
    SettingDropdown(
        label = "Default Markdown Flavor",
        selected = general.defaultMarkdownFlavor,
        options = listOf("Obsidian", "CommonMark", "Github Flavor"),
        onSelected = { /* TODO */ }
    )
    SettingToggle(
        label = "Confirm Node/Edge Deletion",
        checked = general.confirmNodeEdgeDeletion,
        onCheckedChange = viewModel::onConfirmNodeEdgeDeletionChange
    )
    SettingToggle(
        label = "Confirm Schema Deletion",
        checked = general.confirmSchemaDeletion,
        onCheckedChange = viewModel::onConfirmSchemaDeletionChange
    )
}

// --- About Section ---
@Composable
private fun AboutSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Nexus Note Version 1.0.0", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { /* TODO */ }, modifier = Modifier.fillMaxWidth()) {
            Text("Check for Updates")
        }
        Button(onClick = { /* TODO */ }, modifier = Modifier.fillMaxWidth()) {
            Text("View Licenses")
        }
        Button(onClick = { /* TODO */ }, modifier = Modifier.fillMaxWidth()) {
            Text("View README")
        }
    }
}

// --- Reusable Setting Composables ---

@Composable
private fun SettingToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (value > 100) value.roundToInt().toString() else String.format("%.2f", value),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }


}

/**
 * A compact, two-row widget for editing a color.
 * Includes label, color preview, hex input, and RGB sliders.
 */
@OptIn(ExperimentalStdlibApi::class)
@Composable
private fun ColorSettingItem(
    label: String,
    color: Color,
    onColorChange: (Color) -> Unit
) {
    val hex = "#" + color.toArgb().toHexString(HexFormat.UpperCase).substring(2)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Top row: Label, Preview, Hex
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(label, modifier = Modifier.width(100.dp))
            Box(
                modifier = Modifier.size(24.dp)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface)
            )
            OutlinedTextField(
                value = hex,
                onValueChange = { onColorChange(hexToColor(it)) },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Bottom row: Sliders
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Red
            Text("R", color = Color.Red, fontWeight = FontWeight.Bold)
            Slider(
                value = color.red,
                onValueChange = { onColorChange(color.copy(red = it)) },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red)
            )
            // Green
            Text("G", color = Color.Green, fontWeight = FontWeight.Bold)
            Slider(
                value = color.green,
                onValueChange = { onColorChange(color.copy(green = it)) },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Color.Green, activeTrackColor = Color.Green)
            )
            // Blue
            Text("B", color = Color.Blue, fontWeight = FontWeight.Bold)
            Slider(
                value = color.blue,
                onValueChange = { onColorChange(color.copy(blue = it)) },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue)
            )
        }


    }
}

@Composable
private fun InfoCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.padding(end = 8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}