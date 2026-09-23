package de.willheisenberg.hawakelight.timeline

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelinePlannerTest {
    /** Mittwoch, 23.09.2026, 12:00 Uhr Ortszeit. */
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 23, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun entry(id: Long, hour: Int, minute: Int, days: Set<Int> = emptySet(), enabled: Boolean = true) =
        TimelineEntry(id = id, hour = hour, minute = minute, days = days, enabled = enabled)

    @Test
    fun `naechster Eintrag am selben Tag`() {
        val due = TimelinePlanner.next(listOf(entry(1, 20, 0), entry(2, 18, 30)), now)

        assertEquals(2L, due!!.entry.id)
        assertEquals(18, hourOf(due.at))
    }

    @Test
    fun `vergangene Eintraege zaehlen erst am naechsten Tag`() {
        val due = TimelinePlanner.next(listOf(entry(1, 7, 0)), now)

        assertEquals(1L, due!!.entry.id)
        assertEquals(dayOf(now) + 1, dayOf(due.at))
    }

    @Test
    fun `Wochentage werden beachtet`() {
        // Nur sonntags, von Mittwoch aus also in vier Tagen.
        val due = TimelinePlanner.next(listOf(entry(1, 9, 0, days = setOf(Calendar.SUNDAY))), now)

        assertEquals(Calendar.SUNDAY, dayOfWeekOf(due!!.at))
    }

    @Test
    fun `ausgeschaltete Eintraege werden uebersprungen`() {
        val entries = listOf(entry(1, 18, 0, enabled = false), entry(2, 19, 0))

        assertEquals(2L, TimelinePlanner.next(entries, now)!!.entry.id)
    }

    @Test
    fun `ohne Eintraege gibt es nichts zu tun`() {
        assertNull(TimelinePlanner.next(emptyList(), now))
        assertNull(TimelinePlanner.next(listOf(entry(1, 8, 0, enabled = false)), now))
    }

    private fun field(at: Long, field: Int) =
        Calendar.getInstance().apply { timeInMillis = at }.get(field)

    private fun hourOf(at: Long) = field(at, Calendar.HOUR_OF_DAY)
    private fun dayOf(at: Long) = field(at, Calendar.DAY_OF_YEAR)
    private fun dayOfWeekOf(at: Long) = field(at, Calendar.DAY_OF_WEEK)
}
