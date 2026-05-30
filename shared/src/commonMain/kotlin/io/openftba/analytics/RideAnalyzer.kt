package io.openftba.analytics

import io.openftba.model.AvailableChannels
import io.openftba.model.ElevationSource
import io.openftba.model.GeoPoint
import io.openftba.model.ParsedTrack
import io.openftba.model.Ride
import io.openftba.model.RideMetrics
import io.openftba.model.SensorChannel
import io.openftba.model.Split
import io.openftba.model.TrackSegment
import kotlin.math.abs
import kotlin.math.max

/** Tunable parameters for analytics. Sensible defaults for cycling. */
data class AnalyzerConfig(
    /** Below this speed (m/s) a point is considered "stopped" for moving-time. ~3.6 km/h. */
    val movingSpeedThreshold: Double = 1.0,
    /**
     * Sustained stopped time (s) that ends a "non-stop" run. OpenTracks does not always split
     * a segment when the rider stops (lights, breaks), so standing still longer than this
     * breaks the longest-non-stop distance even within one segment. ~10 s ignores GPS jitter
     * but catches real stops.
     */
    val stopResetSeconds: Double = 10.0,
    /** Moving-average window (points) used to denoise the GPS elevation series. */
    val elevationSmoothingWindow: Int = 7,
    /** Hysteresis (m): only count climb/descent once cumulative change exceeds this. */
    val elevationThreshold: Double = 2.0,
    /** Sanity cap for instantaneous speed (m/s) to reject GPS spikes. ~108 km/h. */
    val maxPlausibleSpeed: Double = 30.0,
    /** Split length in meters (1 km by default). */
    val splitMeters: Double = 1000.0,
    /**
     * Ignore the track's own (GPS/baro) elevation (user setting). DEM correction, when enabled and
     * covering the route, still applies — this only suppresses the GPS fallback. Distrusting the
     * ELEVATION sensor channel ([disabledChannels]) is the stronger switch that also turns DEM off.
     */
    val ignoreElevation: Boolean = false,
    /** Athlete profile for intensity (TRIMP/TSS) and tiers. */
    val profile: AthleteProfile = AthleteProfile(),
    /** Wave 2: correct elevation from a DEM (e.g. SRTM tiles) instead of GPS. */
    val useDem: Boolean = false,
    val elevationProvider: ElevationProvider? = null,
    /** Wave 5: channels the user distrusts; treated as absent even if present in the track. */
    val disabledChannels: Set<SensorChannel> = emptySet(),
)

/**
 * Turns a [ParsedTrack] into a [Ride] with computed [RideMetrics].
 *
 * Elevation source for the slice: smoothed GPS (barometer/DEM come in later waves).
 * "Longest non-stop distance" is the longest run between stops: segment boundaries are
 * pauses, and within a segment a sustained low-speed stretch (>= [AnalyzerConfig.stopResetSeconds])
 * also breaks the run — OpenTracks does not always split a segment when the rider stops.
 */
object RideAnalyzer {

    fun analyze(track: ParsedTrack, config: AnalyzerConfig = AnalyzerConfig()): Ride? {
        if (track.allPoints.size < 2) return null

        val (segments, effectiveConfig) = prepare(track, config)
        val points = segments.flatMap { it.points }

        val channels = detectChannels(points)
        val metrics = computeMetrics(segments, channels, effectiveConfig)

        val start = points.first().time
        val end = points.last().time
        return Ride(
            id = track.trackId ?: track.sourceFileName,
            trackId = track.trackId,
            name = track.name ?: track.sourceFileName,
            startTime = start,
            endTime = end,
            sourceFileName = track.sourceFileName,
            activityType = track.activityType,
            channels = channels,
            metrics = metrics,
        )
    }

