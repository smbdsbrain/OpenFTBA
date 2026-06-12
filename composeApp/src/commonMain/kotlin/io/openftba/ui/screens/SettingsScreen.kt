package io.openftba.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.openftba.data.RepoState
import io.openftba.settings.AppSettings
import io.openftba.settings.UnitSystem
import io.openftba.ui.components.Pill
import io.openftba.ui.components.SectionHeader
import io.openftba.ui.i18n.Language
import io.openftba.ui.i18n.LocalStrings
import io.openftba.ui.theme.Dimens
import io.openftba.ui.theme.Palette

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onRescan: () -> Unit,
    state: RepoState,
    onDownloadDem: suspend () -> String = { "" },
    onPickFolder: (() -> Unit)? = null,
    onPickDemFolder: (() -> Unit)? = null,
) {
    val s = LocalStrings.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var demStatus by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(Dimens.ScreenPad)) {
        Text(s.navSettings, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = Palette.OnBase)
        Spacer(Modifier.height(16.dp))

        SectionHeader(s.settingsWatchFolder)
        when {
            !state.foldersEditable -> ReadOnlyPath(settings.watchFolder, s.settingsServerManaged)
            onPickFolder != null -> {
                // Android: choose the folder via the system picker (SAF), not a typed path.
                Button(onClick = onPickFolder) { Text(s.settingsWatchFolder) }
                Text(
                    settings.watchFolder ?: s.settingsWatchFolderHint,
                    style = MaterialTheme.typography.labelSmall, color = Palette.OnMuted,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            else -> OutlinedTextField(
                value = settings.watchFolder.orEmpty(),
                onValueChange = { onChange(settings.copy(watchFolder = it.ifBlank { null })) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text(s.settingsWatchFolderHint, color = Palette.OnMuted) },
            )
        }
        if (state.error == "no-folder") {
            Text(s.noRidesHint, style = MaterialTheme.typography.labelSmall, color = Palette.Danger)
        }

        Spacer(Modifier.height(8.dp))
        SectionHeader(s.settingsDemFolder)
        when {
            !state.foldersEditable -> ReadOnlyPath(settings.demFolder, s.settingsServerManaged)
            onPickDemFolder != null -> {
                // Android: choose the folder via the system picker (SAF), not a typed path.
                Button(onClick = onPickDemFolder) { Text(s.settingsDemFolder) }
                Text(
                    settings.demFolder ?: s.settingsDemFolderHint,
                    style = MaterialTheme.typography.labelSmall, color = Palette.OnMuted,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            else -> OutlinedTextField(
                value = settings.demFolder.orEmpty(),
                onValueChange = { onChange(settings.copy(demFolder = it.ifBlank { null })) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text(s.settingsDemFolderHint, color = Palette.OnMuted) },
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(s.settingsUseDem, style = MaterialTheme.typography.bodyMedium, color = Palette.OnBase)
            Switch(checked = settings.useDemElevation, onCheckedChange = { onChange(settings.copy(useDemElevation = it)) })
        }
        // DEM tile download is a desktop-only networked action; the server's tiles are fixed.
        if (state.foldersEditable) {
            Button(
                onClick = { scope.launch { demStatus = s.downloading; demStatus = onDownloadDem() } },
                enabled = settings.demFolder != null,
            ) { Text(s.settingsDownloadDem) }
            if (demStatus.isNotEmpty()) {
                Text(demStatus, style = MaterialTheme.typography.labelSmall, color = Palette.OnMuted, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(s.settingsIgnoreElevation, style = MaterialTheme.typography.bodyMedium, color = Palette.OnBase)
            Switch(checked = settings.ignoreElevation, onCheckedChange = { onChange(settings.copy(ignoreElevation = it)) })
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader(s.settingsUnits)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill(s.settingsUnitsMetric, settings.units == UnitSystem.METRIC) { onChange(settings.copy(units = UnitSystem.METRIC)) }
            ChoicePill(s.settingsUnitsImperial, settings.units == UnitSystem.IMPERIAL) { onChange(settings.copy(units = UnitSystem.IMPERIAL)) }
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader(s.settingsLanguage)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Language.entries.forEach { lang ->
                ChoicePill(lang.displayName, settings.languageCode == lang.code) {
                    onChange(settings.copy(languageCode = lang.code))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader(s.settingsSensors)
        Text(s.settingsSensorsHint, style = MaterialTheme.typography.labelSmall, color = Palette.OnMuted)
        val sensorRows = listOf(
            io.openftba.model.SensorChannel.HEART_RATE to s.chartHeartRate,
            io.openftba.model.SensorChannel.CADENCE to s.chartCadence,
            io.openftba.model.SensorChannel.POWER to s.chartPower,
            io.openftba.model.SensorChannel.SPEED to s.chartSpeed,
            io.openftba.model.SensorChannel.ELEVATION to s.chartElevation,
        )
        sensorRows.forEach { (ch, label) ->
            val name = ch.name
            val trusted = name !in settings.disabledChannels
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.OnBase)
                Switch(checked = trusted, onCheckedChange = { on ->
                    val set = settings.disabledChannels.toMutableSet()
                    if (on) set.remove(name) else set.add(name)
                    onChange(settings.copy(disabledChannels = set))
                })
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader(s.settingsProfile)
        NumberField(s.settingsWeight, settings.weightKg?.toString()) { v ->
            onChange(settings.copy(weightKg = v?.toDoubleOrNull()))
        }
        NumberField(s.settingsMaxHr, settings.maxHr?.toString()) { v ->
            onChange(settings.copy(maxHr = v?.toIntOrNull()))
        }
        NumberField(s.settingsFtp, settings.ftpWatts?.toString()) { v ->
            onChange(settings.copy(ftpWatts = v?.toIntOrNull()))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ReadOnlyPath(path: String?, hint: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            path ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = if (path != null) Palette.OnBase else Palette.OnMuted,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(hint, style = MaterialTheme.typography.labelSmall, color = Palette.OnMuted, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun NumberField(label: String, value: String?, onChange: (String?) -> Unit) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = { onChange(it.ifBlank { null }) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = true,
    )
}

@Composable
private fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Pill(label, if (selected) Palette.Accent else Palette.OnMuted, Modifier.clickable(onClick = onClick))
}
