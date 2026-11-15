package ru.azimut.app.core.export

import ru.azimut.app.core.model.GeoPoint
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

/** Desktop renderer: java.awt offscreen image, PNG via ImageIO. */
actual object TrackImageRenderer {
    actual fun renderPng(sheet: TrackSheet, width: Int, height: Int): ByteArray? = runCatching {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x18, 0x1D, 0x16)
        g.fillRect(0, 0, width, height)

        val textHeight = 18
        val lines = sheet.lines.size
        val textBlock = lines * (textHeight + 4) + 12
        val mapHeight = height - textBlock
        val profileHeight = if (sheet.heights.any { it != null }) 110 else 0
        val sketchHeight = mapHeight - profileHeight

        drawSketch(g, sheet, 0, 0, width, sketchHeight)
        if (profileHeight > 0) drawProfile(g, sheet, 0, sketchHeight, width, profileHeight)

        g.color = Color.WHITE
        g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, textHeight - 2)
        var y = sketchHeight + profileHeight + textHeight
        for (line in sheet.lines) {
            g.drawString(line, 10, y)
            y += textHeight + 4
        }
        g.dispose()
        ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }.getOrNull()

    private fun drawSketch(g: Graphics2D, sheet: TrackSheet, x: Int, y: Int, w: Int, h: Int) {
        val pts = sheet.track.points
        if (pts.isEmpty()) return
        var latMin = 90.0; var latMax = -90.0; var lonMin = 180.0; var lonMax = -180.0
        for (p in pts) {
            latMin = min(latMin, p.lat); latMax = max(latMax, p.lat)
            lonMin = min(lonMin, p.lon); lonMax = max(lonMax, p.lon)
        }
        for (wp in sheet.waypoints) {
            latMin = min(latMin, wp.lat); latMax = max(latMax, wp.lat)
            lonMin = min(lonMin, wp.lon); lonMax = max(lonMax, wp.lon)
        }
        val margin = 24
        val spanLat = max(latMax - latMin, 1e-5)
        val spanLon = max(lonMax - lonMin, 1e-5)
        val lonScale = kotlin.math.cos(((latMin + latMax) / 2) * Math.PI / 180.0)
        val scaleX = (w - margin * 2) / (spanLon * lonScale)
        val scaleY = (h - margin * 2) / spanLat
        val scale = min(scaleX, scaleY)
        fun px(lat: Double, lon: Double): Pair<Float, Float> {
            val fx = (lon - (lonMin + lonMax) / 2) * lonScale * scale + w / 2.0
            val fy = ((latMin + latMax) / 2 - lat) * scale + h / 2.0
            return (x + fx).toFloat() to (y + fy).toFloat()
        }

        val path = Path2D.Float()
        pts.forEachIndexed { i, p ->
            val (fx, fy) = px(p.lat, p.lon)
            if (i == 0) path.moveTo(fx, fy) else path.lineTo(fx, fy)
        }
        g.stroke = BasicStroke(2.4f)
        g.color = Color(0xFF, 0x6D, 0x00)
        g.draw(path)

        if (sheet.backgroundLine.size >= 2) {
            val bg = Path2D.Float()
            sheet.backgroundLine.forEachIndexed { i, p ->
                val (fx, fy) = px(p.lat, p.lon)
                if (i == 0) bg.moveTo(fx, fy) else bg.lineTo(fx, fy)
            }
            g.stroke = BasicStroke(1.6f)
            g.color = Color(0x78, 0x90, 0x9C)
            g.draw(bg)
        }
        g.color = Color(0x2E, 0x7D, 0x32)
        for (wp in sheet.waypoints) {
            val (fx, fy) = px(wp.lat, wp.lon)
            g.fillOval((fx - 3).toInt(), (fy - 3).toInt(), 6, 6)
        }
        // Scale bar approximation.
        g.color = Color.LIGHT_GRAY
        g.stroke = BasicStroke(1f)
        val metersPerPx = 111_320.0 * lonScale / scale
        val barMeters = listOf(100, 500, 1000, 5000, 10000, 50000)
            .first { m -> m >= metersPerPx * 60 }
        val barPx = (barMeters / metersPerPx).toInt().coerceAtMost(w - 40)
        val barY = y0For(h)
        g.drawLine(20, barY, 20 + barPx, barY)
        g.drawString(formatMeters(barMeters.toDouble()), 20, barY - 6)
    }

    private fun y0For(h: Int): Int = h - 12
    private fun Int.pow(n: Int): Int = Math.pow(this.toDouble(), n.toDouble()).toInt()

    private fun formatMeters(m: Double): String =
        if (m < 1000) "${m.toInt()} m" else "${(m / 1000).toInt()} km"

    private fun drawProfile(g: Graphics2D, sheet: TrackSheet, x: Int, y: Int, w: Int, h: Int) {
        val heights = sheet.heights
        val known = heights.filterNotNull()
        if (known.isEmpty()) return
        val minH = known.min()
        val maxH = known.max()
        val span = max(maxH - minH, 10.0)
        g.color = Color(0x26, 0x2C, 0x22)
        g.fillRect(x, y, w, h)
        g.color = Color(0x8B, 0xC3, 0x4A)
        val path = Path2D.Float()
        var started = false
        for ((i, hh) in heights.withIndex()) {
            val value = hh ?: continue
            val fx = x + i.toDouble() / (heights.size - 1).coerceAtLeast(1) * w
            val fy = y + h - ((value - minH) / span * (h - 20) + 10)
            if (!started) {
                path.moveTo(fx, fy)
                started = true
            } else {
                path.lineTo(fx, fy)
            }
        }
        g.stroke = BasicStroke(1.8f)
        g.draw(path)
        g.color = Color.LIGHT_GRAY
        g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12)
        g.drawString("${maxH.toInt()} m", x + 8, y + 14)
        g.drawString("${minH.toInt()} m", x + 8, y + h - 6)
    }
}

actual object TrackImagePixels {
    actual fun fromPng(png: ByteArray): Triple<IntArray, Int, Int>? = runCatching {
        val image = ImageIO.read(ByteArrayInputStream(png)) ?: return null
        val w = image.width
        val h = image.height
        val rgb = IntArray(w * h)
        image.getRGB(0, 0, w, h, rgb, 0, w)
        Triple(rgb, w, h)
    }.getOrNull()
}

actual object Deflate {
    actual fun compress(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        DeflaterOutputStream(out).use { it.write(bytes) }
    }.toByteArray()
}
