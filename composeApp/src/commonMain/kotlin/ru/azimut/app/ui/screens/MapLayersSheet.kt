package ru.azimut.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.azimut.app.core.map.MapStyle
import ru.azimut.app.core.map.MapStyles
import ru.azimut.app.core.model.AppSettings
import ru.azimut.app.ui.tr

private val SheetBg = Color(0xFFF2F2F7)
private val CardBg = Color.White
private val TextMain = Color(0xFF1C1C1E)
private val TextHint = Color(0xFF8E8E93)

/**
 * Guru-style layers sheet with three tabs: «Слои» (overlays, POI, relief),
 * «Офлайн-карты» (imported maps) and «Онлайн-карты» (built-in and XYZ sources).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapLayersSheet(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    allStyles: List<MapStyle>,
    overlayOptions: List<Pair<String, Pair<String, String>>>,
    onPoiTypes: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val (offline, online) = allStyles.partition { it.localPath != null }
    val lang = settings.language
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBg,
        contentColor = TextMain,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth()) {
                listOf(
                    tr("Слои", "Layers"),
                    tr("Офлайн-карты", "Offline maps"),
                    tr("Онлайн-карты", "Online maps"),
                ).forEachIndexed { index, title ->
                    val selected = tab == index
                    Column(
                        Modifier.weight(1f).clickable { tab = index }.padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            title,
                            color = if (selected) MapAccent else TextMain,
                            fontWeight = FontWeight.SemiBold,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) MapAccent else Color.Transparent))
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFE5E5EA))
            Column(
                Modifier.fillMaxWidth().padding(12.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    0 -> {
                        Card {
                            ToggleRow(
                                tr("Спутник", "Satellite"),
                                settings.mapLayer == "satellite" && !settings.overlayEnabled,
                            ) {
                                onSettingsChange(
                                    if (it) settings.copy(mapLayer = "satellite", overlayEnabled = false)
                                    else settings.copy(mapLayer = "base", overlayEnabled = false),
                                )
                            }
                            Divider()
                            ToggleRow(tr("Рельеф (тени)", "Hillshade"), settings.hillshade) {
                                onSettingsChange(settings.copy(hillshade = it))
                            }
                            Divider()
                            ToggleRow(tr("Показывать POI", "Show POI"), settings.showPois) {
                                onSettingsChange(settings.copy(showPois = it))
                            }
                            Divider()
                            ChoiceRow(
                                tr("Типы POI", "POI types") + " (${settings.poiTypes.size}/${ru.azimut.app.core.model.PoiTypes.ALL.size})",
                                false,
                            ) { onDismiss(); onPoiTypes() }
                        }
                        Hint(tr("Наложение поверх базовой карты", "Overlay on top of the base map"))
                        Card {
                            ChoiceRow(tr("Без наложения", "No overlay"), !settings.overlayEnabled) {
                                onSettingsChange(settings.copy(overlayEnabled = false))
                            }
                            overlayOptions.forEach { (layerId, titles) ->
                                Divider()
                                ChoiceRow(
                                    if (lang == "en") titles.second else titles.first,
                                    settings.overlayEnabled && settings.overlayLayer == layerId,
                                ) {
                                    onSettingsChange(
                                        settings.copy(mapLayer = "base", overlayEnabled = true, overlayLayer = layerId),
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        if (offline.isEmpty()) {
                            Hint(
                                tr(
                                    "Здесь появятся импортированные карты (.mbtiles) из папки NavData/maps.",
                                    "Imported maps (.mbtiles) from NavData/maps will appear here.",
                                ),
                            )
                        } else {
                            Card { StyleRows(offline, settings, onSettingsChange) }
                        }
                    }
                    else -> Card { StyleRows(online, settings, onSettingsChange) }
                }
            }
        }
    }
}

@Composable
private fun StyleRows(styles: List<MapStyle>, settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    styles.forEachIndexed { index, style ->
        if (index > 0) Divider()
        ChoiceRow(
            style.title(settings.language),
            settings.mapLayer == "base" && !settings.overlayEnabled && settings.mapStyle == style.id,
        ) {
            onSettingsChange(settings.copy(mapLayer = "base", mapStyle = style.id, overlayEnabled = false))
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg)) { content() }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = Color(0xFFE5E5EA))

@Composable
private fun Hint(text: String) =
    Text(text, color = TextHint, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 6.dp))

@Composable
private fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), color = TextMain)
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MapAccent)
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), color = TextMain)
        Switch(checked = checked, onCheckedChange = null)
    }
}
