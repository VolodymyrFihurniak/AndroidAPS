package app.aaps.plugins.aps.learned.grid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhasesTest {

    private val minute = 60_000L
    private fun readings(everyMinutes: Int, count: Int) = (0 until count).map { BgReading(it * everyMinutes * minute, 100.0) }

    @Test
    fun `one minute sensor gets five phases`() {
        assertTrue(Phases.isOneMinuteSensor(readings(1, 200)))
        assertEquals(listOf(0L, 60_000L, 120_000L, 180_000L, 240_000L), Phases.offsets(readings(1, 200)))
    }

    @Test
    fun `five minute sensor gets one phase`() {
        assertFalse(Phases.isOneMinuteSensor(readings(5, 200)))
        assertEquals(listOf(0L), Phases.offsets(readings(5, 200)))
    }

    @Test
    fun `no readings get one phase`() = assertEquals(listOf(0L), Phases.offsets(emptyList()))
}
