package de.hawakelight.probe

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Einfaches Textlog in den App-Dateien, eine Zeile pro Ereignis. */
object ProbeLog {
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY)

    private fun file(context: Context) = File(context.filesDir, "probe.log")

    @Synchronized
    fun append(context: Context, message: String) {
        file(context).appendText("${timestampFormat.format(Date())}  $message\n")
    }

    @Synchronized
    fun read(context: Context): String =
        file(context).takeIf { it.exists() }?.readText() ?: ""

    @Synchronized
    fun clear(context: Context) {
        file(context).delete()
    }
}
