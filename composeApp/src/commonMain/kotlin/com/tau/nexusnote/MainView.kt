package com.tau.nexusnote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tau.nexusnote.datamodels.CodexItem
import com.tau.nexusnote.codex.CodexView
import com.tau.nexusnote.nexus.NexusView
import com.tau.nexusnote.settings.SettingsView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(mainViewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val selectedScreen by mainViewModel.selectedScreen.collectAsState()
    val codexViewModel by mainViewModel.codexViewModel.collectAsState()
    // Observe the currently opened item for the label
    val openedCodexItem by mainViewModel.openedCodexItem.collectAsState()

    // --- Error Handling Observers ---
    val mainError by mainViewModel.errorFlow.collectAsState()
    val codexError by codexViewModel?.errorFlow?.collectAsState() ?: mutableStateOf(null)

    LaunchedEffect(mainError) {
        mainError?.let {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = it,
                    withDismissAction = true
                )
            }
            mainViewModel.clearError()
        }
    }

    LaunchedEffect(codexError) {
        codexError?.let {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = it,
                    withDismissAction = true
                )
            }
            codexViewModel?.clearError()
        }
    }
    // --- End Error Handling ---

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                title = {
                    val title = when (selectedScreen) {
                        Screen.NEXUS -> "Nexus"
                        Screen.CODEX -> "Codex: ${openedCodexItem?.name}" ?: "Codex"
                        Screen.SETTINGS -> "Settings"
                    }
                    Text(title)
                },
                actions = { }
            )
        }
    ) { contentPadding ->
        Row(modifier = Modifier.padding(contentPadding).fillMaxSize()) {

            NavigationRail {
                Spacer(Modifier.height(12.dp))

                // Home Item
                NavigationRailItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Nexus") },
                    label = { Text("Nexus") },
                    selected = selectedScreen == Screen.NEXUS,
                    onClick = {
                        mainViewModel.closeCodex()
                    }
                )

                // Codex Item
                val isCodexLoaded = codexViewModel != null
                // Use the opened codex name, or fallback to "Codex"
                val codexLabel = "Codex"

                NavigationRailItem(
                    icon = { Icon(Icons.Default.Storage, contentDescription = "Codex") },
                    label = { Text(codexLabel) },
                    selected = selectedScreen == Screen.CODEX,
                    enabled = isCodexLoaded, // Disable if no codex loaded
                    onClick = {
                        if (isCodexLoaded) {
                            mainViewModel.navigateTo(Screen.CODEX)
                        }
                    }
                )

                // Settings Item
                NavigationRailItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = selectedScreen == Screen.SETTINGS,
                    onClick = {
                        mainViewModel.navigateTo(Screen.SETTINGS)
                    }
                )
            }

            // Content Area
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedScreen) {
                    Screen.NEXUS -> NexusView(
                        viewModel = mainViewModel
                    )
                    Screen.CODEX -> {
                        val vm = codexViewModel
                        if (vm != null) {
                            CodexView(viewModel = vm)
                        } else {
                            NexusView(viewModel = mainViewModel)
                        }
                    }
                    Screen.SETTINGS -> {
                        SettingsView(
                            viewModel = mainViewModel.settingsViewModel
                        )
                    }
                }
            }
        }
    }
}