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
