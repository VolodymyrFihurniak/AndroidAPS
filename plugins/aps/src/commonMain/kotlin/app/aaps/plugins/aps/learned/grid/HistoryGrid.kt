package app.aaps.plugins.aps.learned.grid

import app.aaps.core.data.model.ICfg

/**
 * autoISF build, personal prediction: the history on a grid of 5-minute steps. Step `i` covers
 * `[start + i * STEP_MS, start + (i + 1) * STEP_MS)`. Kept as plain arrays, so 2 years fit in a few MB.
 *
 * Built by [HistoryGridBuilder]. The model, its training and the live prediction all read only this, so they see the
 * data the same way.
 */
class HistoryGrid(
    val start: Long,
    val size: Int,
    /** BG at the step time in mg/dl, NaN for a gap */
    val bg: DoubleArray,
    /** The insulin curves in this history, one per distinct peak and duration */
    val curves: List<ICfg>,
    /** Per curve: units delivered in each step. Net for temp basals, so it can be below 0. */
    val insulin: List<DoubleArray>,
    /** Grams of carbs that start to act in each step */
    val carbs: DoubleArray,
    /** Profile values per step, NaN where there was no profile */
    val isf: DoubleArray,
    val ic: DoubleArray,
    val basal: DoubleArray,
    val target: DoubleArray,
    /** Steps not used for learning: the start of a sensor, right after a calibration */
    val excluded: BooleanArray,
    /** Local time of day as an angle, 0 at midnight, 2 * PI after 24 h */
    val dayAngle: DoubleArray,
    /** Steps in each step, NaN without data */
    val steps: DoubleArray = DoubleArray(size) { Double.NaN },
    /** Mean heart rate in each step, NaN without data */
    val heartRate: DoubleArray = DoubleArray(size) { Double.NaN }
) {

    fun timeOf(index: Int): Long = start + index * STEP_MS

    /** The step that holds [timestamp] */
    fun indexOf(timestamp: Long): Int = ((timestamp - start).floorDiv(STEP_MS)).toInt()

    fun hasProfile(index: Int): Boolean = !isf[index].isNaN()
}
