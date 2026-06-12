package io.openftba.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.openftba.data.RepoState
import io.openftba.settings.UnitSystem
import io.openftba.analytics.TrainingLoad
import io.openftba.ui.charts.AxisSpec
import io.openftba.ui.charts.CalendarHeatmap
import io.openftba.ui.charts.DonutChart
import io.openftba.ui.charts.DonutSegment
import io.openftba.ui.charts.InteractiveBarChart
import io.openftba.ui.charts.InteractiveLineChart
import io.openftba.ui.charts.LineSeries
import io.openftba.ui.components.SectionHeader
import io.openftba.ui.components.StatTile
import io.openftba.ui.format.Format
import io.openftba.ui.i18n.LocalStrings
import io.openftba.ui.info.MetricInfoDot
import io.openftba.ui.info.MetricKey
import io.openftba.ui.LocalWidthClass
import io.openftba.ui.WidthClass
import io.openftba.ui.overview.OverviewStats
import io.openftba.ui.theme.Dimens
import io.openftba.ui.theme.Palette

@Composable
fun OverviewScreen(state: RepoState) {
    val s = LocalStrings.current
    val units = state.settings.units

    if (state.rides.isEmpty()) {
        EmptyState(title = s.noRidesTitle, hint = s.noRidesHint)
        return
    }

    val stats = OverviewStats.from(state.rides)
    val scroll = rememberScrollState()
    val profile = io.openftba.analytics.AthleteProfile(
        weightKg = state.settings.weightKg, sex = state.settings.sex,
        maxHr = state.settings.maxHr, restHr = state.settings.restHr, ftpWatts = state.settings.ftpWatts,
    )
    val fitness = io.openftba.analytics.FitnessScale.evaluate(profile, state.rides)

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(Dimens.ScreenPad)) {
        Text(s.navOverview, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = Palette.OnBase)
        Spacer(Modifier.height(12.dp))
        AthleteBadge(fitness, units)
        Spacer(Modifier.height(4.dp))

        // Totals
        TileGrid(
            listOf(
                Tile(s.totalRides, stats.totalRides.toString(), accent = Palette.Accent, info = MetricKey.TOTAL_RIDES),
                Tile(s.totalDistance, Format.distance(stats.totalDistanceMeters, units), info = MetricKey.TOTAL_DISTANCE),
                Tile(s.totalTime, Format.duration(stats.totalMovingSeconds), info = MetricKey.TOTAL_TIME),
                Tile(s.totalElevation, Format.elevation(stats.totalElevationGain, units), info = MetricKey.TOTAL_ELEVATION),
                Tile(s.avgRideDistance, Format.distance(stats.avgRideDistanceMeters, units), info = MetricKey.AVG_RIDE_DISTANCE),
            ),
        )

        Spacer(Modifier.height(8.dp))
        SectionHeader(s.records)
        TileGrid(
            buildList {
                stats.maxSpeed?.let { add(Tile(s.maxSpeed, Format.speed(it.value, units), record = true, info = MetricKey.MAX_SPEED)) }
                stats.longestNonStop?.let { add(Tile(s.longestNonStop, Format.distance(it.value, units), record = true, info = MetricKey.LONGEST_NONSTOP)) }
                stats.longestRide?.let { add(Tile("${s.distance} (max)", Format.distance(it.value, units), record = true, info = MetricKey.LONGEST_RIDE)) }
                stats.biggestClimb?.let { add(Tile(s.biggestClimb, Format.elevation(it.value, units), record = true, info = MetricKey.BIGGEST_CLIMB)) }
                stats.maxRideElevation?.let { add(Tile("${s.elevationGain} (max)", Format.elevation(it.value, units), record = true, info = MetricKey.MAX_RIDE_ELEVATION)) }
                stats.bestAvgSpeed?.let { add(Tile("${s.avgSpeed} (max)", Format.speed(it.value, units), record = true, info = MetricKey.BEST_AVG_SPEED)) }
            },
        )

        Spacer(Modifier.height(12.dp))
        SectionHeader("${s.navRides} · last 26 weeks")
        CalendarHeatmap(weeks = stats.heatmapCells, distanceLabel = { Format.distance(it, units) })

        // Training load: Fitness (CTL) / Fatigue (ATL) / Form (TSB). X = calendar day.
        val load = TrainingLoad.compute(state.rides)
        if (load.size >= 2) {
            Spacer(Modifier.height(12.dp))
            SectionHeader(s.loadCurve, info = MetricKey.LOAD_CURVE)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(s.fitness, Palette.Accent, MetricKey.FITNESS)
                LegendDot(s.fatigue, Palette.Power, MetricKey.FATIGUE)
                LegendDot(s.form, Palette.Elevation, MetricKey.FORM)
            }
            val xs = load.map { it.epochDay.toDouble() }
            val dayAxis = AxisSpec("", "", { d -> kotlinx.datetime.LocalDate.fromEpochDays(d.toInt()).toString() })
            InteractiveLineChart(
                series = listOf(
                    LineSeries(xs, load.map { it.ctl }, Palette.Accent, fillUnder = false),
                    LineSeries(xs, load.map { it.atl }, Palette.Power, fillUnder = false),
                    LineSeries(xs, load.map { it.tsb }, Palette.Elevation, fillUnder = false),
                ),
                xAxis = dayAxis, yAxis = AxisSpec(s.load),
                seriesLabels = listOf(s.fitness, s.fatigue, s.form),
                showMarkers = false, height = 190.dp,
            )
        }

        // Intensity tier distribution (donut).
        val tierOrder = listOf("recovery", "endurance", "tempo", "race", "threshold_burn")
        val tierLabels = mapOf(
            "recovery" to s.tierRecovery, "endurance" to s.tierEndurance, "tempo" to s.tierTempo,
            "race" to s.tierRace, "threshold_burn" to s.tierThresholdBurn,
        )
        val counts = state.rides.mapNotNull { it.metrics.intensityTierKey }.groupingBy { it }.eachCount()
        val donutSegments = tierOrder.filter { counts[it] != null }.map {
            DonutSegment(counts.getValue(it).toDouble(), Palette.intensity[it] ?: Palette.Accent, tierLabels[it] ?: it)
        }
        if (donutSegments.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionHeader(s.tierDist, info = MetricKey.INTENSITY)
            DonutChart(donutSegments)
        }

        // Distance histogram (5 km / 5 mi buckets). Y = ride count.
        Spacer(Modifier.height(12.dp))
        SectionHeader(s.distHist, info = MetricKey.DIST_HIST)
        val histo = distanceHistogram(state.rides, units)
        val bucket = if (units == UnitSystem.METRIC) 5 else 5
        val distUnit = if (units == UnitSystem.METRIC) s.unitKm else "mi"
        InteractiveBarChart(
            values = histo,
            yAxis = AxisSpec(s.distHist),
            labels = histo.indices.map { "${it * bucket}–${(it + 1) * bucket} $distUnit" },
        )

        Spacer(Modifier.height(12.dp))
        SectionHeader("${s.distance} · ${s.navRides}", info = MetricKey.RECENT_DIST)
        InteractiveBarChart(
            values = stats.recentDistances.map { if (units == UnitSystem.METRIC) it / 1000.0 else it / 1609.344 },
            yAxis = AxisSpec(s.distance, distUnit),
        )
        Spacer(Modifier.height(24.dp))
    }
}

