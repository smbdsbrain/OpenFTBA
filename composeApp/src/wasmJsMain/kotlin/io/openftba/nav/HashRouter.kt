package io.openftba.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import io.openftba.ui.nav.NavState
import io.openftba.ui.nav.Tab
import kotlinx.browser.window
import org.w3c.dom.events.Event

/**
 * Hash routing for the web target: `#/` (overview), `#/rides`, `#/settings`, `#/ride/{id}`.
 *
 * The history stack always mirrors the app hierarchy `[#/, tab, ride]`, not the raw click
 * trail: going deeper pushes (with intermediate entries synthesized), lateral tab switches
 * replace, and going shallower pops via history.go() — so browser back walks
 * detail → rides → overview → previous site, and forward stays sane.
 */

private fun routeOf(nav: NavState): String = when {
    nav.openRideId != null -> "#/ride/${nav.openRideId}"
    nav.tab == Tab.RIDES -> "#/rides"
    nav.tab == Tab.SETTINGS -> "#/settings"
    else -> "#/"
}

private fun depth(route: String): Int = when {
    route.startsWith("#/ride/") -> 2
    route == "#/rides" || route == "#/settings" -> 1
    else -> 0
}

private fun applyRoute(nav: NavState, route: String) = when {
    route.startsWith("#/ride/") -> nav.applyRoute(Tab.RIDES, route.removePrefix("#/ride/"))
    route == "#/rides" -> nav.applyRoute(Tab.RIDES, null)
    route == "#/settings" -> nav.applyRoute(Tab.SETTINGS, null)
    else -> nav.applyRoute(Tab.OVERVIEW, null)
}

private fun currentHash(): String = window.location.hash.ifBlank { "#/" }

@Composable
fun HashRouter(nav: NavState) {
    // Route we asked history.go() to land on; popstate then reconciles the entry's hash
    // (the popped entry may hold a stale route, e.g. rides vs settings at depth 1).
    var pendingRoute by remember { mutableStateOf<String?>(null) }

    // Initial load / deep link: synthesize the hierarchy beneath so Back walks it.
    LaunchedEffect(Unit) {
        val initial = currentHash()
        if (depth(initial) > 0) {
            window.history.replaceState(null, "", "#/")
            if (depth(initial) == 2) window.history.pushState(null, "", "#/rides")
            window.history.pushState(null, "", initial)
        }
        applyRoute(nav, initial)
    }

    // Browser → app (back/forward buttons).
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = {
            val landed = currentHash()
            val pending = pendingRoute
            if (pending != null) {
                pendingRoute = null
                if (landed != pending) window.history.replaceState(null, "", pending)
            } else {
                applyRoute(nav, landed)
            }
        }
        window.addEventListener("popstate", listener)
        onDispose { window.removeEventListener("popstate", listener) }
    }

    // App → browser. Bails when the hash already matches (covers popstate echoes).
    LaunchedEffect(nav) {
        snapshotFlow { routeOf(nav) }.collect { route ->
            val current = currentHash()
            if (route == current) return@collect
            val dNew = depth(route)
            val dCur = depth(current)
            when {
                dNew > dCur -> {
                    if (dNew == 2 && dCur == 0) window.history.pushState(null, "", "#/rides")
                    window.history.pushState(null, "", route)
                }
                dNew == dCur -> window.history.replaceState(null, "", route)
                else -> {
                    pendingRoute = route
                    window.history.go(dNew - dCur)
                }
            }
        }
    }
}
