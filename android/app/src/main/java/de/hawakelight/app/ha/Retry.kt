package de.hawakelight.app.ha

import kotlinx.coroutines.delay

/**
 * Wiederholt [block], bis es klappt – für den Fall, dass Home Assistant beim Auslösen
 * gerade neu startet oder das Handy noch nicht im Heim-WLAN ist.
 */
suspend fun <T> withRetry(
    attempts: Int = 3,
    delayMillis: (attempt: Int) -> Long = { attempt -> 5_000L * attempt },
    block: suspend () -> Result<T>,
): Result<T> {
    var last: Result<T> = Result.failure(HaException("Kein Versuch ausgeführt"))
    repeat(attempts) { index ->
        last = block()
        if (last.isSuccess) return last
        if (index < attempts - 1) delay(delayMillis(index + 1))
    }
    return last
}
