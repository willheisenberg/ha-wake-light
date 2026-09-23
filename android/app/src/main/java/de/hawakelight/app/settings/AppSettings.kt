package de.hawakelight.app.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Einstellungen der App. Der Token liegt in verschlüsselten SharedPreferences;
 * scheitert das (z. B. beschädigter Keystore), wird auf normale Preferences ausgewichen.
 */
class AppSettings(context: Context) {
    private val prefs: SharedPreferences = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "settings_encrypted",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    var baseUrl: String
        get() = prefs.getString(KEY_URL, "") ?: ""
        set(value) = prefs.edit { putString(KEY_URL, value.trim()) }

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit { putString(KEY_TOKEN, value.trim()) }

    var entityId: String
        get() = prefs.getString(KEY_ENTITY, "light.danszimmer") ?: ""
        set(value) = prefs.edit { putString(KEY_ENTITY, value.trim()) }

    /** Vorlauf des Sonnenaufgangs vor der Weckzeit, in Minuten. */
    var sunriseMinutes: Int
        get() = prefs.getInt(KEY_SUNRISE, 20)
        set(value) = prefs.edit { putInt(KEY_SUNRISE, value.coerceIn(1, 120)) }

    /** Gewählte Farbtemperatur des Sonnenaufgangs in Kelvin – kleiner ist wärmer. */
    var colorTempKelvin: Int
        get() = prefs.getInt(KEY_KELVIN, DEFAULT_WARMEST_KELVIN)
        set(value) = prefs.edit { putInt(KEY_KELVIN, value) }

    /** Grenzen der gewählten Lampe, beim Prüfen aus Home Assistant übernommen. */
    var minKelvin: Int
        get() = prefs.getInt(KEY_MIN_KELVIN, DEFAULT_WARMEST_KELVIN)
        set(value) = prefs.edit { putInt(KEY_MIN_KELVIN, value) }

    var maxKelvin: Int
        get() = prefs.getInt(KEY_MAX_KELVIN, DEFAULT_COLDEST_KELVIN)
        set(value) = prefs.edit { putInt(KEY_MAX_KELVIN, value) }

    /** "kelvin" = Weißton über die Farbtemperatur, "rgb" = Farbe aus dem Farbkreis. */
    var colorMode: String
        get() = prefs.getString(KEY_COLOR_MODE, MODE_KELVIN) ?: MODE_KELVIN
        set(value) = prefs.edit { putString(KEY_COLOR_MODE, value) }

    /** Farbton im Farbkreis in Grad (0–360). */
    var hue: Float
        get() = prefs.getFloat(KEY_HUE, 30f)
        set(value) = prefs.edit { putFloat(KEY_HUE, value) }

    /** Sättigung im Farbkreis (0–1). */
    var saturation: Float
        get() = prefs.getFloat(KEY_SATURATION, 1f)
        set(value) = prefs.edit { putFloat(KEY_SATURATION, value.coerceIn(0f, 1f)) }

    /** Zielhelligkeit des Sonnenaufgangs in Prozent. */
    var brightnessPct: Int
        get() = prefs.getInt(KEY_BRIGHTNESS, 100)
        set(value) = prefs.edit { putInt(KEY_BRIGHTNESS, value.coerceIn(1, 100)) }

    /** Dunkles Design – null bedeutet: dem System folgen. */
    var darkTheme: Boolean?
        get() = if (prefs.contains(KEY_DARK)) prefs.getBoolean(KEY_DARK, false) else null
        set(value) = prefs.edit { if (value == null) remove(KEY_DARK) else putBoolean(KEY_DARK, value) }

    /** Ist der Lichtwecker eingeschaltet? */
    var sunriseEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    /** Zeitpunkt des zuletzt ausgelösten Sonnenaufgangs – dient der Schlummer-Erkennung. */
    var lastSunriseAt: Long?
        get() = prefs.getLong(KEY_LAST_SUNRISE, 0L).takeIf { it > 0L }
        set(value) = prefs.edit { putLong(KEY_LAST_SUNRISE, value ?: 0L) }

    val isConfigured: Boolean
        get() = baseUrl.isNotEmpty() && token.isNotEmpty() && entityId.isNotEmpty()

    companion object {
        const val DEFAULT_WARMEST_KELVIN = 2000
        const val DEFAULT_COLDEST_KELVIN = 6500
        const val MODE_KELVIN = "kelvin"
        const val MODE_RGB = "rgb"

        private const val KEY_URL = "base_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_ENTITY = "entity_id"
        private const val KEY_SUNRISE = "sunrise_minutes"
        private const val KEY_KELVIN = "color_temp_kelvin"
        private const val KEY_MIN_KELVIN = "min_color_temp_kelvin"
        private const val KEY_MAX_KELVIN = "max_color_temp_kelvin"
        private const val KEY_COLOR_MODE = "color_mode"
        private const val KEY_HUE = "hue"
        private const val KEY_SATURATION = "saturation"
        private const val KEY_BRIGHTNESS = "brightness_pct"
        private const val KEY_DARK = "dark_theme"
        private const val KEY_ENABLED = "sunrise_enabled"
        private const val KEY_LAST_SUNRISE = "last_sunrise_at"
    }
}
