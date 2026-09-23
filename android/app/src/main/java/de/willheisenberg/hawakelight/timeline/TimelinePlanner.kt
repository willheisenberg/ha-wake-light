package de.willheisenberg.hawakelight.timeline

import java.util.Calendar

/** Sucht den nächsten fälligen Eintrag des Lichtplans. */
object TimelinePlanner {
    data class Due(val entry: TimelineEntry, val at: Long)

    /**
     * Nächster Eintrag nach [now]. Es wird bis zu sieben Tage vorausgeschaut,
     * damit auch Einträge gefunden werden, die nur an einem Wochentag laufen.
     */
    fun next(entries: List<TimelineEntry>, now: Long): Due? {
        val active = entries.filter { it.enabled }
        if (active.isEmpty()) return null

        var best: Due? = null
        for (dayOffset in 0..7) {
            val day = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayOfWeek = day.get(Calendar.DAY_OF_WEEK)
            for (entry in active.filter { it.runsOn(dayOfWeek) }) {
                val at = (day.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, entry.hour)
                    set(Calendar.MINUTE, entry.minute)
                }.timeInMillis
                if (at > now && (best == null || at < best!!.at)) best = Due(entry, at)
            }
            if (best != null) return best
        }
        return best
    }
}
