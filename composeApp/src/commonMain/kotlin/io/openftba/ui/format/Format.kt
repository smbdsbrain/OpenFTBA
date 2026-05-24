package io.openftba.ui.format

import io.openftba.settings.UnitSystem
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Locale-agnostic numeric formatting for dense metric display. */
object Format {

    private fun round(value: Double, decimals: Int): String {
        if (value.isNaN() || value.isInfinite()) return "—"
        var factor = 1.0
        repeat(decimals) { factor *= 10 }
        val r = (value * factor).roundToLong() / factor
        if (decimals == 0) return r.roundToInt().toString()
        // Build fixed-decimal string without platform locale dependence.
        val s = r.toString()
        val dot = s.indexOf('.')
        return if (dot < 0) "$s.${"0".repeat(decimals)}"
        else {
            val frac = s.substring(dot + 1)
            s.substring(0, dot) + "." + (frac + "0".repeat(decimals)).take(decimals)
        }
    }

    fun distance(meters: Double, units: UnitSystem): String = when (units) {
        UnitSystem.METRIC -> if (meters >= 1000) "${round(meters / 1000.0, 1)} km" else "${meters.roundToInt()} m"
        UnitSystem.IMPERIAL -> {
            val miles = meters / 1609.344
            if (miles >= 0.1) "${round(miles, 1)} mi" else "${(meters / 0.3048).roundToInt()} ft"
        }
    }

    fun elevation(meters: Double, units: UnitSystem): String = when (units) {
        UnitSystem.METRIC -> "${meters.roundToInt()} m"
        UnitSystem.IMPERIAL -> "${(meters / 0.3048).roundToInt()} ft"
    }

    /** Speed input is m/s. */
    fun speed(metersPerSecond: Double, units: UnitSystem): String = when (units) {
        UnitSystem.METRIC -> "${round(metersPerSecond * 3.6, 1)} km/h"
        UnitSystem.IMPERIAL -> "${round(metersPerSecond * 2.236936, 1)} mph"
    }

    fun duration(seconds: Double): String {
        val total = seconds.roundToLong()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "${h}h ${m.pad()}m" else "${m}m ${s.pad()}s"
    }

    fun durationClock(seconds: Double): String {
        val total = seconds.roundToLong()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "$h:${m.pad()}:${s.pad()}" else "${m}:${s.pad()}"
    }

    fun bpm(value: Double?): String = value?.let { "${it.roundToInt()} bpm" } ?: "—"
    fun rpm(value: Double?): String = value?.let { "${it.roundToInt()} rpm" } ?: "—"
    fun watts(value: Double?): String = value?.let { "${it.roundToInt()} W" } ?: "—"
    fun int(value: Double): String = value.roundToInt().toString()

    private fun Long.pad(): String = if (this < 10) "0$this" else toString()
}
