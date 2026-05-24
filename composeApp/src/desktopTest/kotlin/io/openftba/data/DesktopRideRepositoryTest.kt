package io.openftba.data

import io.openftba.settings.AppSettings
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Headless end-to-end check of the desktop data pipeline: settings → folder scan →
 * parsed rides → ride detail. Opt-in via OPENFTBA_TRACKS (real tracks are not committed).
 */
class DesktopRideRepositoryTest {

    @Test
    fun scansFolderAndExposesRidesAndDetail() = runBlocking {
        val path = System.getenv("OPENFTBA_TRACKS") ?: run {
            println("[pipeline] OPENFTBA_TRACKS not set — skipping.")
            return@runBlocking
        }
        // Use a throwaway config file so the user's real settings are untouched.
        val tmpConfig = File.createTempFile("openftba-test", ".json").apply { deleteOnExit() }
        val repo = DesktopRideRepository(configFile = tmpConfig)
        repo.updateSettings(AppSettings(watchFolder = path))

        repo.rescan()

        val state = repo.state.value
        assertTrue(state.rides.isNotEmpty(), "no rides parsed from $path")
        println("[pipeline] rides parsed: ${state.rides.size}")

        val first = state.rides.first()
        val detail = repo.detail(first.id)
        assertTrue(detail != null, "no detail for ${first.id}")
        assertTrue(detail!!.track.allPoints.size > 1, "detail has no track points")
        println("[pipeline] first ride ${first.name}: ${detail.track.allPoints.size} points, " +
            "channels hr=${first.channels.heartRate} ele=${first.channels.elevation}")
    }
}
