package de.willheisenberg.hawakelight.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SunrisePlanTest {
    private val now = 1_000_000_000L
    private val minute = 60_000L

    @Test
    fun `startet die Vorlaufzeit vor dem Wecker`() {
        val plan = SunrisePlan.compute(alarmAt = now + 60 * minute, now = now, leadMillis = 20 * minute)

        assertEquals(SunrisePlan.Start(now + 40 * minute, 1200), plan)
    }

    @Test
    fun `startet sofort mit gekuerztem Uebergang wenn der Wecker naeher liegt als die Vorlaufzeit`() {
        val plan = SunrisePlan.compute(alarmAt = now + 5 * minute, now = now, leadMillis = 20 * minute)

        assertEquals(SunrisePlan.Start(now, 300), plan)
    }

    @Test
    fun `erkennt den Schlummer-Wecker nach einem gerade gelaufenen Sonnenaufgang`() {
        val plan = SunrisePlan.compute(
            alarmAt = now + 10 * minute,
            now = now,
            leadMillis = 20 * minute,
            lastSunriseAt = now - 25 * minute,
        )

        assertTrue(plan is SunrisePlan.Skip)
        assertTrue((plan as SunrisePlan.Skip).reason.contains("Schlummer"))
    }

    @Test
    fun `ein alter Sonnenaufgang blockiert den naechsten Wecker nicht`() {
        val plan = SunrisePlan.compute(
            alarmAt = now + 10 * minute,
            now = now,
            leadMillis = 20 * minute,
            lastSunriseAt = now - 5 * 60 * minute,
        )

        assertEquals(SunrisePlan.Start(now, 600), plan)
    }

    @Test
    fun `ein Wecker einer fremden App wird ignoriert`() {
        val plan = SunrisePlan.compute(
            alarmAt = now + 60 * minute,
            now = now,
            leadMillis = 20 * minute,
            creatorPackage = "com.forrestguice.suntimeswidget",
        )

        assertTrue(plan is SunrisePlan.Recheck)
        plan as SunrisePlan.Recheck
        assertTrue(plan.reason.contains("suntimes"))
        // Kurz nach dem fremden Wecker noch einmal nachsehen, dann ist der echte Wecker sichtbar.
        assertEquals(now + 60 * minute + SunrisePlan.RECHECK_DELAY_MS, plan.at)
    }

    @Test
    fun `Wecker der Uhr-App loesen aus`() {
        listOf("com.android.deskclock", "com.google.android.deskclock", null).forEach { pkg ->
            val plan = SunrisePlan.compute(
                alarmAt = now + 60 * minute,
                now = now,
                leadMillis = 20 * minute,
                creatorPackage = pkg,
            )

            assertTrue("Paket $pkg", plan is SunrisePlan.Start)
        }
    }

    @Test
    fun `ohne Wecker passiert nichts`() {
        assertTrue(SunrisePlan.compute(alarmAt = null, now = now, leadMillis = 20 * minute) is SunrisePlan.Skip)
    }

    @Test
    fun `eine vergangene Weckzeit wird ignoriert`() {
        val plan = SunrisePlan.compute(alarmAt = now - minute, now = now, leadMillis = 20 * minute)

        assertTrue(plan is SunrisePlan.Skip)
    }
}
