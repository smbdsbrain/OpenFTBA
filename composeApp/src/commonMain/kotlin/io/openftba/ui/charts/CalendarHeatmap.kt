package io.openftba.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openftba.ui.i18n.LocalStrings
import io.openftba.ui.overview.HeatCell
import io.openftba.ui.theme.Palette

/**
 * GitHub-style calendar heatmap with hover tooltip (date + that day's distance). Columns are
 * weeks (oldest→newest), rows are weekdays (Mon→Sun). Square cells size themselves from the
 * available width (so the widget fills the row on wide screens) and the component height
 * follows. Month labels run along the top, Mon/Wed/Fri labels down the left.
 */
@Composable
fun CalendarHeatmap(
    weeks: List<List<HeatCell?>>,
    distanceLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    accent: Color = Palette.Accent,
) {
    var hover by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val s = LocalStrings.current
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (weeks.isEmpty()) return@BoxWithConstraints
        val cols = weeks.size
        val rows = 7
        val gap = 3.dp
        val leftGutter = 28.dp
        val topGutter = 18.dp

        val cell = ((maxWidth - leftGutter - gap * (cols - 1)) / cols).coerceIn(8.dp, 22.dp)
        val totalHeight = topGutter + cell * rows + gap * (rows - 1)
        val labelStyle = TextStyle(fontSize = ChartDims.tickFontSp.sp, color = Palette.OnMuted)

        Canvas(
            Modifier.fillMaxWidth().height(totalHeight).pointerInput(cols, cell) {
                val side = cell.toPx()
                val gapPx = gap.toPx()
                val leftPx = leftGutter.toPx()
                val topPx = topGutter.toPx()
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val pos = e.changes.first().position
                        when (e.type) {
                            PointerEventType.Exit, PointerEventType.Release -> hover = null
                            else -> {
                                val x = pos.x - leftPx
                                val y = pos.y - topPx
                                val c = if (x >= 0f) (x / (side + gapPx)).toInt() else -1
                                val r = if (y >= 0f) (y / (side + gapPx)).toInt() else -1
                                hover = if (c in 0 until cols && r in 0 until rows && weeks[c].getOrNull(r) != null) c to r else null
                            }
                        }
                    }
                }
            },
        ) {
            val side = cell.toPx()
            val gapPx = gap.toPx()
            val leftPx = leftGutter.toPx()
            val topPx = topGutter.toPx()
            fun cellX(c: Int) = leftPx + c * (side + gapPx)
            fun cellY(r: Int) = topPx + r * (side + gapPx)

            // Month labels: above the first column of each new month, skipping overlaps.
            var lastMonth = -1
            var lastLabelEnd = Float.NEGATIVE_INFINITY
            weeks.forEachIndexed { c, col ->
                val month = col.firstOrNull { it != null }?.date?.monthNumber ?: return@forEachIndexed
                if (month != lastMonth) {
                    lastMonth = month
                    val r = textMeasurer.measure(s.monthsShort[month - 1], labelStyle)
                    val x = cellX(c)
                    if (x >= lastLabelEnd) {
                        drawText(r, topLeft = Offset(x, 0f))
                        lastLabelEnd = x + r.size.width + gapPx * 2
                    }
                }
            }

            // Weekday labels on Mon/Wed/Fri rows, centered on the row.
            intArrayOf(0, 2, 4).forEach { r ->
                val res = textMeasurer.measure(s.weekdaysShort[r], labelStyle)
                drawText(res, topLeft = Offset(0f, cellY(r) + (side - res.size.height) / 2f))
            }

            weeks.forEachIndexed { c, col ->
                for (r in 0 until rows) {
                    val cell0 = col.getOrNull(r) ?: continue
                    val v = cell0.intensity
                    val color = if (v <= 0.0) Palette.SurfaceHigh
                    else accent.copy(alpha = (0.25f + 0.75f * v.toFloat()).coerceIn(0f, 1f))
                    drawRect(color, topLeft = Offset(cellX(c), cellY(r)), size = Size(side, side))
                }
            }
            hover?.let { (c, r) ->
                drawRect(Palette.OnBase, topLeft = Offset(cellX(c) - 1f, cellY(r) - 1f), size = Size(side + 2f, side + 2f), style = Stroke(width = 1.5f))
            }
        }

        val density = LocalDensity.current
        hover?.let { (c, r) ->
            weeks[c][r]?.let { cellData ->
                ChartTooltip(
                    title = cellData.date.toString(),
                    lines = listOf(TipLine("", distanceLabel(cellData.distanceMeters), accent)),
                    cursorPx = with(density) { (leftGutter + (cell + gap) * c).toPx() },
                    widthPx = constraints.maxWidth.toFloat(),
                )
            }
        }
    }
}
