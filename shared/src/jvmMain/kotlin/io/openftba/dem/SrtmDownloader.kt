package io.openftba.dem

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.floor

/**
 * Downloads SRTM `.hgt.gz` tiles into the DEM folder.
 *
 * This is the **only** networked code path in the project and is strictly user-initiated
 * (a button / explicit call) — never run automatically. Source is the public, no-auth AWS
 * "skadi" mirror of SRTM 3-arc-second tiles.
 */
object SrtmDownloader {
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

    /** Downloads any missing tiles; existing `.hgt`/`.hgt.gz` are skipped. */
    fun download(folder: File, tiles: Set<String>): Result {
        folder.mkdirs()
        val downloaded = ArrayList<String>()
        val skipped = ArrayList<String>()
        val failed = ArrayList<String>()
        for (tile in tiles) {
            if (File(folder, "$tile.hgt").isFile || File(folder, "$tile.hgt.gz").isFile) {
                skipped.add(tile); continue
            }
            val band = tile.substring(0, 3) // e.g. N60
            val url = "$BASE/$band/$tile.hgt.gz"
            val ok = runCatching { fetch(url, File(folder, "$tile.hgt.gz")) }.getOrDefault(false)
            if (ok) downloaded.add(tile) else failed.add(tile)
        }
        return Result(downloaded, skipped, failed)
    }

    private fun fetch(url: String, dest: File): Boolean {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            requestMethod = "GET"
        }
        return try {
            if (conn.responseCode != 200) return false
            val tmp = File(dest.parentFile, dest.name + ".part")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(dest)
        } finally {
            conn.disconnect()
        }
    }
}
