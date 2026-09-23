package de.hawakelight.app.color

import kotlin.math.abs

/** Farbe als Rot/Grün/Blau, jeweils 0–255 – so will Home Assistant sie im `rgb_color`. */
data class Rgb(val red: Int, val green: Int, val blue: Int) {
    fun asList(): List<Int> = listOf(red, green, blue)
}

/**
 * Rechnet Farbton und Sättigung des Farbkreises in RGB um. Die Helligkeit bleibt dabei
 * außen vor – die schickt die App getrennt als `brightness_pct`.
 *
 * @param hue Farbton in Grad, 0–360
 * @param saturation Sättigung, 0–1 (0 = weiß, 1 = volle Farbe)
 */
fun hsvToRgb(hue: Float, saturation: Float, value: Float = 1f): Rgb {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)

    val chroma = v * s
    val x = chroma * (1 - abs((h / 60f) % 2 - 1))
    val m = v - chroma
    val (r, g, b) = when {
        h < 60f -> Triple(chroma, x, 0f)
        h < 120f -> Triple(x, chroma, 0f)
        h < 180f -> Triple(0f, chroma, x)
        h < 240f -> Triple(0f, x, chroma)
        h < 300f -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    return Rgb(
        red = ((r + m) * 255).toInt().coerceIn(0, 255),
        green = ((g + m) * 255).toInt().coerceIn(0, 255),
        blue = ((b + m) * 255).toInt().coerceIn(0, 255),
    )
}
