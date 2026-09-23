package de.willheisenberg.hawakelight.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.willheisenberg.hawakelight.alarm.SunriseScheduler
import de.willheisenberg.hawakelight.color.hsvToRgb
import de.willheisenberg.hawakelight.ha.HaClient
import de.willheisenberg.hawakelight.ha.HaEntity
import de.willheisenberg.hawakelight.log.EventLog
import de.willheisenberg.hawakelight.settings.AppSettings
import de.willheisenberg.hawakelight.timeline.TimelineEntry
import de.willheisenberg.hawakelight.timeline.TimelineScheduler
import de.willheisenberg.hawakelight.timeline.TimelineStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = "",
    val token: String = "",
    val entityId: String = "",
    val sunriseMinutes: String = "20",
    val busy: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val suggestions: List<HaEntity> = emptyList(),
    val sunriseEnabled: Boolean = false,
    val exactAlarmsAllowed: Boolean = true,
    val nextAlarm: String = "keiner",
    val scheduleStatus: String = "",
    val events: List<String> = emptyList(),
    val colorTempKelvin: Int = AppSettings.DEFAULT_WARMEST_KELVIN,
    val minKelvin: Int = AppSettings.DEFAULT_WARMEST_KELVIN,
    val maxKelvin: Int = AppSettings.DEFAULT_COLDEST_KELVIN,
    val colorMode: String = AppSettings.MODE_KELVIN,
    val hue: Float = 30f,
    val saturation: Float = 1f,
    val brightnessPct: Int = 100,
    val darkTheme: Boolean? = null,
    val timeline: List<TimelineEntry> = emptyList(),
    val timelineStatus: String = "",
    val editing: TimelineEntry? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = AppSettings(application)
    private val timelineStore = TimelineStore(application)
    private val _state = MutableStateFlow(
        SettingsUiState(
            baseUrl = settings.baseUrl,
            token = settings.token,
            entityId = settings.entityId,
            sunriseMinutes = settings.sunriseMinutes.toString(),
            sunriseEnabled = settings.sunriseEnabled,
            colorTempKelvin = settings.colorTempKelvin,
            minKelvin = settings.minKelvin,
            maxKelvin = settings.maxKelvin,
            colorMode = settings.colorMode,
            hue = settings.hue,
            saturation = settings.saturation,
            brightnessPct = settings.brightnessPct,
            darkTheme = settings.darkTheme,
            timeline = timelineStore.load(),
        ),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /** Liest Status und Protokoll neu – beim Öffnen der App und nach jeder Aktion. */
    fun refresh(reschedule: Boolean = false) {
        val context = getApplication<Application>()
        val status = if (reschedule) {
            SunriseScheduler.reschedule(context)
        } else {
            SunriseScheduler.reschedule(context, logging = false)
        }
        _state.update {
            it.copy(
                sunriseEnabled = settings.sunriseEnabled,
                exactAlarmsAllowed = SunriseScheduler.canScheduleExact(context),
                nextAlarm = SunriseScheduler.describeNextAlarm(context),
                scheduleStatus = status,
                timeline = timelineStore.load(),
                timelineStatus = TimelineScheduler.reschedule(context),
                events = EventLog.read(context),
            )
        }
    }

    fun setSunriseEnabled(enabled: Boolean) {
        settings.sunriseEnabled = enabled
        EventLog.add(getApplication(), if (enabled) "Lichtwecker eingeschaltet" else "Lichtwecker ausgeschaltet")
        refresh(reschedule = true)
    }

    /** Der Regler schreibt direkt in die Einstellungen, damit der Alarm den Wert kennt. */
    fun onColorTempChange(kelvin: Int) {
        val value = kelvin.coerceIn(settings.minKelvin, settings.maxKelvin)
        settings.colorTempKelvin = value
        _state.update { it.copy(colorTempKelvin = value) }
    }

    fun onColorModeChange(mode: String) {
        settings.colorMode = mode
        _state.update { it.copy(colorMode = mode) }
    }

    fun onColorChange(hue: Float, saturation: Float) {
        settings.hue = hue
        settings.saturation = saturation
        _state.update { it.copy(hue = hue, saturation = saturation) }
    }

    fun onBrightnessChange(percent: Int) {
        settings.brightnessPct = percent
        _state.update { it.copy(brightnessPct = settings.brightnessPct) }
    }

    /** Schaltet zwischen hellem und dunklem Design um. */
    fun toggleDarkTheme(systemIsDark: Boolean) {
        val next = !(settings.darkTheme ?: systemIsDark)
        settings.darkTheme = next
        _state.update { it.copy(darkTheme = next) }
    }

    /** Öffnet den Editor – ohne Vorlage für einen neuen Punkt. */
    fun editEntry(entry: TimelineEntry?) {
        _state.update {
            it.copy(
                editing = entry ?: TimelineEntry(
                    id = 0L,
                    hour = 7,
                    minute = 0,
                    brightnessPct = settings.brightnessPct,
                    colorMode = settings.colorMode,
                    colorTempKelvin = settings.colorTempKelvin,
                    hue = settings.hue,
                    saturation = settings.saturation,
                ),
            )
        }
    }

    fun closeEditor() = _state.update { it.copy(editing = null) }

    fun saveEntry(entry: TimelineEntry) {
        val entries = timelineStore.upsert(entry)
        EventLog.add(getApplication(), "Lichtplan geändert: ${entry.timeLabel}")
        _state.update { it.copy(timeline = entries, editing = null) }
        refresh()
    }

    fun deleteEntry(id: Long) {
        val entries = timelineStore.delete(id)
        _state.update { it.copy(timeline = entries, editing = null) }
        refresh()
    }

    fun clearLog() {
        EventLog.clear(getApplication())
        refresh()
    }

    fun onBaseUrlChange(value: String) = _state.update { it.copy(baseUrl = value, message = null) }
    fun onTokenChange(value: String) = _state.update { it.copy(token = value, message = null) }
    fun onEntityIdChange(value: String) = _state.update { it.copy(entityId = value, message = null) }
    fun onSunriseChange(value: String) =
        _state.update { it.copy(sunriseMinutes = value.filter(Char::isDigit), message = null) }

    /** Speichert und prüft dabei Verbindung und Entität. */
    fun saveAndVerify() {
        val current = _state.value
        settings.baseUrl = current.baseUrl
        settings.token = current.token
        settings.entityId = current.entityId
        settings.sunriseMinutes = current.sunriseMinutes.toIntOrNull() ?: 20
        _state.update { it.copy(sunriseMinutes = settings.sunriseMinutes.toString(), busy = true, message = null) }

        viewModelScope.launch {
            val client = client()
            val ping = client.ping()
            if (ping.isFailure) {
                report(ping.exceptionOrNull(), fallback = "Verbindung fehlgeschlagen")
                return@launch
            }
            val entity = client.state(settings.entityId)
            val lights = client.lights().getOrDefault(emptyList())
            entity.fold(
                onSuccess = { found ->
                    // Grenzen der Lampe übernehmen und die Auswahl darauf begrenzen.
                    settings.minKelvin = found.minColorTempKelvin ?: AppSettings.DEFAULT_WARMEST_KELVIN
                    settings.maxKelvin = found.maxColorTempKelvin ?: AppSettings.DEFAULT_COLDEST_KELVIN
                    settings.colorTempKelvin = settings.colorTempKelvin.coerceIn(settings.minKelvin, settings.maxKelvin)
                    _state.update {
                        it.copy(
                            minKelvin = settings.minKelvin,
                            maxKelvin = settings.maxKelvin,
                            colorTempKelvin = settings.colorTempKelvin,
                            busy = false,
                            suggestions = lights,
                            messageIsError = false,
                            message = "Gespeichert. ${found.name} ist gerade ${found.state}.",
                        )
                    }
                    refresh(reschedule = true)
                },
                onFailure = { error ->
                    _state.update { it.copy(suggestions = lights) }
                    report(error, fallback = "Entität konnte nicht geprüft werden")
                },
            )
        }
    }

    /** Schaltet die Lampe so, wie der Sonnenaufgang enden würde – zum Ausprobieren. */
    fun testLight() {
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val rgb = if (settings.colorMode == AppSettings.MODE_RGB) {
                hsvToRgb(settings.hue, settings.saturation)
            } else {
                null
            }
            client().turnOn(
                entityId = settings.entityId,
                brightnessPct = settings.brightnessPct,
                transitionSeconds = 2,
                colorTempKelvin = settings.colorTempKelvin,
                rgbColor = rgb,
            ).fold(
                onSuccess = {
                    _state.update {
                        val farbe = rgb?.let { c -> "RGB ${c.red}/${c.green}/${c.blue}" }
                            ?: "${settings.colorTempKelvin} K"
                        it.copy(
                            busy = false,
                            messageIsError = false,
                            message = "Licht auf ${settings.brightnessPct} % bei $farbe geschaltet.",
                        )
                    }
                },
                onFailure = { error -> report(error, fallback = "Licht konnte nicht geschaltet werden") },
            )
        }
    }

    /** Schaltet die Lampe aus – praktisch beim Ausprobieren. */
    fun turnOffLight() {
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            client().turnOff(settings.entityId, transitionSeconds = 2).fold(
                onSuccess = {
                    _state.update { it.copy(busy = false, messageIsError = false, message = "Licht ausgeschaltet.") }
                },
                onFailure = { error -> report(error, fallback = "Licht konnte nicht ausgeschaltet werden") },
            )
        }
    }

    private fun client() = HaClient(settings.baseUrl, settings.token)

    private fun report(error: Throwable?, fallback: String) {
        _state.update {
            it.copy(busy = false, messageIsError = true, message = error?.message ?: fallback)
        }
    }
}
