package io.openftba.model

import kotlinx.datetime.Instant

/** Which channels actually carry data for a ride; drives which charts/cards show. */
data class AvailableChannels(
    val speed: Boolean = false,
    val cadence: Boolean = false,
    val heartRate: Boolean = false,
    val power: Boolean = false,
    val temperature: Boolean = false,
    val elevation: Boolean = false,
) {
    fun has(channel: SensorChannel): Boolean = when (channel) {
        SensorChannel.SPEED -> speed
        SensorChannel.CADENCE -> cadence
        SensorChannel.HEART_RATE -> heartRate
        SensorChannel.POWER -> power
        SensorChannel.TEMPERATURE -> temperature
        SensorChannel.ELEVATION -> elevation
    }
}

/** Where the elevation series came from. Wave 2 adds DEM. */
enum class ElevationSource { TRACK_RAW, SMOOTHED_GPS, BAROMETER, DEM, IGNORED }

/** Per-kilometer (or per-mile) split. Distances/speeds are SI. */
data class Split(
    val index: Int,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val avgSpeed: Double,
    val elevationGain: Double,
    val avgHeartRate: Double?,
)

/** All computed aggregates for one ride. Nullable metrics mean the channel is absent. */
data class RideMetrics(
    val distanceMeters: Double,
    val movingTimeSeconds: Double,
    val elapsedTimeSeconds: Double,
    val avgSpeed: Double,
    val maxSpeed: Double,
    val avgCadence: Double?,
    val maxCadence: Double?,
    val avgHeartRate: Double?,
    val maxHeartRate: Double?,
    val avgPower: Double?,
    val maxPower: Double?,
    val elevationGain: Double,
    val elevationLoss: Double,
    val elevationSource: ElevationSource,
    val longestNonStopMeters: Double,
    val biggestClimbMeters: Double,
    val splits: List<Split>,
    // Intensity (Wave 1). Null when no usable signal.
    val normalizedPower: Double? = null,
    val intensityFactor: Double? = null,
    val effortScore: Double? = null,
    val intensityTierRank: Int? = null,
    val intensityTierKey: String? = null,
    val intensitySource: io.openftba.analytics.IntensitySource = io.openftba.analytics.IntensitySource.NONE,
)

/** A fully processed ride: identity + channels + metrics. */
data class Ride(
    val id: String,
    val trackId: String?,
    val name: String,
    val startTime: Instant,
    val endTime: Instant,
    val sourceFileName: String,
    val activityType: String?,
    val channels: AvailableChannels,
    val metrics: RideMetrics,
)
