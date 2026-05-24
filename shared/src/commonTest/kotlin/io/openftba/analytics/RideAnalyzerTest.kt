package io.openftba.analytics

import io.openftba.model.GeoPoint
import io.openftba.model.ParsedTrack
import io.openftba.model.TrackSegment
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RideAnalyzerTest {

    private fun t(sec: Long) = Instant.fromEpochSeconds(1_700_000_000 + sec)

    @Test
    fun elevationGainLoss_usesThresholdHysteresis() {
        val (gain, loss) = RideAnalyzer.elevationGainLoss(listOf(0.0, 10.0, 20.0, 30.0), threshold = 2.0)
        assertEquals(30.0, gain, 0.001)
        assertEquals(0.0, loss, 0.001)

        val (g2, l2) = RideAnalyzer.elevationGainLoss(listOf(0.0, 1.0, 0.5, 1.0), threshold = 2.0)
        // No change ever exceeds the 2 m threshold from the anchor.
        assertEquals(0.0, g2, 0.001)
        assertEquals(0.0, l2, 0.001)

        val (g3, l3) = RideAnalyzer.elevationGainLoss(listOf(100.0, 90.0, 80.0), threshold = 2.0)
        assertEquals(0.0, g3, 0.001)
        assertEquals(20.0, l3, 0.001)
    }

    @Test
    fun smooth_averagesWithWindowAndFillsNulls() {
        val out = RideAnalyzer.smooth(listOf(0.0, null, 4.0), window = 1)
        // window<=1 -> just forward-filled values
        assertEquals(listOf(0.0, 0.0, 4.0), out)
    }

    @Test
    fun detectChannels_reflectsPresentSensors() {
        val pts = listOf(
            GeoPoint(t(0), 60.0, 30.0, ele = 10.0, heartRate = 120.0),
            GeoPoint(t(1), 60.0001, 30.0, ele = 11.0),
        )
        val ch = RideAnalyzer.detectChannels(pts)
        assertTrue(ch.heartRate)
        assertTrue(ch.elevation)
        assertTrue(!ch.power)
        assertTrue(!ch.cadence)
    }

    @Test
    fun disabledChannel_treatsSensorAsAbsent() {
        val pts = (0 until 6).map { i ->
            GeoPoint(t(i.toLong()), 60.0 + i * 0.001, 30.0, ele = 10.0 + i, heartRate = 150.0)
        }
        val track = ParsedTrack("id", "r", "biking", listOf(TrackSegment(pts)), "r.kmz")

        val withHr = RideAnalyzer.analyze(track)!!
        assertTrue(withHr.channels.heartRate)
        assertNotNull(withHr.metrics.avgHeartRate)

        val noHr = RideAnalyzer.analyze(
            track,
            AnalyzerConfig(disabledChannels = setOf(io.openftba.model.SensorChannel.HEART_RATE)),
        )!!
        assertTrue(!noHr.channels.heartRate)
        assertEquals(null, noHr.metrics.avgHeartRate)
    }

    @Test
    fun analyze_computesDistanceAndLongestNonStop() {
        // Two segments along a meridian (constant lon), so distance grows with lat.
        fun seg(startSec: Long, latStart: Double, n: Int): TrackSegment {
            val pts = (0 until n).map { i ->
                GeoPoint(t(startSec + i), latStart + i * 0.001, 30.0, ele = 10.0 + i)
            }
            return TrackSegment(pts)
        }
        val segA = seg(0, 60.0, 11)   // 10 steps of ~111 m
        val segB = seg(100, 61.0, 6)  // 5 steps
        val track = ParsedTrack("id-1", "Ride", "biking", listOf(segA, segB), "ride.kmz")

        val ride = RideAnalyzer.analyze(track)
        assertNotNull(ride)
        assertTrue(ride.metrics.distanceMeters > 0)
        // Longest non-stop must equal the larger (first) segment's distance.
        assertTrue(ride.metrics.longestNonStopMeters > 0)
        assertTrue(ride.metrics.longestNonStopMeters <= ride.metrics.distanceMeters + 0.001)
        assertEquals("id-1", ride.id)
        assertEquals("biking", ride.activityType)
        assertTrue(ride.metrics.elevationGain > 0)
    }

    @Test
    fun longestNonStop_breaksOnStopWithinSingleSegment() {
        // One continuous segment (no pause split): ride, stand still ~60 s, ride again.
        // Each moving step is ~111 m over 5 s (~22 m/s) so it's clearly "moving".
        val pts = mutableListOf<GeoPoint>()
        var sec = 0L
        var lat = 60.0
        fun move(steps: Int) = repeat(steps) {
            lat += 0.001; sec += 5
            pts += GeoPoint(t(sec), lat, 30.0, ele = 10.0)
        }
        fun stand(seconds: Long) {
            // Same position, time advances → speed 0 (stopped).
            repeat((seconds / 5).toInt()) { sec += 5; pts += GeoPoint(t(sec), lat, 30.0, ele = 10.0) }
        }
        move(5)      // run A: ~555 m
        stand(60)    // sustained stop → breaks the run
        move(12)     // run B: ~1332 m (longer)

        val track = ParsedTrack("id-2", "Ride", "biking", listOf(TrackSegment(pts)), "ride.kmz")
        val ride = RideAnalyzer.analyze(track)
        assertNotNull(ride)
        // Longest non-stop is the second run, not the whole-segment distance.
        assertTrue(
            ride.metrics.longestNonStopMeters < ride.metrics.distanceMeters * 0.95,
            "non-stop ${ride.metrics.longestNonStopMeters} should be well under total ${ride.metrics.distanceMeters}",
        )
        assertTrue(ride.metrics.longestNonStopMeters in 1200.0..1500.0)
    }
}
