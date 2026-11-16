package ru.azimut.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.azimut.app.AppInfo
import ru.azimut.app.core.model.AppSettings
import ru.azimut.app.core.platform.PlatformInfo
import ru.azimut.app.ui.components.AccordionSection
import ru.azimut.app.ui.components.ChoiceRow
import ru.azimut.app.ui.components.InfoRow
import ru.azimut.app.ui.components.SwitchRow
import ru.azimut.app.ui.tr

/** The «Интерфейс» and «О приложении» groups, split out of SettingsScreen.kt. */

@Composable
fun ColumnScope.InterfaceSection(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    expanded: (String) -> Boolean,
    setExpanded: (String) -> Unit,
) {
    AccordionSection(
        tr("Интерфейс", "Interface"),
        expanded("interface"),
        { setExpanded("interface") },
        icon = Icons.Filled.Palette,
        iconTint = Color(0xFF7B1FA2),
        summary = if (settings.language == "ru") tr("Русский", "Russian") else tr("English", "English"),
    ) {
        ChoiceRow(
            tr("Язык", "Language"),
            listOf("ru" to "Русский", "en" to "English"),
            settings.language,
        ) { onSettingsChange(settings.copy(language = it)) }
        ChoiceRow(
            tr("Тема", "Theme"),
            listOf("dark" to tr("Тёмная", "Dark"), "light" to tr("Светлая", "Light")),
            settings.theme,
        ) { onSettingsChange(settings.copy(theme = it)) }
        InfoRow(tr("Платформа", "Platform"), PlatformInfo.name)
        InfoRow(tr("Кнопки на карте", "Map buttons"), tr("крупные", "large"))
        SwitchRow(
            tr("Большая скорость", "Large speed readout"),
            settings.bigSpeed,
            { onSettingsChange(settings.copy(bigSpeed = it)) },
            tr("Крупные цифры для мотокрепления", "Big digits for handlebar mounting"),
        )
        if (!PlatformInfo.isDesktop) {
            SwitchRow(
                tr("Скрывать системные кнопки", "Hide system buttons"),
                settings.hideSystemButtons,
                { onSettingsChange(settings.copy(hideSystemButtons = it)) },
                tr(
                    "Полноэкранный режим без панели навигации",
                    "Immersive mode without the navigation bar",
                ),
            )
        }
    }
}

@Composable
fun ColumnScope.AboutSection(
    expanded: (String) -> Boolean,
    setExpanded: (String) -> Unit,
    onOpenHelp: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    AccordionSection(
        tr("О приложении", "About"),
        expanded("about"),
        { setExpanded("about") },
        icon = Icons.Filled.Info,
        iconTint = Color(0xFF455A64),
        summary = "v" + AppInfo.VERSION,
    ) {
        InfoRow(tr("Автор", "Author"), "Скляров Александр Михайлович")
        InfoRow(tr("Контакт", "Contact"), "alexsandr23.83@mail.ru")
        InfoRow(tr("Версия", "Version"), ru.azimut.app.AppInfo.VERSION)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenHelp) { Text(tr("Справка", "Help")) }
            OutlinedButton(onClick = onOpenAbout) { Text(tr("Подробнее", "Details")) }
        }
        Spacer(Modifier.height(12.dp))
    }
}
