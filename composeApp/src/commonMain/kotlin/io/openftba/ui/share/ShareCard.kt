package io.openftba.ui.share

/** One stat line on the share card. */
data class ShareStat(val label: String, val value: String, val colorArgb: Int? = null)

/**
 * Precomputed 3D silhouette of the track for the share card: the projected line, its floor
 * shadow, and the floor grid, all in coordinates normalized to 0..1 within the card's art band
 * (band aspect already applied). Colors are fully baked (alpha included) so platform renderers
 * only scale points and stroke segments.
 */
data class ShareTrackArt(
    val xs: List<Double>,
    val ys: List<Double>,
    val colors: List<Int>,          // per-point ARGB for the track line
    val shadowXs: List<Double>,
    val shadowYs: List<Double>,
    val shadowColors: List<Int>,    // per-point ARGB, alpha pre-applied
    val grid: List<Double> = emptyList(),  // flat [x0, y0, x1, y1] per grid segment
    val gridArgb: Int = 0,
    val drops: List<Double> = emptyList(), // sparse vertical drop lines, flat [x0, y0, x1, y1]
    val dropColors: List<Int> = emptyList(), // one ARGB per drop segment, alpha pre-applied
)

/** Platform-agnostic description of a share card; rendered to an image per platform. */
data class ShareSpec(
    val subtitle: String,
    val bigValue: String,
    val bigUnit: String,
    val accentArgb: Int,
    val tierLabel: String? = null,
    val tierColorArgb: Int? = null,
    val spark: List<Double> = emptyList(),
    val trackArt: ShareTrackArt? = null,   // 3D silhouette; falls back to [spark] when null
    val stats: List<ShareStat> = emptyList(),
    val shareText: String,
    val fileNameBase: String = "openftba",
)

/**
 * Render [spec] to a share image and hand it off via the platform's mechanism, returning a
 * short human-readable status. Desktop saves a PNG + copies the text; Android (later) uses
 * ACTION_SEND; web uses the Web Share API.
 */
expect fun exportShareCard(spec: ShareSpec): String
