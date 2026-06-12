package io.openftba.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

    OpenFtbaTheme {
        CompositionLocalProvider(LocalStrings provides strings) {
            Surface(Modifier.fillMaxSize(), color = Palette.Base) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(containerColor = Palette.Surface) {
                        NavigationRailItem(
                            selected = navState.tab == Tab.OVERVIEW && navState.openRideId == null,
                            onClick = { navState.navigate(Tab.OVERVIEW) },
                            icon = { Icon(Icons.Filled.Insights, strings.navOverview) },
                            label = { Text(strings.navOverview) },
                        )
                        NavigationRailItem(
                            selected = navState.tab == Tab.RIDES,
                            onClick = { navState.navigate(Tab.RIDES) },
                            icon = { Icon(Icons.Filled.DirectionsBike, strings.navRides) },
                            label = { Text(strings.navRides) },
                        )
                        NavigationRailItem(
                            selected = navState.tab == Tab.SETTINGS,
                            onClick = { navState.navigate(Tab.SETTINGS) },
                            icon = { Icon(Icons.Filled.Settings, strings.navSettings) },
                            label = { Text(strings.navSettings) },
                        )
                    }

                    Box(Modifier.fillMaxSize().padding(start = 4.dp)) {
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
        }
    }
}
