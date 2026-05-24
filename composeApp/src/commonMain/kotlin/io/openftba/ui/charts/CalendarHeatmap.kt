package io.openftba.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.openftba.ui.overview.HeatCell
import io.openftba.ui.theme.Palette
import kotlin.math.ceil

/**
 * GitHub-style calendar heatmap with hover tooltip (date + that day's distance). Columns are
 * weeks (oldest→newest), rows are weekdays (Mon→Sun). Cells carry their date + distance.
 */
@Composable
fun CalendarHeatmap(
    weeks: List<List<HeatCell?>>,
    distanceLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    accent: Color = Palette.Accent,
) {
    var hover by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    BoxWithConstraints(modifier.fillMaxWidth().height(120.dp)) {
        if (weeks.isEmpty()) return@BoxWithConstraints
        val cols = weeks.size
        val rows = 7
        val gap = 3f

        // Cell side from width (px), capped so 7 rows fit in ~112px height.
        val widthPx = constraints.maxWidth.toFloat()
        val side = ceil(((widthPx - gap * (cols - 1)) / cols).coerceAtMost((112f - gap * (rows - 1)) / rows)).coerceAtLeast(2f)
        fun cellX(c: Int) = c * (side + gap)
        fun cellY(r: Int) = r * (side + gap)

        Canvas(
            Modifier.fillMaxSize().pointerInput(cols) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val pos = e.changes.first().position
                        when (e.type) {
                            PointerEventType.Exit, PointerEventType.Release -> hover = null
                            else -> {
                                val c = (pos.x / (side + gap)).toInt()
                                val r = (pos.y / (side + gap)).toInt()
                                hover = if (c in 0 until cols && r in 0 until rows && weeks[c].getOrNull(r) != null) c to r else null
                            }
                        }
                    }
                }
            },
        ) {
            weeks.forEachIndexed { c, col ->
                for (r in 0 until rows) {
                    val cell = col.getOrNull(r) ?: continue
                    val v = cell.intensity
                    val color = if (v <= 0.0) Palette.SurfaceHigh
                    else accent.copy(alpha = (0.25f + 0.75f * v.toFloat()).coerceIn(0f, 1f))
                    drawRect(color, topLeft = Offset(cellX(c), cellY(r)), size = Size(side, side))
                }
            }
            hover?.let { (c, r) ->
                drawRect(Palette.OnBase, topLeft = Offset(cellX(c) - 1f, cellY(r) - 1f), size = Size(side + 2f, side + 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
            }
        }

        hover?.let { (c, r) ->
            weeks[c][r]?.let { cell ->
                ChartTooltip(
                    title = cell.date.toString(),
                    lines = listOf(TipLine("", distanceLabel(cell.distanceMeters), accent)),
                    cursorPx = cellX(c), widthPx = widthPx,
                )
            }
        }
    }
}
