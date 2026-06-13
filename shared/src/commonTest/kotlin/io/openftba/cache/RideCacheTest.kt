package io.openftba.cache

import io.openftba.analytics.AnalyzerConfig
import io.openftba.analytics.AthleteProfile
import io.openftba.api.ChannelsDto
import io.openftba.api.RideDetailDto
import io.openftba.api.RideSeriesDto
import io.openftba.api.RideSummaryDto
import io.openftba.api.SplitDto
import io.openftba.model.SensorChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class RideCacheTest {

    @Test
    fun fingerprintIsStableForEqualConfigs() {
        val a = AnalyzerConfig(profile = AthleteProfile(ftpWatts = 250, weightKg = 70.0))
        val b = AnalyzerConfig(profile = AthleteProfile(ftpWatts = 250, weightKg = 70.0))
        assertEquals(configFingerprint(a), configFingerprint(b))
    }

    @Test
    fun fingerprintChangesWhenProfileChanges() {
        val base = AnalyzerConfig(profile = AthleteProfile(ftpWatts = 250))
        val changed = AnalyzerConfig(profile = AthleteProfile(ftpWatts = 260))
        assertNotEquals(configFingerprint(base), configFingerprint(changed))
    }

    @Test
    fun fingerprintChangesForElevationAndChannelSettings() {
        val base = AnalyzerConfig()
        assertNotEquals(configFingerprint(base), configFingerprint(base.copy(ignoreElevation = true)))
        assertNotEquals(configFingerprint(base), configFingerprint(base.copy(useDem = true)))
        assertNotEquals(
            configFingerprint(base),
            configFingerprint(base.copy(disabledChannels = setOf(SensorChannel.POWER))),
        )
    }

    @Test
    fun disabledChannelsFingerprintIsOrderIndependent() {
        val a = AnalyzerConfig(disabledChannels = setOf(SensorChannel.POWER, SensorChannel.HEART_RATE))
        val b = AnalyzerConfig(disabledChannels = setOf(SensorChannel.HEART_RATE, SensorChannel.POWER))
        assertEquals(configFingerprint(a), configFingerprint(b))
    }

    @Test
    fun cacheEntryRoundTripsThroughCodec() {
        val detail = sampleDetail()
        val entry = RideCacheEntry("ride.kmz|123|456", "fp", RIDE_CACHE_VERSION, detail)
        val decoded = RideCacheCodec.decode(RideCacheCodec.encode(entry))
        assertEquals(entry, decoded)
        assertEquals(detail, decoded?.detail)
    }

    @Test
    fun codecRejectsGarbage() {
        assertNull(RideCacheCodec.decode("{ not json"))
    }

    @Test
    fun cacheFileNameIsStablePerSource() {
        assertEquals(rideCacheFileName("2026-01-01_ride.kmz"), rideCacheFileName("2026-01-01_ride.kmz"))
        assertNotEquals(rideCacheFileName("a.kmz"), rideCacheFileName("b.kmz"))
    }

    private fun sampleDetail() = RideDetailDto(
        summary = RideSummaryDto(
            id = "id1", name = "Ride", startEpochMs = 1_700_000_000_000, endEpochMs = 1_700_000_360_000,
            activityType = "biking", distanceMeters = 12000.0, movingSeconds = 1800.0, elapsedSeconds = 1860.0,
            avgSpeed = 6.5, maxSpeed = 11.0, elevationGain = 120.0, elevationLoss = 118.0,
            longestNonStopMeters = 8000.0, biggestClimbMeters = 60.0,
            channels = ChannelsDto(speed = true, cadence = false, heartRate = true, power = true, temperature = false, elevation = true),
            effortScore = 55.0, intensityFactor = 0.82, normalizedPower = 210.0,
        ),
        series = RideSeriesDto(
            distanceKm = listOf(0.0, 1.0, 2.0), elevation = listOf(100.0, 110.0, 120.0),
            lat = listOf(45.0, 45.01, 45.02), lon = listOf(10.0, 10.0, 10.0),
        ),
        splits = listOf(SplitDto(0, 1000.0, 150.0, 6.6, 10.0, 140.0)),
    )
}
