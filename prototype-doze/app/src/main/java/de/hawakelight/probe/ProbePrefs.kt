package de.hawakelight.probe

import android.content.Context
import androidx.core.content.edit

/** Einstellungen des Prototyps. Token liegt hier unverschlüsselt – nur für den Test. */
class ProbePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("probe", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("base_url", "") ?: ""
        set(value) = prefs.edit { putString("base_url", value.trim()) }

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(value) = prefs.edit { putString("token", value.trim()) }

    var intervalMinutes: Int
        get() = prefs.getInt("interval_minutes", 15)
        set(value) = prefs.edit { putInt("interval_minutes", value) }

    var running: Boolean
        get() = prefs.getBoolean("running", false)
        set(value) = prefs.edit { putBoolean("running", value) }
}