private data class Tile(val label: String, val value: String, val record: Boolean = false, val accent: androidx.compose.ui.graphics.Color = Palette.OnBase, val info: MetricKey? = null)

@Composable
private fun TileGrid(tiles: List<Tile>) {
    val columns = if (LocalWidthClass.current == WidthClass.Compact) 2 else 3
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    StatTile(
                        label = t.label,
                        value = t.value,
                        accent = t.accent,
                        record = t.record,
                        info = t.info,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun distanceHistogram(rides: List<io.openftba.model.Ride>, units: UnitSystem): List<Double> {
    if (rides.isEmpty()) return emptyList()
    val bucket = if (units == UnitSystem.METRIC) 5000.0 else 8046.72 // 5 km / 5 mi
    val maxDist = rides.maxOf { it.metrics.distanceMeters }
    val n = kotlin.math.ceil(maxDist / bucket).toInt().coerceAtLeast(1)
    val bins = DoubleArray(n)
    rides.forEach { bins[minOf(n - 1, (it.metrics.distanceMeters / bucket).toInt())] += 1.0 }
    return bins.toList()
}

@Composable
private fun LegendDot(label: String, color: androidx.compose.ui.graphics.Color, info: MetricKey? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(Dimens.RadiusXs)).background(color))
        Text(" $label", style = MaterialTheme.typography.labelSmall, color = Palette.OnMuted)
        if (info != null) MetricInfoDot(label, info, Modifier.padding(start = 2.dp))
    }
}

@Composable
private fun AthleteBadge(fitness: io.openftba.analytics.FitnessResult, units: io.openftba.settings.UnitSystem) {
    val s = LocalStrings.current
    val key = fitness.tier.name
    val color = Palette.tier[key] ?: Palette.OnMuted
    val basis = when (fitness.basis) {
        io.openftba.analytics.TierBasis.POWER_WKG -> s.basisPower
        io.openftba.analytics.TierBasis.SPEED_PROXY -> s.basisSpeed
        io.openftba.analytics.TierBasis.NONE -> ""
    }
    val valueText = fitness.value?.let {
        if (fitness.basis == io.openftba.analytics.TierBasis.POWER_WKG)
            "${(kotlin.math.round(it * 10) / 10.0)} W/kg"
        else Format.speed(it / 3.6, units)
    } ?: ""
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Dimens.RadiusCard)).background(Palette.Surface)
            .border(1.dp, Palette.Outline, RoundedCornerShape(Dimens.RadiusCard)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(Dimens.RadiusCard)).border(2.dp, color, RoundedCornerShape(Dimens.RadiusCard)),
            contentAlignment = Alignment.Center,
        ) {
            Text(key, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = color)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.athleteLevel.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.OnMuted)
                MetricInfoDot(s.athleteLevel, MetricKey.ATHLETE)
            }
            // Current figure, prominent and labeled with what it is (W/kg vs speed proxy).
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    valueText.ifEmpty { "—" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (valueText.isNotEmpty()) Palette.OnBase else Palette.OnMuted,
                )
                if (basis.isNotEmpty()) Text(basis, style = MaterialTheme.typography.labelSmall, color = Palette.OnMuted, modifier = Modifier.padding(bottom = 2.dp))
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Palette.SurfaceHigh)) {
                Box(Modifier.fillMaxWidth(fitness.progressToNext.toFloat()).height(6.dp).clip(RoundedCornerShape(50)).background(color))
            }
        }
    }
}

@Composable
fun EmptyState(title: String, hint: String) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Palette.OnBase)
        Spacer(Modifier.height(8.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = Palette.OnMuted)
    }
}
