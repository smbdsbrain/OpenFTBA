package io.openftba.analytics

import io.openftba.model.Ride
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** One day on the fitness/fatigue/form curve. */
data class LoadPoint(
    val epochDay: Long,
    val ctl: Double, // Fitness — 42-day load average
    val atl: Double, // Fatigue — 7-day load average
    val tsb: Double, // Form — fitness minus fatigue
)

/**
 * CTL / ATL / TSB training-load model (TrainingPeaks-style), driven by each ride's
 * `effortScore` (TSS-like). Daily load is summed per calendar day, then smoothed with
 * exponentially-weighted moving averages:
 *   CTL_t = CTL_{t-1} + (load_t − CTL_{t-1}) / 42   (Fitness)
 *   ATL_t = ATL_{t-1} + (load_t − ATL_{t-1}) / 7    (Fatigue)
 *   TSB_t = CTL_t − ATL_t                            (Form)
 * Days with no ride contribute zero load (rest lets fatigue decay and form rise).
 */
object TrainingLoad {

    private const val CTL_TAU = 42.0
    private const val ATL_TAU = 7.0

    fun compute(
        rides: List<Ride>,
        tz: TimeZone = TimeZone.currentSystemDefault(),
        today: LocalDate = Clock.System.now().toLocalDateTime(tz).date,
    ): List<LoadPoint> {
        if (rides.isEmpty()) return emptyList()

        val loadByDay = HashMap<Long, Double>()
        var firstDay = Long.MAX_VALUE
        for (r in rides) {
            val day = r.startTime.toLocalDateTime(tz).date.toEpochDays().toLong()
            loadByDay[day] = (loadByDay[day] ?: 0.0) + (r.metrics.effortScore ?: 0.0)
            if (day < firstDay) firstDay = day
        }
        val lastDay = today.toEpochDays().toLong()
        if (lastDay < firstDay) return emptyList()

        val out = ArrayList<LoadPoint>((lastDay - firstDay + 1).toInt().coerceAtLeast(1))
        var ctl = 0.0
        var atl = 0.0
        var day = firstDay
        while (day <= lastDay) {
            val load = loadByDay[day] ?: 0.0
            ctl += (load - ctl) / CTL_TAU
            atl += (load - atl) / ATL_TAU
            out.add(LoadPoint(day, ctl, atl, ctl - atl))
            day++
        }
        return out
    }

    /** Helper: epoch-day → LocalDate (for axis labels). */
    fun dateOf(epochDay: Long): LocalDate = LocalDate.fromEpochDays(epochDay.toInt())

    @Suppress("unused")
    private fun LocalDate.nextDay(): LocalDate = plus(1, DateTimeUnit.DAY)
}
