package io.openftba.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openftba.ui.theme.Palette
import kotlin.math.abs

/** One line series in chart-space (raw x/y values; the chart autoscales). */
data class LineSeries(
    val xs: List<Double>,
    val ys: List<Double>,
    val color: Color,
    val fillUnder: Boolean = true,
)

/**
 * Interactive multi-series line chart: labelled axes, hover/scrub crosshair + tooltip, and
 * (single-series) min/avg/max markers. Dependency-free Canvas.
 *
 * Pass [cursorX] + [onCursorChange] to synchronise the crosshair across several charts that
 * share the same X domain (e.g. ride-detail speed/HR/elevation). When [onCursorChange] is
 * null the chart manages its own hover cursor.
 */
@Composable
fun InteractiveLineChart(
    series: List<LineSeries>,
    xAxis: AxisSpec,
    yAxis: AxisSpec,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    cursorX: Double? = null,
    onCursorChange: ((Double?) -> Unit)? = null,
    showMarkers: Boolean = true,
    seriesLabels: List<String>? = null,
    gridLines: Int = 4,
    spans: List<ChartSpan> = emptyList(),
) {
    val drawable = series.filter { it.xs.size >= 2 && it.xs.size == it.ys.size }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var internalCursor by remember { mutableStateOf<Double?>(null) }
    val cursor = cursorX ?: internalCursor

    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        if (drawable.isEmpty()) return@BoxWithConstraints
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val leftPx = with(density) { ChartDims.gutterLeft.toPx() }
        val rightPx = with(density) { ChartDims.gutterRight.toPx() }
        val topPx = with(density) { ChartDims.gutterTop.toPx() }
        val bottomPx = with(density) { ChartDims.gutterBottom.toPx() }
        val plotW = (widthPx - leftPx - rightPx).coerceAtLeast(1f)
        val plotH = (heightPx - topPx - bottomPx).coerceAtLeast(1f)

        val xMin = drawable.minOf { it.xs.min() }
        val xMax = drawable.maxOf { it.xs.max() }
        val xSpan = (xMax - xMin).takeIf { it > 0 } ?: 1.0
        val yLo = drawable.minOf { it.ys.min() }
        val yHi = drawable.maxOf { it.ys.max() }
        val pad = (yHi - yLo).takeIf { it > 0 }?.times(0.08) ?: 1.0
        val yMin = yLo - pad
        val yMax = yHi + pad
        val ySpan = (yMax - yMin).takeIf { it > 0 } ?: 1.0

        fun xToPx(x: Double) = leftPx + ((x - xMin) / xSpan).toFloat() * plotW
        fun yToPx(y: Double) = topPx + (1f - ((y - yMin) / ySpan).toFloat()) * plotH

        val ref = drawable.first().xs
        val cursorIdx = cursor?.let { c -> nearestIndex(ref, c) }
        val report: (Double?) -> Unit = onCursorChange ?: { internalCursor = it }

        Canvas(
            Modifier.fillMaxSize().pointerInput(drawable.size, xMin, xMax, onCursorChange) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val pos = e.changes.first().position
                        when (e.type) {
                            PointerEventType.Exit, PointerEventType.Release -> report(null)
                            else -> {
                                val frac = (pos.x - leftPx) / plotW
                                report(if (frac in 0f..1f) xMin + frac * xSpan else null)
                            }
                        }
                    }
                }
            },
        ) {
            drawAxes(measurer, leftPx, topPx, plotW, plotH, xMin, xMax, yMin, yMax, gridLines, xAxis, yAxis)

            // Pause / segment-break overlays, drawn behind the data lines.
            for (sp in spans) {
                val x0 = xToPx(sp.start.coerceIn(xMin, xMax))
                val x1 = xToPx(sp.end.coerceIn(xMin, xMax))
                val w = (x1 - x0)
                if (w >= 1.5f) {
                    drawRect(sp.color.copy(alpha = 0.12f), topLeft = Offset(x0, topPx), size = Size(w, plotH))
                    drawLine(sp.color.copy(alpha = 0.45f), Offset(x0, topPx), Offset(x0, topPx + plotH), 1f)
                    drawLine(sp.color.copy(alpha = 0.45f), Offset(x1, topPx), Offset(x1, topPx + plotH), 1f)
                } else {
                    // Zero-width on this axis (e.g. a stop in distance mode): a faint dashed marker.
                    drawLine(
                        sp.color.copy(alpha = 0.35f), Offset(x0, topPx), Offset(x0, topPx + plotH), 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 6f)),
                    )
                }
            }

            for (s in drawable) {
                val line = Path()
                val fill = Path()
                s.xs.indices.forEach { i ->
                    val px = xToPx(s.xs[i]); val py = yToPx(s.ys[i])
                    if (i == 0) { line.moveTo(px, py); fill.moveTo(px, topPx + plotH); fill.lineTo(px, py) }
                    else { line.lineTo(px, py); fill.lineTo(px, py) }
                }
                if (s.fillUnder) {
                    fill.lineTo(xToPx(s.xs.last()), topPx + plotH)
                    fill.close()
                    drawPath(fill, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.26f), s.color.copy(alpha = 0f)), startY = topPx, endY = topPx + plotH))
                }
                drawPath(line, color = s.color, style = Stroke(width = 2.2f))
            }

            // Markers (single-series charts only — multi-series would be cluttered).
            if (showMarkers && drawable.size == 1) {
                val s = drawable.first()
                val avg = s.ys.average()
                val avgY = yToPx(avg)
                drawLine(Palette.OnMuted.copy(alpha = 0.6f), Offset(leftPx, avgY), Offset(leftPx + plotW, avgY), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                val maxI = s.ys.indices.maxByOrNull { s.ys[it] }!!
                val minI = s.ys.indices.minByOrNull { s.ys[it] }!!
                drawMarker(measurer, xToPx(s.xs[maxI]), yToPx(s.ys[maxI]), Palette.Record, yAxis.format(s.ys[maxI]), leftPx, leftPx + plotW)
                drawMarker(measurer, xToPx(s.xs[minI]), yToPx(s.ys[minI]), Palette.OnMuted, yAxis.format(s.ys[minI]), leftPx, leftPx + plotW)
            }

            // Crosshair + per-series dots at the cursor.
            if (cursorIdx != null) {
                val cx = xToPx(ref[cursorIdx])
                drawLine(Palette.OnBase.copy(alpha = 0.45f), Offset(cx, topPx), Offset(cx, topPx + plotH), 1f)
                for (s in drawable) {
                    val i = cursorIdx.coerceAtMost(s.ys.size - 1)
                    drawCircle(s.color, 4f, Offset(xToPx(s.xs[i]), yToPx(s.ys[i])))
                }
            }
        }

        if (cursorIdx != null) {
            val cx = xToPx(ref[cursorIdx])
            val lines = drawable.mapIndexed { i, s ->
                val idx = cursorIdx.coerceAtMost(s.ys.size - 1)
                TipLine(seriesLabels?.getOrNull(i) ?: "", yAxis.tip(s.ys[idx]), s.color)
            }
            ChartTooltip(title = xAxis.tip(ref[cursorIdx]), lines = lines, cursorPx = cx, widthPx = widthPx)
        }
    }
}

