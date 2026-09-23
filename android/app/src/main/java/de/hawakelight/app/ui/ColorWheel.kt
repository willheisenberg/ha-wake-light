package de.hawakelight.app.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import de.hawakelight.app.color.hsvToRgb
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Farbkreis wie in Home Assistant: der Winkel wählt den Farbton, der Abstand zur Mitte
 * die Sättigung. Die Helligkeit wird daneben als eigener Regler eingestellt.
 */
@Composable
fun ColorWheel(
    hue: Float,
    saturation: Float,
    onColorChange: (hue: Float, saturation: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(8.dp)
            .pointerInput(Unit) {
                detectTapGestures { position -> emit(position, size.width, size.height, onColorChange) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    emit(change.position, size.width, size.height, onColorChange)
                }
            },
    ) {
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Farbtöne rundherum, darüber Weiß von innen nach außen für die Sättigung.
        drawCircle(
            brush = Brush.sweepGradient(
                colors = (0..360 step 10).map { degrees ->
                    val rgb = hsvToRgb(degrees.toFloat(), 1f)
                    Color(rgb.red, rgb.green, rgb.blue)
                },
                center = center,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )

        drawMarker(center, radius, hue, saturation)
    }
}

private fun DrawScope.drawMarker(center: Offset, radius: Float, hue: Float, saturation: Float) {
    val angle = Math.toRadians(hue.toDouble())
    val distance = saturation.coerceIn(0f, 1f) * radius
    val marker = Offset(
        x = center.x + (distance * kotlin.math.cos(angle)).toFloat(),
        y = center.y + (distance * kotlin.math.sin(angle)).toFloat(),
    )
    drawCircle(color = Color.White, radius = 18f, center = marker)
    drawCircle(color = Color.Black, radius = 18f, center = marker, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
}

/** Rechnet die Berührung in Farbton und Sättigung um. */
private fun emit(
    position: Offset,
    width: Int,
    height: Int,
    onColorChange: (Float, Float) -> Unit,
) {
    val radius = min(width, height) / 2f
    val dx = position.x - width / 2f
    val dy = position.y - height / 2f
    val hue = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
    val saturation = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
    onColorChange(hue, saturation)
}
