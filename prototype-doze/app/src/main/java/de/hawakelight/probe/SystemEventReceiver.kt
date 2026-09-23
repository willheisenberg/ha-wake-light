package de.hawakelight.probe

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                ProbeLog.append(context, "Neustart")
                if (ProbePrefs(context).running) ProbeScheduler.scheduleNext(context)
            }
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED ->
                ProbeLog.append(context, "Nächster Wecker geändert: ${Probe.describeNextAlarm(context)}")
        }
    }
}
