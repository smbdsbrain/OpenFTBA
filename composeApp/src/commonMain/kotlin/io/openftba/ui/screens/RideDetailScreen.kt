package io.openftba.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import io.openftba.ui.share.ShareSpec
import io.openftba.ui.share.ShareStat
import io.openftba.ui.share.exportShareCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.openftba.analytics.Geo
import io.openftba.data.RideDetail
import io.openftba.model.GeoPoint
import io.openftba.settings.UnitSystem
import io.openftba.ui.charts.AxisSpec
import io.openftba.ui.charts.ChartSpan
import io.openftba.ui.charts.InteractiveLineChart
import io.openftba.ui.charts.LineSeries
import io.openftba.ui.charts.nearestIndex
import io.openftba.ui.components.Pill
import io.openftba.ui.components.SectionHeader
import io.openftba.ui.components.StatTile
import io.openftba.ui.format.Format
import io.openftba.ui.i18n.LocalStrings
import io.openftba.ui.info.MetricKey
import io.openftba.ui.theme.Dimens
import io.openftba.ui.theme.Palette
import io.openftba.ui.track3d.Track3DView
import io.openftba.ui.track3d.TrackGradient
import io.openftba.ui.track3d.TrackRamps
import io.openftba.ui.track3d.buildTrackArt

private enum class XAxis { TIME, DISTANCE }

