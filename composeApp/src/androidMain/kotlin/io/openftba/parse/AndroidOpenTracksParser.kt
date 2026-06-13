package io.openftba.parse

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.documentfile.provider.DocumentFile
import io.openftba.model.GeoPoint
import io.openftba.model.ParsedTrack
import io.openftba.model.TrackSegment
import kotlinx.datetime.Instant
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Android KMZ/KML reader for the OpenTracks schema. Mirrors the desktop StAX parser but uses
 * `XmlPullParser` (Android has no `javax.xml.stream`). Reads a user-granted SAF tree URI.
 *
 * See docs/metrics-and-formulas.md for the schema (MultiTrack segments, `lon lat ele`
 * coords, index-parallel SimpleArrayData arrays).
 */
object AndroidOpenTracksParser {

    fun parseTree(context: Context, treeUri: String): List<ParsedTrack> {
        return trackFiles(context, treeUri).mapNotNull { parseDocument(context, it) }
    }

    /** The KMZ/KML/GPX documents in the watch tree, used to compute cache keys before parsing. */
    fun trackFiles(context: Context, treeUri: String): List<DocumentFile> {
        val root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) }.getOrNull()
            ?: return emptyList()
        return root.listFiles().filter { file ->
            file.isFile && (file.name?.lowercase()?.let { it.endsWith(".kmz") || it.endsWith(".kml") } == true)
        }
    }

    /** Parse a single watch-folder document (called only for cache misses). */
    fun parseDocument(context: Context, file: DocumentFile): ParsedTrack? {
        val name = file.name ?: return null
        val lower = name.lowercase()
        if (!(lower.endsWith(".kmz") || lower.endsWith(".kml"))) return null
        return runCatching {
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                if (lower.endsWith(".kmz")) parseKmz(input, name) else parseKml(input, name)
            }
        }.getOrNull()
    }

    private fun parseKmz(input: InputStream, sourceName: String): ParsedTrack? {
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".kml", ignoreCase = true)) return parseKml(zip, sourceName)
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun parseKml(input: InputStream, sourceName: String): ParsedTrack? {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var trackId: String? = null
        var name: String? = null
        var activityType: String? = null
        val segments = ArrayList<TrackSegment>()

        var inTrack = false
        var whens = ArrayList<String>()
        var coords = ArrayList<String>()
        var arrays = HashMap<String, MutableList<String>>()
        var currentArray: MutableList<String>? = null
        var pendingDataName: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (local(parser.name)) {
                    "trackid" -> trackId = parser.nextText().trim().ifBlank { null }
                    "Track" -> {
                        inTrack = true
                        whens = ArrayList(); coords = ArrayList(); arrays = HashMap()
                    }
                    "when" -> if (inTrack) whens.add(parser.nextText().trim())
                    "coord" -> if (inTrack) coords.add(parser.nextText().trim())
                    "SimpleArrayData" -> {
                        val arrName = parser.getAttributeValue(null, "name") ?: ""
                        currentArray = ArrayList<String>().also { arrays[arrName] = it }
                    }
                    "Data" -> pendingDataName = parser.getAttributeValue(null, "name")
                    "value" -> {
                        val text = parser.nextText().trim()
                        val arr = currentArray
                        when {
                            arr != null -> arr.add(text)
                            pendingDataName == "activityType" -> { activityType = text.ifBlank { activityType }; pendingDataName = null }
                            pendingDataName == "type" && activityType == null -> { activityType = text.ifBlank { null }; pendingDataName = null }
                            else -> pendingDataName = null
                        }
                    }
                    "name" -> if (name == null) name = parser.nextText().trim().ifBlank { null }
                }
                XmlPullParser.END_TAG -> when (local(parser.name)) {
                    "SimpleArrayData" -> currentArray = null
                    "Track" -> {
                        inTrack = false
                        buildSegment(whens, coords, arrays)?.let { segments.add(it) }
                    }
                }
            }
            event = parser.next()
        }
        return ParsedTrack(trackId, name, activityType, segments, sourceName)
    }

    private fun local(tag: String): String = tag.substringAfterLast(':')

    private fun buildSegment(
        whens: List<String>,
        coords: List<String>,
        arrays: Map<String, List<String>>,
    ): TrackSegment? {
        val speed = arrays["speed"]
        val cadence = arrays["cadence"]
        val heartrate = arrays["heartrate"]
        val power = arrays["power"]
        val temperature = arrays["temperature"]

        val points = ArrayList<GeoPoint>(whens.size)
        val count = minOf(whens.size, coords.size)
        for (i in 0 until count) {
            val coord = coords[i]
            if (coord.isBlank()) continue
            val parts = coord.split(' ', '\t').filter { it.isNotBlank() }
            if (parts.size < 2) continue
            val lon = parts[0].toDoubleOrNull() ?: continue
            val lat = parts[1].toDoubleOrNull() ?: continue
            val ele = parts.getOrNull(2)?.toDoubleOrNull()
            val time = runCatching { Instant.parse(whens[i]) }.getOrNull() ?: continue
            points.add(
                GeoPoint(
                    time = time, lat = lat, lon = lon, ele = ele,
                    speed = speed?.getOrNull(i)?.toDoubleOrNull(),
                    cadence = cadence?.getOrNull(i)?.toDoubleOrNull(),
                    heartRate = heartrate?.getOrNull(i)?.toDoubleOrNull(),
                    power = power?.getOrNull(i)?.toDoubleOrNull(),
                    temperature = temperature?.getOrNull(i)?.toDoubleOrNull(),
                )
            )
        }
        return if (points.size >= 2) TrackSegment(points) else null
    }
}
