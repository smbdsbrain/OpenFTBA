package io.openftba.ui.overview

import io.openftba.model.Ride
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class Record(val rideId: String, val value: Double)

/** One calendar-heatmap cell: its date, that day's total distance, and 0..1 intensity. */
data class HeatCell(val date: LocalDate, val distanceMeters: Double, val intensity: Double)

data class OverviewStats(
    val totalRides: Int,
    val totalDistanceMeters: Double,
    val totalMovingSeconds: Double,
    val totalElevationGain: Double,
    val avgRideDistanceMeters: Double,
    val maxSpeed: Record?,
    val longestNonStop: Record?,
    val longestRide: Record?,
    val biggestClimb: Record?,
    val maxRideElevation: Record?,
    val bestAvgSpeed: Record?,
    /** Heatmap: columns = weeks (oldest→newest), each 7 cells (null = no cell), Mon→Sun. */
    val heatmapCells: List<List<HeatCell?>>,
    /** Distance (meters) of the most recent rides, oldest→newest, for the trend bar chart. */
    val recentDistances: List<Double>,
) {
    companion object {
        private const val HEATMAP_WEEKS = 26

        fun from(rides: List<Ride>, tz: TimeZone = TimeZone.currentSystemDefault()): OverviewStats {
            if (rides.isEmpty()) {
                return OverviewStats(0, 0.0, 0.0, 0.0, 0.0, null, null, null, null, null, null, emptyList<List<HeatCell?>>(), emptyList())
            }
            val totalDistance = rides.sumOf { it.metrics.distanceMeters }
            val totalMoving = rides.sumOf { it.metrics.movingTimeSeconds }
            val totalGain = rides.sumOf { it.metrics.elevationGain }

            fun record(selector: (Ride) -> Double): Record? =
                rides.maxByOrNull(selector)?.let { Record(it.id, selector(it)) }

            return OverviewStats(
                totalRides = rides.size,
                totalDistanceMeters = totalDistance,
                totalMovingSeconds = totalMoving,
                totalElevationGain = totalGain,
                avgRideDistanceMeters = totalDistance / rides.size,
                maxSpeed = record { it.metrics.maxSpeed },
                longestNonStop = record { it.metrics.longestNonStopMeters },
                longestRide = record { it.metrics.distanceMeters },
                biggestClimb = record { it.metrics.biggestClimbMeters },
                maxRideElevation = record { it.metrics.elevationGain },
                bestAvgSpeed = record { it.metrics.avgSpeed },
                heatmapCells = buildHeatmap(rides, tz),
                recentDistances = rides.sortedBy { it.startTime }
                    .takeLast(20).map { it.metrics.distanceMeters },
            )
        }

        private fun buildHeatmap(rides: List<Ride>, tz: TimeZone): List<List<HeatCell?>> {
            val perDay = HashMap<LocalDate, Double>()
            for (r in rides) {
                val d = r.startTime.toLocalDateTime(tz).date
                perDay[d] = (perDay[d] ?: 0.0) + r.metrics.distanceMeters
            }
            if (perDay.isEmpty()) return emptyList()
            val maxDay = perDay.values.max().takeIf { it > 0 } ?: 1.0
            val lastDate = perDay.keys.max()
            // Anchor the grid so the last column ends on the most recent ride's week (Mon-aligned).
            val endMondayOffset = lastDate.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber
            val gridEndMonday = lastDate.minus(endMondayOffset, DateTimeUnit.DAY)
            val gridStart = gridEndMonday.minus((HEATMAP_WEEKS - 1) * 7, DateTimeUnit.DAY)

            val weeks = ArrayList<List<HeatCell?>>(HEATMAP_WEEKS)
            for (w in 0 until HEATMAP_WEEKS) {
                val col = ArrayList<HeatCell?>(7)
                for (day in 0 until 7) {
                    val date = gridStart.plus(w * 7 + day, DateTimeUnit.DAY)
                    if (date > lastDate) col.add(null)
                    else {
                        val dist = perDay[date] ?: 0.0
                        col.add(HeatCell(date, dist, dist / maxDay))
                    }
                }
                weeks.add(col)
            }
            return weeks
        }
    }
}
