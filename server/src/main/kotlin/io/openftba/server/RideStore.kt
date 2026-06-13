package io.openftba.server

import io.openftba.analytics.AnalyzerConfig
import io.openftba.analytics.FitnessScale
import io.openftba.analytics.RideAnalyzer
import io.openftba.analytics.TrainingLoad
import io.openftba.api.AthleteTierDto
import io.openftba.api.LoadPointDto
import io.openftba.api.OverviewDto
import io.openftba.api.RideDetailDto
import io.openftba.api.RideSummaryDto
import io.openftba.api.ScanStatusDto
import io.openftba.api.SplitDto
import io.openftba.api.buildRideSeries
import io.openftba.api.toRide
import io.openftba.api.toSummaryDto
import io.openftba.cache.JvmRideCache
import io.openftba.cache.configFingerprint
import io.openftba.model.Ride
import io.openftba.parse.OpenTracksKmlParser
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Scans the OpenTracks export folder, parses + analyzes each track using the shared
 * engine, and caches the resulting DTOs. Read-only: source files are never modified
 * (the folder is shared with Wanderer/FitTrackee's importer).
 *
 * When [cacheDir] is set (a writable `OPENFTBA_CONFIG_DIR/cache`), analyzed rides are persisted
 * to disk keyed by file identity + analyzer-config fingerprint, so a restart restores unchanged
 * rides without re-parsing them. [status] tracks scan progress for the web "computing" banner.
 */
class RideStore(
    private val folder: File,
    @Volatile private var config: AnalyzerConfig = AnalyzerConfig(),
    cacheDir: File? = null,
) {
    fun setConfig(newConfig: AnalyzerConfig) { config = newConfig }

    private val cache: JvmRideCache? = cacheDir?.let { JvmRideCache(it) }

    private data class Snapshot(
        val summaries: List<RideSummaryDto>,
        val details: Map<String, RideDetailDto>,
        val overview: OverviewDto,
    )

    private val emptyOverview = OverviewDto(AthleteTierDto("f", "NONE", null, 0.0))
    private val snapshot = AtomicReference(Snapshot(emptyList(), emptyMap(), emptyOverview))
    private val scanStatus = AtomicReference(ScanStatusDto())

    val rides: List<RideSummaryDto> get() = snapshot.get().summaries
    fun detail(id: String): RideDetailDto? = snapshot.get().details[id]
    fun overview(): OverviewDto = snapshot.get().overview
    fun status(): ScanStatusDto = scanStatus.get()

    fun rescan(): Int {
        if (!folder.isDirectory) {
            snapshot.set(Snapshot(emptyList(), emptyMap(), emptyOverview))
            scanStatus.set(ScanStatusDto())
            return 0
        }
        val files = folder.listFiles { f ->
            f.isFile && f.name.lowercase().let {
                it.endsWith(".kmz") || it.endsWith(".kml") || it.endsWith(".gpx")
            }
        }?.sortedBy { it.name } ?: emptyList()
        val configFp = configFingerprint(config)

        // Phase A — restore cache hits; collect misses.
        val details = LinkedHashMap<String, RideDetailDto>()
        val misses = ArrayList<File>()
        for (file in files) {
            val dto = cache?.load(file, configFp)
            if (dto != null) details[dto.summary.id] = dto else misses.add(file)
        }
        cache?.prune(files.map { it.name })
        if (cache != null && misses.isNotEmpty()) {
            println("[store] cache: ${details.size} restored, ${misses.size} to analyze")
        }
        scanStatus.set(ScanStatusDto(scanning = misses.isNotEmpty(), done = 0, total = misses.size))
        if (misses.isNotEmpty()) publish(details) // make cached rides visible before analyzing the rest

        // Phase B — analyze new/changed files, persisting each to the cache.
        var done = 0
        for (file in misses) {
            runCatching {
                val track = OpenTracksKmlParser.parseFile(file)
                val ride = RideAnalyzer.analyze(track, config) ?: return@runCatching
                val splits = ride.metrics.splits.map {
                    SplitDto(it.index, it.distanceMeters, it.durationSeconds, it.avgSpeed, it.elevationGain, it.avgHeartRate)
                }
                val dto = RideDetailDto(ride.toSummaryDto(), buildRideSeries(track, config), splits)
                cache?.store(file, configFp, dto)
                details[ride.id] = dto
            }.onFailure { System.err.println("[store] failed ${file.name}: ${it.message}") }
            done++
            scanStatus.set(ScanStatusDto(scanning = done < misses.size, done = done, total = misses.size))
        }

        val count = publish(details)
        scanStatus.set(ScanStatusDto())
        return count
    }

    /** Build summaries + overview from the current detail map and publish the snapshot. */
    private fun publish(details: Map<String, RideDetailDto>): Int {
        val summaries = details.values.map { it.summary }.sortedByDescending { it.startEpochMs }
        val rideList: List<Ride> = details.values.map { it.summary.toRide() }
        val fitness = FitnessScale.evaluate(config.profile, rideList)
        val load = TrainingLoad.compute(rideList).map { LoadPointDto(it.epochDay, it.ctl, it.atl, it.tsb) }
        val overview = OverviewDto(
            athlete = AthleteTierDto(fitness.tier.key, fitness.basis.name, fitness.value, fitness.progressToNext),
            load = load,
        )
        snapshot.set(Snapshot(summaries, LinkedHashMap(details), overview))
        return summaries.size
    }
}
