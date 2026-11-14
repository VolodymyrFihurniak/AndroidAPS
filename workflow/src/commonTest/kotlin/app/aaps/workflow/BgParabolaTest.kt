package app.aaps.workflow

import app.aaps.core.interfaces.aps.GlucoseStatusAutoIsf
import app.aaps.core.interfaces.overview.graph.BgParabolaData
import kotlin.test.Test
import kotlin.test.assertEquals

class BgParabolaTest {

    private val newestBg = 1_700_000_000_000L

    /** BG = 120 + 4 * t + 1 * t², t in steps of 5 minutes */
    private val glucoseStatus = GlucoseStatusAutoIsf(
        glucose = 120.0,
        date = newestBg,
        parabolaMinutes = 25.0,
        a0 = 120.0,
        a1 = 4.0,
        a2 = 1.0,
        corrSqu = 0.95
    )

    @Test
    fun noFitGivesNoCurve() {
        assertEquals(BgParabolaData.EMPTY, bgParabolaData(glucoseStatus.copy(corrSqu = 0.0)) { it })
    }

    @Test
    fun fittedPartCoversTheMinutesOfTheFitAndEndsAtTheNewestBg() {
        val fitted = bgParabolaData(glucoseStatus) { it }.fitted

        assertEquals(26, fitted.size) // -25 .. 0, one point per minute
        assertEquals(newestBg - 25 * 60_000L, fitted.first().timestamp)
        assertEquals(newestBg, fitted.last().timestamp)
        // t = -5: 120 - 20 + 25
        assertEquals(125.0, fitted.first().value, 1e-9)
        assertEquals(120.0, fitted.last().value, 1e-9)
    }

    @Test
    fun extrapolationGoesOn20MinutesFromTheNewestBg() {
        val extrapolation = bgParabolaData(glucoseStatus) { it }.extrapolation

        assertEquals(BG_PARABOLA_EXTRAPOLATION_MINUTES + 1, extrapolation.size)
        assertEquals(newestBg, extrapolation.first().timestamp)
        assertEquals(newestBg + 20 * 60_000L, extrapolation.last().timestamp)
        // t = 4: 120 + 16 + 16
        assertEquals(152.0, extrapolation.last().value, 1e-9)
        // t = 1 (5 minutes): 120 + 4 + 1
        assertEquals(125.0, extrapolation[5].value, 1e-9)
    }

    @Test
    fun valuesAreConvertedToTheUnitsOfTheUser() {
        val mmol = bgParabolaData(glucoseStatus) { it / 18.0 }

        assertEquals(120.0 / 18.0, mmol.fitted.last().value, 1e-9)
        assertEquals(152.0 / 18.0, mmol.extrapolation.last().value, 1e-9)
    }
}
