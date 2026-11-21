package app.aaps.plugins.aps.learned.model

import app.aaps.plugins.aps.learned.grid.HistoryGrid
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.serialization.Serializable

/**
 * autoISF build, personal prediction: the 19 features of the linear part, for step `i` and horizon `h` (in steps).
 * The model predicts `BG(i + h) - BG(i)` as `sum of theta[k] * feature[k]`.
 *
 * - `ins*`: minus profile ISF times the insulin that acts in the window, times 1, sin, cos, sin 2x, cos 2x of the time
 *   of day. Their weights give the personal ISF multiplier by time of day.
 * - `carb_*h`: profile ISF / IC times the grams absorbed in the window with an absorption of 1, 2, 3, 4 or 6 hours.
 *   Their sum is the personal carb factor, where the weight sits says how fast carbs act.
 * - `drift*`: the hours of the window, times the same time-of-day terms: what BG does with no insulin and no carbs.
 * - `trend_*`: the BG delta now, times a curve over `h` that grows and then flattens.
 */
object LinearFeatures {

    val NAMES = listOf(
        "ins", "ins_s1", "ins_c1", "ins_s2", "ins_c2",
        "carb_1h", "carb_2h", "carb_3h", "carb_4h", "carb_6h",
        "drift", "drift_s1", "drift_c1", "drift_s2", "drift_c2",
        "trend_short_15", "trend_mid_15", "trend_mid_45", "trend_long_45"
    )

    const val INSULIN = 0
    const val CARBS = 5
    const val DRIFT = 10
    const val TREND = 15
    val SIZE = NAMES.size

    /** With no data: the profile ISF, the profile carb factor spread evenly, no drift, no trend */
    val PRIOR: DoubleArray = DoubleArray(SIZE).also { prior ->
        prior[INSULIN] = 1.0
        for (k in CARBS until DRIFT) prior[k] = 1.0 / CARB_HOURS.size
    }

    /** The carb weights must not be below 0 */
    val NON_NEGATIVE: Set<Int> = (CARBS until DRIFT).toSet()

    /** The 1, sin, cos, sin 2x, cos 2x terms of a time of day */
    fun dayTerms(angle: Double) = doubleArrayOf(1.0, sin(angle), cos(angle), sin(2 * angle), cos(2 * angle))

    /** Fills [out] with the features of step [i] and horizon [h]. BG at `i`, `i-1`, `i-3`, `i-6` should be there; a missing one gives no trend. */
    fun fill(grid: HistoryGrid, effects: Effects, i: Int, h: Int, out: DoubleArray) {
        val terms = dayTerms(grid.dayAngle[i])
        val insulin = -grid.isf[i] * effects.insulinActing(i, h)
        val csf = grid.isf[i] / grid.ic[i]
        val hours = h * 5.0 / 60.0
        for (t in 0 until 5) {
            out[INSULIN + t] = insulin * terms[t]
            out[DRIFT + t] = hours * terms[t]
        }
        CARB_HOURS.forEachIndexed { k, carbHours -> out[CARBS + k] = csf * effects.carbsAbsorbed(i, h, carbHours) }
        val short = slope(grid, i, 1)
        val mid = slope(grid, i, 3)
        val long = slope(grid, i, 6)
        out[TREND] = short * phi(h, 3.0)
        out[TREND + 1] = mid * phi(h, 3.0)
        out[TREND + 2] = mid * phi(h, 9.0)
        out[TREND + 3] = long * phi(h, 9.0)
    }

    /** BG change per step over the last [steps] steps, 0 when a value is missing */
    fun slope(grid: HistoryGrid, i: Int, steps: Int): Double {
        if (i - steps < 0) return 0.0
        val now = grid.bg[i]
        val before = grid.bg[i - steps]
        return if (now.isNaN() || before.isNaN()) 0.0 else (now - before) / steps
    }

    /** How many steps of the current movement go on after [h] steps, when it fades over [tau] steps */
    private fun phi(h: Int, tau: Double) = tau * (1.0 - exp(-h / tau))
}

/** The fitted weights of [LinearFeatures], in that order */
@Serializable
data class LinearModel(val theta: List<Double>) {

    /** Predicted `BG(i + h) - BG(i)` in mg/dl. The insulin part uses the clamped [isfMultiplier], as shown to the user. */
    fun delta(grid: HistoryGrid, effects: Effects, i: Int, h: Int): Double {
        val x = DoubleArray(LinearFeatures.SIZE)
        LinearFeatures.fill(grid, effects, i, h, x)
        // x[INSULIN] is minus profile ISF times the insulin that acts
        var sum = x[LinearFeatures.INSULIN] * isfMultiplier(grid.dayAngle[i])
        for (k in LinearFeatures.CARBS until LinearFeatures.SIZE) sum += theta[k] * x[k]
        return sum
    }

    /** The personal ISF is the profile ISF times this, at the time of day [angle]. Kept in 0.3..3.0. */
    fun isfMultiplier(angle: Double): Double = rawIsfMultiplier(angle).coerceIn(MIN_ISF_MULTIPLIER, MAX_ISF_MULTIPLIER)

    /** The multiplier before the limits, for the warning in the log */
    fun rawIsfMultiplier(angle: Double): Double = weighted(LinearFeatures.INSULIN, angle)

    /** The personal carb factor: 1.0 is the profile IC */
    fun carbFactor(): Double = (LinearFeatures.CARBS until LinearFeatures.DRIFT).sumOf { theta[it] }

    /** The carb weights, one per [CARB_HOURS] */
    fun carbWeights(): List<Double> = (LinearFeatures.CARBS until LinearFeatures.DRIFT).map { theta[it] }

    /** BG change per hour with no insulin and no carbs, at the time of day [angle] */
    fun driftPerHour(angle: Double): Double = weighted(LinearFeatures.DRIFT, angle)

    private fun weighted(first: Int, angle: Double): Double {
        val terms = LinearFeatures.dayTerms(angle)
        return (0 until 5).sumOf { theta[first + it] * terms[it] }
    }

    companion object {

        const val MIN_ISF_MULTIPLIER = 0.3
        const val MAX_ISF_MULTIPLIER = 3.0
    }
}
