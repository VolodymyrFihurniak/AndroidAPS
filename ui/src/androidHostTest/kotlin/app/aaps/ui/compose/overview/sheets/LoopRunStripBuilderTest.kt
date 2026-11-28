package app.aaps.ui.compose.overview.sheets

import app.aaps.core.interfaces.aps.APSResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LoopRunStripBuilderTest {

    private val minute = 60_000L
    private val now = 1_000_000 * minute

    /** A run [minutesAgo] before now. [percent] or [rate] set a temp basal, none of them means "no change". */
    private fun run(minutesAgo: Int, percent: Int? = null, rate: Double? = null, duration: Int = 30, smb: Double = 0.0): APSResult {
        val result = mock<APSResult>()
        whenever(result.date).thenReturn(now - minutesAgo * minute)
        whenever(result.isTempBasalRequested).thenReturn(percent != null || rate != null)
        whenever(result.usePercent).thenReturn(percent != null)
        whenever(result.percent).thenReturn(percent ?: 100)
        whenever(result.rate).thenReturn(rate ?: 0.0)
        whenever(result.duration).thenReturn(duration)
        whenever(result.smb).thenReturn(smb)
        return result
    }

    private fun strip(vararg runs: APSResult, basal: Double? = 1.0) =
        buildRunStrip(runs.toList(), now, { basal }, emptyList(), "13:35", "14:35")

    @Test
    fun `no runs give an empty strip`() {
        val strip = strip()
        assertEquals(12, strip.percent.size)
        assertTrue(strip.isEmpty)
    }

    @Test
    fun `a temp basal carries over the runs that ask for no change until it ends`() {
        // Asked for 120 % for 30 minutes 58 minutes ago; the runs 53, 48 and 43 minutes ago ask for no change; 28 minutes ago the temp basal is over
        val strip = strip(run(58, percent = 120), run(53), run(48), run(43), run(28))
        assertEquals(listOf(120, 120, 120, 120, null, null, 100, null, null, null, null, null), strip.percent)
    }

    @Test
    fun `a temp basal asked for before the strip starts is still known at its start`() {
        // 70 minutes ago, 60 minutes long: it runs for the first 10 minutes of the strip
        val strip = strip(run(70, percent = 0, duration = 60), run(55))
        assertEquals(0, strip.percent[1])
        assertEquals(null, strip.percent[0])
    }

    @Test
    fun `steps without a run stay empty`() {
        val strip = strip(run(2, percent = 110))
        assertEquals(110, strip.percent[11])
        assertEquals(null, strip.percent[10])
        assertFalse(strip.isEmpty)
    }

    @Test
    fun `an SMB counts in the step of its run and the SMBs of a step add up`() {
        val strip = strip(run(3, smb = 0.3), run(2, smb = 0.2), run(30, smb = 0.5))
        assertEquals(0.5, strip.smb[11], 1e-9)
        assertEquals(0.5, strip.smb[6], 1e-9)
        assertEquals(0.0, strip.smb[0], 1e-9)
    }

    @Test
    fun `an absolute rate is turned into a share of the profile basal at that time`() {
        val strip = strip(run(2, rate = 1.5), basal = 1.0)
        assertEquals(150, strip.percent[11])
    }

    @Test
    fun `an absolute rate without a profile is not drawn as a temp basal`() {
        val strip = strip(run(2, rate = 1.5), basal = null)
        // The run counts as a run at the profile
        assertEquals(100, strip.percent[11])
    }

    @Test
    fun `runs in the future are left out`() {
        val strip = strip(run(-5, percent = 150))
        assertTrue(strip.isEmpty)
    }

    @Test
    fun `the labels and hours are passed on`() {
        val strip = buildRunStrip(emptyList(), now, { 1.0 }, listOf(SheetTimeTick(25, "14:00")), "13:35", "14:35")
        assertEquals("13:35", strip.startText)
        assertEquals("14:35", strip.endText)
        assertEquals(60, strip.spanMinutes)
        assertEquals(listOf(SheetTimeTick(25, "14:00")), strip.hours)
    }
}
