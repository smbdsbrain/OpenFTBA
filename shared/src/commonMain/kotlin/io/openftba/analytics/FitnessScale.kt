package io.openftba.analytics

import io.openftba.model.Ride
import io.openftba.settings.Sex

/** Gamified athlete tier, anchored to real cycling benchmarks (Coggan FTP W/kg). */
enum class AthleteTier(val key: String) {
    S("s"), A("a"), B("b"), C("c"), D("d"), E("e"), F("f")
}

enum class TierBasis { POWER_WKG, SPEED_PROXY, NONE }

data class FitnessResult(
    val tier: AthleteTier,
    val basis: TierBasis,
    /** W/kg when power-based; a km/h proxy figure otherwise. For display/debug. */
    val value: Double?,
    /** 0..1 progress toward the next-higher tier (for a progress bar). */
    val progressToNext: Double,
)

/**
 * Overall athlete level on an S–F scale.
 *
 * Preferred: FTP watts / weight → W/kg, mapped to Coggan male bands (female shifted down
 * ~0.4 W/kg). Without a power meter + weight, falls back to a rough speed proxy from the
 * athlete's best recent rides — explicitly lower-confidence (basis = SPEED_PROXY).
 */
object FitnessScale {

    // Lower bound of each tier in W/kg (male FTP). S is open-topped.
    private val wkgLowerBounds = listOf(
        AthleteTier.S to 5.15,
        AthleteTier.A to 4.3,
        AthleteTier.B to 3.7,
        AthleteTier.C to 2.9,
        AthleteTier.D to 2.2,
        AthleteTier.E to 1.5,
        AthleteTier.F to 0.0,
    )

    // Rough avg-speed (km/h) lower bounds for the no-power proxy (best recent ride).
    private val speedLowerBounds = listOf(
        AthleteTier.S to 32.0,
        AthleteTier.A to 28.0,
        AthleteTier.B to 25.0,
        AthleteTier.C to 22.0,
        AthleteTier.D to 19.0,
        AthleteTier.E to 15.0,
        AthleteTier.F to 0.0,
    )

    fun evaluate(profile: AthleteProfile, rides: List<Ride>): FitnessResult {
        val ftp = profile.ftpWatts?.takeIf { it > 0 }
        val weight = profile.weightKg?.takeIf { it > 0 }

        if (ftp != null && weight != null) {
            var wkg = ftp / weight
            if (profile.sex == Sex.FEMALE) wkg += 0.4 // compare against male bands
            return classify(wkg, wkgLowerBounds, TierBasis.POWER_WKG, displayValue = ftp / weight)
        }

        // Speed proxy: best avg speed (km/h) across recent rides with real distance.
        val bestKmh = rides
            .filter { it.metrics.distanceMeters > 1000 }
            .maxOfOrNull { it.metrics.avgSpeed * 3.6 }
        if (bestKmh != null) {
            var v = bestKmh
            if (profile.sex == Sex.FEMALE) v += 2.0 // shift up to compare against male bands
            return classify(v, speedLowerBounds, TierBasis.SPEED_PROXY, displayValue = bestKmh)
        }

        return FitnessResult(AthleteTier.F, TierBasis.NONE, null, 0.0)
    }

    private fun classify(
        value: Double,
        bounds: List<Pair<AthleteTier, Double>>,
        basis: TierBasis,
        displayValue: Double,
    ): FitnessResult {
        val idx = bounds.indexOfFirst { value >= it.second }.coerceAtLeast(0)
        val tier = bounds[idx].first
        val lower = bounds[idx].second
        val upper = if (idx == 0) lower * 1.2 else bounds[idx - 1].second
        val progress = if (upper > lower) ((value - lower) / (upper - lower)).coerceIn(0.0, 1.0) else 1.0
        return FitnessResult(tier, basis, displayValue, progress)
    }
}
