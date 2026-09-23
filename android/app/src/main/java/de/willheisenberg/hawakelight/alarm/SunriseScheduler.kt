package de.willheisenberg.hawakelight.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import de.willheisenberg.hawakelight.log.EventLog
import de.willheisenberg.hawakelight.settings.AppSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plant den Sonnenaufgang anhand des nächsten Weckers der Uhr-App. */
object SunriseScheduler {
    const val EXTRA_TRANSITION = "transition_seconds"
    const val ACTION_RECHECK = "de.willheisenberg.hawakelight.RECHECK"
    private val timeFormat = SimpleDateFormat("EE dd.MM. HH:mm", Locale.GERMANY)

    /** Liest den nächsten Wecker und plant danach neu. Gibt den Status für die Oberfläche zurück. */
    fun reschedule(context: Context, logging: Boolean = true): String {
        val settings = AppSettings(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        cancel(context)

        if (!settings.sunriseEnabled) return "Lichtwecker ist aus"
        if (!canScheduleExact(context)) {
            return "Berechtigung für exakte Alarme fehlt"
        }
        if (!settings.isConfigured) return "Home Assistant ist noch nicht eingerichtet"

        val alarmAt = nextAlarmAt(context)
        val plan = SunrisePlan.compute(
            alarmAt = alarmAt,
            now = System.currentTimeMillis(),
            leadMillis = settings.sunriseMinutes * 60_000L,
            lastSunriseAt = settings.lastSunriseAt,
            creatorPackage = nextAlarmCreator(context),
        )
        return when (plan) {
            is SunrisePlan.Skip -> {
                if (logging) EventLog.add(context, "Nicht geplant: ${plan.reason}")
                "Nicht geplant: ${plan.reason}"
            }
            is SunrisePlan.Recheck -> {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.at, recheckIntent(context))
                val status = "${plan.reason} – neuer Blick um ${timeFormat.format(Date(plan.at))}"
                if (logging) EventLog.add(context, "Nicht geplant: $status")
                status
            }
            is SunrisePlan.Start -> {
                alarms.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    plan.startAt,
                    pendingIntent(context, plan.transitionSeconds),
                )
                val status = "Sonnenaufgang ab ${timeFormat.format(Date(plan.startAt))}" +
                    " über ${plan.transitionSeconds / 60} min bis ${timeFormat.format(Date(alarmAt!!))}"
                if (logging) EventLog.add(context, "Geplant: $status")
                status
            }
        }
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(pendingIntent(context, 0))
        alarms.cancel(recheckIntent(context))
    }

    fun nextAlarmAt(context: Context): Long? =
        context.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime

    /** Paketname der App, die den nächsten Wecker gestellt hat – `null`, wenn Android ihn verschweigt. */
    fun nextAlarmCreator(context: Context): String? =
        context.getSystemService(AlarmManager::class.java).nextAlarmClock?.showIntent?.creatorPackage

    fun describeNextAlarm(context: Context): String {
        val time = nextAlarmAt(context)?.let { timeFormat.format(Date(it)) } ?: return "keiner"
        return nextAlarmCreator(context)?.let { "$time (von $it)" } ?: time
    }

    fun canScheduleExact(context: Context): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
    }

    private fun recheckIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, SunriseReceiver::class.java).setAction(ACTION_RECHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun pendingIntent(context: Context, transitionSeconds: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, SunriseReceiver::class.java).putExtra(EXTRA_TRANSITION, transitionSeconds),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
