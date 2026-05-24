package io.openftba.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.openftba.ui.i18n.LocalStrings
import io.openftba.ui.i18n.Strings
import io.openftba.ui.theme.Palette

/** Every metric that can show an info footnote. */
enum class MetricKey {
    DISTANCE, MOVING_TIME, AVG_SPEED, MAX_SPEED, ELEVATION_GAIN, LONGEST_NONSTOP, BIGGEST_CLIMB,
    AVG_HR, MAX_HR, AVG_CADENCE, AVG_POWER, MAX_POWER,
    EFFORT, INTENSITY, ATHLETE, FITNESS, FATIGUE, FORM, LOAD_CURVE,
    TOTAL_RIDES, TOTAL_DISTANCE, TOTAL_TIME, TOTAL_ELEVATION, AVG_RIDE_DISTANCE,
    LONGEST_RIDE, MAX_RIDE_ELEVATION, BEST_AVG_SPEED,
    DIST_HIST, RECENT_DIST, SPLITS,
    CH_SPEED, CH_ELEVATION, CH_HR, CH_CADENCE, CH_POWER,
}

/** Where a metric's data comes from — drives a colored source pill. */
enum class MetricSourceKind { GPS, DEVICE, DEM, CALC, APPROX }

/** One row in a categorical metric's scale (e.g. an intensity tier or athlete band). */
data class MetricLevel(val label: String, val range: String, val color: Color? = null)

/** Localized, structured explanation of a metric, shown in the info popup. */
data class MetricInfo(
    val description: String,
    val source: MetricSourceKind? = null,
    val formula: String? = null,
    val levels: List<MetricLevel> = emptyList(),
    /** Optional external reference (opened in the user's browser on tap). */
    val link: String? = null,
)

// Reputable external references (TrainingPeaks — Coggan/Allen training-load science).
private const val LINK_TSS = "https://www.trainingpeaks.com/learn/articles/normalized-power-intensity-factor-training-stress/"
private const val LINK_PMC = "https://www.trainingpeaks.com/coach-blog/a-coachs-guide-to-atl-ctl-tsb/"
private const val LINK_WKG = "https://www.trainingpeaks.com/blog/power-profiling/"

private fun intensityLevels(s: Strings) = listOf(
    MetricLevel(s.tierRecovery, "IF < 0.65", Palette.intensity["recovery"]),
    MetricLevel(s.tierEndurance, "0.65 - 0.75", Palette.intensity["endurance"]),
    MetricLevel(s.tierTempo, "0.75 - 0.85", Palette.intensity["tempo"]),
    MetricLevel(s.tierRace, "0.85 - 0.95", Palette.intensity["race"]),
    MetricLevel(s.tierThresholdBurn, ">= 0.95", Palette.intensity["threshold_burn"]),
)

private fun athleteLevels() = listOf(
    MetricLevel("S", ">= 5.15", Palette.tier["S"]),
    MetricLevel("A", "4.3 - 5.15", Palette.tier["A"]),
    MetricLevel("B", "3.7 - 4.3", Palette.tier["B"]),
    MetricLevel("C", "2.9 - 3.7", Palette.tier["C"]),
    MetricLevel("D", "2.2 - 2.9", Palette.tier["D"]),
    MetricLevel("E", "1.5 - 2.2", Palette.tier["E"]),
    MetricLevel("F", "< 1.5", Palette.tier["F"]),
)

private fun tsbLevels(s: Strings) = listOf(
    MetricLevel(s.tsbFresh, "+5 .. +25", Palette.Accent),
    MetricLevel(s.tsbNeutral, "-10 .. +5", Palette.Speed),
    MetricLevel(s.tsbProductive, "-30 .. -10", Palette.Record),
    MetricLevel(s.tsbHigh, "< -30", Palette.Danger),
)

