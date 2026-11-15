package ru.azimut.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.parseCoordPair
import ru.azimut.app.ui.tr

/** "Go to coordinates" dialog. Reports a parsed point via [onGo] or an error text via [onInvalid]. */
@Composable
internal fun GotoDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onGo: (GeoPoint) -> Unit,
    onInvalid: (String) -> Unit,
) {
    val invalidGoto = tr(
        "Не удалось разобрать координаты. Примеры: 55.75, 37.62 или 55°07'23\"N 37°27'18\"E",
        "Could not parse the coordinates. Examples: 55.75, 37.62 or 55°07'23\"N 37°27'18\"E",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("К координатам", "Go to coordinates")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text(tr("Широта, долгота", "Latitude, longitude")) },
                    singleLine = true,
                )
                Text(
                    tr(
                        "Поддерживаются десятичные градусы и формат ГГ°ММ'СС\"N.",
                        "Decimal degrees and D°M'SS\"N format are supported.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = parseCoordPair(text)
                if (parsed != null) onGo(GeoPoint(parsed.first, parsed.second)) else onInvalid(invalidGoto)
            }) { Text(tr("Перейти", "Go")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Отмена", "Cancel")) }
        },
    )
}

/** Full-screen tap barrier with a hint while the exit confirmation is armed. */
@Composable
internal fun ExitHintOverlay(hint: String) {
    Box(Modifier.fillMaxSize().gestureBarrier(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
        ) {
            Text(
                hint,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun MessageDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("OK", "OK")) } },
        text = { Text(text) },
    )
}