    /**
     * Shared front-end for [analyze] and [chartElevationSeries]: drop distrusted sensor channels
     * (Wave 5) so everything downstream sees them as absent, and derive the effective config —
     * disabling ELEVATION also forces elevation off (ignore + no DEM). Returns the (possibly
     * stripped) segments and the effective config.
     */
    private fun prepare(track: ParsedTrack, config: AnalyzerConfig): Pair<List<TrackSegment>, AnalyzerConfig> {
        val disabled = config.disabledChannels
        val segments = if (disabled.isEmpty()) track.segments
        else track.segments.map { seg -> TrackSegment(seg.points.map { stripDisabled(it, disabled) }) }
        val effectiveConfig =
            if (SensorChannel.ELEVATION in disabled) config.copy(ignoreElevation = true, useDem = false)
            else config
        return segments to effectiveConfig
    }

    /**
     * Per-point elevation series the charts should plot, using the **same** source selection and
     * fallback as the metrics ([buildElevationSeries]: DEM → smoothed GPS → none) so the chart and
     * the ride-list ascent never disagree. The series is aligned to [ParsedTrack.allPoints] and is
     * empty when elevation is ignored/absent.
     */
    fun chartElevationSeries(track: ParsedTrack, config: AnalyzerConfig): Pair<List<Double>, ElevationSource> {
        val (segments, effectiveConfig) = prepare(track, config)
        val flat = segments.flatMap { it.points }
        val channels = detectChannels(flat)
        return buildElevationSeries(flat, channels, effectiveConfig)
    }

    private fun stripDisabled(p: GeoPoint, disabled: Set<SensorChannel>): GeoPoint = p.copy(
        speed = if (SensorChannel.SPEED in disabled) null else p.speed,
        cadence = if (SensorChannel.CADENCE in disabled) null else p.cadence,
        heartRate = if (SensorChannel.HEART_RATE in disabled) null else p.heartRate,
        power = if (SensorChannel.POWER in disabled) null else p.power,
        temperature = if (SensorChannel.TEMPERATURE in disabled) null else p.temperature,
        ele = if (SensorChannel.ELEVATION in disabled) null else p.ele,
    )

    fun detectChannels(points: List<GeoPoint>): AvailableChannels = AvailableChannels(
        speed = points.any { it.speed != null },
        cadence = points.any { it.cadence != null },
        heartRate = points.any { it.heartRate != null },
        power = points.any { it.power != null },
        temperature = points.any { it.temperature != null },
        elevation = points.any { it.ele != null },
    )