private fun DrawScope.drawAxes(
    m: TextMeasurer, leftPx: Float, topPx: Float, plotW: Float, plotH: Float,
    xMin: Double, xMax: Double, yMin: Double, yMax: Double, gridLines: Int,
    xAxis: AxisSpec, yAxis: AxisSpec,
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 8f))
    val tickStyle = TextStyle(color = Palette.OnMuted, fontSize = ChartDims.tickFontSp.sp)
    val titleStyle = TextStyle(color = Palette.OnMuted, fontSize = ChartDims.titleFontSp.sp)

    // Y gridlines + labels. Skip the topmost tick's label so it doesn't collide with the
    // Y-axis title drawn at the top-left.
    val yTicks = evenTicks(yMin, yMax, gridLines)
    yTicks.forEachIndexed { i, t ->
        val y = topPx + (1f - ((t - yMin) / (yMax - yMin)).toFloat()) * plotH
        drawLine(Palette.Outline, Offset(leftPx, y), Offset(leftPx + plotW, y), 1f, pathEffect = dash)
        if (i != yTicks.lastIndex) {
            val r = m.measure(yAxis.format(t), tickStyle)
            drawText(r, topLeft = Offset(leftPx - r.size.width - 6f, y - r.size.height / 2f))
        }
    }
    // X labels. Skip the last one when an X-axis title is shown (both sit bottom-right).
    val xTicks = evenTicks(xMin, xMax, 4)
    xTicks.forEachIndexed { i, t ->
        if (xAxis.title.isNotEmpty() && i == xTicks.lastIndex) return@forEachIndexed
        val x = leftPx + ((t - xMin) / (xMax - xMin)).toFloat() * plotW
        val r = m.measure(xAxis.format(t), tickStyle)
        drawText(r, topLeft = Offset((x - r.size.width / 2f).coerceIn(0f, leftPx + plotW - r.size.width), topPx + plotH + 6f))
    }
    // Axis titles: Y top-left, X bottom-right.
    drawText(m.measure(yAxis.title, titleStyle), topLeft = Offset(2f, 0f))
    val xt = m.measure(xAxis.title, titleStyle)
    drawText(xt, topLeft = Offset(leftPx + plotW - xt.size.width, topPx + plotH + 6f))
}

