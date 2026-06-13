package io.openftba.cache

import io.openftba.analytics.AnalyzerConfig
import io.openftba.api.RideDetailDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Portable, platform-agnostic core of the per-ride analysis cache. The unit of caching is a
 * fully analyzed [RideDetailDto] (metrics + downsampled chart/3D series + splits) — exactly the
 * wire DTO the server already produces and the web client already maps back to a domain ride.
 *
 * A cache entry is valid only when ALL of these match the current run:
 *  - [RideCacheEntry.version] == [RIDE_CACHE_VERSION] (bumped when analytics logic changes),
 *  - [RideCacheEntry.fileKey] (source file identity: name + size + last-modified),
 *  - [RideCacheEntry.configFp] (fingerprint of the [AnalyzerConfig] that affects results).
 *
 * The actual byte IO + source-file identity live in the platform layers (JvmRideCache,
 * AndroidRideCache); only the format + invalidation rules are shared here so every platform
 * agrees on what a valid cache hit is.
 */

/**
 * Cache schema/engine version. **Bump this** whenever [io.openftba.analytics.RideAnalyzer] or
 * [io.openftba.api.buildRideSeries] change in a way that alters computed numbers, so stale
 * entries from the old logic are rejected on the next scan.
 */
const val RIDE_CACHE_VERSION: Int = 1

@Serializable
data class RideCacheEntry(
    val fileKey: String,
    val configFp: String,
    val version: Int,
    val detail: RideDetailDto,
)

/** Source-file identity: cheap, no content read. Same string ⇒ same analysis input. */
fun rideFileKey(name: String, sizeBytes: Long, lastModifiedMs: Long): String =
    "$name|$sizeBytes|$lastModifiedMs"

/**
 * Deterministic fingerprint of every [AnalyzerConfig] field that influences the result. Two
 * configs producing identical metrics/series must yield the same string; any difference (e.g.
 * a changed FTP, elevation mode, or distrusted channel) must change it so the cache invalidates.
 * The [AnalyzerConfig.elevationProvider] instance is not hashed — [AnalyzerConfig.useDem] already
 * reflects whether a usable provider is active.
 */
fun configFingerprint(config: AnalyzerConfig): String = buildString {
    append("v="); append(RIDE_CACHE_VERSION)
    append(";mst="); append(config.movingSpeedThreshold)
    append(";srs="); append(config.stopResetSeconds)
    append(";esw="); append(config.elevationSmoothingWindow)
    append(";eth="); append(config.elevationThreshold)
    append(";mps="); append(config.maxPlausibleSpeed)
    append(";spl="); append(config.splitMeters)
    append(";ign="); append(config.ignoreElevation)
    append(";dem="); append(config.useDem)
    append(";dis="); append(config.disabledChannels.map { it.name }.sorted().joinToString(","))
    val p = config.profile
    append(";wt="); append(p.weightKg)
    append(";sex="); append(p.sex.name)
    append(";mhr="); append(p.maxHr)
    append(";rhr="); append(p.restHr)
    append(";ftp="); append(p.ftpWatts)
}

/** Stable file name for a source ride's cache blob (one cache file per source name). */
fun rideCacheFileName(sourceName: String): String = "ride-" + fnv1a(sourceName) + ".json"

/** FNV-1a 64-bit hash as zero-padded hex — stable across platforms, no crypto dependency. */
private fun fnv1a(s: String): String {
    var h = -0x340d631b7bdddcdbL // 14695981039346656037 (FNV offset basis)
    for (c in s) {
        h = h xor c.code.toLong()
        h *= 0x100000001b3L // FNV prime
    }
    return h.toULong().toString(16).padStart(16, '0')
}

/** Shared JSON codec for cache entries. */
object RideCacheCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(entry: RideCacheEntry): String = json.encodeToString(RideCacheEntry.serializer(), entry)
    fun decode(text: String): RideCacheEntry? =
        runCatching { json.decodeFromString(RideCacheEntry.serializer(), text) }.getOrNull()
}
