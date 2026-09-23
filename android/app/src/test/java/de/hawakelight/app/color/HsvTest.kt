package de.hawakelight.app.color

import org.junit.Assert.assertEquals
import org.junit.Test

class HsvTest {
    @Test
    fun `Grundfarben des Farbkreises`() {
        assertEquals(Rgb(255, 0, 0), hsvToRgb(0f, 1f))
        assertEquals(Rgb(0, 255, 0), hsvToRgb(120f, 1f))
        assertEquals(Rgb(0, 0, 255), hsvToRgb(240f, 1f))
    }

    @Test
    fun `Mitte des Kreises ist weiss`() {
        assertEquals(Rgb(255, 255, 255), hsvToRgb(210f, 0f))
    }

    @Test
    fun `halbe Saettigung hellt die Farbe auf`() {
        assertEquals(Rgb(255, 127, 127), hsvToRgb(0f, 0.5f))
    }

    @Test
    fun `Winkel ausserhalb von 0 bis 360 werden umgerechnet`() {
        assertEquals(hsvToRgb(30f, 1f), hsvToRgb(390f, 1f))
        assertEquals(hsvToRgb(300f, 1f), hsvToRgb(-60f, 1f))
    }
}
