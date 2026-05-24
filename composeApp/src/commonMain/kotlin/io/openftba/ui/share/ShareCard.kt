package io.openftba.ui.share

/** One stat line on the share card. */
data class ShareStat(val label: String, val value: String, val colorArgb: Int? = null)

/** Platform-agnostic description of a share card; rendered to an image per platform. */
data class ShareSpec(
    val subtitle: String,
    val bigValue: String,
    val bigUnit: String,
    val accentArgb: Int,
    val tierLabel: String? = null,
    val tierColorArgb: Int? = null,
    val spark: List<Double> = emptyList(),
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
