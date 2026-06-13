package io.openftba.cache

import io.openftba.api.RideDetailDto
import java.io.File

/**
 * JVM (desktop + server) disk cache for analyzed rides. One JSON blob per source file under
 * [dir]; a hit is returned only when the file identity, config fingerprint and cache version
 * all match (see [RideCache]). Reused by both the desktop repository and the server store —
 * both enumerate source files via `java.io.File` and parse with the StAX reader.
 */
class JvmRideCache(private val dir: File) {

    init { runCatching { dir.mkdirs() } }

    private fun keyOf(file: File): String = rideFileKey(file.name, file.length(), file.lastModified())
    private fun blob(file: File): File = File(dir, rideCacheFileName(file.name))

    /** Cached detail for [file] under [configFp], or null on miss/stale/corrupt. */
    fun load(file: File, configFp: String): RideDetailDto? {
        val f = blob(file)
        if (!f.isFile) return null
        val entry = RideCacheCodec.decode(runCatching { f.readText() }.getOrNull() ?: return null) ?: return null
        return if (entry.version == RIDE_CACHE_VERSION && entry.fileKey == keyOf(file) && entry.configFp == configFp)
            entry.detail else null
    }

    /** Persist [detail] for [file] under [configFp] (best-effort; failures are ignored). */
    fun store(file: File, configFp: String, detail: RideDetailDto) {
        val entry = RideCacheEntry(keyOf(file), configFp, RIDE_CACHE_VERSION, detail)
        runCatching { blob(file).writeText(RideCacheCodec.encode(entry)) }
    }

    /** Delete cache blobs whose source file is no longer present. */
    fun prune(presentNames: Collection<String>) {
        val keep = presentNames.map { rideCacheFileName(it) }.toHashSet()
        dir.listFiles { f -> f.isFile && f.name.startsWith("ride-") && f.name.endsWith(".json") }
            ?.forEach { if (it.name !in keep) runCatching { it.delete() } }
    }

    /** Drop the entire cache (e.g. after a DEM download changes elevations for all rides). */
    fun clear() {
        dir.listFiles { f -> f.isFile && f.name.startsWith("ride-") && f.name.endsWith(".json") }
            ?.forEach { runCatching { it.delete() } }
    }
}
