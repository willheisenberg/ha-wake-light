package de.hawakelight.app.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.hawakelight.app.log.EventLog

/** Plant den Sonnenaufgang neu, wenn sich der Wecker oder die Systemzeit ändert. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> "Wecker geändert"
            Intent.ACTION_BOOT_COMPLETED -> "Neustart"
            Intent.ACTION_TIME_CHANGED -> "Uhrzeit geändert"
            Intent.ACTION_TIMEZONE_CHANGED -> "Zeitzone geändert"
            else -> return
        }
        EventLog.add(context, "$trigger – nächster Wecker: ${SunriseScheduler.describeNextAlarm(context)}")
        SunriseScheduler.reschedule(context)
    }
}
