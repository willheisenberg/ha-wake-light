package de.willheisenberg.hawakelight.timeline

import de.willheisenberg.hawakelight.settings.AppSettings
import kotlinx.serialization.Serializable

/**
 * Ein Punkt im Lichtplan: zu einer Uhrzeit an bestimmten Wochentagen bekommt die Lampe
 * eine Helligkeit und eine Farbe.
 *
 * @param days Wochentage nach [java.util.Calendar]: 1 = Sonntag ... 7 = Samstag. Leer = jeden Tag.
 * @param transitionSeconds Dauer des Übergangs in Sekunden
 */
@Serializable
data class TimelineEntry(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val days: Set<Int> = emptySet(),
    val entityId: String = "",
    val brightnessPct: Int = 100,
    val colorMode: String = AppSettings.MODE_KELVIN,
    val colorTempKelvin: Int = AppSettings.DEFAULT_WARMEST_KELVIN,
    val hue: Float = 30f,
    val saturation: Float = 1f,
    val transitionSeconds: Int = 60,
    val turnOff: Boolean = false,
    val enabled: Boolean = true,
) {
    val timeLabel: String get() = "%02d:%02d".format(hour, minute)

    /** Minuten seit Mitternacht – macht das Sortieren und Vergleichen einfach. */
    val minutesOfDay: Int get() = hour * 60 + minute

    fun runsOn(dayOfWeek: Int): Boolean = days.isEmpty() || dayOfWeek in days
}
