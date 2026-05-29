package io.openftba.api

import io.openftba.analytics.AnalyzerConfig
import io.openftba.analytics.Geo
import io.openftba.analytics.IntensitySource
import io.openftba.analytics.RideAnalyzer
import io.openftba.model.AvailableChannels
import io.openftba.model.ElevationSource
import io.openftba.model.GeoPoint
import io.openftba.model.ParsedTrack
import io.openftba.model.Ride
import io.openftba.model.RideMetrics
import kotlinx.datetime.Instant

/** Domain → wire and back, plus the per-channel chart series builder (shared by all). */

fun Ride.toSummaryDto(): RideSummaryDto = RideSummaryDto(
    id = id,
    name = name,
    startEpochMs = startTime.toEpochMilliseconds(),
    endEpochMs = endTime.toEpochMilliseconds(),
    activityType = activityType,
    distanceMeters = metrics.distanceMeters,
    movingSeconds = metrics.movingTimeSeconds,
    elapsedSeconds = metrics.elapsedTimeSeconds,
    avgSpeed = metrics.avgSpeed,
    maxSpeed = metrics.maxSpeed,
    avgCadence = metrics.avgCadence,
    maxCadence = metrics.maxCadence,
    avgHeartRate = metrics.avgHeartRate,
    maxHeartRate = metrics.maxHeartRate,
    avgPower = metrics.avgPower,
    maxPower = metrics.maxPower,
    elevationGain = metrics.elevationGain,
    elevationLoss = metrics.elevationLoss,
    elevationSource = metrics.elevationSource.name,
    longestNonStopMeters = metrics.longestNonStopMeters,
    biggestClimbMeters = metrics.biggestClimbMeters,
    channels = ChannelsDto(
        channels.speed, channels.cadence, channels.heartRate,
        channels.power, channels.temperature, channels.elevation,
    ),
    effortScore = metrics.effortScore,
    intensityFactor = metrics.intensityFactor,
    intensityTierRank = metrics.intensityTierRank,
    intensityTierKey = metrics.intensityTierKey,
    intensitySource = metrics.intensitySource.name,
    normalizedPower = metrics.normalizedPower,
)

/** Reconstruct a domain Ride from a summary (no per-point data) for client-side analytics. */
fun RideSummaryDto.toRide(): Ride = Ride(
    id = id,
    trackId = id,
    name = name,
    startTime = Instant.fromEpochMilliseconds(startEpochMs),
    endTime = Instant.fromEpochMilliseconds(if (endEpochMs > 0) endEpochMs else startEpochMs),
    sourceFileName = name,
    activityType = activityType,
    channels = AvailableChannels(
        channels.speed, channels.cadence, channels.heartRate,
        channels.power, channels.temperature, channels.elevation,
    ),
    metrics = RideMetrics(
        distanceMeters = distanceMeters,
        movingTimeSeconds = movingSeconds,
        elapsedTimeSeconds = elapsedSeconds,
        avgSpeed = avgSpeed,
        maxSpeed = maxSpeed,
        avgCadence = avgCadence,
        maxCadence = maxCadence,
        avgHeartRate = avgHeartRate,
        maxHeartRate = maxHeartRate,
        avgPower = avgPower,
        maxPower = maxPower,
        elevationGain = elevationGain,
        elevationLoss = elevationLoss,
        elevationSource = runCatching { ElevationSource.valueOf(elevationSource) }.getOrDefault(ElevationSource.SMOOTHED_GPS),
        longestNonStopMeters = longestNonStopMeters,
        biggestClimbMeters = biggestClimbMeters,
        splits = emptyList(),
        normalizedPower = normalizedPower,
        intensityFactor = intensityFactor,
        effortScore = effortScore,
        intensityTierRank = intensityTierRank,
        intensityTierKey = intensityTierKey,
        intensitySource = runCatching { IntensitySource.valueOf(intensitySource) }.getOrDefault(IntensitySource.NONE),
    ),
)

private const val MAX_SERIES_POINTS = 800

// Pause-detection thresholds. A real stop often fragments because brief creeps / GPS jitter
// nudge the speed above the moving threshold, so we detect candidate stops loosely, merge
// runs that are separated by only a little odometer distance, then keep the long ones.
private const val MOVING_SPEED_THRESHOLD = 1.0  // m/s (~3.6 km/h)
private const val STOP_DETECT_SECONDS = 5.0     // candidate stop must last at least this
private const val PAUSE_MERGE_METERS = 150.0    // merge stops separated by less travel than this
private const val STOP_DISPLAY_SECONDS = 30.0   // only annotate merged stops at least this long

/**
 * Build downsampled per-channel chart series from a parsed track (pure, portable).
 *
 * When [config] is supplied, the elevation channel is plotted from the analyzer's chosen
 * source (DEM when active, else smoothed GPS) via [RideAnalyzer.chartElevationSeries], so the
 * chart matches the ride-list ascent — once we switch to DEM the noisy GPS altitude is ignored
 * everywhere. Falls back to the raw track elevation only when no series is available.
 */
