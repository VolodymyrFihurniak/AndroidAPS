package app.aaps.plugins.aps.learned

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.learned.keys.LearnedStringKey
import app.aaps.plugins.aps.learned.train.StoredRun
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * autoISF build, personal prediction: a copy of the AutoISF predictions of the last [KEEP_MS], to compare the model
 * with on the test days. The database may keep its loop runs for a shorter time (when it is made smaller, only 2 days
 * stay), so the plugin keeps its own copy: the time and the main curve at 30, 60 and 120 minutes, one run every 5
 * minutes. About 100 kB, in a setting that is not exported.
 *
 * [update] copies the runs stored since the last call; the first call after a start copies what the database still
 * has. The copy is saved at most once an hour, and by [save].
 */
@SingleIn(AppScope::class)
@Inject
class AutoIsfRunArchive(
    private val preferences: Preferences,
    private val historyReader: HistoryReader
) {

    private val lock = Mutex()
    private var runs: MutableList<StoredRun>? = null
    private var checkedUntil = 0L
    private var lastSave = 0L

    /** Copies the AutoISF runs stored since the last call, drops those older than [KEEP_MS], and saves once an hour */
    suspend fun update(now: Long) = lock.withLock {
        val kept = loaded()
        // A run is written to the database a moment after its time, so the range starts a little before the last end
        val from = max(max(checkedUntil - LATE_MS, (kept.lastOrNull()?.timestamp ?: 0L) + 1), now - KEEP_MS)
        if (now > from) {
            for (run in historyReader.storedRuns(from, now).runs.sortedBy { it.timestamp }) {
                val last = kept.lastOrNull()
                if (last == null || run.timestamp - last.timestamp >= KEEP_EVERY_MS) kept += run
            }
        }
        checkedUntil = now
        kept.removeAll { it.timestamp < now - KEEP_MS }
        if (now - lastSave >= SAVE_EVERY_MS) store(kept, now)
    }

    /** The kept runs of [from]..[to], oldest first */
    suspend fun runs(from: Long, to: Long): List<StoredRun> = lock.withLock { loaded().filter { it.timestamp in from..to } }

    /** Whole days back from [now] to the oldest kept run, for the plugin screen */
    suspend fun days(now: Long): Int = lock.withLock {
        loaded().firstOrNull()?.let { ((now - it.timestamp) / DAY_MS).toInt() } ?: 0
    }

    /** Saves the copy now, for example when the plugin stops */
    suspend fun save() = lock.withLock { store(loaded(), lastSave) }

    private fun loaded(): MutableList<StoredRun> =
        runs ?: decode(preferences.get(LearnedStringKey.AutoIsfRuns)).toMutableList().also { runs = it }

    private fun store(kept: List<StoredRun>, now: Long) {
        preferences.put(LearnedStringKey.AutoIsfRuns, encode(kept))
        lastSave = now
    }

    companion object {

        /** The test days and one more */
        const val KEEP_MS = 15 * 86_400_000L

        /** One run every 5 minutes is enough for the comparison; a little less, so a loop a few seconds late still counts */
        const val KEEP_EVERY_MS = 270_000L

        private const val SAVE_EVERY_MS = 3_600_000L
        private const val LATE_MS = 120_000L
        private const val MINUTE_MS = 60_000L
        private const val DAY_MS = 86_400_000L

        /** "minute,30,60,120" per run, with ";" between runs; BG in whole mg/dl */
        fun encode(runs: List<StoredRun>): String = runs.joinToString(";") { run ->
            "${run.timestamp / MINUTE_MS},${run.at30.roundToInt()},${run.at60.roundToInt()},${run.at120.roundToInt()}"
        }

        /** Runs that cannot be read are left out */
        fun decode(text: String): List<StoredRun> = text.split(';').mapNotNull { entry ->
            val parts = entry.split(',')
            if (parts.size != 4) return@mapNotNull null
            val minute = parts[0].toLongOrNull() ?: return@mapNotNull null
            val values = parts.drop(1).map { it.toDoubleOrNull() ?: return@mapNotNull null }
            StoredRun(minute * MINUTE_MS, values[0], values[1], values[2])
        }
    }
}
