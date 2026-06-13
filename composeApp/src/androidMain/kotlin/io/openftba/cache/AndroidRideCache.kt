package io.openftba.cache

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import io.openftba.api.RideDetailDto
import java.io.File

/**
 * Android disk cache for analyzed rides. Mirrors [JvmRideCache] but derives source-file identity
 * from a SAF [DocumentFile] (the watch folder is a content-tree URI, not a java.io path) and
 * stores blobs in the app's private [Context.getFilesDir] (`ride-cache/`), which survives process
 * death — unlike `cacheDir`, which the OS may purge.
 */
class AndroidRideCache(context: Context) {

    private val dir = File(context.filesDir, "ride-cache").also { runCatching { it.mkdirs() } }

    private fun keyOf(file: DocumentFile): String =
        rideFileKey(file.name ?: "", file.length(), file.lastModified())

    private fun blob(name: String): File = File(dir, rideCacheFileName(name))

    fun load(file: DocumentFile, configFp: String): RideDetailDto? {
        val name = file.name ?: return null
        val f = blob(name)
        if (!f.isFile) return null
        val entry = RideCacheCodec.decode(runCatching { f.readText() }.getOrNull() ?: return null) ?: return null
        return if (entry.version == RIDE_CACHE_VERSION && entry.fileKey == keyOf(file) && entry.configFp == configFp)
            entry.detail else null
    }

    fun store(file: DocumentFile, configFp: String, detail: RideDetailDto) {
        val name = file.name ?: return
        val entry = RideCacheEntry(keyOf(file), configFp, RIDE_CACHE_VERSION, detail)
        runCatching { blob(name).writeText(RideCacheCodec.encode(entry)) }
    }

    fun prune(presentNames: Collection<String>) {
        val keep = presentNames.map { rideCacheFileName(it) }.toHashSet()
        dir.listFiles { f -> f.isFile && f.name.startsWith("ride-") && f.name.endsWith(".json") }
            ?.forEach { if (it.name !in keep) runCatching { it.delete() } }
    }

    fun clear() {
        dir.listFiles { f -> f.isFile && f.name.startsWith("ride-") && f.name.endsWith(".json") }
            ?.forEach { runCatching { it.delete() } }
    }
}
