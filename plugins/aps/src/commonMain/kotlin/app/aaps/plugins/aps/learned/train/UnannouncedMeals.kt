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
 * A meal starts where BG rises faster than the insulin and the entered carbs explain (with the profile ISF and IC), by
 * more than [RISE_MGDL_PER_STEP] on average over 15 minutes. It goes on while the rise is not explained, and its carbs
 * are taken to act for [TAIL_STEPS] more after that. It also covers the [LEAD_STEPS] before it was found: the carbs
 * act before the average shows them. Entered carbs explain as much as any absorption time of [CARB_HOURS] allows at
 * that step, so a meal that was entered but acts faster or slower than the profile says is not taken for one that was
 * not entered: the fit needs those steps to learn how carbs act.
 */
object UnannouncedMeals {

    /**
     * 1.5 mg/dl per 5 minutes, 18 mg/dl per hour, that insulin and entered carbs do not explain. With the tail below the
     * best of the values tried on six made-up closed loop histories (2 and 3 mg/dl, tails of 1 to 3 hours).
     */
    const val RISE_MGDL_PER_STEP = 1.5

    /** 60 minutes after the rise is explained again */
    const val TAIL_STEPS = 12

    /** 20 minutes before the meal is found */
    const val LEAD_STEPS = 4


    private const val SMOOTH_STEPS = 3

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
        val meal = BooleanArray(n)
        var inMeal = false
        var tail = 0
        for (j in 0 until n) {
            val rise = average(unexplained, j)
            if (!inMeal && !rise.isNaN() && rise > RISE_MGDL_PER_STEP) {
                inMeal = true
                for (k in maxOf(0, j - LEAD_STEPS) until j) meal[k] = true
            } else if (inMeal && (rise.isNaN() || rise <= 0.0)) {
                inMeal = false
                tail = TAIL_STEPS
            }
            meal[j] = inMeal || tail > 0
            if (!inMeal && tail > 0) tail--
        }
        return meal
    }

    /** The mean of the last [SMOOTH_STEPS] values up to [j], NaN with fewer than 2 of them */
    private fun average(values: DoubleArray, j: Int): Double {
        var sum = 0.0
        var count = 0
        for (k in maxOf(0, j - SMOOTH_STEPS + 1)..j) {
            if (!values[k].isNaN()) {
                sum += values[k]
                count++
            }
        }
        return if (count >= 2) sum / count else Double.NaN
    }
}
