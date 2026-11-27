package app.aaps.plugins.aps.learned.train

import app.aaps.plugins.aps.learned.SyntheticHistory
import app.aaps.plugins.aps.learned.grid.HistoryGridBuilder
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class CgmOnlyBaselineTest {

    @Test
    fun `it beats BG stays on days it did not learn from`() = runTest(timeout = 5.minutes) {
        val history = SyntheticHistory.generate(days = 30)
        val grid = HistoryGridBuilder.build(history.input, history.start, history.end, TimeZone.UTC)
        val means = CgmOnlyBaseline.dayMeans(grid)
        val steps = (Samples.LEAD_STEPS until grid.size).filter { Samples.valid(grid, it) }
        val futures = steps.associateWith { Samples.futureBg(grid, it)!! }
        val split = grid.indexOf(history.end - 7 * 86_400_000L)
        val learn = steps.filter { it + 24 < split }
        val test = steps.filter { it >= split }
        val fit = assertNotNull(CgmOnlyBaseline.fit(grid, means, learn, futures, DoubleArray(grid.size) { 1.0 }))
        var fitError = 0.0
        var naiveError = 0.0
        var count = 0
        for (i in test) {
            val change = CgmOnlyBaseline.predict(fit, grid, means, i) ?: continue
            val actual = futures.getValue(i)[5] - grid.bg[i]
            fitError += abs(change[5] - actual)
            naiveError += abs(actual)
            count++
        }
        assertTrue(count > 1000, "$count moments")
        assertTrue(fitError < naiveError * 0.9, "30 min error ${fitError / count}, BG stays ${naiveError / count}")
    }

    @Test
    fun `it needs BG of the last hour and of the day`() = runTest {
        val history = SyntheticHistory.generate(days = 3)
        val grid = HistoryGridBuilder.build(history.input, history.start, history.end, TimeZone.UTC)
        val means = CgmOnlyBaseline.dayMeans(grid)
        val out = DoubleArray(CgmOnlyBaseline.FEATURES)
        // The first half day has no 24-hour mean yet
        assertTrue(means[100].isNaN())
        assertFalse(CgmOnlyBaseline.features(grid, means, 100, out))
        val i = (300 until grid.size).first { CgmOnlyBaseline.features(grid, means, it, out) }
        assertEquals(1.0, out[0])
        assertEquals(grid.bg[i] - grid.bg[i - 12], out[4], 1e-9)
        assertEquals(grid.bg[i], out[6], 1e-9)
        assertNull(CgmOnlyBaseline.fit(grid, means, emptyList(), emptyMap(), DoubleArray(grid.size)))
    }
}
