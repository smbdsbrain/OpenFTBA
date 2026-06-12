package io.openftba.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openftba.ui.LocalWidthClass
import io.openftba.ui.WidthClass
import io.openftba.ui.theme.Palette
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.PI

data class DonutSegment(val value: Double, val color: Color, val label: String)

/** Donut with inline legend; hover a slice to highlight it and show count + % in the center. */
@Composable
fun DonutChart(segments: List<DonutSegment>, modifier: Modifier = Modifier) {
    val total = segments.sumOf { it.value }
    val measurer = rememberTextMeasurer()
    var hover by remember { mutableStateOf<Int?>(null) }

    // Narrow screens stack the legend under the donut instead of squeezing it beside.
    if (LocalWidthClass.current == WidthClass.Compact) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Donut(segments, total, measurer, hover) { hover = it }
            Spacer(Modifier.height(12.dp))
            DonutLegend(segments, hover)
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Donut(segments, total, measurer, hover) { hover = it }
            Spacer(Modifier.width(20.dp))
            DonutLegend(segments, hover)
        }
    }
}

@Composable
private fun Donut(
    segments: List<DonutSegment>,
    total: Double,
    measurer: TextMeasurer,
    hover: Int?,
    onHover: (Int?) -> Unit,
) {
    Box(Modifier.size(124.dp)) {
            if (total > 0) Canvas(
                Modifier.size(124.dp).pointerInput(segments.size) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            val pos = e.changes.first().position
                            when (e.type) {
                                PointerEventType.Exit, PointerEventType.Release -> onHover(null)
                                else -> {
                                    val cx = size.width / 2f; val cy = size.height / 2f
                                    val dist = hypot(pos.x - cx, pos.y - cy)
                                    val outer = minOf(size.width, size.height) / 2f
                                    if (dist > outer || dist < outer * 0.58f / 2f) { onHover(null) }
                                    else {
                                        var deg = atan2(pos.y - cy, pos.x - cx) * 180f / PI.toFloat()
                                        var rel = deg - (-90f); if (rel < 0) rel += 360f
                                        var acc = 0f; var found: Int? = null
                                        segments.forEachIndexed { i, seg ->
                                            if (seg.value <= 0) return@forEachIndexed
                                            val sweep = (seg.value / total * 360.0).toFloat()
                                            if (found == null && rel >= acc && rel < acc + sweep) found = i
                                            acc += sweep
                                        }
                                        onHover(found)
                                    }
                                }
                            }
                        }
                    }
                },
            ) {
                var start = -90f
                val outer = size.minDimension / 2f
                segments.forEachIndexed { i, seg ->
                    if (seg.value <= 0) return@forEachIndexed
                    val sweep = (seg.value / total * 360.0).toFloat()
                    val grow = if (i == hover) 4f else 0f
                    drawArc(
                        color = if (hover == null || i == hover) seg.color else seg.color.copy(alpha = 0.45f),
                        startAngle = start, sweepAngle = sweep, useCenter = true, style = Fill,
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + grow * 2, size.height + grow * 2),
                    )
                    start += sweep
                }
                val hole = size.minDimension * 0.58f
                drawCircle(Palette.Surface, hole / 2, Offset(size.width / 2, size.height / 2))

                // Center text: hovered slice (count + %) or total.
                val centerText = hover?.let { i ->
                    val pct = (segments[i].value / total * 100).roundToInt()
                    "${segments[i].value.toInt()} · $pct%"
                } ?: total.toInt().toString()
                val r = measurer.measure(centerText, TextStyle(color = Palette.OnBase, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                drawText(r, topLeft = Offset(size.width / 2 - r.size.width / 2f, size.height / 2 - r.size.height / 2f))
            }
    }
}

@Composable
private fun DonutLegend(segments: List<DonutSegment>, hover: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.filter { it.value > 0 }.forEach { seg ->
            val realIdx = segments.indexOf(seg)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(11.dp)) { drawRect(seg.color, size = Size(size.width, size.height)) }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${seg.label} — ${seg.value.toInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hover == null || hover == realIdx) Palette.OnBase else Palette.OnMuted,
                )
            }
        }
    }
}
