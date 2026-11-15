package ru.azimut.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.azimut.app.core.platform.PlatformFiles
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineEvent
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread

private class DesktopWavRecorder {
    private val format = AudioFormat(
        VOICE_SAMPLE_RATE.toFloat(),
        16,
        1,
        true,
        false,
    )
    private val data = ByteArrayOutputStream()
    private var line: TargetDataLine? = null
    private var worker: Thread? = null

    @Volatile private var active = false

    fun start(): Boolean {
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val input = runCatching { AudioSystem.getLine(info) as TargetDataLine }.getOrNull() ?: return false
        val opened = runCatching {
            input.open(format)
            input.start()
        }.isSuccess
        if (!opened) {
            runCatching { input.close() }
            return false
        }
        line = input
        active = true
        worker = thread(name = "azimut-voice-recorder") {
            val buffer = ByteArray(4_096)
            while (active) {
                val count = runCatching { input.read(buffer, 0, buffer.size) }.getOrDefault(-1)
                if (count > 0) data.write(buffer, 0, count)
            }
        }
        return true
    }

    fun stop(): String? {
        active = false
        runCatching { line?.stop() }
        worker?.join(2_000)
        worker = null
        runCatching { line?.close() }
        line = null
        val bytes = data.toByteArray()
        if (bytes.isEmpty()) return null
        val name = newVoiceFileName()
        val file = File(
            File(PlatformFiles.navDataRoot(), VOICE_DIR).apply { mkdirs() },
            name,
        )
        val written = runCatching {
            AudioInputStream(ByteArrayInputStream(bytes), format, bytes.size.toLong()).use { stream ->
                AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file)
            }
        }.isSuccess
        if (!written) {
            file.delete()
            return null
        }
        return name
    }

    fun cancel() {
        active = false
        val input = line
        val workerThread = worker
        line = null
        worker = null
        thread(name = "azimut-voice-cancel") {
            runCatching { input?.stop() }
            workerThread?.join(2_000)
            runCatching { input?.close() }
        }
    }
}

@Composable
actual fun rememberVoiceRecorder(
    onSaved: (fileName: String) -> Unit,
    onError: () -> Unit,
): VoiceRecorder {
    val saved = rememberUpdatedState(onSaved)
    val failed = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<DesktopWavRecorder?>(null) }

    fun begin() {
        if (recorder != null) return
        val started = DesktopWavRecorder()
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

    return remember(recording) {
        VoiceRecorder(
            recording = recording,
            start = ::begin,
            stop = ::finish,
            cancel = ::cancel,
        )
    }
}

private var clip: Clip? = null

actual fun playVoiceNote(fileName: String) {
    val file = File(PlatformFiles.navDataRoot(), "$VOICE_DIR/$fileName")
    if (!file.isFile) return
    runCatching {
        clip?.stop()
        clip?.close()
        clip = null
        val stream = AudioSystem.getAudioInputStream(file)
        val next = AudioSystem.getClip()
        next.open(stream)
        next.addLineListener { event ->
            if (event.type == LineEvent.Type.STOP) {
                runCatching { event.line.close() }
                if (clip === event.line) clip = null
            }
        }
        next.start()
        clip = next
    }
}
