package app.aaps.plugins.aps.learned

import app.aaps.plugins.aps.learned.grid.HistoryGridBuilder
import app.aaps.plugins.aps.learned.grid.STEP_MS
import app.aaps.plugins.aps.learned.model.Effects
import app.aaps.plugins.aps.learned.model.LearnedModel
import app.aaps.plugins.aps.learned.model.LinearFeatures
import app.aaps.plugins.aps.learned.model.LinearModel
import app.aaps.plugins.aps.learned.model.Metrics
import app.aaps.plugins.aps.learned.model.ResidualNetwork
import app.aaps.plugins.aps.learned.train.Samples
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForecasterTest {

    private val history = SyntheticHistory.generate(days = 3)
    private val hour = 12 * STEP_MS

    private fun model(theta: DoubleArray = LinearFeatures.PRIOR) = LearnedModel(
        trainedAt = 0L, windowDays = 180, samples = 5000,
        linear = LinearModel(theta.toList()),
        network = ResidualNetwork(inputs = Samples.INPUTS, seed = 2).toState(),
        inputMean = List(Samples.INPUTS) { 0f }, inputStd = List(Samples.INPUTS) { 100f },
        model = Metrics(10.0, 20.0, 30.0), autoIsf = null, naive = Metrics(11.0, 22.0, 33.0), autoIsfCoverage = 0.0,
        typicalErrorMgdl = List(24) { 5.0 + it }, ready = true, clampHits = 0
    )

    /** A live grid at [now]: 12 h back and 2 h ahead, no BG after now */
    private fun liveGrid(now: Long) = HistoryGridBuilder.build(
        history.input.copy(bg = history.input.bg.filter { it.timestamp <= now }),
        now - 12 * hour, now + 2 * hour, TimeZone.UTC
    )

    @Test
    fun `no forecast without BG now`() {
        val now = history.start + 30 * hour
        val grid = liveGrid(now)
        grid.bg[grid.indexOf(now)] = Double.NaN
        assertNull(Forecaster.forecast(model(), grid, grid.indexOf(now)))
    }

    @Test
    fun `forecast has 24 points from now on`() {
        val now = history.start + 30 * hour
        val grid = liveGrid(now)
        val forecast = assertNotNull(Forecaster.forecast(model(), grid, grid.indexOf(now)))
        assertEquals(24, forecast.points.size)
        assertEquals(now + 5 * 60_000L, forecast.points.first().timestamp)
        assertEquals(forecast.points[11].mgdl, forecast.at(60))
        assertEquals(50.0, forecast.isfMgdl, 1e-9)
        assertEquals(5.0, forecast.typicalErrorMgdl.first())
    }

    @Test
    fun `forecast stays inside 39 to 400`() {
        val theta = LinearFeatures.PRIOR.copyOf().also { it[LinearFeatures.DRIFT] = 1e4 }
        val now = history.start + 30 * hour
        val grid = liveGrid(now)
        val forecast = assertNotNull(Forecaster.forecast(model(theta), grid, grid.indexOf(now)))
        assertTrue(forecast.points.all { it.mgdl in 39.0..400.0 })
        assertEquals(400.0, forecast.points.last().mgdl)
    }

    @Test
    fun `live features equal training features`() {
        val full = HistoryGridBuilder.build(history.input, history.start, history.end, TimeZone.UTC)
        val i = full.indexOf(history.start + 40 * hour + 7 * STEP_MS)
        val linear = LinearModel(LinearFeatures.PRIOR.toList())
        val training = FloatArray(Samples.INPUTS)
        Samples.networkInputs(full, Effects(full).atLive(i), linear, i, training)
        val now = full.timeOf(i)
        val live = liveGrid(now)
        val j = live.indexOf(now)
        val liveInputs = FloatArray(Samples.INPUTS)
        Samples.networkInputs(live, Effects(live).atLive(j), linear, j, liveInputs)
        for (k in training.indices) assertEquals(training[k], liveInputs[k], 1e-3f, "input $k")
    }

    @Test
    fun `the forecast starts at the newest reading`() {
        // A 5-minute sensor reading 3.5 minutes after each step: the step that holds "now" is matched to the reading
        // before it, 5 minutes older than the newest one
        val shifted = history.input.bg.map { it.copy(timestamp = it.timestamp + 210_000L) }
        val newest = shifted.first { it.timestamp == history.start + 30 * hour + 210_000L }
        val now = newest.timestamp + 60_000L
        val grid = HistoryGridBuilder.build(
            history.input.copy(bg = shifted.filter { it.timestamp <= now }), now - 12 * hour, now + 2 * hour, TimeZone.UTC
        )
        assertTrue(grid.bg[grid.indexOf(now)] != newest.mgdl)

        val index = assertNotNull(Forecaster.liveIndex(grid, newestBg = newest.timestamp, now = now))
        assertEquals(newest.mgdl, assertNotNull(Forecaster.forecast(model(), grid, index)).bgMgdl)
        assertNull(Forecaster.liveIndex(grid, newestBg = now - 11 * 60_000L, now = now))
        assertNull(Forecaster.liveIndex(grid, newestBg = null, now = now))
    }
}
