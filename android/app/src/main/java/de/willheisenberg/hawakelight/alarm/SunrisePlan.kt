package de.willheisenberg.hawakelight.alarm

/**
 * Entscheidet, wann der Sonnenaufgang startet. Reine Rechenlogik ohne Android-Abhängigkeit,
 * damit sie sich testen lässt.
 */
sealed interface SunrisePlan {
    /** Sonnenaufgang um [startAt] starten, Übergang über [transitionSeconds] bis zur Weckzeit. */
    data class Start(val startAt: Long, val transitionSeconds: Int) : SunrisePlan

    /** Nichts zu tun, mit Begründung für das Protokoll. */
    data class Skip(val reason: String) : SunrisePlan

    /**
     * Der nächste Wecker gehört einer fremden App. Android zeigt immer nur den nächsten
     * Wecker, deshalb schaut die App um [at] erneut nach – dann ist der fremde Wecker
     * vorbei und der Wecker der Uhr-App wird sichtbar.
     */
    data class Recheck(val at: Long, val reason: String) : SunrisePlan

    companion object {
        /**
         * Nur Wecker der Uhr-App sollen Licht machen. Verrät Android den Ersteller nicht
         * (`null`), wird der Wecker zugelassen – sonst liefe der Lichtwecker gar nicht.
         */
        fun isClockAlarm(creatorPackage: String?): Boolean {
            if (creatorPackage == null) return true
            return creatorPackage.substringAfterLast('.').contains("clock", ignoreCase = true)
        }

        /** Abstand, mit dem nach einem fremden Wecker erneut nachgesehen wird. */
        const val RECHECK_DELAY_MS = 60_000L

        /** Ein Sonnenaufgang gilt als „läuft noch“, solange er nicht länger als das her ist. */
        const val RECENT_SUNRISE_WINDOW_MS = 60 * 60 * 1000L

        /**
         * @param alarmAt nächste Weckzeit laut System, `null` wenn keiner gestellt ist
         * @param lastSunriseAt Zeitpunkt des letzten ausgelösten Sonnenaufgangs, `null` wenn keiner
         */
        fun compute(
            alarmAt: Long?,
            now: Long,
            leadMillis: Long,
            lastSunriseAt: Long? = null,
            creatorPackage: String? = null,
        ): SunrisePlan {
            if (alarmAt == null) return Skip("kein Wecker gestellt")
            if (!isClockAlarm(creatorPackage)) {
                return Recheck(alarmAt + RECHECK_DELAY_MS, "Wecker stammt von $creatorPackage")
            }
            if (alarmAt <= now) return Skip("Weckzeit liegt in der Vergangenheit")

            val remaining = alarmAt - now
            val sunriseJustRan = lastSunriseAt != null && now - lastSunriseAt < RECENT_SUNRISE_WINDOW_MS
            if (remaining < leadMillis && sunriseJustRan) {
                return Skip("Schlummer-Wecker, Licht brennt bereits")
            }

            // Ist der reguläre Startzeitpunkt schon vorbei, sofort starten und den
            // Übergang auf die verbleibende Zeit kürzen.
            val startAt = maxOf(now, alarmAt - leadMillis)
            return Start(startAt, ((alarmAt - startAt) / 1000).toInt())
        }
    }
}
