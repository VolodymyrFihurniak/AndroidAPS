package app.aaps.plugins.aps.learned.train

import app.aaps.plugins.aps.learned.grid.HistoryGrid
import app.aaps.plugins.aps.learned.model.Effects
import app.aaps.plugins.aps.learned.model.HORIZONS
import app.aaps.plugins.aps.learned.model.LinearFeatures
import app.aaps.plugins.aps.learned.model.LinearModel
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * autoISF build, personal prediction: one stored AutoISF run, for the comparison on the test days. Point `k` of
 * [mainCurve] is the prediction `(k + 1) * 5` minutes after [timestamp], mg/dl.
 */
data class StoredRun(val timestamp: Long, val mainCurve: List<Double>)

/** autoISF build, personal prediction: which steps the model learns from, and what the network sees at a step. */
object Samples {

    /** The last days of the window are kept back to measure the model */
    const val TEST_DAYS = 14

    /** Fewer samples than this and there is no model */
    const val MIN_SAMPLES = 2000

    /** Steps of history a sample needs before it (10 h, more than any insulin duration) */
    const val LEAD_STEPS = 120

    /** Number of network inputs, see [networkInputs] */
    const val INPUTS = 16

    /** The horizons the network learns, in steps (30, 60, 90 and 120 minutes) */
    val KNOTS = intArrayOf(6, 12, 18, 24)

    /** The network part may move the curve by this much at most, mg/dl */
    const val MAX_RESIDUAL = 60.0

    /** Older days count less: half the weight every this many days */
    const val HALF_LIFE_DAYS = 90.0

    private const val DAY_MS = 86_400_000.0
    private const val CAP_MINUTES = 360f

    /**
     * True when step [i] can be learned from: BG now and 5, 15 and 30 minutes ago, a profile, no excluded step in the
     * next 2 hours, enough history before it and BG for the next 2 hours (one missing point is filled).
     */
    fun valid(grid: HistoryGrid, i: Int): Boolean {
        if (i < LEAD_STEPS || i + HORIZONS >= grid.size) return false
        if (!grid.hasProfile(i) || grid.isf[i] <= 0.0 || grid.ic[i] <= 0.0) return false
        if (grid.bg[i].isNaN() || grid.bg[i - 1].isNaN() || grid.bg[i - 3].isNaN() || grid.bg[i - 6].isNaN()) return false
        for (k in i..i + HORIZONS) if (grid.excluded[k]) return false
        return futureBg(grid, i) != null
    }

    /** BG at `i + 1` .. `i + 24`, one missing point filled with a straight line; null with more gaps or the last one missing */
    fun futureBg(grid: HistoryGrid, i: Int): DoubleArray? {
        if (i + HORIZONS >= grid.size) return null
        val out = DoubleArray(HORIZONS)
        var missing = 0
        for (h in 1..HORIZONS) {
            val value = grid.bg[i + h]
            if (value.isNaN()) {
                if (h == HORIZONS || ++missing > 1) return null
                val next = grid.bg[i + h + 1]
                if (next.isNaN()) return null
                out[h - 1] = (grid.bg[i + h - 1] + next) / 2
            } else out[h - 1] = value
        }
        return out
    }

    /** The weight of a sample [ageMs] old */
    fun weight(ageMs: Long): Double = 0.5.pow(ageMs / DAY_MS / HALF_LIFE_DAYS)

    /**
     * The network inputs at step [i], in this order: BG, the change over 5, 15 and 30 minutes (per step), their
     * difference, sin and cos of the time of day, IOB, insulin acting in the next step, COB, minutes since the last
     * carbs, grams of the last carbs, minutes since the last bolus, the temp basal as a ratio of the profile basal,
     * and the linear prediction at 60 and 120 minutes.
     *
     * [live] must be the effects as known at step [i] (`Effects.atLive(i)`), in training as live, so the network
     * never sees what was given later.
     */
    fun networkInputs(grid: HistoryGrid, live: Effects, linear: LinearModel, i: Int, out: FloatArray) {
        val short = LinearFeatures.slope(grid, i, 1)
        val mid = LinearFeatures.slope(grid, i, 3)
        val long = LinearFeatures.slope(grid, i, 6)
        val angle = grid.dayAngle[i]
        out[0] = grid.bg[i].toFloat()
        out[1] = short.toFloat()
        out[2] = mid.toFloat()
        out[3] = long.toFloat()
        out[4] = (short - mid).toFloat()
        out[5] = sin(angle).toFloat()
        out[6] = cos(angle).toFloat()
        out[7] = live.iob(i).toFloat()
        out[8] = live.insulinActing(i, 1).toFloat()
        out[9] = live.cob(i).toFloat()
        out[10] = live.lastCarbsStepsAgo(i)?.let { minOf(it * 5f, CAP_MINUTES) } ?: CAP_MINUTES
        out[11] = live.lastCarbsGrams(i).toFloat()
        out[12] = live.lastBolusStepsAgo(i)?.let { minOf(it * 5f, CAP_MINUTES) } ?: CAP_MINUTES
        out[13] = tempBasalRatio(grid, i)
        out[14] = linear.delta(grid, live, i, 12).toFloat()
        out[15] = linear.delta(grid, live, i, 24).toFloat()
    }

    /** Net insulin of step [i] as a part of the profile basal, kept in -2..10 */
    private fun tempBasalRatio(grid: HistoryGrid, i: Int): Float {
        val basal = grid.basal[i]
        if (basal.isNaN() || basal <= 0.0) return 0f
        val perHour = grid.insulin.sumOf { it[i] } * 12.0
        return (perHour / basal).coerceIn(-2.0, 10.0).toFloat()
    }

    /** The network part at horizon [h] steps from the 4 knot values, 0 at h = 0, straight lines between */
    fun residualAt(knots: DoubleArray, h: Int): Double {
        if (h <= 0) return 0.0
        var previousStep = 0
        var previousValue = 0.0
        for (k in KNOTS.indices) {
            val step = KNOTS[k]
            if (h <= step) return previousValue + (knots[k] - previousValue) * (h - previousStep) / (step - previousStep)
            previousStep = step
            previousValue = knots[k]
        }
        return knots.last()
    }
}
