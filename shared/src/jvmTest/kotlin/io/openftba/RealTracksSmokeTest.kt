package io.openftba

import io.openftba.analytics.AnalyzerConfig
import io.openftba.analytics.RideAnalyzer
import io.openftba.dem.SrtmElevationProvider
import io.openftba.parse.OpenTracksKmlParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Opt-in smoke test against real OpenTracks exports. It only runs when the env var
 * OPENFTBA_TRACKS points at a folder of .kmz/.kml files, so CI and other machines skip
 * it. Real tracks are never committed (see .gitignore).
 */
class RealTracksSmokeTest {

    @Test
    fun parsesRealTracksWhenFolderProvided() {
        val path = System.getenv("OPENFTBA_TRACKS") ?: run {
            println("[smoke] OPENFTBA_TRACKS not set — skipping real-track smoke test.")
            return
        }
        val folder = File(path)
        assertTrue(folder.isDirectory, "OPENFTBA_TRACKS is not a directory: $path")

        val files = folder.listFiles { f ->
            f.isFile && f.name.lowercase().let { it.endsWith(".kmz") || it.endsWith(".kml") }
        }?.sortedBy { it.name } ?: emptyList()
        assertTrue(files.isNotEmpty(), "no track files in $path")

        // Optional DEM folder for a GPS-vs-DEM elevation comparison.
        val demProvider = System.getenv("OPENFTBA_DEM")?.let { File(it) }
            ?.takeIf { it.isDirectory }?.let { SrtmElevationProvider(it) }
        val demConfig = AnalyzerConfig(useDem = true, elevationProvider = demProvider)

        var parsed = 0
        for (file in files) {
            val track = OpenTracksKmlParser.parseFile(file)
            val ride = RideAnalyzer.analyze(track)
            if (ride == null) {
                println("[smoke] ${file.name}: no usable points")
                continue
            }
            parsed++
            if (demProvider != null) {
                val dem = RideAnalyzer.analyze(track, demConfig)
                println("[dem]   ${file.name}: GPS gain ${ride.metrics.elevationGain.format(0)} m " +
                    "→ DEM gain ${dem?.metrics?.elevationGain?.format(0)} m " +
                    "(source ${dem?.metrics?.elevationSource})")
            }
            val m = ride.metrics
            val ch = ride.channels
            println(
                "[smoke] ${file.name}: ${(m.distanceMeters / 1000).format(2)} km, " +
                    "moving ${(m.movingTimeSeconds / 60).format(1)} min, " +
                    "avg ${(m.avgSpeed * 3.6).format(1)} km/h, " +
                    "max ${(m.maxSpeed * 3.6).format(1)} km/h, " +
                    "gain ${m.elevationGain.format(0)} m, " +
                    "longestNonStop ${(m.longestNonStopMeters / 1000).format(2)} km, " +
                    "biggestClimb ${m.biggestClimbMeters.format(0)} m, " +
                    "channels[hr=${ch.heartRate} cad=${ch.cadence} pwr=${ch.power} ele=${ch.elevation}]"
            )
            assertTrue(m.distanceMeters > 0, "${file.name} has zero distance")
        }
        assertTrue(parsed > 0, "no tracks parsed")
        println("[smoke] parsed $parsed/${files.size} tracks OK")
    }

    private fun Double.format(decimals: Int): String {
        val s = toString()
        val dot = s.indexOf('.')
        if (dot < 0) return s
        return if (decimals == 0) s.substring(0, dot)
        else s.substring(0, minOf(s.length, dot + 1 + decimals))
    }
}
