package app.aaps.plugins.aps.learned.train

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MealInProgressTest {

    /** The unexplained change of each step: 0 for 40 steps, then the [last] values */
    private fun changes(vararg last: Double) = DoubleArray(40) + last

    @Test
    fun `no rise that insulin does not explain is no meal`() {
        val unexplained = changes(0.5, -0.3, 0.2, 0.0, 0.4, 0.1)
        assertTrue(MealInProgress.rise(unexplained, unexplained.size).all { it == 0.0 })
    }

    @Test
    fun `a steady rise goes on and fades in an hour`() {
        val unexplained = changes(5.0, 5.0, 5.0, 5.0, 5.0, 5.0)
        val rise = MealInProgress.rise(unexplained, unexplained.size)
        assertEquals(5.0 * (1 - 1.0 / 12), rise[0], 1e-9)
        // It does not slow down: it fades over DEFAULT_FADE_STEPS, and then stays
        assertTrue(rise[5] > rise[0])
        assertEquals(rise[11], rise[23], 1e-9)
        // 5 + 4.58 + ... down to 0 at step 12: 5 * 11 / 2 = 27.5
        assertEquals(27.5, rise[23], 1e-9)
    }

    @Test
    fun `a rise that slows down ends sooner`() {
        val slowing = changes(9.0, 8.0, 7.0, 6.0, 5.0, 4.0)
        val steady = changes(6.5, 6.5, 6.5, 6.5, 6.5, 6.5)
        val slowingRise = MealInProgress.rise(slowing, slowing.size)
        // Now 4 per step, 1 less each step: about 4 more steps, not shorter than MIN_FADE_STEPS
        assertEquals(slowingRise[MealInProgress.MIN_FADE_STEPS - 1], slowingRise[23], 1e-9)
        assertTrue(slowingRise[23] < MealInProgress.rise(steady, steady.size)[23])
    }

    @Test
    fun `missing BG gives no meal`() {
        val unexplained = changes(5.0, Double.NaN, Double.NaN, Double.NaN, 5.0, 5.0)
        assertTrue(MealInProgress.rise(unexplained, unexplained.size).all { it == 0.0 })
    }
}