    private fun computeMetrics(
        segments: List<TrackSegment>,
        channels: AvailableChannels,
        config: AnalyzerConfig,
    ): RideMetrics {
        var totalDistance = 0.0
        var movingTime = 0.0
        var maxSpeed = 0.0
        var longestNonStop = 0.0

        val heartRates = mutableListOf<Double>()
        val cadences = mutableListOf<Double>()
        val powers = mutableListOf<Double>()

        // Cumulative distance + elevation series across all points, for splits & climbs.
        val flat = segments.flatMap { it.points }
        val (elevationSeries, elevationSource) = buildElevationSeries(flat, channels, config)

        for (segment in segments) {
            val pts = segment.points
            // A segment boundary is itself a pause, so each segment starts a fresh run.
            var runDistance = 0.0     // distance of the current non-stop run
            var stoppedTime = 0.0     // sustained stopped time, resets when moving resumes
            for (i in 1 until pts.size) {
                val a = pts[i - 1]
                val b = pts[i]
                val d = Geo.distance(a, b)
                val dt = (b.time - a.time).inWholeMilliseconds / 1000.0
                totalDistance += d

                // Speed: prefer recorded channel, else derive, reject spikes.
                val recorded = b.speed
                val derived = if (dt > 0) d / dt else 0.0
                val v = (recorded ?: derived).coerceAtMost(config.maxPlausibleSpeed)
                if (v > maxSpeed) maxSpeed = v

                val moving = dt > 0 && v >= config.movingSpeedThreshold
                if (moving) {
                    movingTime += dt
                    runDistance += d
                    stoppedTime = 0.0
                } else {
                    // Standing still: accumulate stopped time; a sustained stop ends the run.
                    stoppedTime += dt
                    if (stoppedTime >= config.stopResetSeconds) {
                        longestNonStop = max(longestNonStop, runDistance)
                        runDistance = 0.0
                    }
                }

                b.heartRate?.let { heartRates.add(it) }
                b.cadence?.let { cadences.add(it) }
                b.power?.let { powers.add(it) }
            }
            longestNonStop = max(longestNonStop, runDistance)
        }

        val (gain, loss) = elevationGainLoss(elevationSeries, config.elevationThreshold)
        val biggestClimb = biggestContinuousClimb(segments, elevationSeries, config)

        val elapsed = if (flat.size >= 2) {
            (flat.last().time - flat.first().time).inWholeMilliseconds / 1000.0
        } else 0.0

        val avgSpeed = if (movingTime > 0) totalDistance / movingTime else 0.0
        val splits = computeSplits(flat, elevationSeries, config)

        // Intensity (Wave 1): best available signal, graceful degradation.
        val avgHr = heartRates.takeIf { it.isNotEmpty() }?.average()
        val np = if (powers.isNotEmpty()) IntensityCalculator.normalizedPower(powers) else null
        val intensity = IntensityCalculator.evaluate(
            movingSeconds = movingTime,
            avgHeartRate = avgHr,
            normalizedPower = np,
            avgSpeed = avgSpeed,
            profile = config.profile,
        )

        return RideMetrics(
            distanceMeters = totalDistance,
            movingTimeSeconds = movingTime,
            elapsedTimeSeconds = elapsed,
            avgSpeed = avgSpeed,
            maxSpeed = maxSpeed,
            avgCadence = cadences.takeIf { it.isNotEmpty() }?.average(),
            maxCadence = cadences.maxOrNull(),
            avgHeartRate = avgHr,
            maxHeartRate = heartRates.maxOrNull(),
            avgPower = powers.takeIf { it.isNotEmpty() }?.average(),
            maxPower = powers.maxOrNull(),
            elevationGain = gain,
            elevationLoss = loss,
            elevationSource = elevationSource,
            longestNonStopMeters = longestNonStop,
            biggestClimbMeters = biggestClimb,
            splits = splits,
            normalizedPower = intensity.normalizedPower,
            intensityFactor = intensity.intensityFactor.takeIf { intensity.source != IntensitySource.NONE },
            effortScore = intensity.effortScore.takeIf { intensity.source != IntensitySource.NONE },
            intensityTierRank = intensity.tier.rank.takeIf { intensity.source != IntensitySource.NONE },
            intensityTierKey = intensity.tier.key.takeIf { intensity.source != IntensitySource.NONE },
            intensitySource = intensity.source,
        )
    }

    /** Moving-average smoothing that preserves nulls-as-gaps by linear carry. */
    internal fun smooth(values: List<Double?>, window: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        // Forward/back fill nulls so the window has data to average.
        val filled = DoubleArray(values.size)
        var last = values.firstOrNull { it != null } ?: 0.0
        for (i in values.indices) {
            last = values[i] ?: last
            filled[i] = last
        }
        if (window <= 1) return filled.toList()
        val half = window / 2
        val out = DoubleArray(filled.size)
        for (i in filled.indices) {
            val lo = maxOf(0, i - half)
            val hi = minOf(filled.size - 1, i + half)
            var sum = 0.0
            for (j in lo..hi) sum += filled[j]
            out[i] = sum / (hi - lo + 1)
        }
        return out.toList()
    }

    /** Accumulate gain/loss only after cumulative change crosses the threshold (hysteresis). */
    internal fun elevationGainLoss(ele: List<Double>, threshold: Double): Pair<Double, Double> {
        if (ele.size < 2) return 0.0 to 0.0
        var gain = 0.0
        var loss = 0.0
        var anchor = ele.first()
        for (i in 1 until ele.size) {
            val delta = ele[i] - anchor
            if (delta >= threshold) {
                gain += delta
                anchor = ele[i]
            } else if (delta <= -threshold) {
                loss += -delta
                anchor = ele[i]
            }
        }
        return gain to loss
    }

