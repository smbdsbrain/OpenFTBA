package io.openftba.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Dark, instrument-cluster aesthetic — not default Material slop. Deep graphite base,
 * a restrained cyan/lime data accent, warm amber reserved for records/PRs, and a
 * separate gold→grey ramp for the S–F tiers (used in later waves).
 */
object Palette {
    val Base = Color(0xFF0C0E12)        // near-black graphite (app background)
    val Surface = Color(0xFF14171D)     // cards
    val SurfaceHigh = Color(0xFF1C2027) // elevated cards / hover
    val Outline = Color(0xFF2A2F38)     // hairline borders / gridlines
    val OnBase = Color(0xFFE6E9EF)      // primary text
    val OnMuted = Color(0xFF8A93A3)     // secondary text / labels

    val Accent = Color(0xFF5BE3C8)      // primary data accent (cyan-teal)
    val AccentAlt = Color(0xFFB5F23D)   // secondary accent (lime)
    val Record = Color(0xFFFFB347)      // records / personal bests (amber)
    val Danger = Color(0xFFFF6B6B)

    // Per-channel chart colors (kept distinct & legible on dark).
    val Speed = Color(0xFF5BE3C8)
    val Elevation = Color(0xFF9B8CFF)
    val HeartRate = Color(0xFFFF6B8A)
    val Cadence = Color(0xFFB5F23D)
    val Power = Color(0xFFFFB347)

    // Per-ride intensity tier colors (easy → all-out).
    val intensity = mapOf(
        "recovery" to Color(0xFF5BAEFF),
        "endurance" to Accent,
        "tempo" to AccentAlt,
        "race" to Record,
        "threshold_burn" to Danger,
    )

    // S–F tier ramp (gold → grey), used by gamification wave.
    val tier = mapOf(
        "S" to Color(0xFFFFD54A),
        "A" to Color(0xFF7BE38B),
        "B" to Color(0xFF5BE3C8),
        "C" to Color(0xFF5BAEFF),
        "D" to Color(0xFFB5A0FF),
        "E" to Color(0xFFC98A8A),
        "F" to Color(0xFF6B7280),
    )
}

private val OpenFtbaColors = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.Base,
    secondary = Palette.AccentAlt,
    background = Palette.Base,
    onBackground = Palette.OnBase,
    surface = Palette.Surface,
    onSurface = Palette.OnBase,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.OnMuted,
    outline = Palette.Outline,
    error = Palette.Danger,
)

// Tabular-figure feel for dense metrics; system default family for portability.
private val OpenFtbaTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 18.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.6.sp),
)

@Composable
fun OpenFtbaTheme(content: @Composable () -> Unit) {
    isSystemInDarkTheme() // dark-only product; referenced to avoid unused-import churn
    MaterialTheme(
        colorScheme = OpenFtbaColors,
        typography = OpenFtbaTypography,
        content = content,
    )
}
