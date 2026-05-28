package io.openftba.dem

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.openftba.analytics.ElevationProvider
import java.io.DataInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Android (SAF) counterpart of the desktop [io.openftba.dem.SrtmElevationProvider]. Reads SRTM
 * elevation from `.hgt` / `.hgt.gz` tiles inside a user-granted folder (a Storage Access Framework
 * tree URI) via the content resolver — no network at runtime. SRTM tiles are 1° squares named
 * after their SW corner (e.g. `N60E029.hgt`): a square grid of big-endian int16 meters, row-major
 * from north to south, west to east. 1201×1201 = 3 arc-second (~90 m), 3601×3601 = 1 arc-second
 * (~30 m). Voids are -32768.
 *
 * The decode + bilinear interpolation are copied verbatim from the desktop provider so both
 * platforms agree on the numbers; only tile loading differs (SAF content URIs vs `java.io.File`).
 */
class AndroidSrtmElevationProvider(
    private val context: Context,
    treeUri: String,
) : ElevationProvider {

    private class Tile(val dim: Int, val data: ShortArray)

    private val root: DocumentFile? =
        runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) }.getOrNull()
    private val cache = ConcurrentHashMap<String, Tile>()
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val VOID = (-32768).toShort()

    override fun elevationAt(lat: Double, lon: Double): Double? {
        val baseLat = floor(lat).toInt()
        val baseLon = floor(lon).toInt()
        val tile = tileFor(baseLat, baseLon) ?: return null
        val dim = tile.dim
        val step = dim - 1

        // Row counts from the north edge (lat+1) downward; col from the west edge eastward.
        val rowF = (1.0 - (lat - baseLat)) * step
        val colF = (lon - baseLon) * step
        val r0 = floor(rowF).toInt().coerceIn(0, step)
        val c0 = floor(colF).toInt().coerceIn(0, step)
        val r1 = (r0 + 1).coerceAtMost(step)
        val c1 = (c0 + 1).coerceAtMost(step)
        val dr = rowF - r0
        val dc = colF - c0

        val v00 = sample(tile, r0, c0)
        val v01 = sample(tile, r0, c1)
        val v10 = sample(tile, r1, c0)
        val v11 = sample(tile, r1, c1)
        val valid = listOfNotNull(v00, v01, v10, v11)
        if (valid.isEmpty()) return null
        if (valid.size < 4) return valid.average() // some voids nearby — best effort

        val top = v00!! * (1 - dc) + v01!! * dc
        val bot = v10!! * (1 - dc) + v11!! * dc
        return top * (1 - dr) + bot * dr
    }

    private fun sample(tile: Tile, row: Int, col: Int): Double? {
        val v = tile.data[row * tile.dim + col]
        return if (v == VOID) null else v.toDouble()
    }

    private fun tileFor(baseLat: Int, baseLon: Int): Tile? {
        val name = tileName(baseLat, baseLon)
        cache[name]?.let { return it }
        if (name in missing) return null
        val tile = loadTile(name)
        if (tile == null) { missing.add(name); return null }
        cache[name] = tile
        return tile
    }

    private fun tileName(baseLat: Int, baseLon: Int): String {
        val ns = if (baseLat >= 0) "N" else "S"
        val ew = if (baseLon >= 0) "E" else "W"
        val latStr = kotlin.math.abs(baseLat).toString().padStart(2, '0')
        val lonStr = kotlin.math.abs(baseLon).toString().padStart(3, '0')
        return "$ns$latStr$ew$lonStr"
    }

    private fun loadTile(name: String): Tile? {
        val dir = root ?: return null
        val plain = dir.findFile("$name.hgt")
        val gz = dir.findFile("$name.hgt.gz")
        val (file, gzipped) = when {
            plain != null && plain.isFile -> plain to false
            gz != null && gz.isFile -> gz to true
            else -> return null
        }
        return runCatching {
            val raw = context.contentResolver.openInputStream(file.uri)?.buffered() ?: return@runCatching null
            val bytes = (if (gzipped) GZIPInputStream(raw) else raw).use { it.readBytes() }
            val dim = sqrtSamples(bytes.size) ?: return@runCatching null
            val data = ShortArray(dim * dim)
            DataInputStream(bytes.inputStream()).use { din ->
                for (i in data.indices) data[i] = din.readShort() // DataInputStream is big-endian
            }
            Tile(dim, data)
        }.getOrNull()
    }

    /** SRTM tiles are square; samples per side = sqrt(byteCount / 2). */
    private fun sqrtSamples(byteCount: Int): Int? {
        val samples = byteCount / 2
        val dim = kotlin.math.sqrt(samples.toDouble()).roundToInt()
        return if (dim * dim == samples && dim > 1) dim else null
    }
}
