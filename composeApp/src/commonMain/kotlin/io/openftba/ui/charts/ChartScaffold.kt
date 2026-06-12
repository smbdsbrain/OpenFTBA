package io.openftba.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openftba.ui.theme.Palette
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Axis metadata: [title] (metric name), [unit] (shown in tooltips, not on ticks), and a
 * value [format] for tick labels + tooltip numbers.
 */
data class AxisSpec(val title: String, val unit: String = "", val format: (Double) -> String = ::formatTick) {
    /** Formatted value with unit, for tooltips. */
    fun tip(v: Double): String = if (unit.isEmpty()) format(v) else "${format(v)} $unit"
}

/** Fixed plot gutters (px values derived per-density in charts). Left is fixed so vertical
 *  crosshairs line up across stacked synced charts. */
object ChartDims {
    val gutterLeft = 48.dp
    val gutterBottom = 28.dp
    val gutterTop = 12.dp
    val gutterRight = 12.dp
    const val tickFontSp = 10f
    const val titleFontSp = 10f
}

/** A drawn-on-canvas tooltip line. */
data class TipLine(val label: String, val value: String, val color: Color)

/**
 * A vertical span overlaid on a chart in X data-space — used to mark pauses/segment breaks.
 * [start]==[end] (e.g. a stop has ~no distance) renders as a thin line.
 */
data class ChartSpan(val start: Double, val end: Double, val color: Color, val label: String = "")

/** Compact number formatting for ticks: integer when large/round, else 1 decimal. */
fun formatTick(v: Double): String {
    if (v.isNaN() || v.isInfinite()) return ""
    val a = abs(v)
    return when {
        a >= 100 -> v.roundToLong().toString()
        a >= 10 -> (((v * 10).roundToLong()) / 10.0).let { if (it % 1.0 == 0.0) it.roundToLong().toString() else it.toString() }
        else -> ((v * 10).roundToLong() / 10.0).toString()
    }
}

/** n+1 evenly spaced tick values from min..max. */
fun evenTicks(min: Double, max: Double, n: Int): List<Double> {
    if (n <= 0 || max <= min) return listOf(min)
    val step = (max - min) / n
    return (0..n).map { min + it * step }
}

/** Themed floating tooltip overlay, positioned by cursor pixel-x within a chart of [widthPx]. */
@Composable
fun ChartTooltip(title: String, lines: List<TipLine>, cursorPx: Float, widthPx: Float) {
    Column(
        Modifier
            .padding(top = 6.dp)
            // Measure the real tooltip and place it right of the cursor when it fits,
            // otherwise flip to the left — it never overflows nor covers the hovered point.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val gap = 12f
                val x = if (cursorPx + gap + placeable.width <= widthPx) cursorPx + gap
                else (cursorPx - gap - placeable.width).coerceAtLeast(0f)
                layout(placeable.width, placeable.height) {
                    placeable.place(x.roundToInt(), 0)
                }
            }
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.SurfaceHigh)
            .border(1.dp, Palette.Outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(title, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = Palette.OnMuted)
        lines.forEach { ln ->
            Row(modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    if (ln.label.isEmpty()) ln.value else "${ln.label}: ${ln.value}",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = ln.color,
                )
            }
        }
    }
}
