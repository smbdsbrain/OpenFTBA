package io.openftba.api

import io.openftba.settings.AppSettings
import kotlinx.serialization.Serializable

/**
 * The single wire contract shared by the Ktor server and every client (desktop maps to it
 * locally; the web client receives it over REST). Times are epoch millis; units are SI.
 */

@Serializable
data class ChannelsDto(
    val speed: Boolean,
    val cadence: Boolean,
    val heartRate: Boolean,
    val power: Boolean,
    val temperature: Boolean,
    val elevation: Boolean,
)

@Serializable
data class SplitDto(
    val index: Int,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val avgSpeed: Double,
    val elevationGain: Double,
    val avgHeartRate: Double? = null,
)

@Serializable
data class RideSummaryDto(
    val id: String,
    val name: String,
    val startEpochMs: Long,
    val endEpochMs: Long = 0,
    val activityType: String? = null,
    val distanceMeters: Double,
    val movingSeconds: Double,
    val elapsedSeconds: Double,
    val avgSpeed: Double,
    val maxSpeed: Double,
    val avgCadence: Double? = null,
    val maxCadence: Double? = null,
    val avgHeartRate: Double? = null,
    val maxHeartRate: Double? = null,
    val avgPower: Double? = null,
    val maxPower: Double? = null,
    val elevationGain: Double,
    val elevationLoss: Double,
    val elevationSource: String = "SMOOTHED_GPS",
    val longestNonStopMeters: Double,
    val biggestClimbMeters: Double,
    val channels: ChannelsDto,
    val effortScore: Double? = null,
    val intensityFactor: Double? = null,
    val intensityTierRank: Int? = null,
    val intensityTierKey: String? = null,
    val intensitySource: String = "NONE",
    val normalizedPower: Double? = null,
)

/**
 * A pause on the ride timeline, in chart coordinates for both X axes (km / min). [kind] is
 * "segment" for a recorded segment boundary (OpenTracks auto-pause) or "stop" for a
 * sustained low-speed stretch within a segment.
 */
@Serializable
data class PauseDto(
    val startKm: Double,
    val endKm: Double,
    val startMin: Double,
    val endMin: Double,
    val kind: String,
)

/** Downsampled per-channel series for charting (parallel arrays). */
@Serializable
data class RideSeriesDto(
    val distanceKm: List<Double> = emptyList(),
    val timeMin: List<Double> = emptyList(),
    val speedKmh: List<Double> = emptyList(),
    val elevation: List<Double> = emptyList(),
    val heartRate: List<Double> = emptyList(),
    val cadence: List<Double> = emptyList(),
    val power: List<Double> = emptyList(),
    // Track path for the 3D visualization, aligned 1:1 with the channels above (same
    // downsample indices), so elevation/speed at index i belong to the point at (lat[i], lon[i]).
    val lat: List<Double> = emptyList(),
    val lon: List<Double> = emptyList(),
    val pauses: List<PauseDto> = emptyList(),
)

@Serializable
data class RideDetailDto(
    val summary: RideSummaryDto,
    val series: RideSeriesDto,
    val splits: List<SplitDto> = emptyList(),
)

@Serializable
data class AthleteTierDto(
    val tier: String,
    val basis: String,
    val value: Double? = null,
    val progressToNext: Double = 0.0,
)

@Serializable
data class LoadPointDto(val epochDay: Long, val ctl: Double, val atl: Double, val tsb: Double)

@Serializable
data class OverviewDto(
    val athlete: AthleteTierDto,
    val load: List<LoadPointDto> = emptyList(),
)

/**
 * The full application config exchanged with the server (GET/POST /api/settings). [settings]
 * is the persisted, user-editable [AppSettings]; the remaining fields are read-only server
 * facts for display. On the server/web the watch + DEM folders are fixed by the host
 * (container mounts / env), so [foldersEditable] is false and clients must not change them.
 */
@Serializable
data class AppConfigDto(
    val settings: AppSettings,
    val watchFolder: String? = null,
    val demFolder: String? = null,
    val demAvailable: Boolean = false,
    val foldersEditable: Boolean = false,
)
