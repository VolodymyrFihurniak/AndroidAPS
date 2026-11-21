package app.aaps.plugins.aps.learned.grid

import app.aaps.core.data.model.ICfg
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryGridBuilderTest {

    private val minute = 60_000L
    private val t0 = 1_700_000_100_000L / STEP_MS * STEP_MS
    private val rapid = ICfg("Rapid", peak = 75, dia = 8.0, concentration = 1.0)
    private val fast = ICfg("Fast", peak = 55, dia = 8.0, concentration = 1.0)
    private val profile = ProfileValues(isfMgdl = 50.0, ic = 10.0, basal = 0.7, targetMgdl = 100.0, iCfg = rapid)

    private fun input(
        bg: List<BgReading> = emptyList(),
        doses: List<InsulinDose> = emptyList(),
        tempBasals: List<TempBasalSegment> = emptyList(),
        extended: List<ExtendedSegment> = emptyList(),
        carbs: List<CarbEntry> = emptyList(),
        sensorStarts: List<Long> = emptyList(),
        calibrations: List<Long> = emptyList()
    ) = HistoryInput(bg, doses, tempBasals, extended, carbs, ProfileTimeline { profile }, sensorStarts, calibrations)

    private fun build(input: HistoryInput, steps: Int = 36) = HistoryGridBuilder.build(input, t0, t0 + steps * STEP_MS, TimeZone.UTC)

    @Test
    fun `closest reading within three minutes, else a gap`() {
        val grid = build(input(bg = listOf(BgReading(t0 + minute, 100.0), BgReading(t0 + 4 * minute, 120.0))))
        assertEquals(100.0, grid.bg[0])
        assertEquals(120.0, grid.bg[1])
        assertTrue(grid.bg[2].isNaN())
    }

    @Test
    fun `reading outside 39 to 400 is a gap`() {
        val grid = build(input(bg = listOf(BgReading(t0, 38.0), BgReading(t0 + STEP_MS, 401.0))))
        assertTrue(grid.bg[0].isNaN())
        assertTrue(grid.bg[1].isNaN())
    }

    @Test
    fun `bolus goes to the step of its time`() {
        val grid = build(input(doses = listOf(InsulinDose(t0 + 7 * minute, 2.0, rapid))))
        assertEquals(2.0, grid.insulin[0][1])
        assertEquals(2.0, grid.insulin[0].sum())
    }

    @Test
    fun `temp basal equal to the profile basal is net zero`() {
        val absolute = build(input(tempBasals = listOf(TempBasalSegment(t0, t0 + 60 * minute, 0.7, isAbsolute = true))))
        assertTrue(absolute.insulin.all { curve -> curve.all { abs(it) < 1e-9 } })
        val percent = build(input(tempBasals = listOf(TempBasalSegment(t0, t0 + 60 * minute, 100.0, isAbsolute = false))))
        assertTrue(percent.insulin.all { curve -> curve.all { abs(it) < 1e-9 } })
        val zero = build(input(tempBasals = listOf(TempBasalSegment(t0, t0 + 30 * minute, 0.0, isAbsolute = true))))
        assertEquals(-0.35, zero.insulin.sumOf { it.sum() }, 1e-9)
    }

    @Test
    fun `extended bolus is net insulin`() {
        val grid = build(input(extended = listOf(ExtendedSegment(t0, t0 + 30 * minute, 1.2))))
        assertEquals(0.6, grid.insulin.sumOf { it.sum() }, 1e-9)
    }

    @Test
    fun `carbs with a duration are spread`() {
        val grid = build(input(carbs = listOf(CarbEntry(t0, 30.0, 60 * minute))))
        assertEquals(12, grid.carbs.count { it > 0.0 })
        assertEquals(2.5, grid.carbs[0], 1e-9)
        assertEquals(30.0, grid.carbs.sum(), 1e-9)
        val now = build(input(carbs = listOf(CarbEntry(t0 + 6 * minute, 20.0, 0L))))
        assertEquals(20.0, now.carbs[1])
    }

    @Test
    fun `sensor start excludes two hours, calibration thirty minutes`() {
        val sensor = build(input(sensorStarts = listOf(t0)))
        assertTrue((0 until 24).all { sensor.excluded[it] })
        assertFalse(sensor.excluded[24])
        val calibration = build(input(calibrations = listOf(t0)))
        assertTrue((0 until 6).all { calibration.excluded[it] })
        assertFalse(calibration.excluded[6])
    }

    @Test
    fun `two insulin curves get two arrays`() {
        val grid = build(input(doses = listOf(InsulinDose(t0, 1.0, rapid), InsulinDose(t0, 2.0, fast))))
        assertEquals(2, grid.curves.size)
        assertEquals(3.0, grid.insulin.sumOf { it.sum() }, 1e-9)
    }

    @Test
    fun `DST day keeps UTC steps and local time of day`() {
        val kyiv = TimeZone.of("Europe/Kyiv")
        val from = LocalDateTime(2026, 10, 25, 0, 0).toInstant(kyiv).toEpochMilliseconds()
        val to = from + 24 * 60 * minute
        val grid = HistoryGridBuilder.build(input(), from, to, kyiv)
        assertEquals(288, grid.size)
        assertTrue(grid.dayAngle.none { it.isNaN() })
        // 01:00 UTC is 03:00 local after the clock went back, 00:55 UTC was 03:55 before it
        val oneUtc = grid.indexOf(LocalDateTime(2026, 10, 25, 1, 0).toInstant(TimeZone.UTC).toEpochMilliseconds())
        assertEquals(2 * PI * 3.0 / 24.0, grid.dayAngle[oneUtc], 1e-9)
        assertEquals(2 * PI * (3.0 + 55.0 / 60.0) / 24.0, grid.dayAngle[oneUtc - 1], 1e-9)
    }
}
