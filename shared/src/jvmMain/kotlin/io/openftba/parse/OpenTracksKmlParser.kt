package io.openftba.parse

import io.openftba.model.GeoPoint
import io.openftba.model.ParsedTrack
import io.openftba.model.TrackSegment
import kotlinx.datetime.Instant
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/**
 * Parser for OpenTracks exports.
 *
 * KMZ = a ZIP containing a single `doc.kml` (KML 2.3, OpenTracks v1 schema). The
 * geometry lives in `<MultiTrack>` → `<Track>` segments (a segment boundary == a
 * pause). Inside each `<Track>`:
 *   - interleaved `<when>` (ISO time) and `<coord>` (`"lon lat ele"`, meters; an empty
 *     `<coord/>` marks a gap with no GPS fix),
 *   - then `<ExtendedData><SchemaData>` with index-parallel `<SimpleArrayData name=…>`
 *     arrays: `trackpoint_type`, `speed`, `cadence`, `heartrate`, `power`,
 *     `temperature`, `accuracy_horizontal`. Empty `<value/>` == no reading.
 *
 * The arrays are aligned by index with the `<when>` list, so we zip them together and
 * drop entries that have no coordinate.
 */
object OpenTracksKmlParser {

    private val factory: XMLInputFactory = XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.IS_COALESCING, true)
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty("javax.xml.stream.isSupportingExternalEntities", false)
    }

    /** Parse an OpenTracks file by extension (.kmz or .kml). */
    fun parseFile(file: File): ParsedTrack = when {
        file.name.endsWith(".kmz", ignoreCase = true) ->
            file.inputStream().use { parseKmz(it, file.name) }
        else ->
            file.inputStream().use { parseKml(it, file.name) }
    }

    /** Parse a KMZ stream: find the `.kml` entry and parse it. */
    fun parseKmz(input: InputStream, sourceFileName: String): ParsedTrack {
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".kml", ignoreCase = true)) {
                    // Don't close `zip` via parseKml; read from a non-closing view.
                    return parseKml(zip, sourceFileName)
                }
                entry = zip.nextEntry
            }
        }
        error("No .kml entry found inside KMZ: $sourceFileName")
    }

    fun parseKml(input: InputStream, sourceFileName: String): ParsedTrack {
        val reader = factory.createXMLStreamReader(input)
        try {
            return readDocument(reader, sourceFileName)
        } finally {
            reader.close()
        }
    }

    private fun readDocument(reader: XMLStreamReader, sourceFileName: String): ParsedTrack {
        var trackId: String? = null
        var name: String? = null
        var activityType: String? = null
        val segments = mutableListOf<TrackSegment>()

        // Per-track accumulators.
        var inTrack = false
        var whens = mutableListOf<String>()
        var coords = mutableListOf<String>()
        var arrays = mutableMapOf<String, MutableList<String>>()
        var currentArray: MutableList<String>? = null
        var pendingDataName: String? = null

        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                    "trackid" -> trackId = reader.elementText.trim().ifBlank { null }
                    "Track" -> {
                        inTrack = true
                        whens = mutableListOf()
                        coords = mutableListOf()
                        arrays = mutableMapOf()
                    }
                    "when" -> if (inTrack) whens.add(reader.elementText.trim())
                    "coord" -> if (inTrack) coords.add(reader.elementText.trim())
                    "SimpleArrayData" -> {
                        val arrName = reader.getAttributeValue(null, "name") ?: ""
                        currentArray = mutableListOf<String>().also { arrays[arrName] = it }
                    }
                    "Data" -> pendingDataName = reader.getAttributeValue(null, "name")
                    "value" -> {
                        val text = reader.elementText.trim()
                        val arr = currentArray
                        when {
                            arr != null -> arr.add(text)
                            pendingDataName == "activityType" -> {
                                activityType = text.ifBlank { activityType }
                                pendingDataName = null
                            }
                            pendingDataName == "type" && activityType == null -> {
                                activityType = text.ifBlank { null }
                                pendingDataName = null
                            }
                            else -> pendingDataName = null
                        }
                    }
                    "name" -> if (name == null) name = reader.elementText.trim().ifBlank { null }
                }

                XMLStreamConstants.END_ELEMENT -> when (reader.localName) {
                    "SimpleArrayData" -> currentArray = null
                    "Track" -> {
                        inTrack = false
                        buildSegment(whens, coords, arrays)?.let { segments.add(it) }
                    }
                }
            }
        }

        return ParsedTrack(
            trackId = trackId,
            name = name,
            activityType = activityType,
            segments = segments,
            sourceFileName = sourceFileName,
        )
    }

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
            if (coord.isBlank()) continue // empty <coord/> == no GPS fix; skip the point
            val parts = coord.split(' ', '\t').filter { it.isNotBlank() }
            if (parts.size < 2) continue
            val lon = parts[0].toDoubleOrNull() ?: continue
            val lat = parts[1].toDoubleOrNull() ?: continue
            val ele = parts.getOrNull(2)?.toDoubleOrNull()
            val time = runCatching { Instant.parse(whens[i]) }.getOrNull() ?: continue

            points.add(
                GeoPoint(
                    time = time,
                    lat = lat,
                    lon = lon,
                    ele = ele,
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
