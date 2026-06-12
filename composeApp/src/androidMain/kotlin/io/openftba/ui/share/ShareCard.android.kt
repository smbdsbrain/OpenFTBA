package io.openftba.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Set by MainActivity so the share actual can reach a Context. */
object AndroidShareContext {
    var context: Context? = null
}

/**
 * Android share: render the spec to a 1080² Bitmap, save it via FileProvider, and fire
 * ACTION_SEND with the image + text (system share sheet → messengers/social apps).
 */
actual fun exportShareCard(spec: ShareSpec): String {
    val ctx = AndroidShareContext.context ?: return "no context"
    val bmp = renderCard(spec)
    val dir = File(ctx.cacheDir, "shares").apply { mkdirs() }
    val file = File(dir, "${spec.fileNameBase}-${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, spec.shareText)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "OpenFTBA").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(chooser)
    return ""
}

private fun renderCard(spec: ShareSpec): Bitmap {
    val s = 1080
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(Color.parseColor("#0C0E12"))
    val accent = spec.accentArgb
    val muted = Color.parseColor("#8A93A3")
    val onBase = Color.parseColor("#E6E9EF")

    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    fun text(str: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, center: Boolean = false) {
        p.color = color
        p.textSize = size
        p.typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        p.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        c.drawText(str, x, y, p)
    }

    val pad = 80f
    text("OPEN", pad, 110f, 34f, muted, bold = true)
    p.textSize = 34f; p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val ow = p.measureText("OPEN")
    text("FTBA", pad + ow, 110f, 34f, accent, bold = true)
    text(spec.subtitle, pad, 162f, 30f, muted)

    text(spec.bigValue, pad - 4f, 380f, 190f, onBase, bold = true)
    p.textSize = 190f; p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    val bw = p.measureText(spec.bigValue)
    text(spec.bigUnit, pad + bw + 16f, 380f, 56f, muted)

    val tier = spec.tierLabel
    val tierColor = spec.tierColorArgb
    if (tier != null && tierColor != null) {
        val bx = s - pad - 360f; val by = 250f
        p.style = Paint.Style.FILL; p.color = (tierColor and 0x00FFFFFF) or (40 shl 24)
        c.drawRoundRect(RectF(bx, by, bx + 360f, by + 90f), 24f, 24f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = tierColor
        c.drawRoundRect(RectF(bx, by, bx + 360f, by + 90f), 24f, 24f, p)
        p.style = Paint.Style.FILL
        text(tier, bx + 180f, by + 60f, 44f, tierColor, bold = true, center = true)
    }

    // Art band: 3D track silhouette when available, speed sparkline as the fallback.
    val art = spec.trackArt
    if (art != null && art.xs.size >= 2) {
        drawTrackArt(c, p, art, pad, 415f, s - pad * 2, 230f)
    } else if (spec.spark.size >= 2) {
        drawSpark(c, p, spec.spark, accent, pad, 430f, s - pad * 2, 200f)
    }

    val gx = pad; val gy = 700f; val colW = (s - pad * 2) / 2
    spec.stats.forEachIndexed { i, st ->
        val x = gx + (i % 2) * colW
        val y = gy + (i / 2) * 130f
        text(st.label.uppercase(), x, y, 26f, muted)
        text(st.value, x, y + 66f, 64f, st.colorArgb ?: onBase, bold = true)
    }
    text("local-only cycling analytics · no tracking", pad, s - 56f, 26f, Color.parseColor("#5A6372"))
    return bmp
}

/** Geometry and colors are fully precomputed (normalized 0..1) — just scale and stroke. */
private fun drawTrackArt(c: Canvas, p: Paint, art: ShareTrackArt, x: Float, y: Float, w: Float, h: Float) {
    fun px(v: Double) = x + (v * w).toFloat()
    fun py(v: Double) = y + (v * h).toFloat()
    p.style = Paint.Style.STROKE
    p.strokeCap = Paint.Cap.ROUND

    p.strokeWidth = 1f
    p.color = art.gridArgb
    for (i in 0 until art.grid.size / 4) {
        c.drawLine(px(art.grid[i * 4]), py(art.grid[i * 4 + 1]), px(art.grid[i * 4 + 2]), py(art.grid[i * 4 + 3]), p)
    }
    p.strokeWidth = 3f
    for (i in 0 until art.shadowXs.size - 1) {
        p.color = art.shadowColors[i]
        c.drawLine(px(art.shadowXs[i]), py(art.shadowYs[i]), px(art.shadowXs[i + 1]), py(art.shadowYs[i + 1]), p)
    }
    p.strokeWidth = 1.5f
    for (i in art.dropColors.indices) {
        p.color = art.dropColors[i]
        c.drawLine(px(art.drops[i * 4]), py(art.drops[i * 4 + 1]), px(art.drops[i * 4 + 2]), py(art.drops[i * 4 + 3]), p)
    }
    p.strokeWidth = 4f
    for (i in 0 until art.xs.size - 1) {
        p.color = art.colors[i]
        c.drawLine(px(art.xs[i]), py(art.ys[i]), px(art.xs[i + 1]), py(art.ys[i + 1]), p)
    }
    p.style = Paint.Style.FILL
    p.strokeCap = Paint.Cap.BUTT
}

private fun drawSpark(c: Canvas, p: Paint, values: List<Double>, color: Int, x: Float, y: Float, w: Float, h: Float) {
    val mn = values.min(); val mx = values.max(); val span = (mx - mn).takeIf { it > 0 } ?: 1.0
    fun px(i: Int) = x + (i.toFloat() / (values.size - 1)) * w
    fun py(v: Double) = y + h - ((v - mn) / span * h).toFloat()
    val fill = Path().apply {
        moveTo(x, y + h)
        values.forEachIndexed { i, v -> lineTo(px(i), py(v)) }
        lineTo(x + w, y + h); close()
    }
    p.style = Paint.Style.FILL; p.color = (color and 0x00FFFFFF) or (70 shl 24)
    c.drawPath(fill, p)
    val line = Path().apply {
        values.forEachIndexed { i, v -> if (i == 0) moveTo(px(i), py(v)) else lineTo(px(i), py(v)) }
    }
    p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = color
    c.drawPath(line, p)
    p.style = Paint.Style.FILL
}