    /**
     * Pick the elevation series for the whole ride from the configured source, with
     * graceful fallback: DEM (if enabled and it covers the route) → smoothed GPS → none.
     *
     * DEM is tried first because it is independent of the track's own (GPS/baro) altitude, so
     * [AnalyzerConfig.ignoreElevation] ("ignore GPS elevation") must not suppress it — it only
     * drops the GPS fallback used when no usable DEM is available.
     */
    internal fun buildElevationSeries(
        flat: List<GeoPoint>,
        channels: AvailableChannels,
        config: AnalyzerConfig,
    ): Pair<List<Double>, ElevationSource> {
        if (flat.isEmpty()) return emptyList<Double>() to ElevationSource.IGNORED

        val provider = config.elevationProvider
        if (config.useDem && provider != null) {
            val raw = flat.map { provider.elevationAt(it.lat, it.lon) }
            val valid = raw.count { it != null }
            if (valid >= flat.size / 2) {
                // DEM terrain is already smooth; a light window just fills the rare null.
                return smooth(raw, 3) to ElevationSource.DEM
            }
        }
        // No usable DEM: the track's own elevation is the only source left. Drop it when the user
        // ignores GPS elevation or the channel is absent/distrusted.
        if (config.ignoreElevation || !channels.elevation) return emptyList<Double>() to ElevationSource.IGNORED
        return smooth(flat.map { it.ele }, config.elevationSmoothingWindow) to ElevationSource.SMOOTHED_GPS
    }

    /**
     * Biggest continuous climb: the largest net elevation gain within a single segment
     * (no pause) that is not interrupted by a sustained descent beyond the threshold.
     * Uses the already-built ride elevation series, sliced per segment.
     */
    internal fun biggestContinuousClimb(
        segments: List<TrackSegment>,
        elevationSeries: List<Double>,
        config: AnalyzerConfig,
    ): Double {
        if (elevationSeries.isEmpty()) return 0.0
        var best = 0.0
        var offset = 0
        for (segment in segments) {
            val n = segment.points.size
            val ele = elevationSeries.subList(offset, minOf(offset + n, elevationSeries.size))
            offset += n
            if (ele.size < 2) continue
            var lowAnchor = ele.first()
            var peak = ele.first()
            for (i in 1 until ele.size) {
                val e = ele[i]
                if (e >= peak) {
                    peak = e
                    val current = peak - lowAnchor
                    if (current > best) best = current
                } else if (peak - e >= config.elevationThreshold) {
                    lowAnchor = e
                    peak = e
                }
            }
        }
        return best
    }

    private fun computeSplits(
        points: List<GeoPoint>,
        elevationSeries: List<Double>,
        config: AnalyzerConfig,
    ): List<Split> {
        if (points.size < 2) return emptyList()
        val splits = mutableListOf<Split>()
        var splitIndex = 1
        var cumDistance = 0.0
        var splitStartDistance = 0.0
        var splitStartTime = points.first().time
        var splitHr = mutableListOf<Double>()
        var splitStartEle = elevationSeries.firstOrNull()

        for (i in 1 until points.size) {
            cumDistance += Geo.distance(points[i - 1], points[i])
            points[i].heartRate?.let { splitHr.add(it) }
            if (cumDistance - splitStartDistance >= config.splitMeters || i == points.size - 1) {
                val dist = cumDistance - splitStartDistance
                val dur = (points[i].time - splitStartTime).inWholeMilliseconds / 1000.0
                val endEle = elevationSeries.getOrNull(i)
                val gain = if (splitStartEle != null && endEle != null) {
                    max(0.0, endEle - splitStartEle)
                } else 0.0
                splits.add(
                    Split(
                        index = splitIndex++,
                        distanceMeters = dist,
                        durationSeconds = dur,
                        avgSpeed = if (dur > 0) dist / dur else 0.0,
                        elevationGain = gain,
                        avgHeartRate = splitHr.takeIf { it.isNotEmpty() }?.average(),
                    )
                )
                splitStartDistance = cumDistance
                splitStartTime = points[i].time
                splitHr = mutableListOf()
                splitStartEle = endEle
            }
        }
        return splits
    }
}
