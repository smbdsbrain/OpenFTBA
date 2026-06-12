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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.openftba.model.Ride
import io.openftba.settings.UnitSystem
import io.openftba.ui.components.Pill
import io.openftba.ui.format.Format
import io.openftba.ui.i18n.LocalStrings
import io.openftba.ui.theme.Palette
import io.openftba.data.RepoState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun RideListScreen(
    state: RepoState,
    listState: LazyListState = rememberLazyListState(),
    onOpenRide: (String) -> Unit,
) {
    val s = LocalStrings.current
    if (state.rides.isEmpty()) {
        EmptyState(title = s.noRidesTitle, hint = s.noRidesHint)
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(s.navRides, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = Palette.OnBase)
            Spacer(Modifier.height(8.dp))
        }
        items(state.rides) { ride ->
            RideRow(ride, state.settings.units) { onOpenRide(ride.id) }
        }
    }
}

@Composable
private fun RideRow(ride: Ride, units: UnitSystem, onClick: () -> Unit) {
    val s = LocalStrings.current
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(formatDateTime(ride.startTime), style = MaterialTheme.typography.titleMedium, color = Palette.OnBase)
            Text(Format.distance(ride.metrics.distanceMeters, units), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Palette.Accent)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            MiniMetric(s.duration, Format.duration(ride.metrics.movingTimeSeconds))
            MiniMetric(s.avgSpeed, Format.speed(ride.metrics.avgSpeed, units))
            MiniMetric(s.elevationGain, Format.elevation(ride.metrics.elevationGain, units))
            if (ride.channels.heartRate) MiniMetric(s.avgHeartRate, Format.bpm(ride.metrics.avgHeartRate))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ride.metrics.intensityTierKey?.let { key ->
                val c = Palette.intensity[key] ?: Palette.Accent
                val suffix = if (ride.metrics.intensitySource == io.openftba.analytics.IntensitySource.SPEED) " ~" else ""
                Pill(s.intensityTier(key) + suffix, c)
            }
            if (ride.channels.heartRate) Pill("HR", Palette.HeartRate)
            if (ride.channels.cadence) Pill("CAD", Palette.Cadence)
            if (ride.channels.power) Pill("PWR", Palette.Power)
            if (ride.channels.elevation) Pill("ELEV", Palette.Elevation)
            if (ride.channels.temperature) Pill("TEMP", Palette.Record)
        }
    }
}

@Composable
private fun MiniMetric(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.OnMuted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = Palette.OnBase)
    }
}

internal fun formatDateTime(instant: kotlinx.datetime.Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    fun p(n: Int) = if (n < 10) "0$n" else n.toString()
    return "${dt.year}-${p(dt.monthNumber)}-${p(dt.dayOfMonth)} ${p(dt.hour)}:${p(dt.minute)}"
}
