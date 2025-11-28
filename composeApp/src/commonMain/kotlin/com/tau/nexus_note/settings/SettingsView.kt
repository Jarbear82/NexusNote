package com.tau.nexus_note.settings

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
import com.tau.nexus_note.ui.components.CodexDropdown
import com.tau.nexus_note.ui.components.CodexSectionHeader
import com.tau.nexus_note.ui.components.CodexTab
import com.tau.nexus_note.ui.components.CodexTextField
import com.tau.nexus_note.ui.theme.LocalDensityTokens
import com.tau.nexus_note.utils.hexToColor
import kotlin.math.roundToInt

@Composable
fun SettingsView(
    viewModel: SettingsViewModel
) {
    val settings by viewModel.settingsFlow.collectAsState()
    var selectedCategory by remember { mutableStateOf(SettingsCategory.APPEARANCE) }
    val density = LocalDensityTokens.current

    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight().width(density.navRailWidth),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Spacer(Modifier.height(8.dp))
            SettingsCategory.entries.forEach { category ->
                NavigationRailItem(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    icon = { Icon(category.icon, contentDescription = category.title, modifier = Modifier.size(density.iconSize)) },
                    label = { Text(category.title, fontSize = density.bodyFontSize) },
                    alwaysShowLabel = true
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            when (selectedCategory) {
                SettingsCategory.APPEARANCE -> item {
                    CodexSectionHeader("Appearance & Theme")
                    ThemeSettingsSection(settings.theme, viewModel)
                }
                SettingsCategory.GRAPH -> item {
                    CodexSectionHeader("Graph View")
                    GraphSettingsSection(settings, viewModel)
                }
                SettingsCategory.DATA -> item {
                    CodexSectionHeader("Data & Codex")
                    DataSettingsSection(settings.data, viewModel)
                }
                SettingsCategory.GENERAL -> item {
                    CodexSectionHeader("General")
                    GeneralSettingsSection(settings.general, viewModel)
                }
                SettingsCategory.ABOUT -> item {
                    CodexSectionHeader("About")
                    AboutSection()
                }
            }
        }
    }
}

@Composable
private fun ThemeSettingsSection(theme: ThemeSettings, viewModel: SettingsViewModel) {
    CodexDropdown(
        label = "Theme Mode",
        selectedOption = theme.themeMode.name,
        options = ThemeMode.entries.map { it.name },
        onOptionSelected = { viewModel.onThemeModeChange(ThemeMode.valueOf(it)) },
        modifier = Modifier.padding(vertical = 8.dp)
    )

    CodexDropdown(
        label = "UI Density",
        selectedOption = theme.densityMode.name,
        options = DensityMode.entries.map { it.name },
        onOptionSelected = { viewModel.onDensityModeChange(DensityMode.valueOf(it)) },
        modifier = Modifier.padding(vertical = 8.dp)
    )

    SettingToggle(
        label = "Use Rounded Corners",
        checked = theme.useRoundedCorners,
        onCheckedChange = viewModel::onUseRoundedCornersChange
    )

    ColorSettingItem(
        label = "Accent Color",
        color = Color(theme.accentColor),
        onColorChange = { viewModel.onAccentColorChange(it) }
    )

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

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = viewModel::onResetTheme,
        modifier = Modifier.fillMaxWidth().height(LocalDensityTokens.current.buttonHeight)
    ) {
        Text("Reset Theme to Defaults")
    }
}

@Composable
private fun GraphSettingsSection(settings: SettingsData, viewModel: SettingsViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Physics", "Rendering", "Interaction")

    PrimaryTabRow(selectedTabIndex = selectedTab) {
        tabs.forEachIndexed { index, title ->
            CodexTab(
                selected = selectedTab == index,
                onClick = { selectedTab = index },
                text = title
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
private fun GraphPhysicsSubSection(physics: GraphPhysicsSettings, viewModel: SettingsViewModel) {
    // New: Default Layout Mode selection
    CodexDropdown(
        label = "Default Layout Mode",
        options = GraphLayoutMode.entries,
        selectedOption = physics.layoutMode,
        onOptionSelected = { viewModel.onDefaultLayoutModeChange(it) },
        displayTransform = { it.displayName },
        modifier = Modifier.padding(bottom = 16.dp)
    )

    InfoCard("Recommended Defaults: Gravity: 0.5, Repulsion: 2000, Spring: 0.1, Damping: 0.9, Barnes-Hut: 1.2, Tolerance: 1.0")
    SettingSlider("Gravity", physics.options.gravity, viewModel::onGravityChange, 0f..2f)
    SettingSlider("Repulsion", physics.options.repulsion, viewModel::onRepulsionChange, 0f..10000f)
    SettingSlider("Spring Stiffness", physics.options.spring, viewModel::onSpringChange, 0.01f..1f)
    SettingSlider("Damping", physics.options.damping, viewModel::onDampingChange, 0.5f..1f)
    SettingSlider("Barnes-Hut Theta", physics.options.barnesHutTheta, viewModel::onBarnesHutThetaChange, 0.1f..3f)
    SettingSlider("Tolerance (Speed)", physics.options.tolerance, viewModel::onToleranceChange, 0.1f..10f)

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = viewModel::onResetPhysics,
        modifier = Modifier.fillMaxWidth().height(LocalDensityTokens.current.buttonHeight)
    ) {
        Text("Reset Physics to Defaults")
    }
}

@Composable
private fun GraphRenderingSubSection(rendering: GraphRenderingSettings, viewModel: SettingsViewModel) {
    SettingToggle("Start Simulation on Load", rendering.startSimulationOnLoad, viewModel::onStartSimulationOnLoadChange)
    SettingToggle("Show Node Labels by Default", rendering.showNodeLabels, viewModel::onShowNodeLabelsChange)
    SettingToggle("Show Edge Labels by Default", rendering.showEdgeLabels, viewModel::onShowEdgeLabelsChange)
    SettingToggle("Show Center Crosshairs", rendering.showCrosshairs, viewModel::onShowCrosshairsChange)
}

@Composable
private fun GraphInteractionSubSection(interaction: GraphInteractionSettings, viewModel: SettingsViewModel) {
    SettingSlider("Scroll Zoom Sensitivity", interaction.zoomSensitivity, viewModel::onZoomSensitivityChange, 0.5f..2.0f)
    SettingSlider("Node Base Radius", interaction.nodeBaseRadius, viewModel::onNodeBaseRadiusChange, 5f..50f)
    SettingSlider("Node Radius Edge Factor", interaction.nodeRadiusEdgeFactor, viewModel::onNodeRadiusEdgeFactorChange, 0.5f..5.0f)
}

@Composable
private fun DataSettingsSection(data: DataSettings, viewModel: SettingsViewModel) {
    val density = LocalDensityTokens.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Default Codex Directory", style = MaterialTheme.typography.bodyLarge, fontSize = density.bodyFontSize)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = data.defaultCodexDirectory,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = density.bodyFontSize,
                modifier = Modifier.weight(1f).border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small).padding(8.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = viewModel::onChangeDefaultDirectory, modifier = Modifier.height(density.buttonHeight)) {
                Text("Change")
            }
        }
    }
    SettingToggle("Auto-load Last Codex on Startup", data.autoLoadLastCodex, viewModel::onAutoLoadLastCodexChange)
    SettingToggle("Auto-Refresh Codex Data", data.autoRefreshCodex, viewModel::onAutoRefreshCodexChange)
}

