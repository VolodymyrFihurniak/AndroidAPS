package app.aaps.plugins.aps.learned.train

import app.aaps.plugins.aps.learned.SyntheticHistory
import app.aaps.plugins.aps.learned.grid.HistoryGridBuilder
import app.aaps.plugins.aps.learned.model.LearnedModel
import app.aaps.plugins.aps.learned.model.LinearFeatures
import app.aaps.plugins.aps.learned.model.LinearModel
import app.aaps.plugins.aps.learned.model.Metrics
import app.aaps.plugins.aps.learned.model.ResidualNetwork
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class TrainerTest {

    private val lines = mutableListOf<String>()
    private val trainer = Trainer(log = { lines += it }, progress = {}, bg = { "${it.toInt()} mg/dl" })

    @Test
    fun `trainer finds a known body`() = runTest(timeout = 10.minutes) {
        val history = SyntheticHistory.generate(days = 40)
        val grid = HistoryGridBuilder.build(history.input, history.start, history.end, TimeZone.UTC)
        val model = assertNotNull(trainer.train(grid, now = history.end, windowDays = 40, autoIsfRuns = bgStaysRuns(history)))
        val night = model.linear.isfMultiplier(0.0)
        val day = model.linear.isfMultiplier(PI)
        assertTrue(night in 0.7..0.9, "night ISF multiplier $night\n${lines.joinToString("\n")}")
        assertTrue(day in 1.1..1.3, "day ISF multiplier $day\n${lines.joinToString("\n")}")
        assertTrue(model.linear.carbFactor() in 1.0..1.2, "carb factor ${model.linear.carbFactor()}")
        assertTrue(model.model.mae60 < model.naive.mae60, "model ${model.model}, naive ${model.naive}")
        assertTrue(model.ready)
        assertEquals(24, model.typicalErrorMgdl.size)
        assertTrue(lines.any { it.startsWith("Test days") }, lines.joinToString("\n"))
    }

    @Test
    fun `not enough data`() = runTest {
        val history = SyntheticHistory.generate(days = 5)
        val grid = HistoryGridBuilder.build(history.input, history.start, history.end, TimeZone.UTC)
        assertNull(trainer.train(grid, now = history.end, windowDays = 180, autoIsfRuns = emptyList()))
        assertTrue(lines.any { "not enough data" in it.lowercase() }, lines.joinToString("\n"))
    }

    @Test
    fun `json round trip`() {
        val model = LearnedModel(
            trainedAt = 123L, windowDays = 180, samples = 5000,
            linear = LinearModel(LinearFeatures.PRIOR.toList()),
            network = ResidualNetwork(inputs = Samples.INPUTS, seed = 2).toState(),
            inputMean = List(Samples.INPUTS) { it * 0.5f }, inputStd = List(Samples.INPUTS) { 1f + it },
            model = Metrics(10.0, 20.0, 30.0), autoIsf = null, naive = Metrics(11.0, 22.0, 33.0), autoIsfCoverage = 0.0,
            typicalErrorMgdl = List(24) { it.toDouble() }, ready = false, clampHits = 3
        )
        assertEquals(model, LearnedModel.fromJson(model.toJson()))
        assertNull(LearnedModel.fromJson("{"))
        // A model of version 1 never wrote its version: it is not read, so it is learned again
        assertNull(LearnedModel.fromJson(model.toJson().replace("\"version\":${LearnedModel.VERSION},", "")))
    }

    /** Stored "AutoISF" runs for the last 14 days that say BG stays where it is: easy to beat */
    private fun bgStaysRuns(history: SyntheticHistory.Result) = history.input.bg
        .filter { it.timestamp >= history.end - Samples.TEST_DAYS * 86_400_000L }
        .map { StoredRun(it.timestamp, List(24) { _ -> it.mgdl }) }

    @Test
    fun `no stored AutoISF runs means not ready`() = runTest(timeout = 10.minutes) {
        val history = SyntheticHistory.generate(days = 40)
        val grid = HistoryGridBuilder.build(history.input, history.start, history.end, TimeZone.UTC)
        val model = assertNotNull(trainer.train(grid, now = history.end, windowDays = 40, autoIsfRuns = emptyList()))
        assertFalse(model.ready)
        assertEquals(0.0, model.autoIsfCoverage)
    }

    @Test
    fun `the comparison with AutoISF uses the same moments`() = runTest(timeout = 10.minutes) {
        val history = SyntheticHistory.generate(days = 40)
        val grid = HistoryGridBuilder.build(history.input, history.start, history.end, TimeZone.UTC)
        // Runs only for the last 3 of the 14 test days: too few to compare
        val fewRuns = bgStaysRuns(history).filter { it.timestamp >= history.end - 3 * 86_400_000L }
        val model = assertNotNull(trainer.train(grid, now = history.end, windowDays = 40, autoIsfRuns = fewRuns))
        assertTrue(model.autoIsfCoverage < 0.5, "coverage ${model.autoIsfCoverage}")
        assertFalse(model.ready)
        assertNotNull(model.modelOnAutoIsfMoments)
    }

    @Test
    fun `dropped samples are logged by reason`() = runTest(timeout = 10.minutes) {
        val history = SyntheticHistory.generate(days = 40)
        val grid = HistoryGridBuilder.build(history.input, history.start, history.end, TimeZone.UTC)
        trainer.train(grid, now = history.end, windowDays = 40, autoIsfRuns = emptyList())
        assertTrue(lines.any { it.startsWith("Dropped:") }, lines.joinToString("\n"))
    }

    /**
     * The safety-relevant direction: in closed loop data (entered meals, meals nobody entered, a loop on temp basals,
     * both) the fit must not make insulin look weaker than it is, which would ask for bigger doses. A fit on quiet
     * samples only was tried for v2 and made it 0.2 of the truth with meals nobody entered, so it is not used.
     */
    @Test
    fun `closed loop data does not make insulin look weak`() = runTest(timeout = 10.minutes) {
        for ((name, history) in listOf(
            "clean" to SyntheticHistory.generate(days = 40),
            "unannounced meals" to SyntheticHistory.generate(days = 40, unannouncedMeals = true),
            "temp basal loop" to SyntheticHistory.generate(days = 40, tbrLoop = true),
            "both" to SyntheticHistory.generate(days = 40, unannouncedMeals = true, tbrLoop = true)
        )) {
            val grid = HistoryGridBuilder.build(history.input, history.start, history.end, TimeZone.UTC)
            val model = assertNotNull(trainer.train(grid, history.end, 40, bgStaysRuns(history)))
            for (angle in listOf(0.0, PI)) {
                val ratio = model.linear.rawIsfMultiplier(angle) / SyntheticHistory.trueIsfMultiplier(angle)
                assertTrue(ratio >= 0.85, "$name at ${angle}: ${model.linear.rawIsfMultiplier(angle)}")
            }
        }
    }
}
