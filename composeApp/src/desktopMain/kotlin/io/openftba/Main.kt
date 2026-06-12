package io.openftba

import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.openftba.data.DesktopRideRepository
import io.openftba.ui.App
import io.openftba.ui.nav.rememberNavState

fun main() = application {
    val repo = remember { DesktopRideRepository() }
    val windowState = rememberWindowState(width = 1180.dp, height = 820.dp)
    val nav = rememberNavState()

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "OpenFTBA",
        icon = painterResource("icon.png"),
        // Escape walks back up the hierarchy; unconsumed on the overview (goBack() == false).
        onPreviewKeyEvent = { e ->
            e.type == KeyEventType.KeyDown && e.key == Key.Escape && nav.goBack()
        },
    ) {
        // App triggers an initial rescan via LaunchedEffect when a folder is configured.
        App(repo, nav = nav)
    }
}
