package app.aaps.plugins.aps.learned.train

import app.aaps.core.data.model.ICfg
import app.aaps.plugins.aps.learned.grid.BgReading
import app.aaps.plugins.aps.learned.grid.HistoryGridBuilder
import app.aaps.plugins.aps.learned.grid.HistoryInput
import app.aaps.plugins.aps.learned.grid.ProfileTimeline
import app.aaps.plugins.aps.learned.grid.ProfileValues
import app.aaps.plugins.aps.learned.grid.STEP_MS
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorBandsTest {

    private val t0 = 1_700_000_100_000L / STEP_MS * STEP_MS
    private val profile = ProfileValues(50.0, 10.0, 0.7, 100.0, ICfg("Rapid", peak = 75, dia = 8.0, concentration = 1.0))

    /** BG changing by [perStep] every 5 minutes */
    private fun groupAt(perStep: Double): Int {
        val readings = (0 until 20).map { BgReading(t0 + it * STEP_MS, 120.0 + perStep * it) }
        val grid = HistoryGridBuilder.build(
            HistoryInput(readings, emptyList(), emptyList(), emptyList(), emptyList(), ProfileTimeline { profile }, emptyList(), emptyList()),
            t0, t0 + 20 * STEP_MS, TimeZone.UTC
        )
        return ErrorBands.of(grid, 10)
    }

    @Test
    fun `the group follows how fast BG changes`() {
        assertEquals(ErrorBands.STEADY, groupAt(0.5))
        assertEquals(ErrorBands.MODERATE, groupAt(-2.0))
        assertEquals(ErrorBands.FAST, groupAt(4.0))
        assertEquals(ErrorBands.FAST, groupAt(-5.0))
    }

    @Test
    fun `a group without its own band takes the band of all moments`() {
        val all = List(24) { 9.0 }
        val byGroup = listOf(List(24) { 3.0 }, emptyList(), List(24) { 20.0 })
        assertEquals(3.0, ErrorBands.pick(byGroup, ErrorBands.STEADY, all).first())
        assertEquals(9.0, ErrorBands.pick(byGroup, ErrorBands.MODERATE, all).first())
        assertEquals(20.0, ErrorBands.pick(byGroup, ErrorBands.FAST, all).first())
        assertEquals(9.0, ErrorBands.pick(emptyList(), ErrorBands.FAST, all).first())
    }
}
