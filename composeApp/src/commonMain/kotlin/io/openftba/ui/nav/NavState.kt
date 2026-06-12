package io.openftba.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

enum class Tab { OVERVIEW, RIDES, SETTINGS }

/**
 * Navigation state shared by every platform: a tab plus an optional ride-detail overlay.
 * Back walks the hierarchy — detail → rides list → overview; on overview, [goBack] returns
 * false so the platform default applies (Android exits, browser leaves the page).
 */
@Stable
class NavState {
    var tab by mutableStateOf(Tab.OVERVIEW)
        private set
    var openRideId by mutableStateOf<String?>(null)
        private set

    val canGoBack: Boolean get() = openRideId != null || tab != Tab.OVERVIEW

    fun navigate(t: Tab) {
        tab = t
        openRideId = null
    }

    fun openRide(id: String) {
        tab = Tab.RIDES
        openRideId = id
    }

    /** One step up the hierarchy; false when already at the root (overview). */
    fun goBack(): Boolean = when {
        openRideId != null -> {
            openRideId = null
            true
        }
        tab != Tab.OVERVIEW -> {
            tab = Tab.OVERVIEW
            true
        }
        else -> false
    }

    /** Browser-driven change (hash router): set the destination without stack side effects. */
    fun applyRoute(tab: Tab, rideId: String?) {
        this.tab = tab
        this.openRideId = rideId
    }
}

@Composable
fun rememberNavState(): NavState = remember { NavState() }
