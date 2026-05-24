package io.openftba.analytics

/**
 * Provides terrain elevation (meters) at a coordinate, independent of the (often noisy)
 * GPS elevation in a track. Implementations read a local digital elevation model (DEM)
 * such as SRTM `.hgt` tiles. Pure interface so `commonMain` stays portable; the file-backed
 * implementation lives in `jvmMain`.
 */
interface ElevationProvider {
    /** Elevation in meters at lat/lon, or null if not covered / void. */
    fun elevationAt(lat: Double, lon: Double): Double?
}

/** Which elevation source the analyzer should use. */
enum class ElevationMode { SMOOTHED_GPS, DEM, IGNORE }
