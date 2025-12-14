package com.tau.nexusnote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tau.nexusnote.ui.theme.NexusNoteTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    // Create the MainViewModel which controls navigation and settings
    val mainViewModel = _root_ide_package_.com.tau.nexusnote.rememberMainViewModel()

    // Observe settings to pass into the theme
    val settings by mainViewModel.appSettings.collectAsState()

    // Apply theme
    _root_ide_package_.com.tau.nexusnote.ui.theme.NexusNoteTheme(settings = settings.theme) {
        _root_ide_package_.com.tau.nexusnote.MainView(mainViewModel)

        // cleanup hook
        DisposableEffect(Unit) {
            onDispose {
                mainViewModel.onDispose()
            }
        }
    }
}