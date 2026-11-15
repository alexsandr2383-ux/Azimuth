package ru.azimut.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.azimut.app.core.platform.PlatformFiles
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.max
import kotlin.math.roundToInt

private fun photoFile(fileName: String) = File(PlatformFiles.navDataRoot(), "$PHOTOS_DIR/$fileName")

actual fun loadPhoto(fileName: String, maxSize: Int): ImageBitmap? = runCatching {
    val image = ImageIO.read(photoFile(fileName)) ?: return null
    scaled(image, maxSize).toComposeImageBitmap()
}.getOrNull()

@Composable
actual fun rememberPhotoCapture(
    quality: String,
    onSaved: (fileName: String, geo: Pair<Double, Double>?) -> Unit,
    onError: () -> Unit,
): PhotoCapture {
    val saved = rememberUpdatedState(onSaved)
    val failed = rememberUpdatedState(onError)
    val currentQuality = rememberUpdatedState(quality)
    val scope = rememberCoroutineScope()
    return remember {
        PhotoCapture(
            pick = {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Фото / Photo"
                    isMultiSelectionEnabled = true
                    fileFilter = FileNameExtensionFilter("Images (jpg, png)", "jpg", "jpeg", "png", "bmp", "gif")
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val files = chooser.selectedFiles.toList()
                    scope.launch {
                        files.forEach { source ->
                            runCatching {
                                withContext(Dispatchers.IO) { savePhoto(source, currentQuality.value) }
                            }.onSuccess { saved.value(it.first, it.second) }.onFailure { failed.value() }
                        }
                    }
                }
            },
            takePhoto = null,
        )
    }
}

/** Returns the stored name plus the EXIF GPS of the original (rescaling drops it). */
private fun savePhoto(source: File, quality: String): Pair<String, Pair<Double, Double>?> {
    val (maxSide, jpegQuality) = photoLimits(quality)
    val image = ImageIO.read(source) ?: error("Not an image: $source")
    val geo = ru.azimut.app.core.model.extractExifGeo(source.readBytes())
    val name = newPhotoFileName()
    val target = photoFile(name).apply { parentFile.mkdirs() }
    val writer = ImageIO.getImageWritersByFormatName("jpg").next()
    try {
        ImageIO.createImageOutputStream(target).use { output ->
            writer.output = output
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = jpegQuality / 100f
            }
            writer.write(null, IIOImage(scaled(image, maxSide), null, null), params)
        }
    } finally {
        writer.dispose()
    }
    return name to geo
}

/** Fits into [maxSide] and flattens to RGB on white (JPEG has no alpha). */
private fun scaled(image: BufferedImage, maxSide: Int): BufferedImage {
    val longest = max(image.width, image.height)
    val factor = if (longest > maxSide) maxSide.toDouble() / longest else 1.0
    val width = (image.width * factor).roundToInt().coerceAtLeast(1)
    val height = (image.height * factor).roundToInt().coerceAtLeast(1)
    val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = result.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        g.drawImage(image, 0, 0, width, height, null)
    } finally {
        g.dispose()
    }
    return result
}
