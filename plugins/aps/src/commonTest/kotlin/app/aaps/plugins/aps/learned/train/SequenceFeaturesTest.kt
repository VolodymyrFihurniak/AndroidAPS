package app.aaps.plugins.aps.learned.train

import app.aaps.core.data.model.ICfg
import app.aaps.plugins.aps.learned.grid.ActivityReading
import app.aaps.plugins.aps.learned.grid.BgReading
import app.aaps.plugins.aps.learned.grid.CarbEntry
import app.aaps.plugins.aps.learned.grid.HistoryGrid
import app.aaps.plugins.aps.learned.grid.HistoryGridBuilder
import app.aaps.plugins.aps.learned.grid.HistoryInput
import app.aaps.plugins.aps.learned.grid.InsulinDose
import app.aaps.plugins.aps.learned.grid.ProfileTimeline
import app.aaps.plugins.aps.learned.grid.ProfileValues
import app.aaps.plugins.aps.learned.grid.STEP_MS
import app.aaps.plugins.aps.learned.model.Effects
import app.aaps.plugins.aps.learned.model.LinearFeatures
import app.aaps.plugins.aps.learned.model.LinearModel
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SequenceFeaturesTest {

    private val t0 = 1_700_000_100_000L / STEP_MS * STEP_MS
    private val rapid = ICfg("Rapid", peak = 75, dia = 8.0, concentration = 1.0)
    private val profile = ProfileValues(isfMgdl = 50.0, ic = 10.0, basal = 0.7, targetMgdl = 100.0, iCfg = rapid)
    private val linear = LinearModel(LinearFeatures.PRIOR.toList())

    private fun grid(steps: Int, bg: (Int) -> Double?, doses: List<InsulinDose> = emptyList(), carbs: List<CarbEntry> = emptyList()): HistoryGrid {
        val readings = (0 until steps).mapNotNull { k -> bg(k)?.let { BgReading(t0 + k * STEP_MS, it) } }
        return HistoryGridBuilder.build(
            HistoryInput(readings, doses, emptyList(), emptyList(), carbs, ProfileTimeline { profile }, emptyList(), emptyList()),
            t0, t0 + steps * STEP_MS, TimeZone.UTC
        )
    }

    @Test
    fun `step features use nothing after the step`() {
        val plain = grid(100, { 120.0 + it })
        val withDose = grid(
            100, { 120.0 + it },
            doses = listOf(InsulinDose(t0 + 50 * STEP_MS, 2.0, rapid)),
            carbs = listOf(CarbEntry(t0 + 50 * STEP_MS, 40.0, 0L))
        )
        val a = SequenceFeatures.steps(plain, Effects(plain))
        val b = SequenceFeatures.steps(withDose, Effects(withDose))
        assertEquals(100, b.size)
        assertEquals(SequenceFeatures.STEP_INPUTS, b[0].size)
        for (row in 0..49) assertContentEquals(a[row], b[row], "row $row")
        assertEquals(2f, b[50][2], 1e-6f)
        assertEquals(40f, b[50][5], 1e-6f)
        assertTrue(b[51][4] > 0f, "IOB after the dose")
    }

    @Test
    fun `steps and heart rate go into the rows, a missing heart rate takes the usual one`() {
        val plain = grid(20, { 100.0 })
        val input = HistoryInput(
            (0 until 20).map { BgReading(t0 + it * STEP_MS, 100.0) }, emptyList(), emptyList(), emptyList(), emptyList(),
            ProfileTimeline { profile }, emptyList(), emptyList(),
            steps = listOf(ActivityReading(t0 + 3 * STEP_MS, 400.0)),
            heartRate = listOf(60.0, 70.0, 120.0).mapIndexed { k, bpm -> ActivityReading(t0 + (5 + k) * STEP_MS, bpm) }
        )
        val grid = HistoryGridBuilder.build(input, t0, t0 + 20 * STEP_MS, TimeZone.UTC)
        val rows = SequenceFeatures.steps(grid, Effects(grid))
        val stepsInput = SequenceFeatures.STEP_INPUTS - 2
        val heartInput = SequenceFeatures.STEP_INPUTS - 1
        assertEquals(400f, rows[2][stepsInput])
        assertEquals(0f, rows[10][stepsInput])
        assertEquals(120f, rows[7][heartInput])
        // The middle of 60, 70 and 120
        assertEquals(70f, rows[15][heartInput])
        // Without any activity data: 0 steps and 0 heart rate
        val noWatch = SequenceFeatures.steps(plain, Effects(plain))
        assertEquals(0f, noWatch[5][stepsInput])
        assertEquals(0f, noWatch[5][heartInput])
    }

    @Test
    fun `a gap in bg keeps the last value`() {
        val grid = grid(40, { if (it in 20..24) null else 100.0 + 2 * it })
        val rows = SequenceFeatures.steps(grid, Effects(grid))
        assertEquals(138f, rows[19][0])
        for (row in 20..24) {
            assertEquals(138f, rows[row][0], "row $row")
            assertEquals(0f, rows[row][1], "row $row")
        }
        assertEquals(150f, rows[25][0])
        assertEquals(12f, rows[25][1])
    }

    @Test
    fun `seven day context averages the same time of day`() {
        // Each day 10 mg/dl higher than the day before, rising 0.5 mg/dl per step inside the day
        val grid = grid(8 * 288, { 100.0 + 10.0 * (it / 288) + 0.5 * (it % 288) })
        val i = 7 * 288 + 100
        val context = SequenceFeatures.context(grid, Effects(grid).atLive(i), linear, i)
        assertEquals(SequenceFeatures.CONTEXT, context.size)
        val knots = intArrayOf(6, 12, 18, 24)
        for (k in knots.indices) {
            // Days 1..7 ago are 60, 50, ..., 0 mg/dl lower: on average 30 above the first day
            assertEquals((130.0 + 0.5 * (100 + knots[k])).toFloat(), context[k], 1e-4f, "mean bg at knot $k")
            assertEquals((0.5 * knots[k]).toFloat(), context[4 + k], 1e-4f, "mean change at knot $k")
        }
        assertEquals(7f, context[8])
        assertEquals(50f, context[9])
        assertEquals(10f, context[10])
        for (k in knots.indices) {
            assertEquals(linear.delta(grid, Effects(grid).atLive(i), i, knots[k]).toFloat(), context[11 + k], 1e-4f)
        }
    }

    @Test
    fun `a day with a gap is skipped`() {
        // On the day 3 days ago the reading at +60 minutes is missing; that day is 30 mg/dl lower than today
        val i = 7 * 288 + 100
        val missing = i - 3 * 288 + 12
        val grid = grid(8 * 288, { if (it == missing) null else 100.0 + 10.0 * (it / 288) })
        val context = SequenceFeatures.context(grid, Effects(grid).atLive(i), linear, i)
        assertEquals(6f, context[8])
        // Days 1, 2, 4, 5, 6, 7 ago: 160, 150, 130, 120, 110, 100
        assertEquals((770.0 / 6).toFloat(), context[0], 1e-4f)
    }

    @Test
    fun `first week has no days`() {
        val grid = grid(300, { 100.0 + 0.5 * it })
        val i = 200
        val context = SequenceFeatures.context(grid, Effects(grid).atLive(i), linear, i)
        assertEquals(0f, context[8])
        for (k in 0 until 4) {
            assertEquals(200f, context[k], "mean bg at knot $k is BG now")
            assertEquals(0f, context[4 + k], "change at knot $k")
        }
    }

    @Test
    fun `sequence needs 36 steps`() {
        val grid = grid(60, { 100.0 })
        val rows = SequenceFeatures.steps(grid, Effects(grid))
        assertNull(SequenceFeatures.sequence(rows, 34))
        val sequence = assertNotNull(SequenceFeatures.sequence(rows, 35))
        assertEquals(SequenceFeatures.STEPS, sequence.size)
        assertTrue(sequence.first() === rows[0])
        assertTrue(sequence.last() === rows[35])
        assertNull(SequenceFeatures.sequence(rows, 60))
    }

    @Test
    fun `target is the change from now divided by 50`() {
        val grid = grid(60, { 100.0 + 5.0 * it })
        val target = assertNotNull(SequenceFeatures.target(grid, 10))
        assertEquals(24, target.size)
        assertEquals(0.1f, target[0], 1e-6f)
        assertEquals(2.4f, target[23], 1e-6f)
        assertNull(SequenceFeatures.target(grid, 40))
    }
}
