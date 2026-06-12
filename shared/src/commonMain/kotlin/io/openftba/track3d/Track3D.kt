package io.openftba.track3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * A track in normalized model space, ready for 3D projection: x = east, z = north, y = up
 * (elevation, exaggerated for legibility). The horizontal footprint is centered at the origin
 * and the longer of the two horizontal extents spans exactly 1.0; the vertical extent is
 * centered too, with the floor plane at [floorY].
 */
class TrackModel(
    val xs: DoubleArray,
    val ys: DoubleArray,
    val zs: DoubleArray,
    val floorY: Double,
)

/**
 * Pure, dependency-free 3D math for the "track suspended in space" visualization: a local
 * tangent-plane projection of lat/lon/ele into a unit box plus a rotate-and-project pipeline.
 * Lives in shared so the Compose view, the share-card silhouette builder, and unit tests all
 * use the same numbers.
 */
object Track3D {

    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val RAD = PI / 180.0

    /** Synthetic line height above the floor for tracks with no elevation variation. */
    const val FLAT_TRACK_HEIGHT = 0.08

    /**
     * Build the normalized model from raw coordinates. Elevation is exaggerated so the track's
     * vertical span reads as roughly [verticalRatio] of its horizontal span, clamped to
     * [1, maxExaggeration] so mountainous rides aren't squashed and flat rides aren't absurd.
     * Missing elevation (empty or shorter list) is treated as 0. Returns null for degenerate
     * input: fewer than 2 points, mismatched sizes, or a horizontal extent under a meter.
     */
    fun buildModel(
        lat: List<Double>,
        lon: List<Double>,
        ele: List<Double>,
        verticalRatio: Double = 0.22,
        maxExaggeration: Double = 8.0,
    ): TrackModel? {
        val n = lat.size
        if (n < 2 || lon.size != n) return null
        val lat0 = lat.average()
        val lon0 = lon.average()
        val cosLat = cos(lat0 * RAD)

        val xs = DoubleArray(n)
        val zs = DoubleArray(n)
        val ys = DoubleArray(n)
        for (i in 0 until n) {
            xs[i] = (lon[i] - lon0) * RAD * cosLat * EARTH_RADIUS_M
            zs[i] = (lat[i] - lat0) * RAD * EARTH_RADIUS_M
            ys[i] = ele.getOrElse(i) { 0.0 }
        }

        val xMin = xs.min(); val xMax = xs.max()
        val zMin = zs.min(); val zMax = zs.max()
        val yMin = ys.min(); val yMax = ys.max()
        val horizontal = max(xMax - xMin, zMax - zMin)
        if (horizontal < 1.0) return null

        val yRange = yMax - yMin
        val exaggeration =
            if (yRange <= 0.0) 1.0 else (verticalRatio * horizontal / yRange).coerceIn(1.0, maxExaggeration)
        val scale = 1.0 / horizontal
        // Flat tracks still get a small lift so the line visibly floats above the floor.
        val yHeight = if (yRange <= 0.0) FLAT_TRACK_HEIGHT else yRange * exaggeration * scale
        val floorY = -yHeight / 2.0

        for (i in 0 until n) {
            xs[i] = (xs[i] - (xMin + xMax) / 2.0) * scale
            zs[i] = (zs[i] - (zMin + zMax) / 2.0) * scale
            ys[i] = if (yRange <= 0.0) yHeight / 2.0 else (ys[i] - yMin) * exaggeration * scale + floorY
        }
        return TrackModel(xs, ys, zs, floorY)
    }

    /**
     * Normalize a metric channel (speed, elevation, …) into per-point gradient parameters in
     * 0..1. A constant channel maps to 0.5 everywhere.
     */
    fun gradientParams(values: List<Double>): FloatArray {
        if (values.isEmpty()) return FloatArray(0)
        val min = values.min()
        val range = values.max() - min
        return FloatArray(values.size) { i ->
            if (range <= 0.0) 0.5f else ((values[i] - min) / range).toFloat()
        }
    }

    /**
     * Rotate model-space points (yaw about Y, then pitch about X) and apply a weak perspective
     * projection. Positive pitch places the camera *above* the floor looking down: far (north)
     * floor points rise toward the top of the screen, like looking at a table. Returns packed
     * screen fractions [sx0, sy0, sx1, sy1, ...] with y growing downward; for a unit-box model
     * values stay roughly within ±0.6. The caller maps them to pixels as `center + s * scalePx`.
     */
    fun projectAll(
        xs: DoubleArray,
        ys: DoubleArray,
        zs: DoubleArray,
        yaw: Double,
        pitch: Double,
        cameraDist: Double = 3.5,
    ): FloatArray {
        val cy = cos(yaw); val sy = sin(yaw)
        val cp = cos(pitch); val sp = sin(pitch)
        val out = FloatArray(xs.size * 2)
        for (i in xs.indices) {
            val x1 = xs[i] * cy - zs[i] * sy
            val z1 = xs[i] * sy + zs[i] * cy
            val y2 = ys[i] * cp + z1 * sp
            val z2 = z1 * cp - ys[i] * sp
            val s = cameraDist / (cameraDist + z2)
            out[i * 2] = (x1 * s).toFloat()
            out[i * 2 + 1] = (-y2 * s).toFloat()
        }
        return out
    }

    /**
     * Floor grid for the model's horizontal bounding square at y = floorY: (n+1) lines along
     * each axis, each entry packed as [x0, y, z0, x1, y, z1] in model space.
     */
    fun floorGrid(model: TrackModel, n: Int = 8): List<DoubleArray> {
        val xMin = model.xs.min(); val xMax = model.xs.max()
        val zMin = model.zs.min(); val zMax = model.zs.max()
        // A square footprint (side = the longer extent) keeps the grid stable as the user
        // rotates and reads as a "ground plane" rather than hugging the track.
        val half = max(xMax - xMin, zMax - zMin) * 0.62
        val cx = (xMin + xMax) / 2.0
        val cz = (zMin + zMax) / 2.0
        val y = model.floorY
        val lines = ArrayList<DoubleArray>(2 * (n + 1))
        for (k in 0..n) {
            val t = cx - half + 2.0 * half * k / n
            lines.add(doubleArrayOf(t, y, cz - half, t, y, cz + half))
            val u = cz - half + 2.0 * half * k / n
            lines.add(doubleArrayOf(cx - half, y, u, cx + half, y, u))
        }
        return lines
    }
}
