package io.openftba.data

import io.openftba.api.RideDetailDto
import io.openftba.api.RideSeriesDto
import io.openftba.api.toRide
import io.openftba.model.ParsedTrack
import io.openftba.model.Ride
import io.openftba.settings.AppSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * Ride + its precomputed chart series (desktop builds from the track; web gets it via REST).
 * [track] is only populated on platforms that parse locally AND analyzed this session — used to
 * derive which DEM tiles to download. Rides restored from the on-disk cache leave it null (the
 * raw points are not cached), so DEM tile selection falls back to the downsampled [series].
 */
data class RideDetail(
    val ride: Ride,
    val series: RideSeriesDto,
    val splits: List<io.openftba.api.SplitDto> = emptyList(),
    val track: ParsedTrack? = null,
)

/** Reconstruct a domain [RideDetail] from a cached/wire [RideDetailDto] (no raw track). */
fun RideDetailDto.toRideDetail(): RideDetail = RideDetail(summary.toRide(), series, splits, track = null)

data class RepoState(
    val rides: List<Ride> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val loading: Boolean = false,
    val error: String? = null,
    /** True while new (uncached) rides are being parsed + analyzed in the background. Cached
     *  rides already appear in [rides]; the UI shows a "computing N new rides" banner. */
    val analyzing: Boolean = false,
    /** Progress of the current analysis pass over uncached rides. */
    val analyzingDone: Int = 0,
    val analyzingTotal: Int = 0,
    /** True when this client may edit the watch/DEM folder paths (desktop/Android). On the
     *  web they are fixed by the server/container, so the fields render read-only. */
    val foldersEditable: Boolean = true,
    /** Whether the server has a usable DEM folder (for the web Settings display). */
    val demAvailable: Boolean = false,
)

/**
 * Source of rides + settings for the UI. The desktop implementation parses the
 * OpenTracks watch folder in-process; the web target (later) talks to the Ktor server.
 */
interface RideRepository {
    val state: StateFlow<RepoState>
    fun updateSettings(settings: AppSettings)
    suspend fun rescan()
    fun detail(id: String): RideDetail?

    /**
     * User-initiated download of DEM tiles covering the loaded rides into the DEM folder,
     * then rescan. The only networked action; returns a short human-readable status.
     */
    suspend fun downloadDemTiles(): String = "not supported"
}
