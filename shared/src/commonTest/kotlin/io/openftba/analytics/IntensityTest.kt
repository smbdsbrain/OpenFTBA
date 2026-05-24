package io.openftba.analytics

import io.openftba.settings.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntensityTest {

    private val profile = AthleteProfile(maxHr = 190, restHr = 60, weightKg = 75.0, ftpWatts = 225)

    @Test
    fun powerPathUsesIfFromNpAndFtp() {
        // NP ~ FTP → IF ~ 1.0 → THRESHOLD_BURN; power wins over HR when both present.
        val r = IntensityCalculator.evaluate(
            movingSeconds = 3600.0, avgHeartRate = 120.0, normalizedPower = 225.0, avgSpeed = 8.0, profile = profile,
        )
        assertEquals(IntensitySource.POWER, r.source)
        assertEquals(1.0, r.intensityFactor, 0.01)
        assertEquals(IntensityTier.THRESHOLD_BURN, r.tier)
        assertEquals(100.0, r.effortScore, 1.0) // 1h at IF 1.0 ≈ 100
    }

    @Test
    fun heartRatePathWhenNoPower() {
        // avgHR 150, HRR=(150-60)/(190-60)=0.69 → IF=0.69/0.85≈0.81 → TEMPO
        val r = IntensityCalculator.evaluate(
            movingSeconds = 3600.0, avgHeartRate = 150.0, normalizedPower = null, avgSpeed = 8.0,
            profile = AthleteProfile(maxHr = 190, restHr = 60),
        )
        assertEquals(IntensitySource.HEART_RATE, r.source)
        assertEquals(IntensityTier.TEMPO, r.tier)
        assertTrue(r.intensityFactor in 0.78..0.84)
    }

    @Test
    fun speedFallbackIsLowConfidence() {
        val r = IntensityCalculator.evaluate(
            movingSeconds = 3600.0, avgHeartRate = null, normalizedPower = null, avgSpeed = 7.0,
            profile = AthleteProfile(),
        )
        assertEquals(IntensitySource.SPEED, r.source)
        assertTrue(r.lowConfidence)
    }

    @Test
    fun banisterTrimpFemaleVsMaleDiffers() {
        val male = IntensityCalculator.banisterTrimp(150.0, 3600.0, AthleteProfile(sex = Sex.MALE, maxHr = 190, restHr = 60))
        val female = IntensityCalculator.banisterTrimp(150.0, 3600.0, AthleteProfile(sex = Sex.FEMALE, maxHr = 190, restHr = 60))
        assertTrue(male > 0 && female > 0)
        assertTrue(male != female)
    }

    @Test
    fun normalizedPowerOfConstantEqualsValue() {
        val np = IntensityCalculator.normalizedPower(List(120) { 200.0 })
        assertEquals(200.0, np!!, 0.001)
    }
}
