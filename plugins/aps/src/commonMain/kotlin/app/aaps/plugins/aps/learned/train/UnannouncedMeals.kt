package app.aaps.plugins.aps.learned.train

import app.aaps.plugins.aps.learned.grid.HistoryGrid
import app.aaps.plugins.aps.learned.model.CARB_HOURS
import app.aaps.plugins.aps.learned.model.Effects

/**
 * autoISF build, personal prediction: finds meals that were eaten but not entered, from BG alone.
 *
 * A full closed loop without carb entries gives its insulin as SMBs while BG rises from a meal. A fit that does not
 * know about the meal sees insulin given while BG keeps rising, and the meal times move from day to day, so it splits
 * them between the ISF and what BG does on its own by time of day. A phone that enters no carbs learned 30 to 50 % of
 * the profile ISF. The steps found here are left out of the fit of the linear part.
 *
 * The BG change of each step that insulin and entered carbs do not explain (with the profile ISF and IC) is averaged
 * over [SMOOTH_STEPS]. A meal is a stretch where that average stays above 0 and the change it holds adds up to at
 * least [MIN_RISE_MGDL]: a sensor that is a little off, or BG going up and down by itself, makes short stretches of a
 * few mg/dl, and those stay in the fit. Leaving them out took away only the rises and kept the falls, and a phone then
 * learned that BG falls by 3 mmol/l per hour on its own. A meal also covers the [SMOOTH_STEPS] before it (the average
 * is late) and [TAIL_STEPS] after it. Entered carbs explain as much as any absorption time of [CARB_HOURS] allows at
 * that step, so a meal that was entered but acts faster or slower than the profile says is not taken for one that was
 * not entered: the fit needs those steps to learn how carbs act.
 */
object UnannouncedMeals {

    /**
     * 30 mg/dl that insulin and entered carbs do not explain, in one stretch. With the other values the best of those
     * tried on eight made-up closed loop histories, with and without a noisy sensor and a body that goes up and down.
     */
    const val MIN_RISE_MGDL = 30.0

    /** 30 minutes */
    const val SMOOTH_STEPS = 6

    /** 60 minutes after the rise is explained again */
    const val TAIL_STEPS = 12

    /** Per step of [grid]: true inside a meal that was not entered. [effects] sees all the insulin of the grid. */
    fun find(grid: HistoryGrid, effects: Effects): BooleanArray {
        val n = grid.size
        // The BG change of each step that insulin and entered carbs do not explain, NaN where BG or the profile is missing
        val unexplained = DoubleArray(n) { Double.NaN }
        for (j in 0 until n - 1) {
            val now = grid.bg[j]
            val next = grid.bg[j + 1]
            if (now.isNaN() || next.isNaN() || !grid.hasProfile(j) || grid.ic[j] <= 0.0) continue
            val carbs = CARB_HOURS.maxOf { hours -> effects.carbsAbsorbed(j, 1, hours) }
            val explained = -grid.isf[j] * effects.insulinActing(j, 1) + grid.isf[j] / grid.ic[j] * carbs
            unexplained[j] = next - now - explained
        }
        val average = DoubleArray(n) { average(unexplained, it) }
        val meal = BooleanArray(n)
        var j = 0
        while (j < n) {
            if (average[j].isNaN() || average[j] <= 0.0) {
                j++
                continue
            }
            // A stretch with a rise that is not explained: [j, end)
            var end = j
            var rise = 0.0
            while (end < n && !average[end].isNaN() && average[end] > 0.0) {
                if (!unexplained[end].isNaN()) rise += unexplained[end]
                end++
            }
            if (rise >= MIN_RISE_MGDL) for (k in maxOf(0, j - SMOOTH_STEPS) until minOf(n, end + TAIL_STEPS)) meal[k] = true
            j = end
        }
        return meal
    }

    /** The mean of the last [SMOOTH_STEPS] values up to [j], NaN when fewer than two thirds of them are there */
    private fun average(values: DoubleArray, j: Int): Double {
        var sum = 0.0
        var count = 0
        for (k in maxOf(0, j - SMOOTH_STEPS + 1)..j) {
            if (!values[k].isNaN()) {
                sum += values[k]
                count++
            }
        }
        return if (count * 3 >= SMOOTH_STEPS * 2) sum / count else Double.NaN
    }
}
