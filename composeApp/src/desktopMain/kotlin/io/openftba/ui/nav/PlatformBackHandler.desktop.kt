package io.openftba.ui.nav

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Escape is handled at the Window level in Main.kt.
}
