package app.aaps.plugins.aps.learned

import app.aaps.plugins.aps.learned.grid.BgReading
import app.aaps.plugins.aps.learned.grid.HistoryInput
import app.aaps.plugins.aps.learned.grid.ProfileTimeline
import app.aaps.plugins.aps.learned.grid.HistoryGridBuilder
import app.aaps.plugins.aps.learned.grid.STEP_MS
import app.aaps.plugins.aps.learned.model.Effects
import app.aaps.plugins.aps.learned.model.GruNetwork
import app.aaps.plugins.aps.learned.model.LearnedModel
import app.aaps.plugins.aps.learned.model.LinearFeatures
import app.aaps.plugins.aps.learned.model.LinearModel
import app.aaps.plugins.aps.learned.model.Metrics
import app.aaps.plugins.aps.learned.model.ResidualNetwork
import app.aaps.plugins.aps.learned.train.ErrorBands
import app.aaps.plugins.aps.learned.train.Samples
import app.aaps.plugins.aps.learned.train.SequenceFeatures
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForecasterTest {

    private val history = SyntheticHistory.generate(days = 3)
    private val hour = 12 * STEP_MS

    private fun model(theta: DoubleArray = LinearFeatures.PRIOR, withGru: Boolean = false) = LearnedModel(
        trainedAt = 0L, windowDays = 180, samples = 5000,
        linear = LinearModel(theta.toList()),
        network = ResidualNetwork(inputs = Samples.INPUTS, seed = 2).toState(),
        inputMean = List(Samples.INPUTS) { 0f }, inputStd = List(Samples.INPUTS) { 100f },
        model = Metrics(10.0, 20.0, 30.0), autoIsf = null, naive = Metrics(11.0, 22.0, 33.0), autoIsfCoverage = 0.0,
        typicalErrorMgdl = List(24) { 5.0 + it }, ready = true, clampHits = 0,
        expected = if (withGru) GruNetwork(
            steps = SequenceFeatures.STEPS, inputs = SequenceFeatures.STEP_INPUTS, context = SequenceFeatures.CONTEXT,
            units = 4, dense = 4, outputs = 24, seed = 3
        ).toState() else null,
        expectedStepMean = if (withGru) List(SequenceFeatures.STEP_INPUTS) { 0f } else emptyList(),
        expectedStepStd = if (withGru) List(SequenceFeatures.STEP_INPUTS) { 100f } else emptyList(),
        expectedContextMean = if (withGru) List(SequenceFeatures.CONTEXT) { 0f } else emptyList(),
        expectedContextStd = if (withGru) List(SequenceFeatures.CONTEXT) { 100f } else emptyList(),
        expectedTypicalErrorMgdl = if (withGru) List(24) { 3.0 + it } else emptyList(),
        expectedReady = withGru
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
        // The drift is limited, so a huge trend weight pushes the curve to a limit, up or down with the trend now
        val theta = LinearFeatures.PRIOR.copyOf().also { for (k in LinearFeatures.TREND until LinearFeatures.SIZE) it[k] = 1e4 }
        val now = history.start + 30 * hour
        val grid = liveGrid(now)
        val forecast = assertNotNull(Forecaster.forecast(model(theta), grid, grid.indexOf(now)))
        assertTrue(forecast.points.all { it.mgdl in 39.0..400.0 })
        assertTrue(forecast.points.last().mgdl == 400.0 || forecast.points.last().mgdl == 39.0, "${forecast.points.last()}")
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
    fun `a rise that insulin does not explain goes on in the forecast`() {
        // No insulin and no carbs: BG rose 4 mg/dl every 5 minutes for the last hour, from a meal nobody entered
        val start = history.start
        val readings = (0..144).map { k -> BgReading(start + k * STEP_MS, if (k < 132) 100.0 else 100.0 + 4.0 * (k - 131)) }
        val input = HistoryInput(readings, emptyList(), emptyList(), emptyList(), emptyList(), ProfileTimeline { SyntheticHistory.profile }, emptyList(), emptyList())
        val newest = readings.last()
        val grid = Forecaster.liveGrid(input, newest.timestamp, TimeZone.UTC)
        // A network that adds nothing, and the prior model has no drift and no trend: without the meal going on the
        // curve would stay flat
        val silent = ResidualNetwork(inputs = Samples.INPUTS, seed = 2).also { it.setParameters(DoubleArray(it.parameters().size)) }
        val forecast = assertNotNull(Forecaster.forecast(model().copy(network = silent.toState()), grid, Forecaster.LIVE_BACK_STEPS))
        assertTrue(forecast.at(30)!! > newest.mgdl + 10.0, "${forecast.at(30)} after ${newest.mgdl}")
        assertTrue(forecast.at(120)!! >= forecast.at(30)!!)
    }

    @Test
    fun `the band is the one of how fast BG changes now`() {
        val now = history.start + 30 * hour
        val grid = liveGrid(now)
        val i = grid.indexOf(now)
        val byChange = listOf(List(24) { 1.0 }, List(24) { 2.0 }, List(24) { 3.0 })
        val forecast = assertNotNull(Forecaster.forecast(model().copy(typicalErrorByChange = byChange), grid, i))
        assertEquals((ErrorBands.of(grid, i) + 1).toDouble(), forecast.typicalErrorMgdl.first())
    }

    @Test
    fun `the parts at 60 minutes add up to the curve`() {
        val now = history.start + 30 * hour
        val grid = liveGrid(now)
        val forecast = assertNotNull(Forecaster.forecast(model(), grid, grid.indexOf(now)))
        val parts = assertNotNull(forecast.parts60)
        val sum = parts.insulin + parts.carbs + parts.meal + parts.drift + parts.trend + parts.network
        assertEquals(forecast.at(60)!!, (forecast.bgMgdl + sum).coerceIn(39.0, 400.0), 1e-6)
    }

    @Test
    fun `forecast has the expected curve`() {
        val now = history.start + 30 * hour
        val grid = liveGrid(now)
        val forecast = assertNotNull(Forecaster.forecast(model(withGru = true), grid, grid.indexOf(now)))
        assertEquals(24, forecast.expectedPoints.size)
        assertEquals(now + 5 * 60_000L, forecast.expectedPoints.first().timestamp)
        assertEquals(now + 120 * 60_000L, forecast.expectedPoints.last().timestamp)
        assertTrue(forecast.expectedPoints.all { it.mgdl in 39.0..400.0 })
        assertEquals(forecast.expectedPoints[11].mgdl, forecast.expectedAt(60))
        assertEquals(3.0, forecast.expectedTypicalErrorMgdl.first())
        assertTrue(forecast.expectedReady)
    }

    @Test
    fun `a model without a gru has no expected curve`() {
        val now = history.start + 30 * hour
        val grid = liveGrid(now)
        val forecast = assertNotNull(Forecaster.forecast(model(), grid, grid.indexOf(now)))
        assertTrue(forecast.expectedPoints.isEmpty())
        assertTrue(forecast.expectedTypicalErrorMgdl.isEmpty())
        assertNull(forecast.expectedAt(60))
        assertFalse(forecast.expectedReady)
    }

    @Test
    fun `anchored live grid starts at the newest reading`() {
        // A reading 3 minutes after a 5-minute step, 2 minutes before now: the forecast starts exactly at it
        val shifted = history.input.bg.map { it.copy(timestamp = it.timestamp + 180_000L) }
        val newest = shifted.first { it.timestamp == history.start + 30 * hour + 180_000L }
        val now = newest.timestamp + 2 * 60_000L
        val input = history.input.copy(bg = shifted.filter { it.timestamp <= now })
        val grid = Forecaster.liveGrid(input, newest.timestamp, TimeZone.UTC)
        assertEquals(newest.timestamp - Forecaster.LIVE_BACK_STEPS * STEP_MS, grid.start)
        val index = assertNotNull(Forecaster.liveIndex(grid, newest.timestamp, now))
        assertEquals(Forecaster.LIVE_BACK_STEPS, index)
        val forecast = assertNotNull(Forecaster.forecast(model(withGru = true), grid, index))
        assertEquals(newest.timestamp, forecast.timestamp)
        assertEquals(newest.mgdl, forecast.bgMgdl)
        assertEquals(newest.timestamp + 5 * 60_000L, forecast.expectedPoints.first().timestamp)
    }

    @Test
    fun `old newest reading gives no forecast`() {
        val newest = history.input.bg.first { it.timestamp == history.start + 30 * hour }
        val input = history.input.copy(bg = history.input.bg.filter { it.timestamp <= newest.timestamp })
        val grid = Forecaster.liveGrid(input, newest.timestamp, TimeZone.UTC)
        assertNull(Forecaster.liveIndex(grid, newest.timestamp, now = newest.timestamp + 11 * 60_000L))
        assertNotNull(Forecaster.liveIndex(grid, newest.timestamp, now = newest.timestamp + 9 * 60_000L))
    }

    @Test
    fun `live grid holds 7 days of BG for the context`() {
        val week = SyntheticHistory.generate(days = 9)
        val newest = week.input.bg.first { it.timestamp == week.start + 8 * 24 * hour }
        val input = week.input.copy(bg = week.input.bg.filter { it.timestamp <= newest.timestamp })
        val grid = Forecaster.liveGrid(input, newest.timestamp, TimeZone.UTC)
        val i = Forecaster.LIVE_BACK_STEPS
        val context = SequenceFeatures.context(grid, Effects(grid).atLive(i), LinearModel(LinearFeatures.PRIOR.toList()), i)
        assertEquals(7f, context[8])
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
