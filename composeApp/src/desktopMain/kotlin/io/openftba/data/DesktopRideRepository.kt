package io.openftba.data

import io.openftba.analytics.AnalyzerConfig
import io.openftba.analytics.AthleteProfile
import io.openftba.analytics.ElevationProvider
import io.openftba.analytics.RideAnalyzer
import io.openftba.api.RideDetailDto
import io.openftba.api.SplitDto
import io.openftba.api.buildRideSeries
import io.openftba.api.toSummaryDto
import io.openftba.cache.JvmRideCache
import io.openftba.cache.configFingerprint
import io.openftba.dem.SrtmDownloader
import io.openftba.dem.SrtmElevationProvider
import io.openftba.parse.OpenTracksKmlParser
import io.openftba.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Desktop repository: reads the OpenTracks export folder, parses each track in-process,
 * computes metrics, dedups by `trackid`, and exposes the result as state.
 *
 * Analyzed rides are cached to `~/.openftba/cache/` keyed by file identity + a fingerprint of
 * the analyzer config, so on later launches unchanged files are restored from the cache instead
 * of being re-parsed and re-analyzed. New/changed files are analyzed incrementally with a UI
 * progress flag.
 */
class DesktopRideRepository(
    private val configFile: File = File(System.getProperty("user.home"), ".openftba/settings.json"),
) : RideRepository {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val details = LinkedHashMap<String, RideDetail>()
    private val cache = JvmRideCache(File(configFile.parentFile ?: File("."), "cache"))

    private val _state = MutableStateFlow(RepoState(settings = loadSettings()))
    override val state: StateFlow<RepoState> = _state.asStateFlow()

    override fun updateSettings(settings: AppSettings) {
        _state.update { it.copy(settings = settings) }
        saveSettings(settings)
    }

    override fun detail(id: String): RideDetail? = details[id]

    override suspend fun rescan() {
        val settings = _state.value.settings
        val folder = settings.watchFolder?.let { File(it) }
        if (folder == null || !folder.isDirectory) {
            details.clear()
            _state.update { it.copy(rides = emptyList(), loading = false, error = "no-folder", analyzing = false) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }

        val config = buildConfig(settings)
        val configFp = configFingerprint(config)

        // Phase A — restore cache hits without parsing; collect misses.
        val (hits, misses) = withContext(Dispatchers.IO) {
            val files = folder.listFiles { f ->
                f.isFile && f.name.lowercase().let { it.endsWith(".kmz") || it.endsWith(".kml") || it.endsWith(".gpx") }
            }?.sortedBy { it.name } ?: emptyList()
            val hits = LinkedHashMap<String, RideDetail>()
            val misses = ArrayList<File>()
            for (file in files) {
                val dto = cache.load(file, configFp)
                if (dto != null) hits[dto.summary.id] = dto.toRideDetail() else misses.add(file)
            }
            cache.prune(files.map { it.name })
            hits to misses
        }
        details.clear()
        details.putAll(hits)
        publish(loading = misses.isNotEmpty(), analyzing = misses.isNotEmpty(), done = 0, total = misses.size)

        // Phase B — analyze new/changed files incrementally, updating state as each lands.
        var done = 0
        for (file in misses) {
            val detail = withContext(Dispatchers.IO) { analyzeAndCache(file, config, configFp) }
            done++
            if (detail != null) details[detail.ride.id] = detail
            publish(loading = false, analyzing = done < misses.size, done = done, total = misses.size)
        }
        publish(loading = false, analyzing = false, done = 0, total = 0)
    }

    /** Parse + analyze a single file, persist the result to the cache, return the domain detail. */
    private fun analyzeAndCache(file: File, config: AnalyzerConfig, configFp: String): RideDetail? =
        runCatching {
            val track = OpenTracksKmlParser.parseFile(file)
            val ride = RideAnalyzer.analyze(track, config) ?: return@runCatching null
            val series = buildRideSeries(track, config)
            val splits = ride.metrics.splits.map {
                SplitDto(it.index, it.distanceMeters, it.durationSeconds, it.avgSpeed, it.elevationGain, it.avgHeartRate)
            }
            cache.store(file, configFp, RideDetailDto(ride.toSummaryDto(), series, splits))
            RideDetail(ride, series, splits, track)
        }.getOrNull()

    /** Recompute the rides list from [details] and push the current progress to state. */
    private fun publish(loading: Boolean, analyzing: Boolean, done: Int, total: Int) {
        val rides = details.values.map { it.ride }.sortedByDescending { it.startTime }
        _state.update {
            it.copy(rides = rides, loading = loading, error = null, analyzing = analyzing, analyzingDone = done, analyzingTotal = total)
        }
    }

    private fun buildConfig(settings: AppSettings): AnalyzerConfig {
        val demProvider: ElevationProvider? = settings.demFolder
            ?.let { File(it) }
            ?.takeIf { settings.useDemElevation && it.isDirectory }
            ?.let { SrtmElevationProvider(it) }
        val disabled = settings.disabledChannels
            .mapNotNull { runCatching { io.openftba.model.SensorChannel.valueOf(it) }.getOrNull() }
            .toSet()
        return AnalyzerConfig(
            ignoreElevation = settings.ignoreElevation,
            useDem = settings.useDemElevation && demProvider != null,
            elevationProvider = demProvider,
            disabledChannels = disabled,
            profile = AthleteProfile(
                weightKg = settings.weightKg,
                sex = settings.sex,
                maxHr = settings.maxHr,
                restHr = settings.restHr,
                ftpWatts = settings.ftpWatts,
            ),
        )
    }

    override suspend fun downloadDemTiles(): String {
        val settings = _state.value.settings
        val folder = settings.demFolder?.let { File(it) }
            ?: return "Set the DEM folder in settings first"
        // Sample coordinates from the loaded rides' (downsampled) series to determine which 1°
        // tiles are needed — plenty for coverage, and works for cache-restored rides too.
        val coords = details.values.asSequence()
            .flatMap { d -> d.series.lat.indices.asSequence().map { d.series.lat[it] to d.series.lon[it] } }
            .toList()
        if (coords.isEmpty()) return "No rides loaded to derive tiles from"
        val tiles = SrtmDownloader.tilesFor(coords)
        val result = withContext(Dispatchers.IO) {
            folder.mkdirs()
            SrtmDownloader.download(folder, tiles)
        }
        if (settings.useDemElevation) {
            // Elevations change but file/config keys don't — drop the cache so all rides re-analyze.
            cache.clear()
            rescan()
        }
        return "DEM tiles — downloaded: ${result.downloaded.size}, present: ${result.skipped.size}" +
            if (result.failed.isEmpty()) "" else ", failed: ${result.failed.size}"
    }

    private fun loadSettings(): AppSettings = runCatching {
        if (configFile.isFile) json.decodeFromString(AppSettings.serializer(), configFile.readText())
        else AppSettings()
    }.getOrDefault(AppSettings())

    private fun saveSettings(settings: AppSettings) {
        runCatching {
            configFile.parentFile?.mkdirs()
            configFile.writeText(json.encodeToString(AppSettings.serializer(), settings))
        }
    }
}
