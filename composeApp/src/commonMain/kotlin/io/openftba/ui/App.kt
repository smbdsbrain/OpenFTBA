package io.openftba.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.openftba.data.RideRepository
import io.openftba.ui.i18n.Language
import io.openftba.ui.i18n.LocalStrings
import io.openftba.ui.i18n.stringsFor
import io.openftba.ui.nav.NavState
import io.openftba.ui.nav.PlatformBackHandler
import io.openftba.ui.nav.Tab
import io.openftba.ui.nav.rememberNavState
import io.openftba.ui.screens.EmptyState
import io.openftba.ui.screens.OverviewScreen
import io.openftba.ui.screens.RideDetailScreen
import io.openftba.ui.screens.RideListScreen
import io.openftba.ui.screens.SettingsScreen
import io.openftba.ui.theme.OpenFtbaTheme
import io.openftba.ui.theme.Palette

/** Coarse window-width class: phones get a bottom bar and denser grids. */
enum class WidthClass { Compact, Expanded }

val LocalWidthClass = staticCompositionLocalOf { WidthClass.Expanded }

@Composable
fun App(
    repo: RideRepository,
    onPickFolder: (() -> Unit)? = null,
    onPickDemFolder: (() -> Unit)? = null,
    nav: NavState? = null,
) {
    val state by repo.state.collectAsState()
    val strings = stringsFor(Language.fromCode(state.settings.languageCode))

    val navState = nav ?: rememberNavState()
    // Hoisted so scroll positions survive switching tabs / opening a ride detail.
    val ridesListState = rememberLazyListState()

    PlatformBackHandler(enabled = navState.canGoBack) { navState.goBack() }

    // Initial scan when a folder is configured.
    LaunchedEffect(
        state.settings.watchFolder,
        state.settings.ignoreElevation,
        state.settings.useDemElevation,
        state.settings.demFolder,
        state.settings.disabledChannels,
    ) {
        if (state.settings.watchFolder != null) repo.rescan()
    }

    val tabs = listOf(
        NavTabSpec(Tab.OVERVIEW, Icons.Filled.Insights) { it.navOverview },
        NavTabSpec(Tab.RIDES, Icons.Filled.DirectionsBike) { it.navRides },
        NavTabSpec(Tab.SETTINGS, Icons.Filled.Settings) { it.navSettings },
    )

    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize()) {
            AnalyzingBanner(state.analyzing, state.analyzingDone, state.analyzingTotal, strings)
            Box(Modifier.fillMaxSize().weight(1f)) {
                val detailId = navState.openRideId
                when {
                    detailId != null -> {
                        val detail = repo.detail(detailId)
                        when {
                            detail != null -> RideDetailScreen(
                                detail = detail,
                                units = state.settings.units,
                                onBack = { navState.goBack() },
                            )
                            // On the web, rides and details arrive async (deep link) —
                            // keep the route alive instead of silently dropping it.
                            state.loading || state.rides.any { it.id == detailId } ->
                                EmptyState(title = strings.loadingRide, hint = "")
                            else -> EmptyState(title = strings.rideNotFound, hint = "")
                        }
                    }
                    navState.tab == Tab.OVERVIEW -> OverviewScreen(state)
                    navState.tab == Tab.RIDES -> RideListScreen(
                        state,
                        listState = ridesListState,
                        onOpenRide = { navState.openRide(it) },
                    )
                    navState.tab == Tab.SETTINGS -> SettingsScreen(
                        settings = state.settings,
                        onChange = repo::updateSettings,
                        onRescan = { },
                        state = state,
                        onDownloadDem = { repo.downloadDemTiles() },
                        onPickFolder = onPickFolder,
                        onPickDemFolder = onPickDemFolder,
                    )
                }
            }
        }
    }

    OpenFtbaTheme {
        CompositionLocalProvider(LocalStrings provides strings) {
            Surface(Modifier.fillMaxSize(), color = Palette.Base) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val widthClass = if (maxWidth < 600.dp) WidthClass.Compact else WidthClass.Expanded
                    CompositionLocalProvider(LocalWidthClass provides widthClass) {
                        if (widthClass == WidthClass.Compact) {
                            Column(Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxWidth().weight(1f)) { content() }
                                NavigationBar(containerColor = Palette.Surface) {
                                    tabs.forEach { spec ->
                                        NavigationBarItem(
                                            selected = navState.tab == spec.tab,
                                            onClick = { navState.navigate(spec.tab) },
                                            icon = { Icon(spec.icon, spec.label(strings)) },
                                            label = { Text(spec.label(strings)) },
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(Modifier.fillMaxSize()) {
                                NavigationRail(containerColor = Palette.Surface) {
                                    tabs.forEach { spec ->
                                        NavigationRailItem(
                                            selected = navState.tab == spec.tab,
                                            onClick = { navState.navigate(spec.tab) },
                                            icon = { Icon(spec.icon, spec.label(strings)) },
                                            label = { Text(spec.label(strings)) },
                                        )
                                    }
                                }
                                Box(Modifier.fillMaxSize().padding(start = 4.dp)) { content() }
                            }
                        }
                    }
                }
            }
        }
    }
}

private class NavTabSpec(
    val tab: Tab,
    val icon: ImageVector,
    val label: (io.openftba.ui.i18n.Strings) -> String,
)

/** Thin top strip shown while new (uncached) rides are analyzed in the background. */
@Composable
private fun AnalyzingBanner(analyzing: Boolean, done: Int, total: Int, strings: io.openftba.ui.i18n.Strings) {
    if (!analyzing) return
    Surface(color = Palette.Accent, modifier = Modifier.fillMaxWidth()) {
        Text(
            strings.analyzingRides(done, total),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = Palette.Base,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
