package de.willheisenberg.hawakelight.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.willheisenberg.hawakelight.color.hsvToRgb
import de.willheisenberg.hawakelight.settings.AppSettings
import de.willheisenberg.hawakelight.timeline.TimelineEntry
import java.util.Calendar

/** Kürzel der Wochentage in der Reihenfolge, wie sie angezeigt werden. */
private val weekdays = listOf(
    Calendar.MONDAY to "Mo",
    Calendar.TUESDAY to "Di",
    Calendar.WEDNESDAY to "Mi",
    Calendar.THURSDAY to "Do",
    Calendar.FRIDAY to "Fr",
    Calendar.SATURDAY to "Sa",
    Calendar.SUNDAY to "So",
)

fun describeDays(days: Set<Int>): String = when {
    days.isEmpty() || days.size == 7 -> "täglich"
    else -> weekdays.filter { it.first in days }.joinToString(", ") { it.second }
}

/** Dialog zum Anlegen und Ändern eines Punktes im Lichtplan. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineEditor(
    initial: TimelineEntry,
    defaultEntityId: String,
    minKelvin: Int,
    maxKelvin: Int,
    onSave: (TimelineEntry) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var entry by remember { mutableStateOf(initial) }
    val rgb = hsvToRgb(entry.hue, entry.saturation)
    val usesColor = entry.colorMode == AppSettings.MODE_RGB

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(entry) }) { Text("Speichern") } },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Löschen") }
                }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
        title = { Text(if (initial.id == 0L) "Neuer Punkt" else "Punkt ${initial.timeLabel}") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> entry = entry.copy(hour = hour, minute = minute) },
                        entry.hour,
                        entry.minute,
                        true,
                    ).show()
                }) {
                    Text("Uhrzeit: ${entry.timeLabel}", style = MaterialTheme.typography.titleMedium)
                }

                Text("Wochentage (keiner ausgewählt = täglich)", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    weekdays.forEach { (day, label) ->
                        FilterChip(
                            selected = day in entry.days,
                            onClick = {
                                entry = entry.copy(
                                    days = if (day in entry.days) entry.days - day else entry.days + day,
                                )
                            },
                            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = entry.turnOff,
                        onCheckedChange = { entry = entry.copy(turnOff = it) },
                    )
                    Text("  Licht ausschalten")
                }

                if (!entry.turnOff) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !usesColor,
                            onClick = { entry = entry.copy(colorMode = AppSettings.MODE_KELVIN) },
                            label = { Text("Weißton") },
                        )
                        FilterChip(
                            selected = usesColor,
                            onClick = { entry = entry.copy(colorMode = AppSettings.MODE_RGB) },
                            label = { Text("Farbe") },
                        )
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    if (usesColor) {
                                        Color(rgb.red, rgb.green, rgb.blue)
                                    } else {
                                        kelvinPreview(entry.colorTempKelvin)
                                    },
                                ),
                        )
                    }

                    if (usesColor) {
                        ColorWheel(
                            hue = entry.hue,
                            saturation = entry.saturation,
                            onColorChange = { hue, saturation ->
                                entry = entry.copy(hue = hue, saturation = saturation)
                            },
                        )
                    } else {
                        Text("Farbtemperatur: ${entry.colorTempKelvin} K")
                        Slider(
                            value = entry.colorTempKelvin.toFloat(),
                            onValueChange = { entry = entry.copy(colorTempKelvin = it.toInt()) },
                            valueRange = minKelvin.toFloat()..maxKelvin.toFloat(),
                            steps = ((maxKelvin - minKelvin) / 100 - 1).coerceAtLeast(0),
                        )
                    }

                    Text("Helligkeit: ${entry.brightnessPct} %")
                    Slider(
                        value = entry.brightnessPct.toFloat(),
                        onValueChange = { entry = entry.copy(brightnessPct = it.toInt()) },
                        valueRange = 1f..100f,
                    )
                }

                Text("Übergang: ${entry.transitionSeconds / 60} min ${entry.transitionSeconds % 60} s")
                Slider(
                    value = entry.transitionSeconds.toFloat(),
                    onValueChange = { entry = entry.copy(transitionSeconds = it.toInt()) },
                    valueRange = 0f..1800f,
                    steps = 59,
                )

                Text(
                    "Lampe: ${entry.entityId.ifEmpty { "$defaultEntityId (Standard)" }}",
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = entry.enabled,
                        onCheckedChange = { entry = entry.copy(enabled = it) },
                    )
                    Text("  Punkt aktiv")
                }
            }
        },
    )
}

/** Vorschau der Farbtemperatur, damit die Farbe im Dialog sichtbar ist. */
internal fun kelvinPreview(kelvin: Int): Color {
    val fraction = ((kelvin - 2000f) / 4500f).coerceIn(0f, 1f)
    return Color(red = 1f, green = 0.65f + 0.3f * fraction, blue = 0.35f + 0.65f * fraction)
}

/** Eine Zeile in der Liste des Lichtplans. */
@Composable
fun TimelineRow(entry: TimelineEntry, onClick: () -> Unit) {
    val rgb = hsvToRgb(entry.hue, entry.saturation)
    val preview = when {
        entry.turnOff -> Color.DarkGray
        entry.colorMode == AppSettings.MODE_RGB -> Color(rgb.red, rgb.green, rgb.blue)
        else -> kelvinPreview(entry.colorTempKelvin)
    }
    val summary = if (entry.turnOff) {
        "aus"
    } else {
        "${entry.brightnessPct} %, " +
            if (entry.colorMode == AppSettings.MODE_RGB) {
                "RGB ${rgb.red}/${rgb.green}/${rgb.blue}"
            } else {
                "${entry.colorTempKelvin} K"
            }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (entry.enabled) preview else Color.Gray.copy(alpha = 0.3f)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                "${entry.timeLabel}  ·  ${describeDays(entry.days)}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                summary + if (entry.enabled) "" else "  (aus)",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onClick) { Text("Ändern") }
    }
}
