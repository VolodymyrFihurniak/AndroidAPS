package app.aaps.plugins.aps.learned

import app.aaps.plugins.aps.learned.grid.BgReading
import app.aaps.plugins.aps.learned.model.Metrics
import app.aaps.plugins.aps.learned.train.StoredRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveScoreTest {

    private val start = 1_767_225_600_000L
    private val step = 5 * 60_000L

    /** BG stays at 100; the linear model says 110, the GRU 100, AutoISF 105, at every point */
    private fun score(steps: Int, withRuns: Boolean = true, bgGapAt: Long? = null): LiveScore {
        val bg = (0 until steps + 30).map { BgReading(start + it * step, 100.0) }.filter { it.timestamp != bgGapAt }
        val forecasts = (0 until steps).map { KeptForecast(start + it * step, 100.0, listOf(110.0, 110.0, 110.0), listOf(100.0, 100.0, 100.0)) }
        val runs = if (withRuns) (0 until steps).map { StoredRun(start + it * step + 30_000L, 105.0, 105.0, 105.0) } else emptyList()
        return LiveScore.compute(forecasts, runs, bg)
    }

    @Test
    fun `each curve is scored against the real BG on the same moments`() {
        val score = score(steps = 50)
        assertEquals(50, score.moments)
        assertEquals(50, score.pairedMoments)
        assertEquals(Metrics(10.0, 10.0, 10.0), score.pairedLinear)
        assertEquals(Metrics(0.0, 0.0, 0.0), score.pairedGru)
        assertEquals(Metrics(5.0, 5.0, 5.0), score.pairedAutoIsf)
        assertEquals(Metrics(0.0, 0.0, 0.0), score.pairedNaive)
    }

    @Test
    fun `a model may give a dose only after 3 days of moments with AutoISF`() {
        val short = score(steps = LiveScore.MIN_PAIRED_MOMENTS - 1)
        assertFalse(short.enough)
        assertFalse(short.gruReady)
        val long = score(steps = LiveScore.MIN_PAIRED_MOMENTS)
        assertTrue(long.enough)
        assertTrue(long.gruReady, "the GRU is better than AutoISF")
        assertFalse(long.linearReady, "the linear model is worse than AutoISF")
    }

    @Test
    fun `without AutoISF predictions only all moments are scored`() {
        val score = score(steps = 50, withRuns = false)
        assertEquals(50, score.moments)
        assertEquals(0, score.pairedMoments)
        assertNull(score.pairedAutoIsf)
        assertEquals(10.0, assertNotNull(score.linear).mae60, 1e-9)
        assertFalse(score.linearReady)
    }

    @Test
    fun `a forecast without the real BG later is not scored`() {
        // The reading at step 12 is missing and the ones next to it are 5 minutes away: the forecast of step 0 needs it
        // at +60 minutes and the one of step 6 at +30 minutes
        val score = score(steps = 10, bgGapAt = start + 12 * step)
        assertEquals(8, score.moments)
    }

    @Test
    fun `a kept forecast reads back the same`() {
        val withGru = KeptForecast(start, 123.0, listOf(130.0, 140.0, 150.0), listOf(120.0, 115.0, 110.0))
        assertEquals(withGru, ForecastArchive.decodeForecast(ForecastArchive.encodeForecast(withGru)))
        val noGru = withGru.copy(gru = null)
        assertEquals(noGru, ForecastArchive.decodeForecast(ForecastArchive.encodeForecast(noGru)))
        assertNull(ForecastArchive.decodeForecast("broken"))
    }
}
