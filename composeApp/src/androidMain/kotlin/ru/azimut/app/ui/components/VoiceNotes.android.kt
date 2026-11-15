package ru.azimut.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.azimut.app.core.platform.PlatformFiles
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.concurrent.thread
import kotlin.math.max

private class AndroidWavRecorder {
    private val file = File(
        File(PlatformFiles.navDataRoot(), VOICE_DIR).apply { mkdirs() },
        newVoiceFileName(),
    )
    private var record: AudioRecord? = null
    private var worker: Thread? = null

    @Volatile private var active = false

    fun start(): Boolean {
        val minBuffer = AudioRecord.getMinBufferSize(
            VOICE_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return false
        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                VOICE_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuffer * 2, VOICE_SAMPLE_RATE / 5 * 2),
            )
        }.getOrNull() ?: return false
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }
        val started = runCatching {
            recorder.startRecording()
            recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING
        }.getOrDefault(false)
        if (!started) {
            runCatching { recorder.release() }
            return false
        }
        active = true
        record = recorder
        worker = thread(name = "azimut-voice-recorder") {
            runCatching {
                FileOutputStream(file).use { out ->
                    out.write(ByteArray(44))
                    val samples = ShortArray(2_048)
                    val bytes = ByteArray(samples.size * 2)
                    while (active) {
                        val count = recorder.read(samples, 0, samples.size)
                        if (count <= 0) continue
                        for (i in 0 until count) {
                            val value = samples[i].toInt()
                            bytes[i * 2] = (value and 0xFF).toByte()
                            bytes[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
                        }
                        out.write(bytes, 0, count * 2)
                    }
                }
            }
        }
        return true
    }

    fun stop(): String? {
        active = false
        val recorder = record
        runCatching { recorder?.stop() }
        worker?.join(2_500)
        worker = null
        record = null
        runCatching { recorder?.release() }
        val dataLength = (file.length() - 44).coerceAtLeast(0).toInt()
        if (dataLength <= 0) {
            file.delete()
            return null
        }
        val patched = runCatching {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                raf.write(wavHeader(dataLength))
            }
        }.isSuccess
        if (!patched) {
            file.delete()
            return null
        }
        return file.name
    }

    fun cancel() {
        active = false
        val recorder = record
        val workerThread = worker
        record = null
        worker = null
        thread(name = "azimut-voice-cancel") {
            runCatching { recorder?.stop() }
            workerThread?.join(2_500)
            runCatching { recorder?.release() }
            file.delete()
        }
    }
}

@Composable
actual fun rememberVoiceRecorder(
    onSaved: (fileName: String) -> Unit,
    onError: () -> Unit,
): VoiceRecorder {
    val context = LocalContext.current
    val saved = rememberUpdatedState(onSaved)
    val failed = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<AndroidWavRecorder?>(null) }

    fun begin() {
        if (recorder != null) return
        val started = AndroidWavRecorder()
        if (started.start()) {
            recorder = started
            recording = true
        } else {
            failed.value()
        }
    }

    fun finish() {
        val current = recorder ?: return
        recorder = null
        recording = false
        scope.launch {
            val name = withContext(Dispatchers.IO) { current.stop() }
            if (name == null) failed.value() else saved.value(name)
        }
    }

    fun cancel() {
        val current = recorder ?: return
        recorder = null
        recording = false
        current.cancel()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) begin() else failed.value()
    }

    return remember(recording) {
        VoiceRecorder(
            recording = recording,
            start = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    begin()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            stop = ::finish,
            cancel = ::cancel,
        )
    }
}

private val playerLock = Any()
private var player: MediaPlayer? = null

actual fun playVoiceNote(fileName: String) {
    val file = File(PlatformFiles.navDataRoot(), "$VOICE_DIR/$fileName")
    if (!file.isFile) return
    thread(name = "azimut-voice-player") {
        runCatching {
            synchronized(playerLock) {
                player?.release()
                player = null
                val media = MediaPlayer()
                media.setDataSource(file.absolutePath)
                media.setOnCompletionListener {
                    synchronized(playerLock) {
                        if (player === it) player = null
                    }
                    it.release()
                }
                media.prepare()
                media.start()
                player = media
            }
        }
    }
}
