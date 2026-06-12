package io.openftba.ui.track3d

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.openftba.track3d.Track3D
import io.openftba.ui.share.ShareTrackArt
import io.openftba.ui.theme.Palette
import kotlin.math.max
import kotlin.math.min

/**
 * Build the share-card silhouette: the track projected from a fixed pleasant angle, with its
 * floor shadow and grid, letterboxed into a 0..1 box whose pixel aspect ratio (w/h) is
 * [aspect] — renderers can then map naively (x*w, y*h) without distortion. Returns null on
 * degenerate input (same rules as [Track3D.buildModel]).
 */
fun buildTrackArt(
    lat: List<Double>,
    lon: List<Double>,
    ele: List<Double>,
    colorValues: List<Double>,
    ramp: List<Color>,
    aspect: Double,
    yaw: Double = -0.7,
    pitch: Double = 0.5,
    maxPoints: Int = 300,
): ShareTrackArt? {
    fun thin(v: List<Double>): List<Double> {
        if (v.size <= maxPoints) return v
        val step = v.size.toDouble() / maxPoints
        return (0 until maxPoints).map { v[(it * step).toInt()] }
    }
    val tLat = thin(lat)
    val tLon = thin(lon)
    val tEle = thin(ele)
    val tVals = thin(colorValues)
    // The static card can't be rotated to read depth, so push the relief harder than the
    // interactive view does.
    val model = Track3D.buildModel(tLat, tLon, tEle, verticalRatio = 0.3, maxExaggeration = 14.0) ?: return null
    val n = model.xs.size

    val lineP = Track3D.projectAll(model.xs, model.ys, model.zs, yaw, pitch)
    val floorYs = DoubleArray(n) { model.floorY }
    val shadowP = Track3D.projectAll(model.xs, floorYs, model.zs, yaw, pitch)
    val gridLines = Track3D.floorGrid(model)
    val gridP = Track3D.projectAll(
        DoubleArray(gridLines.size * 2) { gridLines[it / 2][if (it % 2 == 0) 0 else 3] },
        DoubleArray(gridLines.size * 2) { gridLines[it / 2][if (it % 2 == 0) 1 else 4] },
        DoubleArray(gridLines.size * 2) { gridLines[it / 2][if (it % 2 == 0) 2 else 5] },
        yaw, pitch,
    )

    // Joint bounding box of everything drawn, then a uniform fit into the aspect-corrected
    // unit box (x stretched by 1/aspect relative to y in pixel space).
    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    for (p in listOf(lineP, shadowP, gridP)) {
        for (i in 0 until p.size / 2) {
            minX = min(minX, p[i * 2]); maxX = max(maxX, p[i * 2])
            minY = min(minY, p[i * 2 + 1]); maxY = max(maxY, p[i * 2 + 1])
        }
    }
    val spanX = (maxX - minX).toDouble().coerceAtLeast(1e-9)
    val spanY = (maxY - minY).toDouble().coerceAtLeast(1e-9)
    // Uniform scale in pixel space: one projected unit maps to `s` fractions of height, or
    // s/aspect fractions of width.
    val s = min(aspect / spanX, 1.0 / spanY) * 0.96
    val offX = (1.0 - spanX * s / aspect) / 2.0 - minX * s / aspect
    val offY = (1.0 - spanY * s) / 2.0 - minY * s
    fun nx(v: Float) = v * s / aspect + offX
    fun ny(v: Float) = v * s + offY

    val ts = Track3D.gradientParams(tVals)
    val colors = List(n) { i -> rampColor(ramp, ts.getOrElse(i) { 0.5f }) }
    return ShareTrackArt(
        xs = List(n) { nx(lineP[it * 2]) },
        ys = List(n) { ny(lineP[it * 2 + 1]) },
        colors = colors.map { it.toArgb() },
        shadowXs = List(n) { nx(shadowP[it * 2]) },
        shadowYs = List(n) { ny(shadowP[it * 2 + 1]) },
        shadowColors = colors.map { it.copy(alpha = 0.20f).toArgb() },
        grid = buildList {
            for (i in 0 until gridP.size / 4) {
                add(nx(gridP[i * 4])); add(ny(gridP[i * 4 + 1]))
                add(nx(gridP[i * 4 + 2])); add(ny(gridP[i * 4 + 3]))
            }
        },
        gridArgb = Palette.Outline.copy(alpha = 0.55f).toArgb(),
        drops = buildList {
            for (i in 0 until n step max(1, n / 24)) {
                add(nx(lineP[i * 2])); add(ny(lineP[i * 2 + 1]))
                add(nx(shadowP[i * 2])); add(ny(shadowP[i * 2 + 1]))
            }
        },
        dropColors = buildList {
            for (i in 0 until n step max(1, n / 24)) add(colors[i].copy(alpha = 0.30f).toArgb())
        },
    )
}
