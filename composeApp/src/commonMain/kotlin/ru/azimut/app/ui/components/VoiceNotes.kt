package ru.azimut.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Clock
import ru.azimut.app.core.platform.PlatformFiles
import ru.azimut.app.ui.tr
import kotlin.random.Random

const val VOICE_DIR = "voice"
const val VOICE_SAMPLE_RATE = 16_000

fun newVoiceFileName(): String =
    "voice-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(1000, 9999)}.wav"

fun deleteVoice(fileName: String) {
    runCatching { PlatformFiles.delete("$VOICE_DIR/$fileName") }
}

/** 44-byte PCM WAV header for 16-bit mono audio. */
fun wavHeader(dataLength: Int, sampleRate: Int = VOICE_SAMPLE_RATE): ByteArray {
    val channels = 1
    val bits = 16
    val byteRate = sampleRate * channels * bits / 8
    val blockAlign = channels * bits / 8
    fun le16(value: Int) = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
    )
    fun le32(value: Int) = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )
    fun ascii(value: String) = value.toByteArray(Charsets.US_ASCII)
    return ascii("RIFF") + le32(36 + dataLength) + ascii("WAVE") +
        ascii("fmt ") + le32(16) + le16(1) + le16(channels) + le32(sampleRate) +
        le32(byteRate) + le16(blockAlign) + le16(bits) +
        ascii("data") + le32(dataLength)
}

class VoiceRecorder(
    val recording: Boolean,
    val start: () -> Unit,
    val stop: () -> Unit,
    /** Stops and deletes the unfinished note (used when the editor is dismissed). */
    val cancel: () -> Unit,
)

/** Records a WAV voice note into NavData/voice/ and reports the file name. */
@Composable
expect fun rememberVoiceRecorder(
    onSaved: (fileName: String) -> Unit,
    onError: () -> Unit,
): VoiceRecorder

/** Plays one note at a time; starting another stops the previous one. */
expect fun playVoiceNote(fileName: String)

/** Playback row for waypoint cards and dialogs. */
@Composable
fun VoiceNoteStrip(notes: List<String>, onRemove: ((String) -> Unit)? = null) {
    if (notes.isEmpty()) return
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        notes.forEachIndexed { index, note ->
            TextButton(onClick = { playVoiceNote(note) }) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                Text(" " + tr("Заметка", "Note") + " ${index + 1}")
            }
            if (onRemove != null) {
                IconButton(onClick = { onRemove(note) }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        tr("Удалить заметку", "Delete note"),
                        Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/** Voice-note block for waypoint dialogs. */
@Composable
fun VoiceEditor(
    notes: List<String>,
    onAdded: (String) -> Unit,
    onRemoved: (String) -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    val errorText = tr("Не удалось записать заметку.", "Could not record the note.")
    val recorder = rememberVoiceRecorder(
        onSaved = {
            error = null
            onAdded(it)
        },
        onError = { error = errorText },
    )
    val currentRecorder by rememberUpdatedState(recorder)
    DisposableEffect(Unit) {
        onDispose { if (currentRecorder.recording) currentRecorder.cancel() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        VoiceNoteStrip(notes, onRemove = onRemoved)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { if (recorder.recording) recorder.stop() else recorder.start() },
            ) {
                Icon(
                    if (recorder.recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    null,
                    Modifier.size(18.dp),
                )
                Text(
                    " " + if (recorder.recording) {
                        tr("Стоп", "Stop")
                    } else {
                        tr("Голосовая заметка", "Voice note")
                    },
                )
            }
            if (recorder.recording) {
                Text(
                    tr("Идёт запись…", "Recording…"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
