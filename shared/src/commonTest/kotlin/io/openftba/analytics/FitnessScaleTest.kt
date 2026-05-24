package io.openftba.analytics

import io.openftba.settings.Sex
import kotlin.test.Test
import kotlin.test.assertEquals

class FitnessScaleTest {

    @Test
    fun powerWkgMapsToCogganBands() {
        // 300 W / 70 kg = 4.29 W/kg → B (>=3.7, <4.3)
        val r = FitnessScale.evaluate(AthleteProfile(weightKg = 70.0, ftpWatts = 300), emptyList())
        assertEquals(TierBasis.POWER_WKG, r.basis)
        assertEquals(AthleteTier.B, r.tier)

        // World class
        val s = FitnessScale.evaluate(AthleteProfile(weightKg = 65.0, ftpWatts = 360), emptyList())
        assertEquals(AthleteTier.S, s.tier) // 5.54 W/kg
    }

    @Test
    fun femaleShiftComparesAgainstMaleBands() {
        // 4.0 W/kg female + 0.4 → 4.4 → A; same raw value male → B.
        val female = FitnessScale.evaluate(AthleteProfile(weightKg = 50.0, ftpWatts = 200, sex = Sex.FEMALE), emptyList())
        val male = FitnessScale.evaluate(AthleteProfile(weightKg = 50.0, ftpWatts = 200, sex = Sex.MALE), emptyList())
        assertEquals(AthleteTier.A, female.tier)
        assertEquals(AthleteTier.B, male.tier)
    }

    @Test
    fun noPowerFallsBackToNoneWithoutRides() {
        val r = FitnessScale.evaluate(AthleteProfile(), emptyList())
        assertEquals(TierBasis.NONE, r.basis)
    }
}
