package io.openftba.ui.track3d

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.openftba.ui.theme.Palette

/** Which metric drives the per-point coloring of the 3D track line. */
enum class TrackGradient { SPEED, ELEVATION }

/** Multi-stop color ramps built from the existing palette (slow/low → fast/high). */
object TrackRamps {
    val speed = listOf(Color(0xFF5BAEFF), Palette.Accent, Palette.AccentAlt)
    val elevation = listOf(Palette.Accent, Palette.Elevation, Palette.Record)

    fun forGradient(g: TrackGradient): List<Color> = when (g) {
        TrackGradient.SPEED -> speed
        TrackGradient.ELEVATION -> elevation
    }
}

/** Piecewise-linear interpolation over [ramp] at parameter [t] in 0..1. */
fun rampColor(ramp: List<Color>, t: Float): Color {
    if (ramp.isEmpty()) return Palette.Accent
    if (ramp.size == 1) return ramp[0]
    val clamped = t.coerceIn(0f, 1f) * (ramp.size - 1)
    val i = clamped.toInt().coerceAtMost(ramp.size - 2)
    return lerp(ramp[i], ramp[i + 1], clamped - i)
}
