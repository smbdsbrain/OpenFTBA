package io.openftba.parse

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenTracksKmlParserTest {

    private val sampleKml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.3" xmlns:opentracks="http://opentracksapp.com/xmlschemas/v1">
        <Document>
        <name><![CDATA[Test Ride]]></name>
        <Placemark>
        <opentracks:trackid>uuid-123</opentracks:trackid>
        <ExtendedData><Data name="activityType"><value><![CDATA[biking]]></value></Data></ExtendedData>
        <MultiTrack>
        <Track>
        <when>2020-01-01T10:00:00.000+00:00</when>
        <coord/>
        <when>2020-01-01T10:00:05.000+00:00</when>
        <coord>10.000000 50.000000 27.8</coord>
        <when>2020-01-01T10:00:15.000+00:00</when>
        <coord>10.000100 50.000100 26.5</coord>
        <ExtendedData><SchemaData>
        <SimpleArrayData name="trackpoint_type"><value>SEGMENT_START_MANUAL</value><value>TRACKPOINT</value><value>TRACKPOINT</value></SimpleArrayData>
        <SimpleArrayData name="speed"><value/><value>3.2</value><value>4.1</value></SimpleArrayData>
        <SimpleArrayData name="cadence"><value/><value/><value/></SimpleArrayData>
        <SimpleArrayData name="heartrate"><value/><value>110</value><value>115</value></SimpleArrayData>
        </SchemaData></ExtendedData>
        </Track>
        </MultiTrack>
        </Placemark>
        </Document>
        </kml>
    """.trimIndent()

    private fun parse() =
        OpenTracksKmlParser.parseKml(ByteArrayInputStream(sampleKml.toByteArray()), "test.kml")

    @Test
    fun parsesIdentityAndActivity() {
        val track = parse()
        assertEquals("uuid-123", track.trackId)
        assertEquals("Test Ride", track.name)
        assertEquals("biking", track.activityType)
    }

    @Test
    fun skipsEmptyCoordAndAlignsSensorArrays() {
        val track = parse()
        assertEquals(1, track.segments.size)
        val points = track.segments.first().points
        // First (empty <coord/>) is dropped; two valid points remain.
        assertEquals(2, points.size)

        val p0 = points[0]
        assertEquals(50.000000, p0.lat, 1e-6)   // lat is 2nd token
        assertEquals(10.000000, p0.lon, 1e-6)   // lon is 1st token
        assertEquals(27.8, p0.ele!!, 1e-6)
        assertEquals(3.2, p0.speed!!, 1e-6)      // sensor index aligned past the empty entry
        assertEquals(110.0, p0.heartRate!!, 1e-6)
        assertEquals(null, p0.cadence)           // all-empty cadence array stays null
    }

    @Test
    fun detectsAvailableChannels() {
        val track = parse()
        val pts = track.allPoints
        assertNotNull(pts)
        assertTrue(pts.all { it.power == null })           // no power array
        assertTrue(pts.any { it.heartRate != null })       // hr present
        assertTrue(pts.any { it.speed != null })           // speed present
    }
}
