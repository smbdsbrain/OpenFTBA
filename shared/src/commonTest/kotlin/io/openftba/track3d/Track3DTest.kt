package io.openftba.track3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** All coordinates are synthetic — never use real GPS data in tests. */
class Track3DTest {

    @Test
    fun buildModel_rejectsDegenerateInput() {
        assertNull(Track3D.buildModel(emptyList(), emptyList(), emptyList()))
        assertNull(Track3D.buildModel(listOf(45.0), listOf(10.0), listOf(0.0)))
        assertNull(Track3D.buildModel(listOf(45.0, 45.1), listOf(10.0), emptyList()))
        // Identical points: horizontal extent < 1 m.
        assertNull(Track3D.buildModel(listOf(45.0, 45.0), listOf(10.0, 10.0), listOf(0.0, 100.0)))
    }

    @Test
    fun buildModel_normalizesToUnitBoxCenteredAtOrigin() {
        // A 0.01°×0.01° square around (45, 10); latitude span is the longer axis
        // (longitude is shrunk by cos 45°).
        val lat = listOf(45.0, 45.0, 45.01, 45.01)
        val lon = listOf(10.0, 10.01, 10.01, 10.0)
        val m = assertNotNull(Track3D.buildModel(lat, lon, listOf(100.0, 110.0, 120.0, 130.0)))
        assertEquals(1.0, m.zs.max() - m.zs.min(), 1e-9)
        assertEquals(0.0, m.zs.max() + m.zs.min(), 1e-9)   // centered
        assertEquals(0.0, m.xs.max() + m.xs.min(), 1e-9)
        assertTrue(m.xs.max() - m.xs.min() < 1.0)           // shorter axis
        // Vertical span centered on 0 with the floor at the bottom.
        assertEquals(-(m.ys.max()), m.floorY, 1e-9)
        assertEquals(m.ys.min(), m.floorY, 1e-9)
    }

    @Test
    fun buildModel_exaggerationClampsBothWays() {
        val lat = listOf(45.0, 45.01)
        val lon = listOf(10.0, 10.0)
        // Tiny elevation range on a ~1.1 km track: raw ratio would exceed the cap.
        val tiny = assertNotNull(Track3D.buildModel(lat, lon, listOf(0.0, 1.0), maxExaggeration = 8.0))
        val tinyHeight = tiny.ys.max() - tiny.ys.min()
        assertEquals(8.0 * 1.0 / 1112.0, tinyHeight, 1e-3)  // exag clamped to 8, scale = 1/extent
        // Huge elevation range: exaggeration floors at 1 (never compress below true scale).
        val steep = assertNotNull(Track3D.buildModel(lat, lon, listOf(0.0, 800.0)))
        val steepHeight = steep.ys.max() - steep.ys.min()
        assertEquals(800.0 / 1112.0, steepHeight, 1e-2)
    }

    @Test
    fun buildModel_flatTrackGetsSyntheticLift() {
        val m = assertNotNull(Track3D.buildModel(listOf(45.0, 45.01), listOf(10.0, 10.0), listOf(50.0, 50.0)))
        assertEquals(Track3D.FLAT_TRACK_HEIGHT / 2.0, m.ys[0], 1e-9)
        assertEquals(-Track3D.FLAT_TRACK_HEIGHT / 2.0, m.floorY, 1e-9)
        // Missing elevation behaves the same as constant elevation.
        val noEle = assertNotNull(Track3D.buildModel(listOf(45.0, 45.01), listOf(10.0, 10.0), emptyList()))
        assertEquals(m.ys[0], noEle.ys[0], 1e-9)
    }

