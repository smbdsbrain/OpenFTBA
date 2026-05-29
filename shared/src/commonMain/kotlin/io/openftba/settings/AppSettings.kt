package io.openftba.settings

import kotlinx.serialization.Serializable

enum class UnitSystem { METRIC, IMPERIAL }

enum class Sex { MALE, FEMALE, UNSPECIFIED }

/** Persisted application settings. Serialized to a local JSON file (no network). */
@Serializable
data class AppSettings(
    val watchFolder: String? = null,
    val demFolder: String? = null,
    /** Ignore the track's GPS/baro elevation. DEM correction (when enabled) still applies. */
    val ignoreElevation: Boolean = false,
    val useDemElevation: Boolean = false,
    /** Sensor channels the user distrusts (names of SensorChannel), treated as absent. */
    val disabledChannels: Set<String> = emptySet(),
    val units: UnitSystem = UnitSystem.METRIC,
    val languageCode: String = "en",
    // Athlete profile (used by intensity/tier waves; collected up front).
    val weightKg: Double? = null,
    val sex: Sex = Sex.UNSPECIFIED,
    val maxHr: Int? = null,
    val restHr: Int? = null,
    val ftpWatts: Int? = null,
)
