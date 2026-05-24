package io.openftba

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.openftba.data.DesktopRideRepository
import io.openftba.ui.App

fun main() = application {
    val repo = remember { DesktopRideRepository() }
    val windowState = rememberWindowState(width = 1180.dp, height = 820.dp)

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "OpenFTBA",
    ) {
        // App triggers an initial rescan via LaunchedEffect when a folder is configured.
        App(repo)
    }
}
