package io.openftba.ui.nav

import androidx.compose.runtime.Composable

/**
 * Intercepts the platform back gesture while [enabled]. Android wires the system back
 * button/gesture; desktop handles Escape at the window level; the web routes browser
 * history through [io.openftba.ui.nav.NavState] directly — both of those are no-ops here.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