    @Test
    fun projectAll_identityAndAxisSwaps() {
        val xs = doubleArrayOf(0.3); val ys = doubleArrayOf(0.2); val zs = doubleArrayOf(0.1)
        val ortho = 1e9   // effectively orthographic

        val id = Track3D.projectAll(xs, ys, zs, yaw = 0.0, pitch = 0.0, cameraDist = ortho)
        assertEquals(0.3f, id[0], 1e-6f)
        assertEquals(-0.2f, id[1], 1e-6f)  // screen y grows downward

        val yawed = Track3D.projectAll(xs, ys, zs, yaw = PI / 2, pitch = 0.0, cameraDist = ortho)
        assertEquals(-0.1f, yawed[0], 1e-6f)   // x' = -z
        assertEquals(-0.2f, yawed[1], 1e-6f)

        val topDown = Track3D.projectAll(xs, ys, zs, yaw = 0.0, pitch = PI / 2, cameraDist = ortho)
        assertEquals(0.3f, topDown[0], 1e-6f)
        assertEquals(-0.1f, topDown[1], 1e-6f)  // sy = -z (north points up-screen)
    }

    @Test
    fun projectAll_positivePitchLooksDownFromAbove() {
        // A floor point to the north must rise toward the top of the screen and move away from
        // the camera — otherwise the scene reads as viewed from below.
        val north = Track3D.projectAll(doubleArrayOf(0.0), doubleArrayOf(0.0), doubleArrayOf(1.0), yaw = 0.0, pitch = 0.5)
        assertTrue(north[1] < 0f, "north floor point should project above center")
        // And a point above the floor still reads as "up".
        val up = Track3D.projectAll(doubleArrayOf(0.0), doubleArrayOf(1.0), doubleArrayOf(0.0), yaw = 0.0, pitch = 0.5)
        assertTrue(up[1] < 0f, "elevated point should project above center")
        // Depth check via perspective: the north point must shrink (be farther) vs the south one.
        val south = Track3D.projectAll(doubleArrayOf(0.3), doubleArrayOf(0.0), doubleArrayOf(-1.0), yaw = 0.0, pitch = 0.5)
        val northX = Track3D.projectAll(doubleArrayOf(0.3), doubleArrayOf(0.0), doubleArrayOf(1.0), yaw = 0.0, pitch = 0.5)
        assertTrue(abs(south[0]) > abs(northX[0]), "south (near) should project larger than north (far)")
    }

    @Test
    fun projectAll_perspectiveShrinksFarPoints() {
        val xs = doubleArrayOf(0.5, 0.5)
        val ys = doubleArrayOf(0.0, 0.0)
        val zs = doubleArrayOf(-0.5, 0.5)  // near, far
        val p = Track3D.projectAll(xs, ys, zs, yaw = 0.0, pitch = 0.0, cameraDist = 3.5)
        assertTrue(abs(p[0]) > abs(p[2]), "near point should project larger than far point")
    }

    @Test
    fun gradientParams_normalizesAndHandlesConstant() {
        val ramp = Track3D.gradientParams(listOf(10.0, 15.0, 20.0))
        assertEquals(0.0f, ramp[0], 1e-6f)
        assertEquals(0.5f, ramp[1], 1e-6f)
        assertEquals(1.0f, ramp[2], 1e-6f)
        Track3D.gradientParams(listOf(7.0, 7.0, 7.0)).forEach { assertEquals(0.5f, it, 1e-6f) }
        assertEquals(0, Track3D.gradientParams(emptyList()).size)
    }

    @Test
    fun floorGrid_liesOnFloorAndCoversFootprint() {
        val m = assertNotNull(
            Track3D.buildModel(listOf(45.0, 45.01), listOf(10.0, 10.005), listOf(0.0, 50.0)),
        )
        val grid = Track3D.floorGrid(m, n = 4)
        assertEquals(10, grid.size)  // (n+1) lines each way
        grid.forEach { line ->
            assertEquals(m.floorY, line[1], 1e-9)
            assertEquals(m.floorY, line[4], 1e-9)
        }
        // The grid square covers the track footprint.
        val gxMin = grid.minOf { minOf(it[0], it[3]) }
        val gxMax = grid.maxOf { maxOf(it[0], it[3]) }
        assertTrue(gxMin <= m.xs.min() && gxMax >= m.xs.max())
    }
}
