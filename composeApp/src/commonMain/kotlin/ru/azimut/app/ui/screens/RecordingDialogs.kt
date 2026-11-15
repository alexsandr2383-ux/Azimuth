package ru.azimut.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.azimut.app.core.model.AppSettings
import ru.azimut.app.ui.tr

/** Recording accuracy presets (idea from AlpineQuest): one choice instead of raw seconds. */
internal enum class RecordingPreset(val intervalSec: Int, val batterySaver: Boolean) {
    ECONOMY(10, true),
    GOOD(3, false),
    FAST(1, false);

    @Composable
    fun title(): String = when (this) {
        ECONOMY -> tr("Экономно", "Economy")
        GOOD -> tr("Хорошая точность", "Good accuracy")
        FAST -> tr("Быстрая запись", "Fast recording")
    }

    @Composable
    fun hint(): String = when (this) {
        ECONOMY -> tr("Многодневные походы, экономит батарею", "Multi-day hikes, saves battery")
        GOOD -> tr("Многочасовые поездки", "Rides of several hours")
        FAST -> tr("Короткие выезды с переменной скоростью", "Short rides with changing speed")
    }

    companion object {
        fun of(settings: AppSettings): RecordingPreset? =
            entries.firstOrNull { it.intervalSec == settings.recordingIntervalSec && it.batterySaver == settings.batterySaver }
    }
}

/** Shown when the record button is pressed while idle: pick the preset, then start. */
@Composable
internal fun StartRecordingDialog(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val current = RecordingPreset.of(settings)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Запись трека", "Record track")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RecordingPreset.entries.forEach { preset ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = current == preset,
                            role = Role.RadioButton,
                            onClick = { onSettingsChange(settings.copy(recordingIntervalSec = preset.intervalSec, batterySaver = preset.batterySaver)) },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current == preset, onClick = null)
                        Column(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
                            Text(preset.title(), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                preset.hint(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (current == null) {
                    Text(
                        tr(
                            "Свой интервал: ${settings.recordingIntervalSec} с (из настроек)",
                            "Custom interval: ${settings.recordingIntervalSec} s (from settings)",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onStart) { Text(tr("Начать запись", "Start")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена", "Cancel")) } },
    )
}

/** Guards against accidentally ending a recording with one tap. */
@Composable
internal fun StopRecordingDialog(onStop: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Остановить запись?", "Stop recording?")) },
        text = { Text(tr("Трек будет сохранён в журнал.", "The track will be saved to the journal.")) },
        confirmButton = { TextButton(onClick = onStop) { Text(tr("Остановить и сохранить", "Stop and save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Продолжить запись", "Keep recording")) } },
    )
}
