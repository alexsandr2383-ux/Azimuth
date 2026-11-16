package ru.azimut.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.azimut.app.Screen
import ru.azimut.app.ui.screens.MapAccent
import ru.azimut.app.ui.tr

/** Tab strip shared by the two collection screens: tracks (journal) and points. */
@Composable
fun CollectionsTabs(current: Screen, onSwitch: (Screen) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            listOf(
                Screen.Journal to tr("Треки", "Tracks"),
                Screen.Routes to tr("Маршруты", "Routes"),
                Screen.Points to tr("Точки", "Points"),
            ).forEach { (screen, title) ->
                val selected = current == screen
                Column(
                    Modifier.weight(1f).clickable { if (!selected) onSwitch(screen) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        title,
                        color = if (selected) MapAccent else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) MapAccent else Color.Transparent))
                }
            }
        }
        HorizontalDivider()
    }
}
