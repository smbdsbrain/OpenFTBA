package io.openftba.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.openftba.ui.info.MetricInfoDot
import io.openftba.ui.info.MetricKey
import io.openftba.ui.theme.Dimens
import io.openftba.ui.theme.Palette

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: String? = null, info: MetricKey? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Palette.OnMuted,
            )
            if (info != null) MetricInfoDot(text, info)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelSmall, color = Palette.OnMuted)
    }
}

/** A single metric tile: muted label on top, large value, optional accent + sub line. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    accent: Color = Palette.OnBase,
    record: Boolean = false,
    info: MetricKey? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(Dimens.RadiusCard))
            .background(Palette.Surface)
            .border(1.dp, if (record) Palette.Record.copy(alpha = 0.5f) else Palette.Outline, RoundedCornerShape(Dimens.RadiusCard))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.OnMuted)
            if (info != null) MetricInfoDot(label, info)
        }
        Box(Modifier.padding(top = 6.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (record) Palette.Record else accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (sub != null) {
            Text(sub, style = MaterialTheme.typography.labelSmall, color = Palette.OnMuted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Small colored pill (e.g. channel availability, tier badge later). */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
