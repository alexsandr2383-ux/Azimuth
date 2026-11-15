package ru.azimut.app.core.model

object PoiTypes {
    const val WATER = "water"
    const val SHELTER = "shelter"
    const val CAMP = "camp"
    const val FOOD = "food"
    const val FUEL = "fuel"
    const val MEDICAL = "medical"
    const val SIGHT = "sight"
    const val TRANSPORT = "transport"
    const val OTHER = "other"
    const val OWN = "own"

    val ALL = listOf(WATER, SHELTER, CAMP, FOOD, FUEL, MEDICAL, SIGHT, TRANSPORT, OTHER, OWN)
    val defaults: Set<String> = ALL.toSet()

    fun fromGpx(sym: String?, type: String?, name: String): String {
        // Our own exports write the exact category in <type>; trust it over the heuristic.
        type?.let { if (it in ALL) return it }
        val text = listOfNotNull(sym, type, name).joinToString(" ").lowercase()
        return when {
            word(text, "own", "my point") || any(text, "мои точки") -> OWN
            any(text, "водопад", "смотров", "музе", "достопримечатель", "памятник",
                "viewpoint", "museum", "monument", "attraction", "sight") -> SIGHT
            any(text, "вода", "родник", "источник", "колодец", "drink", "water") ||
                word(text, "spring", "well") -> WATER
            any(text, "приют", "изба", "хижина", "балаган", "shelter", "bivouac") ||
                word(text, "hut") -> SHELTER
            any(text, "кемпинг", "стоянка", "лагерь", "палатк", "camp", "camping") ||
                word(text, "tent") -> CAMP
            any(text, "магазин", "продукты", "еда", "кафе", "столовая", "ресторан",
                "cafe", "restaurant", "market") ||
                word(text, "food", "shop", "bar") -> FOOD
            any(text, "азс", "заправка", "топливо", "бензин", "fuel", "petrol") ||
                word(text, "gas") -> FUEL
            any(text, "больниц", "аптек", "фельдшер", "врач", "медицин",
                "hospital", "clinic", "doctor", "pharmacy", "medical") -> MEDICAL
            any(text, "парковка", "остановка", "вокзал", "станция", "аэропорт",
                "parking", "station", "airport", "transport") ||
                word(text, "bus", "train") -> TRANSPORT
            else -> OTHER
        }
    }

    /** Substring match: for word stems and long unambiguous keys (Russian morphology). */
    private fun any(text: String, vararg keys: String): Boolean = keys.any { text.contains(it) }

    /** Whole-word match: for short keys that hide inside other words (own→town, bus→business). */
    private fun word(text: String, vararg keys: String): Boolean = keys.any { key ->
        var i = text.indexOf(key)
        while (i >= 0) {
            val beforeOk = i == 0 || !text[i - 1].isLetterOrDigit()
            val afterOk = i + key.length >= text.length || !text[i + key.length].isLetterOrDigit()
            if (beforeOk && afterOk) return@any true
            i = text.indexOf(key, i + 1)
        }
        false
    }
}
