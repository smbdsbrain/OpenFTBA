package io.openftba.model

import kotlinx.datetime.Instant

/** Sensor channels that may or may not be present in an OpenTracks export. */
enum class SensorChannel { SPEED, CADENCE, HEART_RATE, POWER, TEMPERATURE, ELEVATION }

/**
 * A single recorded track point. Sensor fields are nullable because a given device
 * may not have recorded them (graceful degradation is a core product principle).
 *
 * Units are SI: meters, m/s, rpm, bpm, watts, Celsius.
 */
data class GeoPoint(
    val time: Instant,
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    val speed: Double? = null,
    val cadence: Double? = null,
    val heartRate: Double? = null,
    val power: Double? = null,
    val temperature: Double? = null,
)

/**
 * A contiguous run of points. In the OpenTracks KML schema, `<MultiTrack>` splits a
 * recording into `<Track>` segments at pause boundaries — so a segment boundary marks
 * a pause. This is the direct source for "longest non-stop distance".
 */
data class TrackSegment(val points: List<GeoPoint>)

/** Raw, parser-level representation of one activity file (before analytics). */
data class ParsedTrack(
    val trackId: String?,
    val name: String?,
    val activityType: String?,
    val segments: List<TrackSegment>,
    val sourceFileName: String,
) {
    val allPoints: List<GeoPoint> get() = segments.flatMap { it.points }
}
