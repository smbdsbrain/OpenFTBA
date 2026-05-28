package io.openftba.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.openftba.data.RideRepository
import io.openftba.ui.i18n.Language
import io.openftba.ui.i18n.LocalStrings
import io.openftba.ui.i18n.stringsFor
import io.openftba.ui.screens.OverviewScreen
import io.openftba.ui.screens.RideDetailScreen
import io.openftba.ui.screens.RideListScreen
import io.openftba.ui.screens.SettingsScreen
import io.openftba.ui.theme.OpenFtbaTheme
import io.openftba.ui.theme.Palette

private enum class Tab { OVERVIEW, RIDES, SETTINGS }

@Composable
fun App(repo: RideRepository, onPickFolder: (() -> Unit)? = null, onPickDemFolder: (() -> Unit)? = null) {
    val state by repo.state.collectAsState()
    val strings = stringsFor(Language.fromCode(state.settings.languageCode))

    var tab by remember { mutableStateOf(Tab.OVERVIEW) }
    var openRideId by remember { mutableStateOf<String?>(null) }

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
                            selected = tab == Tab.OVERVIEW && openRideId == null,
                            onClick = { tab = Tab.OVERVIEW; openRideId = null },
                            icon = { Icon(Icons.Filled.Insights, strings.navOverview) },
                            label = { Text(strings.navOverview) },
                        )
                        NavigationRailItem(
                            selected = tab == Tab.RIDES,
                            onClick = { tab = Tab.RIDES; openRideId = null },
                            icon = { Icon(Icons.Filled.DirectionsBike, strings.navRides) },
                            label = { Text(strings.navRides) },
                        )
                        NavigationRailItem(
                            selected = tab == Tab.SETTINGS,
                            onClick = { tab = Tab.SETTINGS; openRideId = null },
                            icon = { Icon(Icons.Filled.Settings, strings.navSettings) },
                            label = { Text(strings.navSettings) },
                        )
                    }

                    Box(Modifier.fillMaxSize().padding(start = 4.dp)) {
                        val detailId = openRideId
                        when {
                            detailId != null -> {
                                val detail = repo.detail(detailId)
                                if (detail != null) {
                                    RideDetailScreen(
                                        detail = detail,
                                        units = state.settings.units,
                                        onBack = { openRideId = null },
                                    )
                                } else {
                                    openRideId = null
                                }
                            }
                            tab == Tab.OVERVIEW -> OverviewScreen(state)
                            tab == Tab.RIDES -> RideListScreen(state, onOpenRide = { openRideId = it })
                            tab == Tab.SETTINGS -> SettingsScreen(
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
