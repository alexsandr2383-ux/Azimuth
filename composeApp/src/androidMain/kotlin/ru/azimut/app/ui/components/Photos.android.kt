package ru.azimut.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.azimut.app.core.platform.PlatformFiles
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

private fun photosDir() = File(PlatformFiles.navDataRoot(), PHOTOS_DIR)

actual fun loadPhoto(fileName: String, maxSize: Int): ImageBitmap? = runCatching {
    val file = File(photosDir(), fileName)
    decodeSampled({ file.inputStream() }, maxSize)?.asImageBitmap()
}.getOrNull()

@Composable
actual fun rememberPhotoCapture(
    quality: String,
    onSaved: (fileName: String, geo: Pair<Double, Double>?) -> Unit,
    onError: () -> Unit,
): PhotoCapture {
    val context = LocalContext.current
    val saved = rememberUpdatedState(onSaved)
    val failed = rememberUpdatedState(onError)
    val currentQuality = rememberUpdatedState(quality)
    val scope = rememberCoroutineScope()
    // Survives the activity being recreated while the camera app is open.
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }

    fun process(open: () -> InputStream?, cleanup: () -> Unit = {}) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { savePhoto(open, currentQuality.value) } }
                .onSuccess { saved.value(it.first, it.second) }
                .onFailure { failed.value() }
            cleanup()
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) process({ context.contentResolver.openInputStream(uri) })
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val temp = pendingCapture?.let { File(photosDir(), it) }
        pendingCapture = null
        if (ok && temp != null && temp.length() > 0) {
            process({ temp.inputStream() }) { temp.delete() }
        } else {
            temp?.delete()
        }
    }

    return remember(pickLauncher, cameraLauncher) {
        PhotoCapture(
            pick = {
                runCatching {
                    pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }.onFailure { failed.value() }
            },
            takePhoto = {
                runCatching {
                    val name = ".capture-${System.currentTimeMillis()}.jpg"
                    val file = File(photosDir().apply { mkdirs() }, name)
                    file.createNewFile()
                    pendingCapture = name
                    cameraLauncher.launch(FileProvider.getUriForFile(context, context.fileAuthority(), file))
                }.onFailure {
                    pendingCapture = null
                    failed.value()
                }
            },
        )
    }
}

private fun Context.fileAuthority() = "$packageName.files"

/** Scales to the quality limit, applies EXIF rotation and stores a JPEG in NavData/photos/.
 *  Returns the stored name plus the EXIF GPS of the original (null when the photo has none). */
private fun savePhoto(open: () -> InputStream?, quality: String): Pair<String, Pair<Double, Double>?> {
    // One read of the original: bitmap decode, rotation flag and GPS tags all come from it
    // (rescaling to JPEG drops EXIF, so the tags must be taken before the write).
    val original = open()?.use { it.readBytes() } ?: error("Not an image")
    val geo = ru.azimut.app.core.model.extractExifGeo(original)
    val reopen = { java.io.ByteArrayInputStream(original) }
    val (maxSide, jpegQuality) = photoLimits(quality)
    var bitmap = decodeSampled(reopen, maxSide) ?: error("Not an image")
    val longest = max(bitmap.width, bitmap.height)
    if (longest > maxSide) {
        val factor = maxSide.toFloat() / longest
        bitmap = Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * factor).roundToInt(), (bitmap.height * factor).roundToInt(), true,
        )
    }
    val degrees = reopen().use { stream ->
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    if (degrees != 0f) {
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
    }
    val name = newPhotoFileName()
    File(photosDir().apply { mkdirs() }, name).outputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
    }
    return name to geo
}

/** Decodes with the largest power-of-two subsampling that keeps the long side ≥ [maxSide]. */
private fun decodeSampled(open: () -> InputStream?, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    return open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
}
