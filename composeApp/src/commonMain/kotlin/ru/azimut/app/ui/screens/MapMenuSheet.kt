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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
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
    onGoto: () -> Unit,
    onPoints: () -> Unit,
    onJournal: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onAbout: () -> Unit,
    onDownloadPoi: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBackground,
        contentColor = RowText,
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 12.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MenuGroup {
                MenuRow(Icons.Filled.Navigation, Color(0xFF0F766E), tr("К координатам", "Go to coordinates"), onGoto)
                MenuDivider()
                MenuRow(Icons.Filled.CloudDownload, Color(0xFF0E7490), tr("Загрузить POI области", "Download area POI"), onDownloadPoi)
            }
            MenuGroup {
                MenuRow(Icons.Filled.Place, Color(0xFF65A30D), tr("Коллекции", "Collections"), onJournal)
            }
            MenuGroup {
                MenuRow(Icons.Filled.Settings, Color(0xFF8E8E93), tr("Настройки", "Settings"), onSettings)
                MenuDivider()
                MenuRow(Icons.AutoMirrored.Filled.Help, Color(0xFF0891B2), tr("Справка", "Help"), onHelp)
                MenuDivider()
                MenuRow(Icons.Filled.Info, Color(0xFF7C5C3B), tr("О приложении", "About"), onAbout)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
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
internal fun MenuRow(icon: ImageVector, tile: Color, title: String, onClick: () -> Unit) {
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
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Chevron)
    }
}
