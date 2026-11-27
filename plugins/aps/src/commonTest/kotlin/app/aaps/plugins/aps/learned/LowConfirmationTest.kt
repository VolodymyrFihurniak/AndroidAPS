package app.aaps.plugins.aps.learned

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LowConfirmationTest {

    private val t0 = 1_767_225_600_000L
    private val minute = 60_000L
    private val low = 70.0
    private val isf = 50.0
    private val alert = ForecastAlerts.Alert(ForecastAlerts.Kind.LOW, 65.0, 25)

    @Test fun distantLowNeedsTwoReadings() {
        val c = LowConfirmation()
        assertFalse(c.confirm(alert, t0, 120.0, low, iob = 1.0, insulinNext30 = 0.1, isfMgdl = isf))
        assertTrue(c.confirm(alert, t0 + minute, 118.0, low, iob = 1.0, insulinNext30 = 0.1, isfMgdl = isf))
    }

    @Test fun sameReadingDoesNotConfirm() {
        val c = LowConfirmation()
        assertFalse(c.confirm(alert, t0, 120.0, low, 1.0, 0.1, isf))
        assertFalse(c.confirm(alert, t0, 120.0, low, 1.0, 0.1, isf))
    }

    @Test fun noLowResetsConfirmation() {
        val c = LowConfirmation()
        assertFalse(c.confirm(alert, t0, 120.0, low, 1.0, 0.1, isf))
        assertFalse(c.confirm(null, t0 + minute, 120.0, low, 1.0, 0.1, isf))
        assertFalse(c.confirm(alert, t0 + 2 * minute, 118.0, low, 1.0, 0.1, isf))
        assertTrue(c.confirm(alert, t0 + 3 * minute, 116.0, low, 1.0, 0.1, isf))
    }

    @Test fun gapResetsConfirmation() {
        val c = LowConfirmation()
        assertFalse(c.confirm(alert, t0, 120.0, low, 1.0, 0.1, isf))
        assertFalse(c.confirm(alert, t0 + 7 * minute, 118.0, low, 1.0, 0.1, isf))
    }

    @Test fun urgentLowWarnsAtOnce() {
        val c = LowConfirmation()
        assertTrue(c.confirm(alert.copy(minutes = 10), t0, 82.0, low, 1.0, 0.1, isf))
    }

    @Test fun strongInsulinWarnsAtOnce() {
        val c = LowConfirmation()
        // 0.5 U in the next 30 min * 50 = 25 mg/dl drop, half the way from 120 to 70 is 25
        assertTrue(c.confirm(alert, t0, 120.0, low, 2.0, 0.5, isf))
    }

    /** baseline 100 for 6 minutes, rise to 120 in 8 minutes, then down again, no insulin */
    private fun riseAndFall(c: LowConfirmation, iob: Double): List<Boolean> {
        val out = mutableListOf<Boolean>()
        var t = t0
        for (bg in listOf(100.0, 101.0, 100.0, 99.0, 100.0, 101.0, 100.0)) {
            c.confirm(null, t, bg, low, iob, 0.0, isf); t += minute
        }
        for (bg in listOf(103.0, 106.0, 109.0, 112.0, 115.0, 118.0, 120.0)) {
            c.confirm(null, t, bg, low, iob, 0.0, isf); t += minute
        }
        for (bg in listOf(116.0, 112.0, 108.0, 104.0, 100.0, 98.0, 95.0)) {
            out += c.confirm(alert, t, bg, low, iob, 0.0, isf); t += minute
        }
        return out
    }

    @Test fun fallBackAfterRiseWaitsWithoutInsulin() {
        val out = riseAndFall(LowConfirmation(), iob = 0.0)
        // waits while BG stays at or above baseline - 3 (97), then confirms at 95
        assertFalse(out.take(6).any { it })
        assertTrue(out.last())
    }

    @Test fun fallBackWithInsulinIsNotExcused() {
        val out = riseAndFall(LowConfirmation(), iob = 1.0)
        assertFalse(out[0])
        assertTrue(out[1])
    }
}