@Composable
fun RideDetailScreen(detail: RideDetail, units: UnitSystem, onBack: () -> Unit) {
    val s = LocalStrings.current
    val ride = detail.ride
    val scroll = rememberScrollState()
    var axis by remember { mutableStateOf(XAxis.DISTANCE) }
    var gradient by remember { mutableStateOf(TrackGradient.SPEED) }

    val series = detail.series
    val xs = if (axis == XAxis.TIME) series.timeMin else series.distanceKm
    val scope = rememberCoroutineScope()
    var shareStatus by remember { mutableStateOf("") }
    // Shared cursor across all per-channel charts (they share the same X domain).
    var cursorX by remember { mutableStateOf<Double?>(null) }
    val xAxis = if (axis == XAxis.TIME) AxisSpec(s.axisTime, s.unitMin) else AxisSpec(s.axisDistance, s.unitKm)

    // Pause / segment-break overlays, in the coordinates of the active X axis.
    val spans = series.pauses.map { p ->
        val (a, b) = if (axis == XAxis.TIME) p.startMin to p.endMin else p.startKm to p.endKm
        ChartSpan(a, b, if (p.kind == "segment") Palette.Record else Palette.OnMuted)
    }

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(Dimens.ScreenPad)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "back",
                tint = Palette.OnMuted,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp),
            )
            Text(formatDateTime(ride.startTime), style = MaterialTheme.typography.headlineMedium, color = Palette.OnBase)
            Spacer(Modifier.weight(1f))
            Pill(
                "📤 " + s.share,
                Palette.Accent,
                Modifier.clickable {
                    scope.launch {
                        shareStatus = withContext(Dispatchers.Default) {
                            exportShareCard(buildShareSpec(detail, units, s, gradient))
                        }
                    }
                },
            )
        }
        if (shareStatus.isNotEmpty()) {
            Text(shareStatus, style = MaterialTheme.typography.labelSmall, color = Palette.OnMuted, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(12.dp))

        // Summary tiles
        SectionHeader(s.rideSummary)
        val tiles = buildList {
            add(STile(s.distance, Format.distance(ride.metrics.distanceMeters, units), Palette.Accent, MetricKey.DISTANCE))
            ride.metrics.intensityTierKey?.let { key ->
                add(STile(s.intensity, s.intensityTier(key), Palette.intensity[key] ?: Palette.Accent, MetricKey.INTENSITY))
            }
            ride.metrics.effortScore?.let { add(STile(s.effort, Format.int(it), info = MetricKey.EFFORT)) }
            add(STile(s.movingTime, Format.duration(ride.metrics.movingTimeSeconds), info = MetricKey.MOVING_TIME))
            add(STile(s.avgSpeed, Format.speed(ride.metrics.avgSpeed, units), info = MetricKey.AVG_SPEED))
            add(STile(s.maxSpeed, Format.speed(ride.metrics.maxSpeed, units), info = MetricKey.MAX_SPEED))
            add(STile(s.elevationGain, Format.elevation(ride.metrics.elevationGain, units), info = MetricKey.ELEVATION_GAIN))
            add(STile(s.longestNonStop, Format.distance(ride.metrics.longestNonStopMeters, units), info = MetricKey.LONGEST_NONSTOP))
            add(STile(s.biggestClimb, Format.elevation(ride.metrics.biggestClimbMeters, units), info = MetricKey.BIGGEST_CLIMB))
            if (ride.channels.heartRate) {
                add(STile(s.avgHeartRate, Format.bpm(ride.metrics.avgHeartRate), info = MetricKey.AVG_HR))
                add(STile(s.maxHeartRate, Format.bpm(ride.metrics.maxHeartRate), info = MetricKey.MAX_HR))
            }
            if (ride.channels.cadence) add(STile(s.avgCadence, Format.rpm(ride.metrics.avgCadence), info = MetricKey.AVG_CADENCE))
            if (ride.channels.power) {
                add(STile(s.avgPower, Format.watts(ride.metrics.avgPower), info = MetricKey.AVG_POWER))
                add(STile(s.maxPower, Format.watts(ride.metrics.maxPower), info = MetricKey.MAX_POWER))
            }
        }
        TileGridLocal(tiles)

        // 3D track — the route suspended in space (deliberately not a map).
        if (series.lat.size >= 2) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // weight() caps the header's fillMaxWidth so the chips stay visible.
                SectionHeader(s.track3d, Modifier.weight(1f))
                AxisChip(s.chartSpeed, gradient == TrackGradient.SPEED) { gradient = TrackGradient.SPEED }
                Spacer(Modifier.width(8.dp))
                if (ride.channels.elevation) {
                    AxisChip(s.chartElevation, gradient == TrackGradient.ELEVATION) { gradient = TrackGradient.ELEVATION }
                }
            }
            Track3DView(
                lat = series.lat, lon = series.lon, ele = series.elevation,
                colorValues = if (gradient == TrackGradient.SPEED) series.speedKmh else series.elevation,
                ramp = TrackRamps.forGradient(gradient),
                cursorIndex = cursorX?.let { c -> nearestIndex(xs, c) },
            )
            Text(s.track3dHint, style = MaterialTheme.typography.labelSmall, color = Palette.OnMuted)
        }

        Spacer(Modifier.height(12.dp))
        // X-axis toggle
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AxisChip(s.axisDistance, axis == XAxis.DISTANCE) { axis = XAxis.DISTANCE }
            AxisChip(s.axisTime, axis == XAxis.TIME) { axis = XAxis.TIME }
        }
        if (spans.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (series.pauses.any { it.kind == "segment" }) LegendSwatch(s.segmentBreak, Palette.Record)
                if (series.pauses.any { it.kind == "stop" }) LegendSwatch(s.pauseStop, Palette.OnMuted)
            }
        }
        Spacer(Modifier.height(8.dp))

        ChartBlock(s.chartSpeed, xs, series.speedKmh, Palette.Speed, xAxis, AxisSpec(s.chartSpeed, s.unitKmh), cursorX, spans, MetricKey.CH_SPEED) { cursorX = it }
        if (ride.channels.elevation) ChartBlock(s.chartElevation, xs, series.elevation, Palette.Elevation, xAxis, AxisSpec(s.chartElevation, s.unitM), cursorX, spans, MetricKey.CH_ELEVATION) { cursorX = it }
        if (ride.channels.heartRate) ChartBlock(s.chartHeartRate, xs, series.heartRate, Palette.HeartRate, xAxis, AxisSpec(s.chartHeartRate, s.unitBpm), cursorX, spans, MetricKey.CH_HR) { cursorX = it }
        if (ride.channels.cadence) ChartBlock(s.chartCadence, xs, series.cadence, Palette.Cadence, xAxis, AxisSpec(s.chartCadence, s.unitRpm), cursorX, spans, MetricKey.CH_CADENCE) { cursorX = it }
        if (ride.channels.power) ChartBlock(s.chartPower, xs, series.power, Palette.Power, xAxis, AxisSpec(s.chartPower, s.unitW), cursorX, spans, MetricKey.CH_POWER) { cursorX = it }

        if (detail.splits.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionHeader(s.splits, info = MetricKey.SPLITS)
            detail.splits.forEach { split ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("#${split.index}", style = MaterialTheme.typography.bodyMedium, color = Palette.OnMuted)
                    Text(Format.durationClock(split.durationSeconds), style = MaterialTheme.typography.bodyMedium, color = Palette.OnBase)
                    Text(Format.speed(split.avgSpeed, units), style = MaterialTheme.typography.bodyMedium, color = Palette.Accent)
                    if (split.avgHeartRate != null) Text(Format.bpm(split.avgHeartRate), style = MaterialTheme.typography.bodyMedium, color = Palette.HeartRate)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ChartBlock(
    title: String, xs: List<Double>, ys: List<Double>, color: Color,
    xAxis: AxisSpec, yAxis: AxisSpec,
    cursorX: Double?, spans: List<ChartSpan>, info: MetricKey?, onCursorChange: (Double?) -> Unit,
) {
    if (ys.size < 2) return
    SectionHeader(title, info = info)
    InteractiveLineChart(
        series = listOf(LineSeries(xs = xs, ys = ys, color = color)),
        xAxis = xAxis, yAxis = yAxis,
        cursorX = cursorX, onCursorChange = onCursorChange,
        showMarkers = true, spans = spans,
    )
}

@Composable
private fun LegendSwatch(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier.padding(end = 6.dp).height(10.dp)
                .clip(RoundedCornerShape(Dimens.RadiusXs)).background(color.copy(alpha = 0.5f))
                .width(14.dp),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.OnMuted)
    }
}

