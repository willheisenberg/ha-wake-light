package de.willheisenberg.hawakelight.timeline

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import de.willheisenberg.hawakelight.alarm.SunriseScheduler
import de.willheisenberg.hawakelight.log.EventLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plant immer nur den nächsten fälligen Punkt des Lichtplans. */
object TimelineScheduler {
    const val EXTRA_ENTRY_ID = "entry_id"
    private val timeFormat = SimpleDateFormat("EE HH:mm", Locale.GERMANY)

    fun reschedule(context: Context, logging: Boolean = false): String {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(pendingIntent(context, 0L))

        val entries = TimelineStore(context).load()
        if (entries.none { it.enabled }) return "Keine aktiven Punkte"
        if (!SunriseScheduler.canScheduleExact(context)) return "Berechtigung für exakte Alarme fehlt"

        val due = TimelinePlanner.next(entries, System.currentTimeMillis())
            ?: return "Kein nächster Punkt"
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due.at, pendingIntent(context, due.entry.id))

        val status = "Nächster Punkt: ${timeFormat.format(Date(due.at))}"
        if (logging) EventLog.add(context, "Lichtplan geplant – $status")
        return status
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, 0L))
    }

    private fun pendingIntent(context: Context, entryId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, TimelineReceiver::class.java).putExtra(EXTRA_ENTRY_ID, entryId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
