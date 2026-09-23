package de.hawakelight.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.hawakelight.app.color.hsvToRgb
import de.hawakelight.app.ha.HaClient
import de.hawakelight.app.ha.withRetry
import de.hawakelight.app.log.EventLog
import de.hawakelight.app.settings.AppSettings
import kotlinx.coroutines.runBlocking

/** Löst den Sonnenaufgang aus: Licht auf 100 % mit Übergang bis zur Weckzeit. */
class SunriseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = AppSettings(context)
        if (!settings.sunriseEnabled) return
        if (intent.action == SunriseScheduler.ACTION_RECHECK) {
            // Der fremde Wecker ist vorbei – jetzt ist der Wecker der Uhr-App sichtbar.
            EventLog.add(context, "Erneuter Blick auf den nächsten Wecker")
            SunriseScheduler.reschedule(context)
            return
        }
        val transitionSeconds = intent.getIntExtra(SunriseScheduler.EXTRA_TRANSITION, 0)
        settings.lastSunriseAt = System.currentTimeMillis()

        val pending = goAsync()
        Thread {
            try {
                // Bis zu drei Versuche, falls Home Assistant gerade neu startet.
                val client = HaClient(settings.baseUrl, settings.token)
                val useRgb = settings.colorMode == AppSettings.MODE_RGB
                val kelvin = settings.colorTempKelvin.coerceIn(settings.minKelvin, settings.maxKelvin)
                val rgb = if (useRgb) hsvToRgb(settings.hue, settings.saturation) else null
                val result = runBlocking {
                    withRetry(attempts = 3, delayMillis = { attempt -> 5_000L * attempt }) {
                        client.turnOn(
                            entityId = settings.entityId,
                            brightnessPct = settings.brightnessPct,
                            transitionSeconds = transitionSeconds,
                            colorTempKelvin = kelvin,
                            rgbColor = rgb,
                        )
                    }
                }
                val minutes = transitionSeconds / 60
                EventLog.add(
                    context,
                    result.fold(
                        onSuccess = {
                            val farbe = rgb?.let { "RGB ${it.red}/${it.green}/${it.blue}" } ?: "$kelvin K"
                            "Sonnenaufgang gestartet, $minutes min bis ${settings.brightnessPct} % bei $farbe"
                        },
                        onFailure = { "Sonnenaufgang fehlgeschlagen: ${it.message}" },
                    ),
                )
            } finally {
                pending.finish()
            }
        }.start()
    }
}
