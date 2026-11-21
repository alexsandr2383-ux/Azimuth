package ru.azimut.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.azimut.app.ui.tr

private val SheetBackground = Color(0xFFF2F2F7)
private val GroupColor = Color.White
private val RowText = Color(0xFF1C1C1E)
private val Chevron = Color(0xFFC7C7CC)

/** Guru-style grouped menu: white rounded groups, coloured icon tiles, chevrons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapMenuSheet(
    onDismiss: () -> Unit,
    onPoints: () -> Unit,
    onJournal: () -> Unit,
    onRoutes: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onDownloadPoi: () -> Unit,
    onDownloadArea: (() -> Unit)?,
    baseSet: Boolean,
    onSaveBase: () -> Unit,
    susaninOn: Boolean,
    onToggleSusanin: () -> Unit,
    onExitHere: (() -> Unit)?,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBackground,
        contentColor = RowText,
    ) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MenuSectionLabel(tr("Мои данные", "My data"))
            MenuGroup {
                MenuRow(Icons.Filled.Timeline, Color(0xFFEA580C), tr("Треки", "Tracks"), onJournal)
                MenuDivider()
                MenuRow(Icons.Filled.Route, Color(0xFF2563EB), tr("Маршруты", "Routes"), onRoutes)
                MenuDivider()
                MenuRow(Icons.Filled.Place, Color(0xFF65A30D), tr("Точки", "Waypoints"), onPoints)
            }
            MenuSectionLabel(tr("Навигация", "Navigation"))
            MenuGroup {
                MenuRow(
                    Icons.Filled.Home, Color(0xFF2E7D32), tr("«База» здесь", "Base here"), onSaveBase,
                    value = if (baseSet) tr("задана", "set") else null,
                )
                MenuDivider()
                MenuRow(
                    Icons.Filled.Warning, Color(0xFFF59E0B), tr("Режим Сусанина", "Explorer Mode"), onToggleSusanin,
                    value = if (susaninOn) tr("вкл", "on") else tr("выкл", "off"),
                )
                if (onExitHere != null) {
                    MenuDivider()
                    MenuRow(Icons.AutoMirrored.Filled.ExitToApp, Color(0xFFDC2626), tr("Выйти отсюда", "Get me out"), onExitHere)
                }
            }
            MenuSectionLabel(tr("Офлайн", "Offline"))
            MenuGroup {
                if (onDownloadArea != null) {
                    MenuRow(Icons.Filled.CloudDownload, Color(0xFF7C3AED), tr("Карта этой области", "Map of this area"), onDownloadArea)
                    MenuDivider()
                }
                MenuRow(Icons.Filled.CloudDownload, Color(0xFF0E7490), tr("POI этой области", "POI of this area"), onDownloadPoi)
            }
            MenuGroup {
                MenuRow(Icons.Filled.Settings, Color(0xFF8E8E93), tr("Настройки", "Settings"), onSettings)
                MenuDivider()
                MenuRow(Icons.AutoMirrored.Filled.Help, Color(0xFF0891B2), tr("Справка", "Help"), onHelp)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
internal fun MenuSectionLabel(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(start = 16.dp, top = 4.dp),
        color = Color(0xFF6D6D72),
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
    )
}

@Composable
internal fun MenuGroup(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GroupColor)) { content() }
}

@Composable
internal fun MenuDivider() {
    HorizontalDivider(Modifier.padding(start = 64.dp), color = Color(0xFFE5E5EA))
}

@Composable
internal fun MenuRow(icon: ImageVector, tile: Color, title: String, onClick: () -> Unit, value: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(title, Modifier.weight(1f).padding(start = 14.dp), color = RowText)
        if (value != null) Text(value, color = Color(0xFF8E8E93), modifier = Modifier.padding(end = 6.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Chevron)
    }
}
