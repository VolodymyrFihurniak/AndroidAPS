package app.aaps.plugins.aps.learned

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * autoISF build, personal prediction: records the plugin keeps for [keepMs] in a setting that is not exported, at most
 * one every [KEEP_EVERY_MS], oldest first. Saved at most once an hour, and by [save]. Used for the copy of the AutoISF
 * predictions and for the plugin's own forecasts.
 *
 * [encode] must not give a ";", that separates the records. [decode] gives null for a record it cannot read.
 */
internal class KeptRecords<T>(
    private val preferences: Preferences,
    private val key: StringNonPreferenceKey,
    private val keepMs: Long,
    private val timeOf: (T) -> Long,
    private val encode: (T) -> String,
    private val decode: (String) -> T?
) {

    private val lock = Mutex()
    private var records: MutableList<T>? = null
    private var lastSave = 0L

    /** Runs [block] with the records, then drops those older than [keepMs] and saves once an hour */
    suspend fun <R> update(now: Long, block: suspend (MutableList<T>) -> R): R = lock.withLock {
        val kept = loaded()
        val result = block(kept)
        kept.removeAll { timeOf(it) < now - keepMs }
        if (now - lastSave >= SAVE_EVERY_MS) store(kept, now)
        result
    }

    /** Adds [record] at the end, unless the last record is less than [KEEP_EVERY_MS] older. True when it was added. */
    fun MutableList<T>.addSpaced(record: T): Boolean {
        val last = lastOrNull()
        if (last != null && timeOf(record) - timeOf(last) < KEEP_EVERY_MS) return false
        add(record)
        return true
    }

    /** The records of [from]..[to], oldest first */
    suspend fun range(from: Long, to: Long): List<T> = lock.withLock { loaded().filter { timeOf(it) in from..to } }

    suspend fun oldest(): T? = lock.withLock { loaded().firstOrNull() }

    /** Saves now, for example when the plugin stops */
    suspend fun save() = lock.withLock { store(loaded(), lastSave) }

    private fun loaded(): MutableList<T> = records ?: run {
        val read = mutableListOf<T>()
        for (entry in preferences.get(key).split(';')) if (entry.isNotEmpty()) decode(entry)?.let { read += it }
        records = read
        read
    }

    private fun store(kept: List<T>, now: Long) {
        preferences.put(key, kept.joinToString(";") { encode(it) })
        lastSave = now
    }

    companion object {

        /** One record every 5 minutes is enough; a little less, so a loop a few seconds late still counts */
        const val KEEP_EVERY_MS = 270_000L

        private const val SAVE_EVERY_MS = 3_600_000L
    }
}
