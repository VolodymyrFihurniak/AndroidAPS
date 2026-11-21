package app.aaps.plugins.aps.learned.model

import app.aaps.core.data.model.ICfg
import app.aaps.plugins.aps.learned.grid.BgReading
import app.aaps.plugins.aps.learned.grid.HistoryGridBuilder
import app.aaps.plugins.aps.learned.grid.HistoryInput
import app.aaps.plugins.aps.learned.grid.InsulinDose
import app.aaps.plugins.aps.learned.grid.ProfileTimeline
import app.aaps.plugins.aps.learned.grid.ProfileValues
import app.aaps.plugins.aps.learned.grid.STEP_MS
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinearModelTest {

    private val t0 = 1_700_000_100_000L / STEP_MS * STEP_MS
    private val rapid = ICfg("Rapid", peak = 75, dia = 8.0, concentration = 1.0)
    private val profile = ProfileValues(isfMgdl = 50.0, ic = 10.0, basal = 0.7, targetMgdl = 100.0, iCfg = rapid)

    @Test
    fun `features and prior have one entry per name`() {
        assertEquals(19, LinearFeatures.NAMES.size)
        assertEquals(19, LinearFeatures.PRIOR.size)
        assertEquals(1.0, LinearFeatures.PRIOR[LinearFeatures.NAMES.indexOf("ins")])
        assertEquals(1.0, LinearFeatures.NON_NEGATIVE.sumOf { LinearFeatures.PRIOR[it] }, 1e-9)
    }

    @Test
    fun `linear model with prior predicts the profile effect`() {
        val grid = HistoryGridBuilder.build(
            HistoryInput(
                bg = (0 until 60).map { BgReading(t0 + it * STEP_MS, 100.0) },
                doses = listOf(InsulinDose(t0 + 10 * STEP_MS, 1.0, rapid)),
                tempBasals = emptyList(), extended = emptyList(), carbs = emptyList(),
                profile = ProfileTimeline { profile }, sensorStarts = emptyList(), calibrations = emptyList()
            ),
            t0, t0 + 150 * STEP_MS, TimeZone.UTC
        )
        val effects = Effects(grid)
        val delta = LinearModel(LinearFeatures.PRIOR.toList()).delta(grid, effects, 10, 24)
        assertEquals(-50.0 * effects.insulinActing(10, 24), delta, 1e-6)
        assertTrue(delta < 0.0)
    }

    @Test
    fun `isf multiplier is clamped`() {
        val theta = LinearFeatures.PRIOR.copyOf().also { it[0] = 5.0 }
        assertEquals(3.0, LinearModel(theta.toList()).isfMultiplier(0.0))
        val low = LinearFeatures.PRIOR.copyOf().also { it[0] = 0.1 }
        assertEquals(0.3, LinearModel(low.toList()).isfMultiplier(0.0))
    }
}
