package io.openftba.api

import io.openftba.model.GeoPoint
import io.openftba.model.ParsedTrack
import io.openftba.model.TrackSegment
import kotlinx.datetime.Instant
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** All coordinates are synthetic — never use real GPS data in tests. */
class BuildRideSeriesTest {

    private fun t(sec: Long) = Instant.fromEpochSeconds(1_700_000_000 + sec)

    // speed encodes the source index (i m/s) and lat encodes it too (45 + i·1e-5), so any
    // downsample misalignment between channels is detectable on the output.
    private fun syntheticTrack(n: Int) = ParsedTrack(
        trackId = "id", name = "r", activityType = "biking",
        segments = listOf(
            TrackSegment(
                (0 until n).map { i ->
                    GeoPoint(t(i.toLong()), lat = 45.0 + i * 1e-5, lon = 10.0, ele = 100.0 + i, speed = i.toDouble())
                },
            ),
        ),
        sourceFileName = "r.kmz",
    )

    @Test
    fun latLonAlignWithOtherChannelsAfterDownsampling() {
        val series = buildRideSeries(syntheticTrack(2000))
        assertEquals(800, series.speedKmh.size)
        assertEquals(800, series.lat.size)
        assertEquals(800, series.lon.size)
        assertEquals(series.speedKmh.size, series.elevation.size)
        for (j in series.lat.indices) {
            val srcIdx = (series.speedKmh[j] / 3.6).roundToInt()
            assertEquals(45.0 + srcIdx * 1e-5, series.lat[j], 1e-12)
            assertEquals(100.0 + srcIdx, series.elevation[j], 1e-9)
        }
        assertTrue(series.lon.all { it == 10.0 })
    }

    @Test
    fun shortTrackKeepsEveryPointInEveryChannel() {
        val series = buildRideSeries(syntheticTrack(50))
        assertEquals(50, series.lat.size)
        assertEquals(50, series.lon.size)
        assertEquals(50, series.speedKmh.size)
        assertEquals(45.0, series.lat[0], 1e-12)
        assertEquals(45.0 + 49 * 1e-5, series.lat[49], 1e-12)
    }
}
