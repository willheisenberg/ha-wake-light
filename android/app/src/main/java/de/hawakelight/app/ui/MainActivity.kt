package de.hawakelight.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.hawakelight.app.color.hsvToRgb
import de.hawakelight.app.settings.AppSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SettingsScreen() }
    }
}

@Composable
private fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val systemIsDark = isSystemInDarkTheme()
    var logExpanded by remember { mutableStateOf(false) }
    var lightsExpanded by remember { mutableStateOf(false) }
    val dark = state.darkTheme ?: systemIsDark

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Lichtwecker", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = { viewModel.toggleDarkTheme(systemIsDark) }) {
                        Text(if (dark) "☀ Hell" else "🌙 Dunkel")
                    }
                }

                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = viewModel::onBaseUrlChange,
                    label = { Text("Home-Assistant-URL") },
                    placeholder = { Text("http://192.168.178.192:8123") },
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.token,
                    onValueChange = viewModel::onTokenChange,
                    label = { Text("Long-Lived Access Token") },
                    singleLine = true,
                    enabled = !state.busy,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.entityId,
                    onValueChange = viewModel::onEntityIdChange,
                    label = { Text("Lampe (entity_id)") },
                    placeholder = { Text("light.danszimmer") },
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.sunriseMinutes,
                    onValueChange = viewModel::onSunriseChange,
                    label = { Text("Sonnenaufgang startet ... Minuten vor dem Wecker") },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                LightSection(state, viewModel)

                Button(
                    onClick = viewModel::saveAndVerify,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Speichern und prüfen")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = viewModel::testLight,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Licht testen")
                    }
                    OutlinedButton(
                        onClick = viewModel::turnOffLight,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Licht aus")
                    }
                }

                state.message?.let { message ->
                    Text(
                        message,
                        color = if (state.messageIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = state.sunriseEnabled, onCheckedChange = viewModel::setSunriseEnabled)
                    Text("  Lichtwecker aktiv", style = MaterialTheme.typography.titleMedium)
                }
                Text("Nächster Wecker: ${state.nextAlarm}", style = MaterialTheme.typography.bodyMedium)
                Text(state.scheduleStatus, style = MaterialTheme.typography.bodyMedium)

                if (!state.exactAlarmsAllowed) {
                    Button(onClick = { openExactAlarmSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Exakte Alarme erlauben")
                    }
                }

                if (state.events.isNotEmpty()) {
                    CollapsibleSection(
                        title = "Protokoll",
                        summary = state.events.first(),
                        expanded = logExpanded,
                        onToggle = { logExpanded = !logExpanded },
                    ) {
                        Text(
                            "löschen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { viewModel.clearLog() }
                                .padding(vertical = 4.dp),
                        )
                        state.events.forEach { line ->
                            Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }

                if (state.suggestions.isNotEmpty()) {
                    CollapsibleSection(
                        title = "Lampen in Home Assistant",
                        summary = "${state.suggestions.size} gefunden",
                        expanded = lightsExpanded,
                        onToggle = { lightsExpanded = !lightsExpanded },
                    ) {
                        state.suggestions.forEach { light ->
                            Text(
                                "${light.name} – ${light.entityId}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !state.busy) { viewModel.onEntityIdChange(light.entityId) }
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Abschnitt mit Kopfzeile zum Auf- und Zuklappen; zugeklappt bleibt nur die Zusammenfassung. */
@Composable
private fun CollapsibleSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (summary.isNotEmpty()) {
                    Text(summary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing?.invoke()
                Text(if (expanded) "  ▲" else "  ▼")
            }
        }
        if (expanded) content()
    }
}

/** Farbe und Helligkeit. Zugeklappt bleibt nur eine Zeile mit Vorschau übrig. */
@Composable
private fun LightSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val rgb = hsvToRgb(state.hue, state.saturation)
    val usesColor = state.colorMode == AppSettings.MODE_RGB
    val preview = if (usesColor) Color(rgb.red, rgb.green, rgb.blue) else kelvinToColor(state.colorTempKelvin)
    val summary = if (usesColor) "RGB ${rgb.red}/${rgb.green}/${rgb.blue}" else "${state.colorTempKelvin} K"

    CollapsibleSection(
        title = "Farbe und Helligkeit",
        summary = "$summary · ${state.brightnessPct} %",
        expanded = expanded,
        onToggle = { expanded = !expanded },
        trailing = {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(preview),
            )
        },
    ) {
        run {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !usesColor,
                    onClick = { viewModel.onColorModeChange(AppSettings.MODE_KELVIN) },
                    label = { Text("Weißton") },
                )
                FilterChip(
                    selected = usesColor,
                    onClick = { viewModel.onColorModeChange(AppSettings.MODE_RGB) },
                    label = { Text("Farbe") },
                )
            }

            if (usesColor) {
                ColorWheel(
                    hue = state.hue,
                    saturation = state.saturation,
                    onColorChange = viewModel::onColorChange,
                )
            } else {
                Text(
                    "Farbtemperatur: ${state.colorTempKelvin} K" +
                        when (state.colorTempKelvin) {
                            state.minKelvin -> " (wärmstes Weiß)"
                            state.maxKelvin -> " (kältestes Weiß)"
                            else -> ""
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = state.colorTempKelvin.toFloat(),
                    onValueChange = { viewModel.onColorTempChange(it.toInt()) },
                    valueRange = state.minKelvin.toFloat()..state.maxKelvin.toFloat(),
                    steps = ((state.maxKelvin - state.minKelvin) / 100 - 1).coerceAtLeast(0),
                    enabled = !state.busy,
                )
            }

            Text("Helligkeit: ${state.brightnessPct} %", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = state.brightnessPct.toFloat(),
                onValueChange = { viewModel.onBrightnessChange(it.toInt()) },
                valueRange = 1f..100f,
                enabled = !state.busy,
            )
        }
    }
}

/** Grobe Vorschau der Farbtemperatur: warm ist orange, kalt ist bläulich. */
private fun kelvinToColor(kelvin: Int): Color {
    val fraction = ((kelvin - 2000f) / 4500f).coerceIn(0f, 1f)
    return Color(red = 1f, green = 0.65f + 0.3f * fraction, blue = 0.35f + 0.65f * fraction)
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
        )
    }
}