private fun DrawScope.drawMarker(m: TextMeasurer, x: Float, y: Float, color: Color, label: String, loX: Float, hiX: Float) {
    drawCircle(color, 4f, Offset(x, y))
    val r = m.measure(label, TextStyle(color = color, fontSize = 9.sp))
    drawText(r, topLeft = Offset((x - r.size.width / 2f).coerceIn(loX, hiX - r.size.width), y - r.size.height - 6f))
}

internal fun nearestIndex(xs: List<Double>, x: Double): Int {
    var best = 0; var bestD = Double.MAX_VALUE
    for (i in xs.indices) { val d = abs(xs[i] - x); if (d < bestD) { bestD = d; best = i } }
    return best
}

/** Compact inline sparkline (no axes), for list rows and stat cards. */
@Composable
fun Sparkline(values: List<Double>, color: Color = Palette.Accent, modifier: Modifier = Modifier, height: Dp = 28.dp) {
    if (values.size < 2) { Box(modifier.fillMaxWidth().height(height)); return }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val mn = values.min(); val mx = values.max(); val span = (mx - mn).takeIf { it > 0 } ?: 1.0
        val path = Path()
        values.indices.forEach { i ->
            val px = (i.toFloat() / (values.size - 1)) * size.width
            val py = (size.height - ((values[i] - mn) / span) * size.height).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, color = color, style = Stroke(width = 2f))
    }
}

/**
 * Interactive bar chart: labelled axes, avg line + max-bar highlight, hover tooltip.
 * [labels] (optional) are per-bar X categories shown on hover.
 */
@Composable
fun InteractiveBarChart(
    values: List<Double>,
    yAxis: AxisSpec,
    modifier: Modifier = Modifier,
    color: Color = Palette.Accent,
    height: Dp = 170.dp,
    labels: List<String>? = null,
    showAvg: Boolean = true,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var hover by remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@BoxWithConstraints
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val leftPx = with(density) { ChartDims.gutterLeft.toPx() }
        val rightPx = with(density) { ChartDims.gutterRight.toPx() }
        val topPx = with(density) { ChartDims.gutterTop.toPx() }
        val bottomPx = with(density) { ChartDims.gutterBottom.toPx() }
        val plotW = (widthPx - leftPx - rightPx).coerceAtLeast(1f)
        val plotH = (heightPx - topPx - bottomPx).coerceAtLeast(1f)

        val mx = values.max().takeIf { it > 0 } ?: 1.0
        val n = values.size
        val gap = plotW * 0.02f / n
        val barW = (plotW - gap * (n + 1)) / n
        val maxI = values.indices.maxByOrNull { values[it] }!!

        fun barX(i: Int) = leftPx + gap + i * (barW + gap)

        Canvas(
            Modifier.fillMaxSize().pointerInput(n) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val pos = e.changes.first().position
                        when (e.type) {
                            PointerEventType.Exit, PointerEventType.Release -> hover = null
                            else -> {
                                val rel = pos.x - leftPx - gap
                                val i = (rel / (barW + gap)).toInt()
                                hover = if (i in 0 until n) i else null
                            }
                        }
                    }
                }
            },
        ) {
            drawAxes(measurer, leftPx, topPx, plotW, plotH, 0.0, n.toDouble(), 0.0, mx, 4, AxisSpec(title = "", format = { "" }), yAxis)
            values.forEachIndexed { i, v ->
                val h = ((v / mx) * plotH).toFloat()
                val x = barX(i)
                val highlight = i == maxI || i == hover
                val top = if (i == maxI) color else color.copy(alpha = if (i == hover) 1f else 0.9f)
                drawRect(
                    Brush.verticalGradient(listOf(top.copy(alpha = if (highlight) 1f else 0.85f), top.copy(alpha = 0.35f)), startY = topPx + plotH - h, endY = topPx + plotH),
                    topLeft = Offset(x, topPx + plotH - h), size = Size(barW.coerceAtLeast(1f), h),
                )
            }
            if (showAvg && n > 1) {
                val avg = values.average()
                val ay = topPx + (1f - (avg / mx).toFloat()) * plotH
                drawLine(Palette.OnMuted.copy(alpha = 0.6f), Offset(leftPx, ay), Offset(leftPx + plotW, ay), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
        }

        hover?.let { i ->
            val cx = barX(i) + barW / 2f
            ChartTooltip(
                title = labels?.getOrNull(i) ?: "#${i + 1}",
                lines = listOf(TipLine("", yAxis.tip(values[i]), color)),
                cursorPx = cx, widthPx = widthPx,
            )
        }
    }
}
