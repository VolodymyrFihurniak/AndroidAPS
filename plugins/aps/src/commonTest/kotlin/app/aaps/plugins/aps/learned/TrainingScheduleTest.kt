package app.aaps.plugins.aps.learned

import app.aaps.plugins.aps.learned.model.LearnedModel
import app.aaps.plugins.aps.learned.model.LinearFeatures
import app.aaps.plugins.aps.learned.model.LinearModel
import app.aaps.plugins.aps.learned.model.Metrics
import app.aaps.plugins.aps.learned.model.ResidualNetwork
import app.aaps.plugins.aps.learned.train.Samples
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrainingScheduleTest {

    private val hour = 3_600_000L
    private val now = 1_767_225_600_000L

    private fun model(trainedAt: Long) = LearnedModel(
        trainedAt = trainedAt, windowDays = 180, samples = 5000,
        linear = LinearModel(LinearFeatures.PRIOR.toList()),
        network = ResidualNetwork(inputs = Samples.INPUTS).toState(),
        inputMean = List(Samples.INPUTS) { 0f }, inputStd = List(Samples.INPUTS) { 1f },
        model = Metrics(1.0, 2.0, 3.0), autoIsf = null, naive = Metrics(1.0, 2.0, 3.0), autoIsfCoverage = 0.0,
        typicalErrorMgdl = List(24) { 1.0 }, ready = true, clampHits = 0
    )

    @Test
    fun `no model and no attempt starts`() = assertTrue(TrainingSchedule.shouldStart(now, null, lastAttempt = 0L, running = false))

    @Test
    fun `no model but a recent attempt waits`() = assertFalse(TrainingSchedule.shouldStart(now, null, lastAttempt = now - hour / 2, running = false))

    @Test
    fun `a model younger than a day waits`() = assertFalse(TrainingSchedule.shouldStart(now, model(now - 23 * hour), lastAttempt = 0L, running = false))

    @Test
    fun `a model older than a day starts`() = assertTrue(TrainingSchedule.shouldStart(now, model(now - 25 * hour), lastAttempt = now - 2 * hour, running = false))

    @Test
    fun `a running training is not started again`() = assertFalse(TrainingSchedule.shouldStart(now, null, lastAttempt = 0L, running = true))
}
