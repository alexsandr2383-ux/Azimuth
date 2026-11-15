package ru.azimut.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import ru.azimut.app.core.platform.PlatformInfo
import ru.azimut.app.ui.components.BackHeader
import ru.azimut.app.ui.tr

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxSize()) {
        BackHeader(tr("О приложении", "About"), onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Азимут / Azimuth", style = MaterialTheme.typography.headlineSmall)
            Text(
                tr("Офлайн-навигация для эндуро и походов", "Offline navigation for enduro and hiking"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            Text(tr("Автор", "Author"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("Скляров Александр Михайлович", style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { uriHandler.openUri("mailto:alexsandr23.83@mail.ru") }) {
                Text("alexsandr23.83@mail.ru")
            }

            Spacer(Modifier.height(16.dp))
            Info(tr("Версия", "Version"), ru.azimut.app.AppInfo.VERSION)
            Info(tr("Платформа", "Platform"), PlatformInfo.name)
            Info(tr("Минимальные версии", "Minimum versions"), "Android 8.0 · Windows 10 (1903) · iOS 15")

            Spacer(Modifier.height(16.dp))
            Text(tr("Приватность", "Privacy"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                tr(
                    "Никакой телеметрии и аналитики по умолчанию. Данные не уходят на серверы. " +
                        "Всё хранится локально в папке NavData/ и синхронизируется только между вашими устройствами.",
                    "No telemetry or analytics by default. Data never leaves your devices. " +
                        "Everything is stored locally in NavData/ and synced only between your own devices.",
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(16.dp))
            Text(tr("Лицензии данных", "Data licenses"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                tr(
                    "• Карты Esri World Street Map / World Imagery — © Esri, Maxar, Earthstar Geographics, HERE, Garmin, участники OSM.\n" +
                        "• Copernicus DEM GLO-30 / GLO-90 — Free & Open, ESA.\n" +
                        "• SRTM 30 м — NASA/USGS, public domain.\n" +
                        "• MapLibre GL — BSD. Compose Multiplatform — Apache 2.0.\n\n" +
                        "Перед коммерческим использованием растровых тайлов проверьте условия провайдера.",
                    "• Esri World Street Map / World Imagery — © Esri, Maxar, Earthstar Geographics, HERE, Garmin, OSM contributors.\n" +
                        "• Copernicus DEM GLO-30 / GLO-90 — Free & Open, ESA.\n" +
                        "• SRTM 30 m — NASA/USGS, public domain.\n" +
                        "• MapLibre GL — BSD. Compose Multiplatform — Apache 2.0.\n\n" +
                        "Verify provider terms before commercial use of raster tiles.",
                ),
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(16.dp))
            Text(tr("Юридическое предупреждение", "Legal notice"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                tr(
                    "Картографические данные и DEM могут содержать ошибки. Профиль «Эндуро» допускает движение по пешим тропам, " +
                        "но не отменяет местных запретов и правил ООПТ. Решение о маршруте принимает пользователь.",
                    "Map and DEM data may contain errors. The Enduro profile allows hiking trails but does not " +
                        "override local prohibitions or protected-area rules. Route decisions are the user's.",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
