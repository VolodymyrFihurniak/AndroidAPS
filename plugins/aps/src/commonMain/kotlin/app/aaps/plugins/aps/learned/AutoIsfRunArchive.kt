package app.aaps.plugins.aps.learned

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.learned.keys.LearnedStringKey
import app.aaps.plugins.aps.learned.train.StoredRun
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
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
    preferences: Preferences,
    private val historyReader: HistoryReader
) {

    private val kept = KeptRecords(preferences, LearnedStringKey.AutoIsfRuns, KEEP_MS, { it.timestamp }, ::encodeRun, ::decodeRun)
    private var checkedUntil = 0L

    /** Copies the AutoISF runs stored since the last call, drops those older than [KEEP_MS], and saves once an hour */
    suspend fun update(now: Long) = kept.update(now) { runs ->
        // A run is written to the database a moment after its time, so the range starts a little before the last end
        val from = max(max(checkedUntil - LATE_MS, (runs.lastOrNull()?.timestamp ?: 0L) + 1), now - KEEP_MS)
        if (now > from) with(kept) {
            for (run in historyReader.storedRuns(from, now).runs.sortedBy { it.timestamp }) runs.addSpaced(run)
        }
        checkedUntil = now
    }

    /** The kept runs of [from]..[to], oldest first */
    suspend fun runs(from: Long, to: Long): List<StoredRun> = kept.range(from, to)

    /** Whole days back from [now] to the oldest kept run, for the plugin screen */
    suspend fun days(now: Long): Int = kept.oldest()?.let { ((now - it.timestamp) / DAY_MS).toInt() } ?: 0

    /** Saves the copy now, for example when the plugin stops */
    suspend fun save() = kept.save()

    companion object {

        /** The test days and one more */
        const val KEEP_MS = 15 * 86_400_000L

        private const val LATE_MS = 120_000L
        private const val MINUTE_MS = 60_000L
        private const val DAY_MS = 86_400_000L

        /** "minute,30,60,120", BG in whole mg/dl */
        fun encodeRun(run: StoredRun): String =
            "${run.timestamp / MINUTE_MS},${run.at30.roundToInt()},${run.at60.roundToInt()},${run.at120.roundToInt()}"

        /** Null when it cannot be read */
        fun decodeRun(entry: String): StoredRun? {
            val parts = entry.split(',')
            if (parts.size != 4) return null
            val minute = parts[0].toLongOrNull() ?: return null
            val values = parts.drop(1).map { it.toDoubleOrNull() ?: return null }
            return StoredRun(minute * MINUTE_MS, values[0], values[1], values[2])
        }
    }
}
