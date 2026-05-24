package io.openftba.analytics

import io.openftba.settings.Sex
import kotlin.math.E
import kotlin.math.pow

/** Which signal the intensity was derived from (drives a confidence hint in the UI). */
enum class IntensitySource { POWER, HEART_RATE, SPEED, NONE }

/**
 * Five auto-assigned intensity bands per ride, from easy stroll to all-out, keyed by an
 * intensity factor (IF ≈ effort relative to threshold). Keys map to i18n labels in the UI.
 */
enum class IntensityTier(val rank: Int, val key: String) {
    RECOVERY(1, "recovery"),
    ENDURANCE(2, "endurance"),
    TEMPO(3, "tempo"),
    RACE(4, "race"),
    THRESHOLD_BURN(5, "threshold_burn");

    companion object {
        fun fromIntensityFactor(intensityFactor: Double): IntensityTier = when {
            intensityFactor >= 0.95 -> THRESHOLD_BURN
            intensityFactor >= 0.85 -> RACE
            intensityFactor >= 0.75 -> TEMPO
            intensityFactor >= 0.65 -> ENDURANCE
            else -> RECOVERY
        }
    }
}

/** Athlete profile needed for HR/power-based intensity and the S–F scale. */
data class AthleteProfile(
    val weightKg: Double? = null,
    val sex: Sex = Sex.UNSPECIFIED,
    val maxHr: Int? = null,
    val restHr: Int? = null,
    val ftpWatts: Int? = null,
) {
    // Sensible fallbacks so HR-based effort still works without a fully filled profile.
    val effectiveMaxHr: Int get() = maxHr ?: 190
    val effectiveRestHr: Int get() = restHr ?: 60
}

data class IntensityResult(
    val source: IntensitySource,
    val intensityFactor: Double,
    val effortScore: Double,
    val normalizedPower: Double?,
    val tier: IntensityTier,
) {
    /** Lower confidence when derived from speed alone (no HR/power). */
    val lowConfidence: Boolean get() = source == IntensitySource.SPEED
}

/**
 * Composite intensity with graceful degradation, picking the best available signal:
 *   1. Power + FTP → IF = NP/FTP, effortScore = TSS.
 *   2. Heart rate → Banister TRIMP for load; IF from %HRR (≈85% HRR ~ threshold).
 *   3. Speed only → rough IF vs a reference pace (flagged low-confidence).
 *
 * `effortScore` is TSS-like (a 1-hour all-out effort ≈ 100).
 */
object IntensityCalculator {

    /** Reference flat-road pace (m/s) used only for the speed-only fallback (~25 km/h). */
    private const val REFERENCE_SPEED = 7.0

    fun evaluate(
        movingSeconds: Double,
        avgHeartRate: Double?,
        normalizedPower: Double?,
        avgSpeed: Double,
        profile: AthleteProfile,
    ): IntensityResult {
        val hours = movingSeconds / 3600.0
        val ftp = profile.ftpWatts?.takeIf { it > 0 }?.toDouble()

        // 1) Power + FTP.
        if (normalizedPower != null && ftp != null) {
            val ifv = (normalizedPower / ftp).coerceIn(0.0, 1.5)
            return result(IntensitySource.POWER, ifv, hours, normalizedPower)
        }

        // 2) Heart rate.
        if (avgHeartRate != null) {
            val hrr = ((avgHeartRate - profile.effectiveRestHr) /
                (profile.effectiveMaxHr - profile.effectiveRestHr)).coerceIn(0.0, 1.0)
            // ~85% HRR treated as threshold (IF = 1.0).
            val ifv = (hrr / 0.85).coerceIn(0.0, 1.3)
            return result(IntensitySource.HEART_RATE, ifv, hours, null)
        }

        // 3) Speed-only fallback.
        if (avgSpeed > 0) {
            val ifv = (avgSpeed / REFERENCE_SPEED).coerceIn(0.0, 1.3)
            return result(IntensitySource.SPEED, ifv, hours, null)
        }

        return IntensityResult(IntensitySource.NONE, 0.0, 0.0, null, IntensityTier.RECOVERY)
    }

    private fun result(source: IntensitySource, ifv: Double, hours: Double, np: Double?): IntensityResult {
        val effort = hours * ifv * ifv * 100.0
        return IntensityResult(source, ifv, effort, np, IntensityTier.fromIntensityFactor(ifv))
    }

    /**
     * Banister TRIMP from average HR (training load over the session). Exposed for callers
     * that want the classic load number alongside effortScore.
     */
    fun banisterTrimp(avgHeartRate: Double, movingSeconds: Double, profile: AthleteProfile): Double {
        val hrr = ((avgHeartRate - profile.effectiveRestHr) /
            (profile.effectiveMaxHr - profile.effectiveRestHr)).coerceIn(0.0, 1.0)
        val (k, b) = if (profile.sex == Sex.FEMALE) 0.86 to 1.67 else 0.64 to 1.92
        return (movingSeconds / 60.0) * hrr * k * E.pow(b * hrr)
    }

    /**
     * Normalized Power: 30-sample rolling average of power, raised to the 4th power, meaned,
     * then 4th-rooted. Points are ~irregular in time; a fixed sample window is a good-enough
     * approximation for typical 1 Hz OpenTracks data.
     */
    fun normalizedPower(power: List<Double>, window: Int = 30): Double? {
        val clean = power.filter { it >= 0 }
        if (clean.size < window) return clean.takeIf { it.isNotEmpty() }?.average()
        var sumPow4 = 0.0
        var count = 0
        var windowSum = 0.0
        for (i in clean.indices) {
            windowSum += clean[i]
            if (i >= window) windowSum -= clean[i - window]
            if (i >= window - 1) {
                val rollingAvg = windowSum / window
                sumPow4 += rollingAvg.pow(4)
                count++
            }
        }
        if (count == 0) return null
        return (sumPow4 / count).pow(0.25)
    }
}
