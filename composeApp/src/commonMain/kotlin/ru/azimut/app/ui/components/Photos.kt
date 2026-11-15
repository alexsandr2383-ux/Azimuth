package ru.azimut.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import ru.azimut.app.core.platform.PlatformFiles
import ru.azimut.app.ui.tr
import kotlin.random.Random

const val PHOTOS_DIR = "photos"

fun newPhotoFileName(): String =
    "photo-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(1000, 9999)}.jpg"

/** Longest side in px and JPEG quality for the "Photo quality" setting. */
fun photoLimits(quality: String): Pair<Int, Int> = when (quality) {
    "low" -> 1280 to 75
    "medium" -> 2048 to 85
    else -> 4096 to 92
}

fun deletePhoto(fileName: String) {
    runCatching { PlatformFiles.delete("$PHOTOS_DIR/$fileName") }
}

/** Decodes a stored photo scaled down to roughly [maxSize] px on the long side. */
expect fun loadPhoto(fileName: String, maxSize: Int): ImageBitmap?

/** [takePhoto] is null where there is no camera (desktop). */
class PhotoCapture(val pick: () -> Unit, val takePhoto: (() -> Unit)?)

/** Saves picked/taken photos into NavData/photos/ (scaled per [quality]) and reports
 *  the file name plus the EXIF GPS coordinates of the ORIGINAL file (null when absent). */
@Composable
expect fun rememberPhotoCapture(
    quality: String,
    onSaved: (fileName: String, geo: Pair<Double, Double>?) -> Unit,
    onError: () -> Unit,
): PhotoCapture

@Composable
fun PhotoThumb(fileName: String, size: Dp = 64.dp, onClick: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, fileName) {
        value = withContext(Dispatchers.Default) { loadPhoto(fileName, 256) }
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Thumbnails; tap opens the photo full screen, the cross (if [onRemove] is set) removes it. */
@Composable
fun PhotoStrip(photos: List<String>, onRemove: ((String) -> Unit)? = null) {
    if (photos.isEmpty()) return
    var open by remember { mutableStateOf<String?>(null) }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        photos.forEach { photo ->
            Box {
                PhotoThumb(photo) { open = photo }
                if (onRemove != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { onRemove(photo) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, tr("Убрать фото", "Remove photo"), Modifier.size(14.dp), tint = Color.White)
                    }
                }
            }
        }
    }
    open?.let { PhotoViewer(it) { open = null } }
}

@Composable
fun PhotoViewer(fileName: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val bitmap by produceState<ImageBitmap?>(null, fileName) {
            value = withContext(Dispatchers.Default) { loadPhoto(fileName, 2048) }
        }
        Box(
            Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            if (image == null) {
                CircularProgressIndicator()
            } else {
                Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * Photo block for waypoint dialogs. Photos taken here but not kept (removed or the dialog cancelled)
 * must be deleted by the caller via [deletePhoto].
 */
@Composable
fun PhotoEditor(
    photos: List<String>,
    quality: String,
    onAdded: (String, Pair<Double, Double>?) -> Unit,
    onRemoved: (String) -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    val errorText = tr("Не удалось сохранить фото.", "Could not save the photo.")
    val capture = rememberPhotoCapture(quality, onSaved = { f, geo -> error = null; onAdded(f, geo) }, onError = { error = errorText })
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PhotoStrip(photos, onRemove = onRemoved)
        Row {
            capture.takePhoto?.let { take ->
                TextButton(onClick = take) {
                    Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                    Text(" " + tr("Снять", "Camera"))
                }
            }
            TextButton(onClick = capture.pick) {
                Icon(Icons.Filled.AddPhotoAlternate, null, Modifier.size(18.dp))
                Text(" " + tr("Добавить фото", "Add photo"))
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