fun buildRideSeries(track: ParsedTrack, config: AnalyzerConfig? = null): RideSeriesDto {
    val points = track.allPoints
    if (points.isEmpty()) return RideSeriesDto()
    // Analyzer-chosen elevation (DEM / smoothed GPS), aligned 1:1 with `points`. Use it when it
    // covers the whole track; otherwise keep the raw per-point elevation below.
    val analyzedEle = config?.let { RideAnalyzer.chartElevationSeries(track, it).first }
        ?.takeIf { it.size == points.size }
    val start = points.first().time
    val time = ArrayList<Double>(points.size)
    val dist = ArrayList<Double>(points.size)
    val speed = ArrayList<Double>(points.size)
    val ele = ArrayList<Double>(points.size)
    val hr = ArrayList<Double>(points.size)
    val cad = ArrayList<Double>(points.size)
    val pwr = ArrayList<Double>(points.size)

    // Flattened index at which each segment (after the first) begins → recorded pauses.
    val segmentStarts = HashSet<Int>()
    run {
        var idx = 0
        for ((k, seg) in track.segments.withIndex()) {
            if (k > 0) segmentStarts.add(idx)
            idx += seg.points.size
        }
    }

    val pauses = ArrayList<PauseDto>()
    var stopStartIdx = -1   // first index of the current sustained-stop candidate
    var stoppedTime = 0.0

    fun flushStop(endIdx: Int) {
        if (stopStartIdx in 0..endIdx && stoppedTime >= STOP_DETECT_SECONDS) {
            pauses.add(PauseDto(dist[stopStartIdx], dist[endIdx], time[stopStartIdx], time[endIdx], "stop"))
        }
        stopStartIdx = -1
        stoppedTime = 0.0
    }

    var cum = 0.0
    for (i in points.indices) {
        val p: GeoPoint = points[i]
        val dt = if (i > 0) (p.time - points[i - 1].time).inWholeMilliseconds / 1000.0 else 0.0
        if (i > 0) cum += Geo.distance(points[i - 1], p)
        time.add((p.time - start).inWholeMilliseconds / 60000.0)
        dist.add(cum / 1000.0)
        val v = p.speed ?: if (i > 0 && dt > 0) Geo.distance(points[i - 1], p) / dt else 0.0
        speed.add(v * 3.6)
        ele.add(analyzedEle?.get(i) ?: p.ele ?: 0.0)
        hr.add(p.heartRate ?: 0.0)
        cad.add(p.cadence ?: 0.0)
        pwr.add(p.power ?: 0.0)

        if (i in segmentStarts) {
            // Recorded segment boundary: close any open within-segment stop, then mark the gap.
            flushStop(i - 1)
            pauses.add(PauseDto(dist[i - 1], dist[i], time[i - 1], time[i], "segment"))
        } else if (i > 0) {
            if (v >= MOVING_SPEED_THRESHOLD) {
                flushStop(i - 1) // movement resumed; the stop (if long enough) ended at i-1
            } else {
                if (stopStartIdx < 0) stopStartIdx = i - 1 // stop began at the last moving point
                stoppedTime += dt
            }
        }
    }
    flushStop(points.lastIndex)

    return RideSeriesDto(
        distanceKm = down(dist), timeMin = down(time), speedKmh = down(speed),
        elevation = down(ele), heartRate = down(hr), cadence = down(cad), power = down(pwr),
        pauses = mergePauses(pauses),
    )
}

/**
 * Merge adjacent pauses separated by less than [PAUSE_MERGE_METERS] of travel (a real stop
 * fragments when a brief creep lifts the speed above threshold), then keep stops that last at
 * least [STOP_DISPLAY_SECONDS]. Segment breaks are always kept.
 */
private fun mergePauses(raw: List<PauseDto>): List<PauseDto> {
    if (raw.isEmpty()) return raw
    val sorted = raw.sortedBy { it.startMin }
    val merged = ArrayList<PauseDto>()
    var cur = sorted.first()
    for (i in 1 until sorted.size) {
        val n = sorted[i]
        if ((n.startKm - cur.endKm) * 1000.0 <= PAUSE_MERGE_METERS) {
            cur = cur.copy(
                endKm = maxOf(cur.endKm, n.endKm),
                endMin = maxOf(cur.endMin, n.endMin),
                kind = if (cur.kind == "segment" || n.kind == "segment") "segment" else "stop",
            )
        } else {
            merged.add(cur); cur = n
        }
    }
    merged.add(cur)
    return merged.filter { it.kind == "segment" || (it.endMin - it.startMin) * 60.0 >= STOP_DISPLAY_SECONDS }
}

private fun down(list: List<Double>, max: Int = MAX_SERIES_POINTS): List<Double> {
    if (list.size <= max) return list
    val step = list.size.toDouble() / max
    return (0 until max).map { list[(it * step).toInt()] }
}
