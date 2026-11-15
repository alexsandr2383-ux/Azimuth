package ru.azimut.app.core.platform

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.azimut.app.core.location.LocationFix
import ru.azimut.app.core.location.NmeaParser
import ru.azimut.app.core.location.TrackRecorder
import kotlin.random.Random

/**
 * Windows PCs rarely have GPS: positions come from an external NMEA receiver on a serial
 * (USB/Bluetooth COM) port, or from a simulator for testing at the desk.
 * The source keeps running while the app is open; recording only decides whether fixes are stored.
 */
actual object PlatformLocation {
    actual val isSimulated: Boolean
        get() = source == SIMULATOR_SOURCE

    actual val satellites: StateFlow<Int?> = MutableStateFlow<Int?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var port: SerialPort? = null
    private var source = SIMULATOR_SOURCE
    private var baud = 9600

    private var lat = 55.751244
    private var lon = 37.618423
    private var course = 45.0

    actual fun start() = startPreview()

    actual fun stop() = Unit

    @Synchronized
    actual fun startPreview() {
        if (job != null) return
        job = if (isSimulated) simulate() else readSerial(source, baud)
    }

    @Synchronized
    actual fun stopPreview() {
        if (TrackRecorder.state.value.recording) return
        halt()
    }

    actual fun externalPorts(): List<String>? =
        runCatching { SerialPort.getCommPorts().map { it.systemPortName } }.getOrDefault(emptyList())

    @Synchronized
    actual fun configure(source: String, baud: Int) {
        if (source == this.source && baud == this.baud) return
        this.source = source
        this.baud = baud
        if (job != null) {
            halt()
            startPreview()
        }
    }

    actual fun applySettings(recordingIntervalSec: Int, batterySaver: Boolean, backgroundRecording: Boolean) = Unit

    private fun halt() {
        job?.cancel()
        job = null
        // A blocking read only ends when the port is closed.
        port?.closePort()
        port = null
    }

    private fun readSerial(name: String, baud: Int): Job = scope.launch {
        while (isActive) {
            val serial = SerialPort.getCommPort(name).apply {
                baudRate = baud
                setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 3_000, 0)
            }
            if (!serial.openPort()) {
                delay(3_000)
                continue
            }
            port = serial
            try {
                val parser = NmeaParser()
                val reader = serial.inputStream.bufferedReader(Charsets.US_ASCII)
                while (isActive) {
                    val line = reader.readLine() ?: break
                    parser.feed(line)?.let(TrackRecorder::onFix)
                }
            } catch (_: Exception) {
                // Unplugged or silent receiver: reopen below.
            } finally {
                serial.closePort()
                if (port === serial) port = null
            }
            delay(2_000)
        }
    }

    private fun simulate(): Job {
        if (TrackRecorder.state.value.lastFix == null) startAtSavedMapView()
        TrackRecorder.onFix(LocationFix(lat, lon, 156.0, 4f, 0f, course.toFloat(), System.currentTimeMillis()))
        return scope.launch {
            while (isActive) {
                delay(1500)
                lat += (Random.nextDouble() - 0.45) * 0.00045
                lon += (Random.nextDouble() - 0.45) * 0.00060
                course = (course + (Random.nextDouble() - 0.5) * 20.0 + 360.0) % 360.0
                TrackRecorder.onFix(
                    LocationFix(
                        lat = lat,
                        lon = lon,
                        altitude = 150.0 + Random.nextDouble() * 20.0,
                        accuracy = 3f + Random.nextDouble().toFloat() * 4f,
                        speed = 3.5f + Random.nextDouble().toFloat() * 3f,
                        bearing = course.toFloat(),
                        timeMillis = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    /** Starts the simulated walk where the user last looked at the map instead of always in Moscow. */
    private fun startAtSavedMapView() {
        val parts = PlatformFiles.readText("map_view.txt")?.trim()?.split(',') ?: return
        val savedLat = parts.getOrNull(0)?.toDoubleOrNull() ?: return
        val savedLon = parts.getOrNull(1)?.toDoubleOrNull() ?: return
        if (savedLat !in -85.0..85.0 || savedLon !in -180.0..180.0) return
        lat = savedLat
        lon = savedLon
    }
}
