package io.openftba.ui.share

/**
 * Web build: serialize the spec to JSON and hand it to the bundled `share.js`, which draws
 * the 1080² card and shares it via the Web Share API (or downloads + copies text).
 */
actual fun exportShareCard(spec: ShareSpec): String {
    callOpenftbaShare(specToJson(spec))
    return ""
}

private fun callOpenftbaShare(json: String) {
    js("openftbaShare(json)")
}

private fun hex(argb: Int): String {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    fun h(v: Int) = v.toString(16).padStart(2, '0')
    return "#${h(r)}${h(g)}${h(b)}"
}

/** Alpha-aware CSS color — [hex] drops the alpha channel, so the track art must use this. */
private fun rgba(argb: Int): String {
    val a = ((argb shr 24) and 0xFF) / 255.0
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "rgba($r,$g,$b,$a)"
}

private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

private fun StringBuilder.appendNumArray(key: String, values: List<Double>) {
    append("\"").append(key).append("\":[").append(values.joinToString(",") { it.toString() }).append("],")
}

private fun StringBuilder.appendColorArray(key: String, values: List<Int>) {
    append("\"").append(key).append("\":[").append(values.joinToString(",") { "\"${rgba(it)}\"" }).append("],")
}

private fun specToJson(spec: ShareSpec): String {
    val sb = StringBuilder("{")
    sb.append("\"subtitle\":\"").append(esc(spec.subtitle)).append("\",")
    sb.append("\"bigValue\":\"").append(esc(spec.bigValue)).append("\",")
    sb.append("\"bigUnit\":\"").append(esc(spec.bigUnit)).append("\",")
    sb.append("\"accent\":\"").append(hex(spec.accentArgb)).append("\",")
    spec.tierLabel?.let { sb.append("\"tierLabel\":\"").append(esc(it)).append("\",") }
    spec.tierColorArgb?.let { sb.append("\"tierColor\":\"").append(hex(it)).append("\",") }
    sb.append("\"spark\":[").append(spec.spark.joinToString(",") { it.toString() }).append("],")
    spec.trackArt?.let { art ->
        sb.append("\"trackArt\":{")
        sb.appendNumArray("xs", art.xs)
        sb.appendNumArray("ys", art.ys)
        sb.appendColorArray("colors", art.colors)
        sb.appendNumArray("shadowXs", art.shadowXs)
        sb.appendNumArray("shadowYs", art.shadowYs)
        sb.appendColorArray("shadowColors", art.shadowColors)
        sb.appendNumArray("grid", art.grid)
        sb.appendNumArray("drops", art.drops)
        sb.appendColorArray("dropColors", art.dropColors)
        sb.append("\"gridColor\":\"").append(rgba(art.gridArgb)).append("\"},")
    }
    sb.append("\"stats\":[")
    sb.append(spec.stats.joinToString(",") { st ->
        val color = st.colorArgb?.let { ",\"color\":\"${hex(it)}\"" } ?: ""
        "{\"label\":\"${esc(st.label)}\",\"value\":\"${esc(st.value)}\"$color}"
    })
    sb.append("],")
    sb.append("\"text\":\"").append(esc(spec.shareText)).append("\",")
    sb.append("\"fileNameBase\":\"").append(esc(spec.fileNameBase)).append("\"}")
    return sb.toString()
}
