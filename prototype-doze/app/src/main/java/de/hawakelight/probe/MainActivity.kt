package de.hawakelight.probe

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    // Wird bei jedem onResume erhöht, damit der Screen Status und Log neu liest.
    private var resumeCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    ProbeScreen(resumeCount)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
    }
}

@Composable
private fun ProbeScreen(resumeCount: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ProbePrefs(context) }

    var baseUrl by remember { mutableStateOf(prefs.baseUrl) }
    var token by remember { mutableStateOf(prefs.token) }
    var interval by remember { mutableStateOf(prefs.intervalMinutes.toString()) }
    var running by remember { mutableStateOf(prefs.running) }
    var refresh by remember { mutableIntStateOf(0) }

    val tick = resumeCount + refresh
    val exactAllowed = remember(tick) {
        Probe.canScheduleExact(context.getSystemService(AlarmManager::class.java))
    }
    val environment = remember(tick) { Probe.describeEnvironment(context) }
    val nextAlarm = remember(tick) { Probe.describeNextAlarm(context) }
    val log = remember(tick) { ProbeLog.read(context).lines().filter { it.isNotBlank() }.reversed() }

    fun save() {
        prefs.baseUrl = baseUrl
        prefs.token = token
        prefs.intervalMinutes = interval.toIntOrNull()?.coerceAtLeast(10) ?: 15
        interval = prefs.intervalMinutes.toString()
    }

    Column(
        Modifier
            .safeDrawingPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("HA Doze-Test", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Prüft, ob Home Assistant nachts im Doze-Modus über einen exakten Alarm erreichbar ist. " +
                "Es wird nur GET /api/ aufgerufen, kein Licht geschaltet.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Home-Assistant-URL") },
            placeholder = { Text("http://192.168.1.10:8123") },
            singleLine = true,
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Long-Lived Access Token") },
            singleLine = true,
            enabled = !running,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = interval,
            onValueChange = { interval = it.filter(Char::isDigit) },
            label = { Text("Intervall in Minuten (min. 10)") },
            singleLine = true,
            enabled = !running,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Nächster Wecker: $nextAlarm", style = MaterialTheme.typography.bodyMedium)
        Text(environment, style = MaterialTheme.typography.bodyMedium)

        if (!exactAllowed) {
            Button(onClick = { openExactAlarmSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Exakte Alarme erlauben")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = !running,
                onClick = {
                    save()
                    scope.launch {
                        withContext(Dispatchers.IO) { Probe.run(context, "Manuell") }
                        refresh++
                    }
                },
            ) { Text("Jetzt testen") }

            Button(
                enabled = exactAllowed || running,
                onClick = {
                    if (running) {
                        prefs.running = false
                        ProbeScheduler.cancel(context)
                        ProbeLog.append(context, "Nachttest gestoppt")
                    } else {
                        save()
                        prefs.running = true
                        ProbeLog.append(
                            context,
                            "Nachttest gestartet, alle ${prefs.intervalMinutes} min | ${Probe.describeEnvironment(context)}",
                        )
                        ProbeScheduler.scheduleNext(context)
                    }
                    running = prefs.running
                    refresh++
                },
            ) { Text(if (running) "Nachttest stoppen" else "Nachttest starten") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { shareLog(context) }) { Text("Log teilen") }
            OutlinedButton(onClick = {
                ProbeLog.clear(context)
                refresh++
            }) { Text("Log löschen") }
        }

        Text("Log (neueste zuerst)", style = MaterialTheme.typography.titleSmall)
        log.forEach { line ->
            Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
        )
    }
}

private fun shareLog(context: Context) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "HA Doze-Test Log")
        .putExtra(Intent.EXTRA_TEXT, ProbeLog.read(context))
    context.startActivity(Intent.createChooser(send, "Log teilen"))
}
