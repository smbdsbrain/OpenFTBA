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
import io.openftba.api.SplitDto
import io.openftba.api.buildRideSeries
import io.openftba.api.toSummaryDto
import io.openftba.model.Ride
import io.openftba.parse.OpenTracksKmlParser
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Scans the OpenTracks export folder, parses + analyzes each track using the shared
 * engine, and caches the resulting DTOs. Read-only: source files are never modified
 * (the folder is shared with Wanderer/FitTrackee's importer).
 */
class RideStore(
    private val folder: File,
    @Volatile private var config: AnalyzerConfig = AnalyzerConfig(),
) {
    fun setConfig(newConfig: AnalyzerConfig) { config = newConfig }

    private data class Snapshot(
        val summaries: List<RideSummaryDto>,
        val details: Map<String, RideDetailDto>,
        val overview: OverviewDto,
    )

    private val emptyOverview = OverviewDto(AthleteTierDto("f", "NONE", null, 0.0))
    private val snapshot = AtomicReference(Snapshot(emptyList(), emptyMap(), emptyOverview))

    val rides: List<RideSummaryDto> get() = snapshot.get().summaries
    fun detail(id: String): RideDetailDto? = snapshot.get().details[id]
    fun overview(): OverviewDto = snapshot.get().overview

    fun rescan(): Int {
        if (!folder.isDirectory) {
            snapshot.set(Snapshot(emptyList(), emptyMap(), emptyOverview))
            return 0
        }
        val files = folder.listFiles { f ->
            f.isFile && f.name.lowercase().let {
                it.endsWith(".kmz") || it.endsWith(".kml") || it.endsWith(".gpx")
            }
        }?.sortedBy { it.name } ?: emptyList()

        val details = LinkedHashMap<String, RideDetailDto>()
        val rideList = ArrayList<Ride>()
        for (file in files) {
            runCatching {
                val track = OpenTracksKmlParser.parseFile(file)
                val ride = RideAnalyzer.analyze(track, config) ?: return@runCatching
                rideList.add(ride)
                val splits = ride.metrics.splits.map {
                    SplitDto(it.index, it.distanceMeters, it.durationSeconds, it.avgSpeed, it.elevationGain, it.avgHeartRate)
                }
                details[ride.id] = RideDetailDto(ride.toSummaryDto(), buildRideSeries(track), splits)
            }.onFailure { System.err.println("[store] failed ${file.name}: ${it.message}") }
        }
        val summaries = details.values.map { it.summary }.sortedByDescending { it.startEpochMs }
        val fitness = FitnessScale.evaluate(config.profile, rideList)
        val load = TrainingLoad.compute(rideList).map { LoadPointDto(it.epochDay, it.ctl, it.atl, it.tsb) }
        val overview = OverviewDto(
            athlete = AthleteTierDto(fitness.tier.key, fitness.basis.name, fitness.value, fitness.progressToNext),
            load = load,
        )
        snapshot.set(Snapshot(summaries, details, overview))
        return summaries.size
    }
}
