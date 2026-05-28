package io.openftba.dem

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.floor

/**
 * Android (SAF) counterpart of the desktop [io.openftba.dem.SrtmDownloader]. Downloads SRTM
 * `.hgt.gz` tiles into a user-granted folder (a Storage Access Framework tree URI) instead of a
 * `java.io.File` directory, since `shared/jvmMain`'s File-based downloader is not on the Android
 * classpath and Android storage is content-URI based.
 *
 * This is the **only** networked code path on Android and is strictly user-initiated. Tile naming
 * and the HTTP fetch are copied verbatim from the desktop downloader so the two agree on tiles.
 * Source: the public, no-auth AWS "skadi" mirror of SRTM 3-arc-second tiles.
 */
object AndroidSrtmDownloader {
    private const val BASE = "https://elevation-tiles-prod.s3.amazonaws.com/skadi"

    fun tileName(lat: Double, lon: Double): String {
        val baseLat = floor(lat).toInt()
        val baseLon = floor(lon).toInt()
        val ns = if (baseLat >= 0) "N" else "S"
        val ew = if (baseLon >= 0) "E" else "W"
        return "$ns${abs(baseLat).toString().padStart(2, '0')}$ew${abs(baseLon).toString().padStart(3, '0')}"
    }

    /** Distinct tile names covering the given coordinates. */
    fun tilesFor(coords: Iterable<Pair<Double, Double>>): Set<String> =
        coords.mapTo(LinkedHashSet()) { (lat, lon) -> tileName(lat, lon) }

    data class Result(val downloaded: List<String>, val skipped: List<String>, val failed: List<String>)

    /** Downloads any missing tiles into the SAF tree; existing `.hgt`/`.hgt.gz` are skipped. */
    fun download(context: Context, treeUri: String, tiles: Set<String>): Result {
        val root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) }.getOrNull()
            ?: return Result(emptyList(), emptyList(), tiles.toList())
        val downloaded = ArrayList<String>()
        val skipped = ArrayList<String>()
        val failed = ArrayList<String>()
        for (tile in tiles) {
            if (root.findFile("$tile.hgt") != null || root.findFile("$tile.hgt.gz") != null) {
                skipped.add(tile); continue
            }
            val band = tile.substring(0, 3) // e.g. N60
            val url = "$BASE/$band/$tile.hgt.gz"
            val ok = runCatching { fetch(context, url, root, "$tile.hgt.gz") }.getOrDefault(false)
            if (ok) downloaded.add(tile) else failed.add(tile)
        }
        return Result(downloaded, skipped, failed)
    }

    private fun fetch(context: Context, url: String, root: DocumentFile, fileName: String): Boolean {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            requestMethod = "GET"
        }
        var created: DocumentFile? = null
        return try {
            if (conn.responseCode != 200) return false
            created = root.createFile("application/gzip", fileName) ?: return false
            val out = context.contentResolver.openOutputStream(created.uri) ?: return false
            conn.inputStream.use { input -> out.use { input.copyTo(it) } }
            true
        } catch (t: Throwable) {
            created?.delete() // don't leave a half-written tile behind
            false
        } finally {
            conn.disconnect()
        }
    }
}
