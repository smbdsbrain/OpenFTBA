package io.openftba.data

import io.openftba.analytics.AnalyzerConfig
import io.openftba.analytics.AthleteProfile
import io.openftba.analytics.ElevationProvider
import io.openftba.analytics.RideAnalyzer
import io.openftba.dem.SrtmDownloader
import io.openftba.dem.SrtmElevationProvider
import io.openftba.model.Ride
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
 */
class DesktopRideRepository(
    private val configFile: File = File(System.getProperty("user.home"), ".openftba/settings.json"),
) : RideRepository {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val details = LinkedHashMap<String, RideDetail>()

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
            _state.update { it.copy(rides = emptyList(), loading = false, error = "no-folder") }
            details.clear()
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        val result = withContext(Dispatchers.IO) {
            val demProvider: ElevationProvider? = settings.demFolder
                ?.let { File(it) }
                ?.takeIf { settings.useDemElevation && it.isDirectory }
                ?.let { SrtmElevationProvider(it) }
            val disabled = settings.disabledChannels
                .mapNotNull { runCatching { io.openftba.model.SensorChannel.valueOf(it) }.getOrNull() }
                .toSet()
            val config = AnalyzerConfig(
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
            val files = folder.listFiles { f ->
                f.isFile && f.name.lowercase().let {
                    it.endsWith(".kmz") || it.endsWith(".kml") || it.endsWith(".gpx")
                }
            }?.sortedBy { it.name } ?: emptyList()

            val byId = LinkedHashMap<String, RideDetail>()
            for (file in files) {
                runCatching {
                    val track = OpenTracksKmlParser.parseFile(file)
                    val ride = RideAnalyzer.analyze(track, config) ?: return@runCatching
                    val splits = ride.metrics.splits.map {
                        io.openftba.api.SplitDto(it.index, it.distanceMeters, it.durationSeconds, it.avgSpeed, it.elevationGain, it.avgHeartRate)
                    }
                    // Dedup: trackid wins; fall back to file name.
                    byId[ride.id] = RideDetail(ride, io.openftba.api.buildRideSeries(track), splits, track)
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
        val folder = settings.demFolder?.let { File(it) }
            ?: return "Set the DEM folder in settings first"
        // Sample coordinates from loaded tracks to determine which 1° tiles are needed.
        val coords = details.values.asSequence()
            .flatMap { (it.track?.allPoints ?: emptyList()).asSequence() }
            .filterIndexed { i, _ -> i % 200 == 0 } // every ~200th point is plenty for tile coverage
            .map { it.lat to it.lon }
            .toList()
        if (coords.isEmpty()) return "No rides loaded to derive tiles from"
        val tiles = SrtmDownloader.tilesFor(coords)
        val result = withContext(Dispatchers.IO) {
            folder.mkdirs()
            SrtmDownloader.download(folder, tiles)
        }
        if (settings.useDemElevation) rescan()
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
