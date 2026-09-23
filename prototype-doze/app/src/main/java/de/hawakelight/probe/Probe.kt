package de.hawakelight.probe

import android.app.AlarmManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ein Messpunkt: Doze-Status, Netz und Erreichbarkeit von Home Assistant. */
object Probe {
    fun run(context: Context, trigger: String, scheduledAt: Long = 0L) {
        val prefs = ProbePrefs(context)
        val power = context.getSystemService(PowerManager::class.java)
        val delay = if (scheduledAt > 0L) " +${(System.currentTimeMillis() - scheduledAt) / 1000}s" else ""
        val doze = power.isDeviceIdleMode
        val network = describeNetwork(context)
        val result = HaClient.ping(prefs.baseUrl, prefs.token)
        ProbeLog.append(context, "$trigger$delay | doze=$doze | netz=$network | $result")
    }

    fun describeEnvironment(context: Context): String {
        val power = context.getSystemService(PowerManager::class.java)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val exactAllowed = canScheduleExact(alarms)
        val batteryExempt = power.isIgnoringBatteryOptimizations(context.packageName)
        return "exakte Alarme=$exactAllowed | Akku-Ausnahme=$batteryExempt"
    }

    fun canScheduleExact(alarms: AlarmManager): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /** Nächster Wecker laut System inklusive der App, die ihn gestellt hat. */
    fun describeNextAlarm(context: Context): String {
        val info = context.getSystemService(AlarmManager::class.java).nextAlarmClock ?: return "keiner"
        val time = SimpleDateFormat("EE dd.MM. HH:mm", Locale.GERMANY).format(Date(info.triggerTime))
        val showIntent = info.showIntent ?: return "$time (showIntent=null)"
        return "$time von ${showIntent.creatorPackage ?: "creatorPackage=null"}"
    }

    private fun describeNetwork(context: Context): String {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return "keins"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wlan"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobil"
            else -> "andere"
        }
    }
}
