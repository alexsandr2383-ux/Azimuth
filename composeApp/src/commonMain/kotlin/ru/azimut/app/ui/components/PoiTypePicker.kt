package ru.azimut.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.azimut.app.core.model.PoiTypes
import ru.azimut.app.ui.tr

@Composable
fun poiTypeLabel(type: String): String = when (type) {
    PoiTypes.WATER -> tr("Вода", "Water")
    PoiTypes.SHELTER -> tr("Жильё/приют", "Shelter")
    PoiTypes.CAMP -> tr("Кемпинг", "Camp")
    PoiTypes.FOOD -> tr("Еда/магазин", "Food/shop")
    PoiTypes.FUEL -> tr("Топливо", "Fuel")
    PoiTypes.MEDICAL -> tr("Медицина", "Medical")
    PoiTypes.SIGHT -> tr("Достопримечательность", "Sight")
    PoiTypes.TRANSPORT -> tr("Транспорт", "Transport")
    PoiTypes.OWN -> tr("Мои точки", "My points")
    else -> tr("Прочее", "Other")
}

fun poiTypeShortLabel(type: String, lang: String): String {
    val ru = lang != "en"
    return when (type) {
        PoiTypes.WATER -> if (ru) "Вода" else "Water"
        PoiTypes.SHELTER -> if (ru) "Приют" else "Shelter"
        PoiTypes.CAMP -> if (ru) "Кемпинг" else "Camp"
        PoiTypes.FOOD -> if (ru) "Еда" else "Food"
        PoiTypes.FUEL -> if (ru) "Топливо" else "Fuel"
        PoiTypes.MEDICAL -> if (ru) "Мед" else "Med"
        PoiTypes.SIGHT -> if (ru) "Место" else "Sight"
        PoiTypes.TRANSPORT -> if (ru) "Транс" else "Trans"
        PoiTypes.OWN -> if (ru) "Моя" else "My"
        else -> if (ru) "Точка" else "Point"
    }
}

fun poiTypeColorHex(type: String): String = when (type) {
    PoiTypes.WATER -> "#1E88E5"
    PoiTypes.SHELTER -> "#6D4C41"
    PoiTypes.CAMP -> "#43A047"
    PoiTypes.FOOD -> "#FB8C00"
    PoiTypes.FUEL -> "#FDD835"
    PoiTypes.MEDICAL -> "#E53935"
    PoiTypes.SIGHT -> "#8E24AA"
    PoiTypes.TRANSPORT -> "#546E7A"
    PoiTypes.OWN -> "#2E7D32"
    else -> "#00897B"
}

fun poiTypeGlyph(type: String): String = when (type) {
    PoiTypes.WATER -> "W"
    PoiTypes.SHELTER -> "H"
    PoiTypes.CAMP -> "C"
    PoiTypes.FOOD -> "F"
    PoiTypes.FUEL -> "G"
    PoiTypes.MEDICAL -> "+"
    PoiTypes.SIGHT -> "S"
    PoiTypes.TRANSPORT -> "T"
    PoiTypes.OWN -> "*"
    else -> "P"
}

@Composable
fun PoiTypePicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Тип", "Type") + ": " + poiTypeLabel(selected))
            Icon(Icons.Filled.ExpandMore, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PoiTypes.ALL.forEach { type ->
                DropdownMenuItem(
                    text = { Text(poiTypeLabel(type)) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
        }
    }
}
