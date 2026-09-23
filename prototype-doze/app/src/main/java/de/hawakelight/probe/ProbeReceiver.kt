package de.hawakelight.probe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

class ProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ProbePrefs(context).running) return
        val scheduledAt = intent.getLongExtra(ProbeScheduler.EXTRA_SCHEDULED_AT, 0L)
        // Zuerst neu planen, damit ein Fehler im Request die Messreihe nicht abbricht.
        ProbeScheduler.scheduleNext(context)
        val pending = goAsync()
        thread {
            try {
                Probe.run(context, "Alarm", scheduledAt)
            } finally {
                pending.finish()
            }
        }
    }
}
