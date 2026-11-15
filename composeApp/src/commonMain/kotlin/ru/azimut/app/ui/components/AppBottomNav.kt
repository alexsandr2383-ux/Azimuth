package ru.azimut.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.azimut.app.Screen
import ru.azimut.app.ui.tr

/**
 * Phone navigation bar: Карта | Точки | Журнал | Ещё. Shown only on narrow
 * screens (App.kt decides by width); the map's "Меню" dropdown keeps the
 * map-specific actions (goto coordinates etc.) on every platform.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomNav(screen: Screen, onNavigate: (Screen) -> Unit) {
    val mapLabel = tr("Карта", "Map")
    val pointsLabel = tr("Точки", "Points")
    val journalLabel = tr("Журнал", "Journal")
    val moreLabel = tr("Ещё", "More")
    val settingsLabel = tr("Настройки", "Settings")
    val helpLabel = tr("Справка", "Help")
    val aboutLabel = tr("О приложении", "About")
    var moreOpen by remember { mutableStateOf(false) }

    val onMainTab = screen == Screen.Map || screen == Screen.Points || screen == Screen.Journal
    NavigationBar {
        NavigationBarItem(
            selected = screen == Screen.Map,
            onClick = { if (screen != Screen.Map) onNavigate(Screen.Map) },
            icon = { Icon(Icons.Filled.Map, contentDescription = null) },
            label = { Text(mapLabel) },
        )
        NavigationBarItem(
            selected = screen == Screen.Points,
            onClick = { if (screen != Screen.Points) onNavigate(Screen.Points) },
            icon = { Icon(Icons.Filled.Place, contentDescription = null) },
            label = { Text(pointsLabel) },
        )
        NavigationBarItem(
            selected = screen == Screen.Journal,
            onClick = { if (screen != Screen.Journal) onNavigate(Screen.Journal) },
            icon = { Icon(Icons.Filled.Description, contentDescription = null) },
            label = { Text(journalLabel) },
        )
        NavigationBarItem(
            selected = !onMainTab,
            onClick = { moreOpen = true },
            icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = null) },
            label = { Text(moreLabel) },
        )
    }

    if (moreOpen) {
        ModalBottomSheet(onDismissRequest = { moreOpen = false }) {
            Column(Modifier.padding(bottom = 16.dp)) {
                SheetItem(settingsLabel, Icons.Filled.Settings) { moreOpen = false; onNavigate(Screen.Settings) }
                SheetItem(helpLabel, Icons.AutoMirrored.Filled.Help) { moreOpen = false; onNavigate(Screen.Help) }
                SheetItem(aboutLabel, Icons.Filled.Info) { moreOpen = false; onNavigate(Screen.About) }
            }
        }
    }
}

@Composable
private fun SheetItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
