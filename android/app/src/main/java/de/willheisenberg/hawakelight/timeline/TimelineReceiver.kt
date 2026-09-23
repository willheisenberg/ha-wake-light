package de.willheisenberg.hawakelight.timeline

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.willheisenberg.hawakelight.color.hsvToRgb
import de.willheisenberg.hawakelight.ha.HaClient
import de.willheisenberg.hawakelight.ha.withRetry
import de.willheisenberg.hawakelight.log.EventLog
import de.willheisenberg.hawakelight.settings.AppSettings
import kotlinx.coroutines.runBlocking

/** Setzt einen Punkt des Lichtplans um und plant den nächsten. */
class TimelineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getLongExtra(TimelineScheduler.EXTRA_ENTRY_ID, 0L)
        val entry = TimelineStore(context).load().firstOrNull { it.id == entryId }
        // Zuerst neu planen, damit ein Fehler den Plan nicht abreißen lässt.
        TimelineScheduler.reschedule(context)
        if (entry == null || !entry.enabled) return

        val settings = AppSettings(context)
        val entityId = entry.entityId.ifEmpty { settings.entityId }
        val pending = goAsync()
        Thread {
            try {
                val client = HaClient(settings.baseUrl, settings.token)
                val result = runBlocking {
                    withRetry(attempts = 3, delayMillis = { attempt -> 5_000L * attempt }) {
                        if (entry.turnOff) {
                            client.turnOff(entityId, entry.transitionSeconds)
                        } else {
                            client.turnOn(
                                entityId = entityId,
                                brightnessPct = entry.brightnessPct,
                                transitionSeconds = entry.transitionSeconds,
                                colorTempKelvin = entry.colorTempKelvin,
                                rgbColor = if (entry.colorMode == AppSettings.MODE_RGB) {
                                    hsvToRgb(entry.hue, entry.saturation)
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
                val what = if (entry.turnOff) "aus" else "${entry.brightnessPct} %"
                EventLog.add(
                    context,
                    result.fold(
                        onSuccess = { "Lichtplan ${entry.timeLabel}: $entityId auf $what" },
                        onFailure = { "Lichtplan ${entry.timeLabel} fehlgeschlagen: ${it.message}" },
                    ),
                )
            } finally {
                pending.finish()
            }
        }.start()
    }
}
