package ru.azimut.app.core.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.io.ByteArrayInputStream
import kotlin.math.max
import kotlin.math.min

/** Android renderer: android.graphics Canvas on an offscreen Bitmap. */
actual object TrackImageRenderer {
    actual fun renderPng(sheet: TrackSheet, width: Int, height: Int): ByteArray? = runCatching {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(0x18, 0x1D, 0x16))

        val textHeight = 18f
        val lines = sheet.lines.size
        val textBlock = (lines * (textHeight + 4) + 12).toInt()
        val mapHeight = height - textBlock
        val profileHeight = if (sheet.heights.any { it != null }) 110 else 0
        val sketchHeight = mapHeight - profileHeight

        val pts = sheet.track.points
        if (pts.isNotEmpty()) {
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
            val scale = min((width - margin * 2) / (spanLon * lonScale), (sketchHeight - margin * 2) / spanLat)
            fun px(lat: Double, lon: Double): Pair<Float, Float> {
                val fx = (lon - (lonMin + lonMax) / 2) * lonScale * scale + width / 2.0
                val fy = ((latMin + latMax) / 2 - lat) * scale + sketchHeight / 2.0
                return fx.toFloat() to fy.toFloat()
            }
            val linePaint = Paint().apply {
                color = Color.rgb(0xFF, 0x6D, 0x00)
                strokeWidth = 2.4f
                style = Paint.Style.STROKE
                isAntiAlias = true
            }
            val path = Path()
            pts.forEachIndexed { i, p ->
                val (fx, fy) = px(p.lat, p.lon)
                if (i == 0) path.moveTo(fx, fy) else path.lineTo(fx, fy)
            }
            canvas.drawPath(path, linePaint)
            val dotPaint = Paint().apply {
                color = Color.rgb(0x2E, 0x7D, 0x32)
                style = Paint.Style.FILL
            }
            for (wp in sheet.waypoints) {
                val (fx, fy) = px(wp.lat, wp.lon)
                canvas.drawCircle(fx, fy, 3f, dotPaint)
            }
        }

        if (profileHeight > 0) {
            val known = sheet.heights.filterNotNull()
            if (known.isNotEmpty()) {
                val minH = known.min()
                val maxH = known.max()
                val span = max(maxH - minH, 10.0)
                canvas.drawRect(0f, sketchHeight.toFloat(), width.toFloat(), mapHeight.toFloat(),
                    Paint().apply { color = Color.rgb(0x26, 0x2C, 0x22) })
                val profPaint = Paint().apply {
                    color = Color.rgb(0x8B, 0xC3, 0x4A)
                    strokeWidth = 1.8f
                    style = Paint.Style.STROKE
                }
                val path = Path()
                var started = false
                val size = sheet.heights.size.coerceAtLeast(2)
                for ((i, hh) in sheet.heights.withIndex()) {
                    val value = hh ?: continue
                    val fx = i.toFloat() / (size - 1) * width
                    val fy = (sketchHeight + profileHeight - ((value - minH) / span * (profileHeight - 20) + 10)).toFloat()
                    if (!started) {
                        path.moveTo(fx, fy)
                        started = true
                    } else {
                        path.lineTo(fx, fy)
                    }
                }
                canvas.drawPath(path, profPaint)
            }
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = textHeight - 2
            isAntiAlias = true
        }
        var y = sketchHeight + profileHeight + textHeight
        for (line in sheet.lines) {
            canvas.drawText(line, 10f, y, textPaint)
            y += (textHeight + 4).toInt()
        }

        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            .toByteArray()
            .also { bitmap.recycle() }
    }.getOrNull()
}

actual object TrackImagePixels {
    actual fun fromPng(png: ByteArray): Triple<IntArray, Int, Int>? = runCatching {
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
        val w = bitmap.width
        val h = bitmap.height
        val rgb = IntArray(w * h)
        bitmap.getPixels(rgb, 0, w, 0, 0, w, h)
        bitmap.recycle()
        Triple(rgb, w, h)
    }.getOrNull()
}

actual object Deflate {
    actual fun compress(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        DeflaterOutputStream(out).use { it.write(bytes) }
    }.toByteArray()
}
