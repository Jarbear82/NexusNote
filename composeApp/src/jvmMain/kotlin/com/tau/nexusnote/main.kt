package com.tau.nexusnote

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.tau.nexusnote.App

fun main() = application {
    // System.setProperty("compose.swing.render.on.graphics", "true")

    System.setProperty("sun.awt.enableExtraMouseButtons", "false")

    Window(
        onCloseRequest = ::exitApplication,
        title = "Nexus Note",
    ) {
        App()
    }
}