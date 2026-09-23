package de.hawakelight.app.log

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Protokoll der letzten Ereignisse – damit nachvollziehbar bleibt, was nachts passiert ist. */
object EventLog {
    private const val MAX_LINES = 200
    private val timestampFormat = SimpleDateFormat("dd.MM. HH:mm:ss", Locale.GERMANY)

    private fun file(context: Context) = File(context.filesDir, "events.log")

    @Synchronized
    fun add(context: Context, message: String) {
        val file = file(context)
        file.appendText("${timestampFormat.format(Date())}  $message\n")
        val lines = file.readLines()
        if (lines.size > MAX_LINES) file.writeText(lines.takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
    }

    @Synchronized
    fun read(context: Context): List<String> =
        file(context).takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() }?.asReversed() ?: emptyList()

    @Synchronized
    fun clear(context: Context) {
        file(context).delete()
    }
}
