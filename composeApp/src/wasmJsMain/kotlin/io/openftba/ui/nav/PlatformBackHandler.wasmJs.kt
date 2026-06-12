package io.openftba.ui.nav

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Browser back/forward drive NavState through the hash router (nav/HashRouter.kt).
}
