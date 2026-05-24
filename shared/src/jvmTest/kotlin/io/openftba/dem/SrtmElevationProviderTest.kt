package io.openftba.dem

import java.io.DataOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SrtmElevationProviderTest {

    /** Write a 2×2 synthetic tile (NW, NE, SW, SE) for N00E000. */
    private fun writeTile(folder: File, nw: Short, ne: Short, sw: Short, se: Short) {
        val f = File(folder, "N00E000.hgt")
        DataOutputStream(f.outputStream()).use { d ->
            d.writeShort(nw.toInt()); d.writeShort(ne.toInt()) // north row: west, east
            d.writeShort(sw.toInt()); d.writeShort(se.toInt()) // south row: west, east
        }
    }

    @Test
    fun bilinearInterpolatesAcrossTile() {
        val dir = createTempDir("dem")
        writeTile(dir, nw = 100, ne = 200, sw = 0, se = 100)
        val p = SrtmElevationProvider(dir)

        // Center of the degree square ≈ mean of corners weighted equally = 100.
        assertEquals(100.0, p.elevationAt(0.5, 0.5)!!, 0.5)
        // Near the SW corner → close to SW value (0).
        assertTrue(p.elevationAt(0.02, 0.02)!! < 10.0)
        // Near the NE corner → close to NE value (200).
        assertTrue(p.elevationAt(0.98, 0.98)!! > 190.0)
        dir.deleteRecursively()
    }

    @Test
    fun missingTileReturnsNull() {
        val dir = createTempDir("dem")
        val p = SrtmElevationProvider(dir)
        assertNull(p.elevationAt(50.0, 50.0))
        dir.deleteRecursively()
    }

    @Test
    fun tileNameMatchesSrtmConvention() {
        assertEquals("N60E029", SrtmDownloader.tileName(60.04, 29.99))
        assertEquals("S01W001", SrtmDownloader.tileName(-0.5, -0.5))
    }
}
