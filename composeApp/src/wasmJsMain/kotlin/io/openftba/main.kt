package io.openftba

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import io.openftba.data.WasmRideRepository
import io.openftba.ui.App

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val repo = WasmRideRepository()
    CanvasBasedWindow(title = "OpenFTBA") {
        App(repo)
    }
}
