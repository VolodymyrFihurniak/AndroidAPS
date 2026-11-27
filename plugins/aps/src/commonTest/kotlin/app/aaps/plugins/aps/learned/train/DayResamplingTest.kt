package app.aaps.plugins.aps.learned.train

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DayResamplingTest {

    @Test
    fun `the same difference every day gives a narrow range`() {
        val range = assertNotNull(DayResampling.range(DoubleArray(14) { -50.0 }, IntArray(14) { 10 }))
        assertEquals(-5.0, range.mean, 1e-9)
        assertEquals(-5.0, range.low, 1e-9)
        assertEquals(-5.0, range.high, 1e-9)
        assertTrue(range.surelyBelowZero)
    }

    @Test
    fun `one good day among bad ones is not sure`() {
        // One day much better, the others a little worse: better on average, but it hangs on one day
        val differences = DoubleArray(14) { if (it == 0) -400.0 else 10.0 }
        val range = assertNotNull(DayResampling.range(differences, IntArray(14) { 10 }))
        assertTrue(range.mean < 0.0, "$range")
        assertTrue(range.low < range.mean && range.mean < range.high, "$range")
        assertFalse(range.surelyBelowZero, "$range")
    }

    @Test
    fun `days without moments are left out`() {
        assertNull(DayResampling.range(DoubleArray(14), IntArray(14).also { it[3] = 5 }))
        val range = assertNotNull(DayResampling.range(doubleArrayOf(-10.0, 0.0, -10.0), intArrayOf(5, 0, 5)))
        assertEquals(-2.0, range.mean, 1e-9)
    }
}
