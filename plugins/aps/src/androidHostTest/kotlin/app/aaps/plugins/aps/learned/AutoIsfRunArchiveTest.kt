package app.aaps.plugins.aps.learned

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.learned.keys.LearnedStringKey
import app.aaps.plugins.aps.learned.train.StoredRun
import app.aaps.plugins.aps.learned.train.StoredRuns
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking

class AutoIsfRunArchiveTest {

    private val minute = 60_000L
    private val day = 24 * 60 * minute
    private val now = 1_767_225_600_000L

    /** What the database holds: one run a minute, as with a 1-minute loop */
    private var database: List<StoredRun> = emptyList()
    private val files = MemoryLearnedFiles()

    private val preferences: Preferences = mock {
        on { get(LearnedStringKey.AutoIsfRuns) } doAnswer { "" }
    }
    private val historyReader: HistoryReader = mock {
        on { storedRuns(any(), any()) } doSuspendableAnswer { call ->
            val from = call.arguments[0] as Long
            val to = call.arguments[1] as Long
            StoredRuns(database.filter { it.timestamp in from..to }, withoutCurve = 0)
        }
    }

    private fun run(timestamp: Long, value: Double = 120.0) = StoredRun(timestamp, value, value + 10, value + 20)

    @Test
    fun `keeps one run per 5 minutes of the last 15 days`() = runBlocking<Unit> {
        database = (0 until 20 * 24 * 60).map { run(now - it * minute) }
        val archive = AutoIsfRunArchive(preferences, files, historyReader)
        archive.update(now)
        val kept = archive.runs(now - 20 * day, now)
        assertThat(kept.minOf { it.timestamp }).isAtLeast(now - AutoIsfRunArchive.KEEP_MS)
        assertThat(kept.size).isIn(15 * 288 - 2..15 * 288 + 2)
        assertThat(kept.zipWithNext { a, b -> b.timestamp - a.timestamp }.all { it >= 4 * minute }).isTrue()
    }

    @Test
    fun `the runs survive a restart`() = runBlocking<Unit> {
        database = listOf(run(now - 10 * minute, 110.0), run(now - 2 * minute, 130.0))
        AutoIsfRunArchive(preferences, files, historyReader).apply {
            update(now)
            save()
        }
        database = emptyList()
        val restarted = AutoIsfRunArchive(preferences, files, historyReader)
        restarted.update(now + minute)
        assertThat(restarted.runs(now - day, now + day)).containsExactly(run(now - 10 * minute, 110.0), run(now - 2 * minute, 130.0)).inOrder()
    }

    @Test
    fun `the database lost runs after they were copied`() = runBlocking<Unit> {
        database = (0 until 3 * 24 * 60).map { run(now - it * minute) }
        val archive = AutoIsfRunArchive(preferences, files, historyReader)
        archive.update(now)
        // The database keeps only the last 2 days now, as after a trim
        database = database.filter { it.timestamp >= now - 2 * day }
        archive.update(now + 10 * minute)
        assertThat(archive.runs(now - 3 * day, now).minOf { it.timestamp }).isLessThan(now - 2 * day - day / 2)
    }

    @Test
    fun `a second update reads only what is new`() = runBlocking<Unit> {
        database = listOf(run(now - minute))
        val archive = AutoIsfRunArchive(preferences, files, historyReader)
        archive.update(now)
        archive.update(now + 6 * minute)
        val from = argumentCaptor<Long>()
        verifyBlocking(historyReader, times(2)) { storedRuns(from.capture(), any()) }
        assertThat(from.lastValue).isAtLeast(now - minute)
    }
}
