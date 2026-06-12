package io.openftba

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import io.openftba.data.WasmRideRepository
import io.openftba.nav.HashRouter
import io.openftba.ui.App
import io.openftba.ui.nav.rememberNavState

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val repo = WasmRideRepository()
    CanvasBasedWindow(title = "OpenFTBA") {
        val nav = rememberNavState()
        HashRouter(nav)
        App(repo, nav = nav)
    }
}
