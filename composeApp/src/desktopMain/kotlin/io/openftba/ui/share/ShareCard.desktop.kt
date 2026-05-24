package io.openftba.ui.share

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.datatransfer.StringSelection
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Desktop share: draw a 1080×1080 dark card with Graphics2D, write a PNG into the user's
 * OpenFTBA folder, and copy the share text to the clipboard. No network involved.
 */
actual fun exportShareCard(spec: ShareSpec): String {
    val s = 1080
    val img = BufferedImage(s, s, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

    val base = Color(0x0C0E12)
    val muted = Color(0x8A93A3)
    val onBase = Color(0xE6E9EF)
    val surfaceHigh = Color(0x1C2027)
    val accent = Color(spec.accentArgb, true)

    g.color = base
    g.fillRect(0, 0, s, s)

    val pad = 80
    fun font(style: Int, size: Int) = Font("SansSerif", style, size)

    // Wordmark
    g.font = font(Font.BOLD, 34)
    g.color = muted
    g.drawString("OPEN", pad, 110)
    val ow = g.fontMetrics.stringWidth("OPEN")
    g.color = accent
    g.drawString("FTBA", pad + ow, 110)

    // Subtitle
    g.font = font(Font.PLAIN, 30)
    g.color = muted
    g.drawString(spec.subtitle, pad, 162)

    // Hero value
    g.color = onBase
    g.font = font(Font.BOLD, 190)
    g.drawString(spec.bigValue, pad - 4, 380)
    val bw = g.fontMetrics.stringWidth(spec.bigValue)
    g.font = font(Font.PLAIN, 56)
    g.color = muted
    g.drawString(spec.bigUnit, pad + bw + 16, 380)

    // Tier badge
    if (spec.tierLabel != null && spec.tierColorArgb != null) {
        val tc = Color(spec.tierColorArgb, true)
        val bx = s - pad - 360; val by = 250
        g.color = Color(tc.red, tc.green, tc.blue, 40)
        g.fill(RoundRectangle2D.Float(bx.toFloat(), by.toFloat(), 360f, 90f, 24f, 24f))
        g.color = tc
        g.draw(RoundRectangle2D.Float(bx.toFloat(), by.toFloat(), 360f, 90f, 24f, 24f))
        g.font = font(Font.BOLD, 44)
        val tw = g.fontMetrics.stringWidth(spec.tierLabel)
        g.drawString(spec.tierLabel, bx + 180 - tw / 2, by + 60)
    }

    // Sparkline band
    if (spec.spark.size >= 2) {
        drawSpark(g, spec.spark, accent, pad, 430, s - pad * 2, 200)
    }

    // Stats grid 2×N
    val gx = pad; val gy = 700; val colW = (s - pad * 2) / 2
    spec.stats.forEachIndexed { i, st ->
        val x = gx + (i % 2) * colW
        val y = gy + (i / 2) * 130
        g.font = font(Font.PLAIN, 26)
        g.color = muted
        g.drawString(st.label.uppercase(), x, y)
        g.font = font(Font.BOLD, 64)
        g.color = st.colorArgb?.let { Color(it, true) } ?: onBase
        g.drawString(st.value, x, y + 66)
    }

    // Footer
    g.font = font(Font.PLAIN, 26)
    g.color = surfaceHigh.brighter()
    g.drawString("local-only cycling analytics · no tracking", pad, s - 56)
    g.dispose()

    val outDir = File(System.getProperty("user.home"), "OpenFTBA").apply { mkdirs() }
    val out = File(outDir, "${spec.fileNameBase}-${System.currentTimeMillis()}.png")
    ImageIO.write(img, "png", out)

    runCatching {
        java.awt.Toolkit.getDefaultToolkit().systemClipboard
            .setContents(StringSelection(spec.shareText), null)
    }
    return "Saved ${out.name} · text copied"
}

private fun drawSpark(g: java.awt.Graphics2D, values: List<Double>, color: Color, x: Int, y: Int, w: Int, h: Int) {
    val mn = values.min(); val mx = values.max(); val span = (mx - mn).takeIf { it > 0 } ?: 1.0
    val xs = IntArray(values.size + 2)
    val ys = IntArray(values.size + 2)
    xs[0] = x; ys[0] = y + h
    values.forEachIndexed { i, v ->
        xs[i + 1] = x + (i.toDouble() / (values.size - 1) * w).toInt()
        ys[i + 1] = y + h - ((v - mn) / span * h).toInt()
    }
    xs[values.size + 1] = x + w; ys[values.size + 1] = y + h
    g.color = Color(color.red, color.green, color.blue, 70)
    g.fillPolygon(xs, ys, xs.size)
    g.color = color
    g.stroke = java.awt.BasicStroke(4f)
    for (i in 1 until values.size) {
        g.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1])
    }
}