/** Assembles the localized [MetricInfo] for a metric. Formulas are language-neutral ASCII. */
object MetricCatalog {
    fun infoFor(key: MetricKey, s: Strings): MetricInfo {
        val calc = MetricSourceKind.CALC
        return when (key) {
            MetricKey.DISTANCE -> MetricInfo(s.metricDesc("distance"), MetricSourceKind.GPS, "Σ haversine(p[i], p[i+1])")
            MetricKey.MOVING_TIME -> MetricInfo(s.metricDesc("movingTime"), calc, "Σ Δt where v >= 3.6 km/h")
            MetricKey.AVG_SPEED -> MetricInfo(s.metricDesc("avgSpeed"), calc, "distance / moving time")
            MetricKey.MAX_SPEED -> MetricInfo(s.metricDesc("maxSpeed"), MetricSourceKind.GPS, "max(v), capped at 108 km/h")
            MetricKey.ELEVATION_GAIN -> MetricInfo(s.metricDesc("elevationGain"), MetricSourceKind.DEM, "Σ Δh where |Δh| > 2 m")
            MetricKey.LONGEST_NONSTOP -> MetricInfo(s.metricDesc("longestNonStop"), calc, "max run between stops")
            MetricKey.BIGGEST_CLIMB -> MetricInfo(s.metricDesc("biggestClimb"), MetricSourceKind.DEM, "max continuous gain in a segment")
            MetricKey.AVG_HR -> MetricInfo(s.metricDesc("avgHr"), MetricSourceKind.DEVICE)
            MetricKey.MAX_HR -> MetricInfo(s.metricDesc("maxHr"), MetricSourceKind.DEVICE)
            MetricKey.AVG_CADENCE -> MetricInfo(s.metricDesc("avgCadence"), MetricSourceKind.DEVICE)
            MetricKey.AVG_POWER -> MetricInfo(s.metricDesc("avgPower"), MetricSourceKind.DEVICE)
            MetricKey.MAX_POWER -> MetricInfo(s.metricDesc("maxPower"), MetricSourceKind.DEVICE)
            MetricKey.EFFORT -> MetricInfo(s.metricDesc("effort"), calc, "effort = hours x IF^2 x 100", link = LINK_TSS)
            MetricKey.INTENSITY -> MetricInfo(
                s.metricDesc("intensity"), calc, "IF = NP/FTP  (HR: %HRR/0.85;  speed: v/vref)",
                intensityLevels(s), link = LINK_TSS,
            )
            MetricKey.ATHLETE -> MetricInfo(s.metricDesc("athlete"), calc, "W/kg = FTP / weight", athleteLevels(), link = LINK_WKG)
            MetricKey.FITNESS -> MetricInfo(s.metricDesc("fitness"), calc, "CTL = CTL_prev + (load - CTL_prev) / 42", link = LINK_PMC)
            MetricKey.FATIGUE -> MetricInfo(s.metricDesc("fatigue"), calc, "ATL = ATL_prev + (load - ATL_prev) / 7", link = LINK_PMC)
            MetricKey.FORM -> MetricInfo(s.metricDesc("form"), calc, "TSB = CTL - ATL", tsbLevels(s), link = LINK_PMC)
            MetricKey.LOAD_CURVE -> MetricInfo(s.metricDesc("loadCurve"), calc, "TSB = CTL - ATL", tsbLevels(s), link = LINK_PMC)
            MetricKey.TOTAL_RIDES -> MetricInfo(s.metricDesc("totalRides"), calc)
            MetricKey.TOTAL_DISTANCE -> MetricInfo(s.metricDesc("totalDistance"), MetricSourceKind.GPS, "Σ ride distance")
            MetricKey.TOTAL_TIME -> MetricInfo(s.metricDesc("totalTime"), calc, "Σ moving time")
            MetricKey.TOTAL_ELEVATION -> MetricInfo(s.metricDesc("totalElevation"), MetricSourceKind.DEM, "Σ elevation gain")
            MetricKey.AVG_RIDE_DISTANCE -> MetricInfo(s.metricDesc("avgRideDistance"), calc, "total distance / rides")
            MetricKey.LONGEST_RIDE -> MetricInfo(s.metricDesc("longestRide"), MetricSourceKind.GPS)
            MetricKey.MAX_RIDE_ELEVATION -> MetricInfo(s.metricDesc("maxRideElevation"), MetricSourceKind.DEM)
            MetricKey.BEST_AVG_SPEED -> MetricInfo(s.metricDesc("bestAvgSpeed"), calc)
            MetricKey.DIST_HIST -> MetricInfo(s.metricDesc("distHist"), calc)
            MetricKey.RECENT_DIST -> MetricInfo(s.metricDesc("recentDist"), MetricSourceKind.GPS)
            MetricKey.SPLITS -> MetricInfo(s.metricDesc("splits"), calc)
            MetricKey.CH_SPEED -> MetricInfo(s.metricDesc("chSpeed"), MetricSourceKind.GPS)
            MetricKey.CH_ELEVATION -> MetricInfo(s.metricDesc("chElevation"), MetricSourceKind.DEM)
            MetricKey.CH_HR -> MetricInfo(s.metricDesc("chHr"), MetricSourceKind.DEVICE)
            MetricKey.CH_CADENCE -> MetricInfo(s.metricDesc("chCadence"), MetricSourceKind.DEVICE)
            MetricKey.CH_POWER -> MetricInfo(s.metricDesc("chPower"), MetricSourceKind.DEVICE)
        }
    }
}

