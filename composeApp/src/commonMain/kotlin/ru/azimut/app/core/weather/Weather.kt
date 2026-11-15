package ru.azimut.app.core.weather

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.azimut.app.core.platform.PlatformFiles
import ru.azimut.app.core.platform.PlatformHttp
import kotlin.math.roundToInt

@Serializable
data class WeatherSnapshot(
    val lat: Double,
    val lon: Double,
    val fetchedAt: Long,
    val temperature: Double? = null,
    val wind: Double? = null,
    val code: Int? = null,
    /** Next 24 h from the same request; empty in old snapshots. */
    val hourly: List<HourlyEntry> = emptyList(),
)

@Serializable
data class HourlyEntry(
    /** ISO local time "2025-11-12T14:00". */
    val time: String,
    val temperatureC: Double? = null,
    val precipitationPercent: Int? = null,
    val windMs: Double? = null,
)

/** One explicit Open-Meteo request, cached locally. Nothing is fetched unless the user asks. */
object WeatherStore {
    private const val PATH = "weather.json"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): WeatherSnapshot? {
        val text = PlatformFiles.readText(PATH) ?: return null
        return runCatching { json.decodeFromString<WeatherSnapshot>(text.removePrefix("\uFEFF")) }
            .getOrNull()
    }

    suspend fun refresh(lat: Double, lon: Double): WeatherSnapshot? {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,wind_speed_10m" +
            "&hourly=temperature_2m,precipitation_probability,wind_speed_10m" +
            "&forecast_days=1&timezone=auto"
        val text = PlatformHttp.getText(url) ?: return null
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val current = root["current"]?.jsonObject ?: return null
        val hourly = parseHourly(root["hourly"]?.jsonObject)
        val snapshot = WeatherSnapshot(
            lat = lat,
            lon = lon,
            fetchedAt = Clock.System.now().toEpochMilliseconds(),
            temperature = current["temperature_2m"]?.jsonPrimitive?.content?.toDoubleOrNull(),
            wind = current["wind_speed_10m"]?.jsonPrimitive?.content?.toDoubleOrNull(),
            code = current["weather_code"]?.jsonPrimitive?.content?.toIntOrNull(),
            hourly = hourly,
        )
        runCatching { PlatformFiles.writeText(PATH, json.encodeToString(snapshot)) }
        return snapshot
    }

    /** Hourly arrays come as parallel lists; entries zip by index, trimmed to 24. */
    private fun parseHourly(obj: kotlinx.serialization.json.JsonObject?): List<HourlyEntry> {
        if (obj == null) return emptyList()
        val times = obj["time"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        val temps = obj["temperature_2m"] as? kotlinx.serialization.json.JsonArray
        val precip = obj["precipitation_probability"] as? kotlinx.serialization.json.JsonArray
        val wind = obj["wind_speed_10m"] as? kotlinx.serialization.json.JsonArray
        return times
            .mapIndexed { index, element ->
                HourlyEntry(
                    time = (element as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapIndexed null,
                    temperatureC = temps?.getOrNull(index)?.jsonPrimitive?.content?.toDoubleOrNull(),
                    precipitationPercent = precip?.getOrNull(index)?.jsonPrimitive?.content
                        ?.toDoubleOrNull()?.roundToInt(),
                    windMs = wind?.getOrNull(index)?.jsonPrimitive?.content?.toDoubleOrNull(),
                )
            }
            .filterNotNull()
            .take(24)
    }
}

fun weatherDescription(code: Int?, lang: String): String {
    val en = lang == "en"
    return when (code) {
        0 -> if (en) "clear" else "ясно"
        1, 2 -> if (en) "partly cloudy" else "переменная облачность"
        3 -> if (en) "overcast" else "облачно"
        45, 48 -> if (en) "fog" else "туман"
        in 51..57 -> if (en) "drizzle" else "морось"
        in 61..67 -> if (en) "rain" else "дождь"
        in 71..77 -> if (en) "snow" else "снег"
        in 80..82 -> if (en) "showers" else "ливни"
        85, 86 -> if (en) "snow showers" else "снегопад"
        in 95..99 -> if (en) "thunderstorm" else "гроза"
        else -> if (en) "unknown" else "нет данных"
    }
}

fun weatherText(snapshot: WeatherSnapshot, lang: String): String {
    val en = lang == "en"
    val temperature = snapshot.temperature?.let { "${it.roundToInt()} °C" }
    val wind = snapshot.wind?.let {
        if (en) "${it.roundToInt()} m/s" else "${it.roundToInt()} м/с"
    }
    val text = listOfNotNull(
        temperature,
        weatherDescription(snapshot.code, lang),
        wind?.let { if (en) "wind $it" else "ветер $it" },
    ).joinToString(" · ")
    return text.ifBlank { if (en) "no data" else "нет данных" }
}
