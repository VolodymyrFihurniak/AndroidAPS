package app.aaps.ui.compose.insulinDialog

import app.aaps.core.interfaces.aps.LearnedForecast
import app.aaps.core.interfaces.aps.LearnedPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelAdviceTest {

    private val now = 1_767_225_600_000L
    private val minute = 60_000L

    private fun forecast(
        eventual: Double,
        points: List<Double> = List(24) { 120.0 },
        ready: Boolean = true,
        timestamp: Long = now - minute,
        isf: Double = 50.0,
        profileIsf: Double = 50.0,
        delta: Double? = 0.0
    ) = LearnedForecast(
        timestamp = timestamp, bgMgdl = 120.0,
        points = points.mapIndexed { k, v -> LearnedPoint(timestamp + (k + 1) * 5 * minute, v) },
        typicalErrorMgdl = List(24) { 10.0 },
        eventualMgdl = eventual, isfMgdl = isf, ic = 12.0, targetMgdl = 100.0, ready = ready, modelTrainedAt = now,
        profileIsfMgdl = profileIsf, deltaMgdl = delta
    )

    private fun advice(forecast: LearnedForecast?, bg: Double? = 120.0, closedLoopSmb: Boolean = false) =
        modelAdviceOf(forecast, bgMgdl = bg, lowMgdl = 70.0, bolusStep = 0.05, closedLoopSmb = closedLoopSmb, now = now)

    @Test
    fun `low now wins over everything`() {
        assertEquals(ModelAdviceKind.LOW_NOW, advice(forecast(eventual = 250.0), bg = 60.0).kind)
        assertEquals(ModelAdviceKind.LOW_NOW, advice(null, bg = 60.0).kind)
    }

    @Test
    fun `no forecast is no data`() = assertEquals(ModelAdviceKind.NO_DATA, advice(null).kind)

    @Test
    fun `old forecast is no data`() =
        assertEquals(ModelAdviceKind.NO_DATA, advice(forecast(eventual = 200.0, timestamp = now - 16 * minute)).kind)

    @Test
    fun `a model not better than AutoISF gives no dose`() {
        val advice = advice(forecast(eventual = 200.0, ready = false))
        assertEquals(ModelAdviceKind.NOT_READY, advice.kind)
        assertEquals(0.0, advice.units)
    }

    @Test
    fun `a predicted low says do not bolus`() {
        val advice = advice(forecast(eventual = 200.0, points = List(24) { if (it == 10) 65.0 else 120.0 }))
        assertEquals(ModelAdviceKind.LOW_PREDICTED, advice.kind)
        assertEquals(65.0, advice.minPredictedMgdl)
    }

    @Test
    fun `missing insulin`() {
        val advice = advice(forecast(eventual = 200.0))
        assertEquals(ModelAdviceKind.MISSING, advice.kind)
        assertEquals(2.0, advice.units, 1e-9)
        assertFalse(advice.loopMayGive)
        assertTrue(advice(forecast(eventual = 200.0), closedLoopSmb = true).loopMayGive)
    }

    @Test
    fun `floored towards zero`() {
        assertEquals(0.65, advice(forecast(eventual = 100.0 + 0.67 * 50)).units, 1e-9)
        val tooMuch = advice(forecast(eventual = 100.0 - 0.67 * 50))
        assertEquals(ModelAdviceKind.TOO_MUCH, tooMuch.kind)
        assertEquals(0.65, tooMuch.units, 1e-9)
        assertEquals(8, tooMuch.carbs)
    }

    @Test
    fun `no bolus needed`() = assertEquals(ModelAdviceKind.NONE, advice(forecast(eventual = 101.0)).kind)

    // Safety: a learned ISF never makes the dose bigger than the profile would

    @Test
    fun `a learned ISF that would give a bigger dose is not used`() {
        val advice = advice(forecast(eventual = 200.0, isf = 20.0, profileIsf = 50.0))
        assertEquals(ModelAdviceKind.MISSING, advice.kind)
        assertEquals(2.0, advice.units, 1e-9)
    }

    @Test
    fun `a learned ISF that gives a smaller dose is used`() =
        assertEquals(1.0, advice(forecast(eventual = 200.0, isf = 100.0, profileIsf = 50.0)).units, 1e-9)

    @Test
    fun `falling BG gives no missing advice`() {
        val advice = advice(forecast(eventual = 200.0, delta = -12.0))
        assertEquals(ModelAdviceKind.FALLING, advice.kind)
        assertEquals(0.0, advice.units)
    }

    @Test
    fun `too much insulin is still said while BG falls`() =
        assertEquals(ModelAdviceKind.TOO_MUCH, advice(forecast(eventual = 50.0, points = List(24) { 90.0 }, delta = -12.0)).kind)
}
