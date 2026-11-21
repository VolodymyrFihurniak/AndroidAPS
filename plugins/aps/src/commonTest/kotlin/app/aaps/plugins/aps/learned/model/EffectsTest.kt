package app.aaps.plugins.aps.learned.model

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.plugins.aps.learned.grid.CarbEntry
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

class EffectsTest {

    private val t0 = 1_700_000_100_000L / STEP_MS * STEP_MS
    private val rapid = ICfg("Rapid", peak = 75, dia = 8.0, concentration = 1.0)
    private val fiasp = ICfg("Fiasp", peak = 55, dia = 8.0, concentration = 1.0)
    private val profile = ProfileValues(isfMgdl = 50.0, ic = 10.0, basal = 0.7, targetMgdl = 100.0, iCfg = rapid)

    private fun grid(doses: List<InsulinDose> = emptyList(), carbs: List<CarbEntry> = emptyList()) = HistoryGridBuilder.build(
        HistoryInput(emptyList(), doses, emptyList(), emptyList(), carbs, ProfileTimeline { profile }, emptyList(), emptyList()),
        t0, t0 + 150 * STEP_MS, TimeZone.UTC
    )

    @Test
    fun `one unit acts fully over its duration`() {
        val effects = Effects(grid(doses = listOf(InsulinDose(t0, 1.0, rapid))))
        val steps = InsulinKernel(rapid).steps
        assertEquals(1.0, effects.insulinActing(0, steps), 0.01)
        assertEquals(1.0, effects.iob(0), 0.01)
        assertTrue(effects.iob(steps) < 0.01)
    }

    @Test
    fun `insulin given inside the window counts, not before its time`() {
        val effects = Effects(grid(doses = listOf(InsulinDose(t0 + 10 * STEP_MS, 1.0, rapid))))
        assertEquals(0.0, effects.insulinActing(0, 5))
        assertTrue(effects.insulinActing(0, 24) > 0.0)
    }

    @Test
    fun `live index ignores later doses`() {
        val grid = grid(doses = listOf(InsulinDose(t0 + 10 * STEP_MS, 1.0, rapid)))
        assertEquals(0.0, Effects(grid, liveIndex = 5).insulinActing(5, 24))
        assertTrue(Effects(grid).insulinActing(5, 24) > 0.0)
    }

    @Test
    fun `carbs absorb in a straight line after the delay`() {
        val effects = Effects(grid(carbs = listOf(CarbEntry(t0, 60.0, 0L))))
        assertEquals(0.0, effects.carbsAbsorbed(0, 2, hours = 2), 1e-9)
        assertEquals(60.0, effects.carbsAbsorbed(0, 26, hours = 2), 1e-6)
        assertEquals(60.0, effects.cob(0, hours = 2), 1e-9)
        assertEquals(30.0, effects.carbsAbsorbed(0, 14, hours = 2), 1e-6)
    }

    @Test
    fun `acted matches iobCalcForTreatment`() {
        val kernel = InsulinKernel(fiasp)
        val iob = fiasp.iobCalcForTreatment(BS(timestamp = t0, amount = 1.0, type = BS.Type.NORMAL, iCfg = fiasp), t0 + 60 * 60_000L)
        assertEquals(iob.iobContrib, 1.0 - kernel.acted(12), 1e-9)
        assertEquals(0.0, kernel.acted(-1))
        assertEquals(1.0, kernel.acted(kernel.steps + 5))
    }

    @Test
    fun `last carbs and last bolus are found`() {
        val effects = Effects(
            grid(
                doses = listOf(InsulinDose(t0 + 2 * STEP_MS, 0.3, rapid), InsulinDose(t0 + 4 * STEP_MS, 2.0, rapid)),
                carbs = listOf(CarbEntry(t0 + 3 * STEP_MS, 40.0, 0L))
            )
        )
        assertEquals(6, effects.lastBolusStepsAgo(10))
        assertEquals(7, effects.lastCarbsStepsAgo(10))
        assertEquals(40.0, effects.lastCarbsGrams(10))
        assertEquals(null, effects.lastCarbsStepsAgo(2))
    }
}
