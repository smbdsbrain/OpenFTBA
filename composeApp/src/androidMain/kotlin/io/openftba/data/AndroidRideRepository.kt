package io.openftba.data

import android.content.Context
import io.openftba.analytics.AnalyzerConfig
import io.openftba.analytics.AthleteProfile
import io.openftba.analytics.ElevationProvider
import io.openftba.analytics.RideAnalyzer
import io.openftba.api.buildRideSeries
import io.openftba.dem.AndroidSrtmDownloader
import io.openftba.dem.AndroidSrtmElevationProvider
import io.openftba.model.SensorChannel
import io.openftba.parse.AndroidOpenTracksParser
import io.openftba.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Android repository: reads OpenTracks exports from a user-granted folder (SAF tree URI)
 * and analyzes them in-process with the shared engine. The KMZ/KML reader uses
 * [AndroidOpenTracksParser] (XmlPullParser) since Android lacks StAX.
 */
class AndroidRideRepository(
    private val context: Context,
    private val store: AndroidSettingsStore,
) : RideRepository {

    private val details = LinkedHashMap<String, RideDetail>()
    private val _state = MutableStateFlow(RepoState(settings = store.load()))
    override val state: StateFlow<RepoState> = _state.asStateFlow()

    override fun updateSettings(settings: AppSettings) {
        _state.update { it.copy(settings = settings) }
        store.save(settings)
    }

    override fun detail(id: String): RideDetail? = details[id]

    override suspend fun rescan() {
        val settings = _state.value.settings
        val treeUri = settings.watchFolder
        if (treeUri.isNullOrBlank()) {
            details.clear()
            _state.update { it.copy(rides = emptyList(), loading = false, error = "no-folder") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        val result = withContext(Dispatchers.IO) {
            val demProvider: ElevationProvider? = settings.demFolder
                ?.takeIf { settings.useDemElevation && it.isNotBlank() }
                ?.let { AndroidSrtmElevationProvider(context, it) }
            val disabled = settings.disabledChannels
                .mapNotNull { runCatching { SensorChannel.valueOf(it) }.getOrNull() }.toSet()
            val config = AnalyzerConfig(
                ignoreElevation = settings.ignoreElevation,
                useDem = settings.useDemElevation && demProvider != null,
                elevationProvider = demProvider,
                disabledChannels = disabled,
                profile = AthleteProfile(
                    weightKg = settings.weightKg, sex = settings.sex,
                    maxHr = settings.maxHr, restHr = settings.restHr, ftpWatts = settings.ftpWatts,
                ),
            )
            val byId = LinkedHashMap<String, RideDetail>()
            for (track in AndroidOpenTracksParser.parseTree(context, treeUri)) {
                runCatching {
                    val ride = RideAnalyzer.analyze(track, config) ?: return@runCatching
                    val splits = ride.metrics.splits.map {
                        io.openftba.api.SplitDto(it.index, it.distanceMeters, it.durationSeconds, it.avgSpeed, it.elevationGain, it.avgHeartRate)
                    }
                    byId[ride.id] = RideDetail(ride, buildRideSeries(track), splits, track)
                }
            }
            byId
        }
        details.clear()
        details.putAll(result)
        val rides = result.values.map { it.ride }.sortedByDescending { it.startTime }
        _state.update { it.copy(rides = rides, loading = false, error = null) }
    }

    override suspend fun downloadDemTiles(): String {
        val settings = _state.value.settings
        val treeUri = settings.demFolder?.takeIf { it.isNotBlank() }
            ?: return "Set the DEM folder in settings first"
        // Sample coordinates from loaded tracks to determine which 1° tiles are needed.
        val coords = details.values.asSequence()
            .flatMap { (it.track?.allPoints ?: emptyList()).asSequence() }
            .filterIndexed { i, _ -> i % 200 == 0 } // every ~200th point is plenty for tile coverage
            .map { it.lat to it.lon }
            .toList()
        if (coords.isEmpty()) return "No rides loaded to derive tiles from"
        val tiles = AndroidSrtmDownloader.tilesFor(coords)
        val result = withContext(Dispatchers.IO) {
            AndroidSrtmDownloader.download(context, treeUri, tiles)
        }
        if (settings.useDemElevation) rescan()
        return "DEM tiles — downloaded: ${result.downloaded.size}, present: ${result.skipped.size}" +
            if (result.failed.isEmpty()) "" else ", failed: ${result.failed.size}"
    }
}