@Composable
private fun AxisChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = if (selected) Palette.Accent else Palette.OnMuted
    Pill(label, c, Modifier.clickable(onClick = onClick))
}

private data class STile(val label: String, val value: String, val accent: Color = Palette.OnBase, val info: MetricKey? = null)

@Composable
private fun TileGridLocal(tiles: List<STile>, columns: Int = 3) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t -> StatTile(t.label, t.value, accent = t.accent, info = t.info, modifier = Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun buildShareSpec(
    detail: RideDetail,
    units: UnitSystem,
    s: io.openftba.ui.i18n.Strings,
    gradient: TrackGradient = TrackGradient.SPEED,
): ShareSpec {
    val m = detail.ride.metrics
    val series = detail.series
    val tierKey = m.intensityTierKey
    // 3D silhouette in the card's art band; the speed sparkline stays as the fallback.
    val trackArt = buildTrackArt(
        lat = series.lat, lon = series.lon, ele = series.elevation,
        colorValues = if (gradient == TrackGradient.SPEED) series.speedKmh else series.elevation,
        ramp = TrackRamps.forGradient(gradient),
        aspect = 920.0 / 230.0,
    )
    val spark = detail.series.speedKmh.let { v ->
        if (v.size <= 140) v else {
            val step = v.size / 140.0
            (0 until 140).map { v[(it * step).toInt()] }
        }
    }
    val km = m.distanceMeters / 1000.0
    val (bigValue, bigUnit) = if (units == UnitSystem.METRIC) {
        oneDp(km) to "km"
    } else {
        oneDp(m.distanceMeters / 1609.344) to "mi"
    }
    val tierTxt = tierKey?.let { " · " + s.intensityTier(it) } ?: ""
    return ShareSpec(
        subtitle = formatDateTime(detail.ride.startTime),
        bigValue = bigValue,
        bigUnit = bigUnit,
        accentArgb = Palette.Accent.toArgb(),
        tierLabel = tierKey?.let { s.intensityTier(it) },
        tierColorArgb = tierKey?.let { (Palette.intensity[it] ?: Palette.Accent).toArgb() },
        spark = spark,
        trackArt = trackArt,
        stats = buildList {
            add(ShareStat(s.movingTime, Format.duration(m.movingTimeSeconds)))
            add(ShareStat(s.avgSpeed, Format.speed(m.avgSpeed, units), Palette.Speed.toArgb()))
            add(ShareStat(s.elevationGain, Format.elevation(m.elevationGain, units), Palette.Elevation.toArgb()))
            m.effortScore?.let { add(ShareStat(s.effort, Format.int(it))) }
        },
        shareText = "🚴 ${Format.distance(m.distanceMeters, units)} · ${Format.duration(m.movingTimeSeconds)} · " +
            "${Format.speed(m.avgSpeed, units)} · ↑${Format.elevation(m.elevationGain, units)}$tierTxt — OpenFTBA",
        fileNameBase = "openftba-ride",
    )
}

private fun oneDp(v: Double): String {
    val r = kotlin.math.round(v * 10) / 10.0
    val i = r.toInt()
    val f = kotlin.math.round((r - i) * 10).toInt()
    return "$i.$f"
}
