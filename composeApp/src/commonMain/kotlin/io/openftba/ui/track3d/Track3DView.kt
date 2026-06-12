package io.openftba.ui.track3d

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.openftba.track3d.Track3D
import io.openftba.ui.theme.Palette
import kotlin.math.max
import kotlin.math.min

/**
 * The ride's route as a 3D figure suspended in space (deliberately not on a map): the track
 * line above a floor grid, with a translucent "shadow" projection and sparse vertical drop
 * lines selling the elevation. Drag to orbit (yaw free, pitch clamped above the floor);
 * mouse wheel or pinch to zoom. [colorValues] (speed or elevation, aligned 1:1 with the
 * points) drives the gradient. [cursorIndex] highlights one point — wired to the shared
 * chart cursor so hovering the charts shows where on the route that moment happened.
 */
@Composable
fun Track3DView(
    lat: List<Double>,
    lon: List<Double>,
    ele: List<Double>,
    colorValues: List<Double>,
    ramp: List<Color>,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
    cursorIndex: Int? = null,
) {
    val model = remember(lat, lon, ele) { Track3D.buildModel(lat, lon, ele) } ?: return
    val colors = remember(colorValues, ramp) {
        val ts = Track3D.gradientParams(colorValues)
        List(lat.size) { i -> rampColor(ramp, ts.getOrElse(i) { 0.5f }) }
    }
    val floorYs = remember(model) { DoubleArray(model.xs.size) { model.floorY } }
    // Grid endpoints flattened into parallel arrays so they go through the same projection call.
    val grid = remember(model) {
        val lines = Track3D.floorGrid(model)
        Triple(
            DoubleArray(lines.size * 2) { lines[it / 2][if (it % 2 == 0) 0 else 3] },
            DoubleArray(lines.size * 2) { lines[it / 2][if (it % 2 == 0) 1 else 4] },
            DoubleArray(lines.size * 2) { lines[it / 2][if (it % 2 == 0) 2 else 5] },
        )
    }

    var yaw by remember { mutableStateOf(-0.65) }
    var pitch by remember { mutableStateOf(0.55) }
    var zoom by remember { mutableStateOf(1f) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                // One-finger drag arrives as pan (orbit), two-finger pinch as zoom.
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    yaw += pan.x * 0.008
                    pitch = (pitch + pan.y * 0.008).coerceIn(0.08, 1.45)
                    zoom = (zoom * gestureZoom).coerceIn(0.5f, 4f)
                }
            }
            .pointerInput(Unit) {
                // Desktop/web mouse wheel.
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Scroll) {
                            val delta = e.changes.fold(0f) { acc, ch -> acc + ch.scrollDelta.y }
                            if (delta != 0f) {
                                zoom = (zoom * (1f - delta * 0.1f)).coerceIn(0.5f, 4f)
                                e.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            },
    ) {
        val n = model.xs.size
        val cx = size.width / 2f
        val cy = size.height / 2f
        val scalePx = min(size.width, size.height) * 0.92f * zoom
        fun px(p: FloatArray, i: Int) = Offset(cx + p[i * 2] * scalePx, cy + p[i * 2 + 1] * scalePx)

        val gridP = Track3D.projectAll(grid.first, grid.second, grid.third, yaw, pitch)
        val shadowP = Track3D.projectAll(model.xs, floorYs, model.zs, yaw, pitch)
        val lineP = Track3D.projectAll(model.xs, model.ys, model.zs, yaw, pitch)

        // Painter's algorithm back-to-front: grid, shadow, drop lines, then the track itself.
        val gridColor = Palette.Outline.copy(alpha = 0.55f)
        for (i in 0 until gridP.size / 4) {
            drawLine(gridColor, px(gridP, i * 2), px(gridP, i * 2 + 1), 1f)
        }
        for (i in 0 until n - 1) {
            drawLine(colors[i].copy(alpha = 0.20f), px(shadowP, i), px(shadowP, i + 1), 3f)
        }
        val dropStep = max(1, n / 40)
        for (i in 0 until n step dropStep) {
            drawLine(colors[i].copy(alpha = 0.25f), px(lineP, i), px(shadowP, i), 1f)
        }
        for (i in 0 until n - 1) {
            drawLine(colors[i], px(lineP, i), px(lineP, i + 1), 2.8f, cap = StrokeCap.Round)
        }

        // Shared chart cursor: mark the moment on the route (point, plumb line, floor echo).
        if (cursorIndex != null && cursorIndex in 0 until n) {
            val pt = px(lineP, cursorIndex)
            val sh = px(shadowP, cursorIndex)
            drawLine(Palette.OnBase.copy(alpha = 0.45f), pt, sh, 1.5f)
            drawCircle(Palette.OnBase.copy(alpha = 0.35f), radius = 4f, center = sh)
            drawCircle(Palette.OnBase, radius = 7.5f, center = pt)
            drawCircle(colors[cursorIndex], radius = 4.5f, center = pt)
        }
    }
}
