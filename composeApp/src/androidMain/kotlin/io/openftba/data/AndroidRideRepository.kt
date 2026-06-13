package io.openftba.data

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import io.openftba.analytics.AnalyzerConfig
import io.openftba.analytics.AthleteProfile
import io.openftba.analytics.ElevationProvider
import io.openftba.analytics.RideAnalyzer
import io.openftba.api.RideDetailDto
import io.openftba.api.SplitDto
import io.openftba.api.buildRideSeries
import io.openftba.api.toSummaryDto
import io.openftba.cache.AndroidRideCache
import io.openftba.cache.configFingerprint
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
 *
 * Analyzed rides are cached to the app's private storage (`ride-cache/`) keyed by file identity
 * + analyzer-config fingerprint, so a relaunch restores unchanged rides instantly (no SAF read /
 * XML parse) and only newly exported tracks are analyzed — shown with a UI progress flag.
 */
class AndroidRideRepository(
    private val context: Context,
    private val store: AndroidSettingsStore,
) : RideRepository {

    private val details = LinkedHashMap<String, RideDetail>()
    private val cache = AndroidRideCache(context)
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
            _state.update { it.copy(rides = emptyList(), loading = false, error = "no-folder", analyzing = false) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }

        val config = buildConfig(settings)
        val configFp = configFingerprint(config)

        // Phase A — restore cache hits without parsing; collect misses.
        val (hits, misses) = withContext(Dispatchers.IO) {
            val files = AndroidOpenTracksParser.trackFiles(context, treeUri)
            val hits = LinkedHashMap<String, RideDetail>()
            val misses = ArrayList<DocumentFile>()
            for (file in files) {
                val dto = cache.load(file, configFp)
                if (dto != null) hits[dto.summary.id] = dto.toRideDetail() else misses.add(file)
            }
            cache.prune(files.mapNotNull { it.name })
            hits to misses
        }
        details.clear()
        details.putAll(hits)
        publish(loading = misses.isNotEmpty(), analyzing = misses.isNotEmpty(), done = 0, total = misses.size)

        // Phase B — analyze new/changed files incrementally.
        var done = 0
        for (file in misses) {
            val detail = withContext(Dispatchers.IO) { analyzeAndCache(file, config, configFp) }
            done++
            if (detail != null) details[detail.ride.id] = detail
            publish(loading = false, analyzing = done < misses.size, done = done, total = misses.size)
        }
        publish(loading = false, analyzing = false, done = 0, total = 0)
    }

    private fun analyzeAndCache(file: DocumentFile, config: AnalyzerConfig, configFp: String): RideDetail? =
        runCatching {
            val track = AndroidOpenTracksParser.parseDocument(context, file) ?: return@runCatching null
            val ride = RideAnalyzer.analyze(track, config) ?: return@runCatching null
            val series = buildRideSeries(track, config)
            val splits = ride.metrics.splits.map {
                SplitDto(it.index, it.distanceMeters, it.durationSeconds, it.avgSpeed, it.elevationGain, it.avgHeartRate)
            }
            cache.store(file, configFp, RideDetailDto(ride.toSummaryDto(), series, splits))
            RideDetail(ride, series, splits, track)
        }.getOrNull()

    private fun publish(loading: Boolean, analyzing: Boolean, done: Int, total: Int) {
        val rides = details.values.map { it.ride }.sortedByDescending { it.startTime }
        _state.update {
            it.copy(rides = rides, loading = loading, error = null, analyzing = analyzing, analyzingDone = done, analyzingTotal = total)
        }
    }

    private fun buildConfig(settings: AppSettings): AnalyzerConfig {
        val demProvider: ElevationProvider? = settings.demFolder
            ?.takeIf { settings.useDemElevation && it.isNotBlank() }
            ?.let { AndroidSrtmElevationProvider(context, it) }
        val disabled = settings.disabledChannels
            .mapNotNull { runCatching { SensorChannel.valueOf(it) }.getOrNull() }.toSet()
        return AnalyzerConfig(
            ignoreElevation = settings.ignoreElevation,
            useDem = settings.useDemElevation && demProvider != null,
            elevationProvider = demProvider,
            disabledChannels = disabled,
            profile = AthleteProfile(
                weightKg = settings.weightKg, sex = settings.sex,
                maxHr = settings.maxHr, restHr = settings.restHr, ftpWatts = settings.ftpWatts,
            ),
        )
    }

    override suspend fun downloadDemTiles(): String {
        val settings = _state.value.settings
        val treeUri = settings.demFolder?.takeIf { it.isNotBlank() }
            ?: return "Set the DEM folder in settings first"
        // Sample coordinates from the loaded rides' (downsampled) series — works for cache-restored
        // rides too (no raw track needed).
        val coords = details.values.asSequence()
            .flatMap { d -> d.series.lat.indices.asSequence().map { d.series.lat[it] to d.series.lon[it] } }
            .toList()
        if (coords.isEmpty()) return "No rides loaded to derive tiles from"
        val tiles = AndroidSrtmDownloader.tilesFor(coords)
        val result = withContext(Dispatchers.IO) {
            AndroidSrtmDownloader.download(context, treeUri, tiles)
        }
        if (settings.useDemElevation) {
            cache.clear()
            rescan()
        }
        return "DEM tiles — downloaded: ${result.downloaded.size}, present: ${result.skipped.size}" +
            if (result.failed.isEmpty()) "" else ", failed: ${result.failed.size}"
    }
}
