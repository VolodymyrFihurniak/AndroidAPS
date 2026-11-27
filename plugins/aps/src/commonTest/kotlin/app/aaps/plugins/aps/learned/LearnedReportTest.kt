package app.aaps.plugins.aps.learned

import app.aaps.core.interfaces.sync.SyncLogEntry
import app.aaps.plugins.aps.learned.model.LearnedModel
import app.aaps.plugins.aps.learned.model.LinearFeatures
import app.aaps.plugins.aps.learned.model.LinearModel
import app.aaps.plugins.aps.learned.model.LowCheck
import app.aaps.plugins.aps.learned.model.Metrics
import app.aaps.plugins.aps.learned.model.Range
import app.aaps.plugins.aps.learned.model.ResidualNetwork
import app.aaps.plugins.aps.learned.train.Samples
import kotlin.test.Test
import kotlin.test.assertTrue

class LearnedReportTest {

    private val model = LearnedModel(
        trainedAt = 0L, windowDays = 180, samples = 5000,
        linear = LinearModel(LinearFeatures.PRIOR.toList()),
        network = ResidualNetwork(inputs = Samples.INPUTS, seed = 2).toState(),
        inputMean = List(Samples.INPUTS) { 0f }, inputStd = List(Samples.INPUTS) { 1f },
        model = Metrics(10.0, 20.0, 30.0), autoIsf = null, naive = Metrics(11.0, 22.0, 33.0), autoIsfCoverage = 0.03,
        typicalErrorMgdl = List(24) { 5.0 }, ready = false, clampHits = 0, phases = 5,
        cgmOnly = Metrics(12.0, 21.0, 32.0),
        lows = mapOf(LearnedModel.LINEAR to LowCheck(lows = 25, caught = 15, warnings = 21, falseWarnings = 6)),
        versusAutoIsf60 = mapOf(LearnedModel.LINEAR to Range(mean = -2.5, low = -4.0, high = -1.0))
    )

    @Test
    fun `the report holds what is needed to find a problem`() {
        val live = LiveScore(
            moments = 1200, gru = Metrics(9.0, 18.0, 30.0), linear = Metrics(10.0, 20.0, 31.0), naive = Metrics(8.0, 19.0, 29.0),
            pairedMoments = 100, pairedGru = null, pairedLinear = null, pairedAutoIsf = null, pairedNaive = null
        )
        val log = listOf(SyncLogEntry("Linear fit", "ISF x0.92 night"), SyncLogEntry("Meals not entered: 12 found"))
        val report = LearnedReport.build(model, live, autoIsfCopyDays = 3, log = log) { "TIME" }
        for (part in listOf("version ${LearnedModel.VERSION}", "5 phases", "ISF multiplier", "BG on its own", "3 %", "1200 moments", "100 with AutoISF",
            "GRU 9.0/18.0/30.0", "kept by the plugin: 3 days", "Linear fit: ISF x0.92 night", "Meals not entered: 12 found",
            "all moments 12.0/21.0/32.0", "linear -2.5 (-4.0..-1.0)", "25 lows; linear 15/6")) {
            assertTrue(part in report, "$part missing in\n$report")
        }
    }

    @Test
    fun `a report without a model says so`() {
        val report = LearnedReport.build(null, null, autoIsfCopyDays = 0, log = emptyList()) { "TIME" }
        assertTrue("No model" in report)
        assertTrue("Live check: not made yet" in report)
    }
}