@Composable
private fun GeneralSettingsSection(general: GeneralSettings, viewModel: SettingsViewModel) {
    CodexDropdown("Startup Screen", listOf("Nexus", "Last Codex"), general.startupScreen, viewModel::onStartupScreenChange, modifier = Modifier.padding(vertical = 8.dp))
    CodexDropdown("Default Codex View", listOf("Graph", "List"), general.defaultCodexView, viewModel::onDefaultCodexViewChange, modifier = Modifier.padding(vertical = 8.dp))
    CodexDropdown("Default Markdown Flavor", listOf("Obsidian", "CommonMark", "Github Flavor"), general.defaultMarkdownFlavor, {}, modifier = Modifier.padding(vertical = 8.dp))
    SettingToggle("Confirm Node/Edge Deletion", general.confirmNodeEdgeDeletion, viewModel::onConfirmNodeEdgeDeletionChange)
    SettingToggle("Confirm Schema Deletion", general.confirmSchemaDeletion, viewModel::onConfirmSchemaDeletionChange)
}

@Composable
private fun AboutSection() {
    val density = LocalDensityTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Nexus Note Version 1.0.0", style = MaterialTheme.typography.bodyLarge, fontSize = density.bodyFontSize)
        Button(onClick = { }, modifier = Modifier.fillMaxWidth().height(density.buttonHeight)) { Text("Check for Updates") }
        Button(onClick = { }, modifier = Modifier.fillMaxWidth().height(density.buttonHeight)) { Text("View Licenses") }
        Button(onClick = { }, modifier = Modifier.fillMaxWidth().height(density.buttonHeight)) { Text("View README") }
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val density = LocalDensityTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = density.bodyFontSize)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingSlider(label: String, value: Float, onValueChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>) {
    val density = LocalDensityTokens.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontSize = density.bodyFontSize)
            Text(text = if (value > 100) value.roundToInt().toString() else String.format("%.2f", value), style = MaterialTheme.typography.bodyMedium, fontSize = density.bodyFontSize)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalStdlibApi::class)
@Composable
private fun ColorSettingItem(label: String, color: Color, onColorChange: (Color) -> Unit) {
    val hex = "#" + color.toArgb().toHexString(HexFormat.UpperCase).substring(2)
    val density = LocalDensityTokens.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.small).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, modifier = Modifier.width(100.dp), fontSize = density.bodyFontSize)
            Box(modifier = Modifier.size(density.buttonHeight).background(color).border(1.dp, MaterialTheme.colorScheme.onSurface))
            CodexTextField(value = hex, onValueChange = { onColorChange(hexToColor(it)) }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("R", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = density.bodyFontSize)
            Slider(value = color.red, onValueChange = { onColorChange(color.copy(red = it)) }, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red))
            Text("G", color = Color.Green, fontWeight = FontWeight.Bold, fontSize = density.bodyFontSize)
            Slider(value = color.green, onValueChange = { onColorChange(color.copy(green = it)) }, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = Color.Green, activeTrackColor = Color.Green))
            Text("B", color = Color.Blue, fontWeight = FontWeight.Bold, fontSize = density.bodyFontSize)
            Slider(value = color.blue, onValueChange = { onColorChange(color.copy(blue = it)) }, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue))
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    val density = LocalDensityTokens.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(density.contentPadding), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.padding(end = 8.dp).size(density.iconSize))
            Text(text, style = MaterialTheme.typography.bodySmall, fontSize = density.bodyFontSize)
        }
    }
}