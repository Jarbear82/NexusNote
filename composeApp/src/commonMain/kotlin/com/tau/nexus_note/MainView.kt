package com.tau.nexus_note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import com.tau.nexus_note.ui.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexus_note.codex.CodexView
import com.tau.nexus_note.nexus.NexusView
import com.tau.nexus_note.settings.SettingsView
import com.tau.nexus_note.ui.theme.LocalDensityTokens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(mainViewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensityTokens.current

    val selectedScreen by mainViewModel.selectedScreen.collectAsState()
    val codexViewModel by mainViewModel.codexViewModel.collectAsState()
    val openedCodexItem by mainViewModel.openedCodexItem.collectAsState()

    // Error Handling
    val mainError by mainViewModel.errorFlow.collectAsState()
    val codexError by codexViewModel?.errorFlow?.collectAsState() ?: mutableStateOf(null)

    LaunchedEffect(mainError) {
        mainError?.let {
            scope.launch { snackbarHostState.showSnackbar(it, withDismissAction = true) }
            mainViewModel.clearError()
        }
    }

    LaunchedEffect(codexError) {
        codexError?.let {
            scope.launch { snackbarHostState.showSnackbar(it, withDismissAction = true) }
            codexViewModel?.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                modifier = Modifier.height(density.listHeight), // Adapt height
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                title = {
                    val title = when (selectedScreen) {
                        Screen.NEXUS -> "Nexus"
                        Screen.CODEX -> "Codex"
                        Screen.SETTINGS -> "Settings"
                    }
                    Text(title, fontSize = density.titleFontSize)
                },
                actions = { }
            )
        }
    ) { contentPadding ->
        Row(modifier = Modifier.padding(contentPadding).fillMaxSize()) {

            NavigationRail(
                modifier = Modifier.width(density.navRailWidth)
            ) {
                Spacer(Modifier.height(12.dp))

                // Home Item
                NavigationRailItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Nexus", modifier = Modifier.height(density.iconSize)) },
                    label = { Text("Nexus", fontSize = density.bodyFontSize) },
                    selected = selectedScreen == Screen.NEXUS,
                    onClick = { mainViewModel.closeCodex() }
                )

                // Codex Item
                val isCodexLoaded = codexViewModel != null
                val codexLabel = openedCodexItem?.name ?: "Codex"

                NavigationRailItem(
                    icon = { Icon(Icons.Default.Storage, contentDescription = "Codex", modifier = Modifier.height(density.iconSize)) },
                    label = { Text(codexLabel, fontSize = density.bodyFontSize) },
                    selected = selectedScreen == Screen.CODEX,
                    enabled = isCodexLoaded,
                    onClick = {
                        if (isCodexLoaded) {
                            mainViewModel.navigateTo(Screen.CODEX)
                        }
                    }
                )

                // Settings Item
                NavigationRailItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.height(density.iconSize)) },
                    label = { Text("Settings", fontSize = density.bodyFontSize) },
                    selected = selectedScreen == Screen.SETTINGS,
                    onClick = { mainViewModel.navigateTo(Screen.SETTINGS) }
                )
            }

            // Content Area
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedScreen) {
                    Screen.NEXUS -> NexusView(viewModel = mainViewModel)
                    Screen.CODEX -> {
                        val vm = codexViewModel
                        if (vm != null) {
                            CodexView(viewModel = vm)
                        } else {
                            NexusView(viewModel = mainViewModel)
                        }
                    }
                    Screen.SETTINGS -> SettingsView(viewModel = mainViewModel.settingsViewModel)
                }
            }
        }
    }
}