private fun sourceLabel(kind: MetricSourceKind, s: Strings) = when (kind) {
    MetricSourceKind.GPS -> s.srcGps
    MetricSourceKind.DEVICE -> s.srcDevice
    MetricSourceKind.DEM -> s.srcDem
    MetricSourceKind.CALC -> s.srcCalc
    MetricSourceKind.APPROX -> s.srcApprox
}

private fun sourceColor(kind: MetricSourceKind) = when (kind) {
    MetricSourceKind.GPS -> Palette.Speed
    MetricSourceKind.DEVICE -> Palette.Accent
    MetricSourceKind.DEM -> Palette.Elevation
    MetricSourceKind.CALC -> Palette.OnMuted
    MetricSourceKind.APPROX -> Palette.Record
}

/**
 * A small "i" footnote next to a metric label. Hover shows the popup; clicking pins it open
 * (so you can read it / follow the link) until you click the dot again or click away. The
 * glyph is a drawn ASCII "i" so it renders in every browser font.
 */
@Composable
fun MetricInfoDot(title: String, key: MetricKey, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val info = remember(key, s) { MetricCatalog.infoFor(key, s) }
    var hovered by remember { mutableStateOf(false) }
    var pinned by remember { mutableStateOf(false) }
    val show = hovered || pinned
    Box(modifier) {
        Box(
            Modifier
                .size(15.dp)
                .clip(CircleShape)
                .border(1.dp, if (show) Palette.Accent else Palette.OnMuted, CircleShape)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            when (awaitPointerEvent().type) {
                                PointerEventType.Enter -> hovered = true
                                PointerEventType.Exit -> hovered = false
                                else -> {}
                            }
                        }
                    }
                }
                .clickable { pinned = !pinned },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "i",
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = if (show) Palette.Accent else Palette.OnMuted,
            )
        }
        if (show) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, 38),
                onDismissRequest = { pinned = false },
                properties = PopupProperties(focusable = pinned),
            ) {
                InfoCard(title, info, s)
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, info: MetricInfo, s: Strings) {
    Column(
        Modifier
            .widthIn(min = 230.dp, max = 320.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.SurfaceHigh)
            .border(1.dp, Palette.Outline, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Palette.OnBase)
            info.source?.let { SourcePill(it, s) }
        }
        Text(info.description, style = MaterialTheme.typography.bodySmall, color = Palette.OnMuted)
        info.formula?.let { f ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(s.infoFormulaLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.OnMuted)
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Palette.Surface).padding(horizontal = 8.dp, vertical = 5.dp)) {
                    Text(f, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp), color = Palette.OnBase)
                }
            }
        }
        if (info.levels.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.infoScaleLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.OnMuted)
                info.levels.forEach { lv ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(lv.color ?: Palette.OnMuted))
                        Text(lv.label, style = MaterialTheme.typography.bodySmall, color = Palette.OnBase)
                        Spacer(Modifier.weight(1f))
                        Text(lv.range, style = MaterialTheme.typography.bodySmall, color = Palette.OnMuted)
                    }
                }
            }
        }
        info.link?.let { url ->
            val uri = LocalUriHandler.current
            Text(
                s.infoLearnMore,
                style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                color = Palette.Accent,
                modifier = Modifier.clickable { uri.openUri(url) },
            )
        }
    }
}

@Composable
private fun SourcePill(kind: MetricSourceKind, s: Strings) {
    val c = sourceColor(kind)
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.16f))
            .border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(sourceLabel(kind, s), style = MaterialTheme.typography.labelSmall, color = c)
    }
}
