package de.willheisenberg.hawakelight.timeline

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Speichert den Lichtplan als JSON – für eine Handvoll Einträge reicht das völlig. */
class TimelineStore(context: Context) {
    private val prefs = context.getSharedPreferences("timeline", Context.MODE_PRIVATE)

    fun load(): List<TimelineEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching { json.decodeFromString(entriesSerializer, raw) }
            .getOrDefault(emptyList())
            .sortedBy { it.minutesOfDay }
    }

    fun save(entries: List<TimelineEntry>) {
        val sorted = entries.sortedBy { it.minutesOfDay }
        prefs.edit { putString(KEY_ENTRIES, json.encodeToString(entriesSerializer, sorted)) }
    }

    /** Legt den Eintrag an oder ersetzt ihn; id = 0 bedeutet „neu“. */
    fun upsert(entry: TimelineEntry): List<TimelineEntry> {
        val entries = load().toMutableList()
        if (entry.id == 0L) {
            val nextId = (entries.maxOfOrNull { it.id } ?: 0L) + 1L
            entries += entry.copy(id = nextId)
        } else {
            val index = entries.indexOfFirst { it.id == entry.id }
            if (index >= 0) entries[index] = entry else entries += entry
        }
        save(entries)
        return load()
    }

    fun delete(id: Long): List<TimelineEntry> {
        save(load().filterNot { it.id == id })
        return load()
    }

    private companion object {
        const val KEY_ENTRIES = "entries"
        val entriesSerializer = ListSerializer(TimelineEntry.serializer())
        val json = Json { ignoreUnknownKeys = true }
    }
}
