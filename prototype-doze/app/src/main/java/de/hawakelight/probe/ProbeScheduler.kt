package de.hawakelight.probe

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Plant den nächsten Messpunkt mit setExactAndAllowWhileIdle – genau der Mechanismus,
 * den die echte App für Sonnenaufgang und Timeline nutzen soll.
 */
object ProbeScheduler {
    const val EXTRA_SCHEDULED_AT = "scheduled_at"

    fun scheduleNext(context: Context) {
        val prefs = ProbePrefs(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (!Probe.canScheduleExact(alarms)) {
            prefs.running = false
            ProbeLog.append(context, "Exakte Alarme nicht erlaubt – Nachttest gestoppt")
            return
        }
        val triggerAt = System.currentTimeMillis() + prefs.intervalMinutes * 60_000L
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context, triggerAt))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, 0L))
    }

    private fun pendingIntent(context: Context, triggerAt: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ProbeReceiver::class.java).putExtra(EXTRA_SCHEDULED_AT, triggerAt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
