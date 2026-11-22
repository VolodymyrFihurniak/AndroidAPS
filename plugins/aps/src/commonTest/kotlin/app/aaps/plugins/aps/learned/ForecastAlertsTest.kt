package app.aaps.plugins.aps.learned

import app.aaps.core.interfaces.aps.LearnedForecast
import app.aaps.core.interfaces.aps.LearnedPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ForecastAlertsTest {

    private val now = 1_767_225_600_000L
    private val step = 5 * 60_000L

    private fun forecast(bg: Double, gru: List<Double>?, linear: List<Double> = List(24) { bg }) = LearnedForecast(
        timestamp = now, bgMgdl = bg,
        points = linear.mapIndexed { k, v -> LearnedPoint(now + (k + 1) * step, v) },
        typicalErrorMgdl = List(24) { 10.0 }, eventualMgdl = bg, isfMgdl = 50.0, ic = 10.0, targetMgdl = 100.0, ready = false, modelTrainedAt = now,
        expectedPoints = gru?.mapIndexed { k, v -> LearnedPoint(now + (k + 1) * step, v) }.orEmpty()
    )

    @Test
    fun `a low on the gru curve within 30 minutes warns`() {
        val alert = ForecastAlerts.low(forecast(100.0, gru = List(24) { 100.0 - 4 * (it + 1) }), lowMgdl = 70.0)
        // 100 - 4 * 8 = 68 at +40 min is too late; the first point below 70 inside 30 min: none
        assertNull(alert)
        val fast = ForecastAlerts.low(forecast(100.0, gru = List(24) { 100.0 - 7 * (it + 1) }), lowMgdl = 70.0)
        // 100 - 7 * 5 = 65 at +25 min
        assertEquals(ForecastAlerts.Alert(ForecastAlerts.Kind.LOW, 65.0, 25), fast)
    }

    @Test
    fun `no warning when BG is already low`() =
        assertNull(ForecastAlerts.low(forecast(60.0, gru = List(24) { 55.0 }), lowMgdl = 70.0))

    @Test
    fun `the linear curve is used without a gru`() {
        val alert = ForecastAlerts.high(forecast(150.0, gru = null, linear = List(24) { 150.0 + 3 * (it + 1) }), highMgdl = 180.0)
        // 150 + 3 * 11 = 183 at +55 min
        assertEquals(ForecastAlerts.Alert(ForecastAlerts.Kind.HIGH, 183.0, 55), alert)
    }

    @Test
    fun `the gru curve wins over the linear one`() {
        // The linear curve goes high, the GRU (with the loop) does not
        val forecast = forecast(150.0, gru = List(24) { 160.0 }, linear = List(24) { 250.0 })
        assertNull(ForecastAlerts.high(forecast, highMgdl = 180.0))
    }
}
