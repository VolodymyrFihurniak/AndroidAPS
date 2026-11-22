package app.aaps.plugins.aps.learned.train

import app.aaps.plugins.aps.learned.grid.HistoryGrid
import app.aaps.plugins.aps.learned.model.Effects
import app.aaps.plugins.aps.learned.model.HORIZONS
import app.aaps.plugins.aps.learned.model.LinearModel
import kotlin.math.cos
import kotlin.math.sin

/**
 * autoISF build, personal prediction v2: the inputs and targets of the GRU for the expected curve, the curve of where
 * BG really goes, the loop included.
 *
 * The step rows are made once per grid. Row `j` only uses what was known at step `j`, so a sequence that ends at step
 * `i` never sees later doses, carbs or BG. A sample is then only a step number: its sequence is a view of 36 rows, and
 * its context and target are made when its batch is built.
 */
object SequenceFeatures {

    /** Steps in a sequence (36 steps = 3 hours) */
    const val STEPS = 36

    /** Values per step, see [steps] */
    const val STEP_INPUTS = 14

    /** Context values per sample, see [context] */
    const val CONTEXT = 15

    /** Outputs and targets are BG changes divided by this, so they are near -1..1 */
    const val TARGET_SCALE = 50.0

    /** How many past days of the same time of day the context looks at */
    const val DAYS = 7

    private const val STEPS_PER_DAY = 288

    /** Absorption time of the carb values in a row */
    private const val CARB_HOURS = 3

    /** The steps ahead of the 4 context knots (30, 60, 90, 120 minutes) */
    private val KNOTS = Samples.KNOTS

    /**
     * One row per step of [grid], with these values: BG, BG change from the step before, net insulin given in the step,
     * insulin that acts in the next step, IOB, carbs eaten in the step, carbs absorbed in the next step (3 h absorption),
     * COB (3 h), the temp basal ratio, sin and cos of the time of day, the change of the step before that insulin
     * and entered carbs do not explain (a meal going on, see [UnannouncedMeals.unexplained]), the steps and the heart
     * rate. Without steps the row has 0; without a heart rate it has the middle heart rate of the grid (0 without any),
     * so a missing watch does not look like a high or a low pulse.
     *
     * A BG gap keeps the last BG, with change 0. Steps before the first reading take the first reading.
     * [effects] is made without a live index: row `j` only counts doses and carbs up to step `j` anyway.
     */
    fun steps(grid: HistoryGrid, effects: Effects): Array<FloatArray> {
        val unexplained = UnannouncedMeals.unexplained(grid, effects)
        val usualHeartRate = grid.heartRate.filter { !it.isNaN() }.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
        val first = grid.bg.firstOrNull { !it.isNaN() } ?: 0.0
        var last = first
        return Array(grid.size) { j ->
            val value = grid.bg[j]
            val bg = if (value.isNaN()) last else value
            val change = bg - last
            last = bg
            val angle = grid.dayAngle[j]
            floatArrayOf(
                bg.toFloat(),
                change.toFloat(),
                grid.insulin.sumOf { it[j] }.toFloat(),
                effects.insulinActing(j, 1).toFloat(),
                effects.iob(j).toFloat(),
                grid.carbs[j].toFloat(),
                effects.carbsAbsorbed(j, 1, CARB_HOURS).toFloat(),
                effects.cob(j, CARB_HOURS).toFloat(),
                Samples.tempBasalRatio(grid, j),
                sin(angle).toFloat(),
                cos(angle).toFloat(),
                // The change of the step before that insulin and entered carbs do not explain: a meal going on
                (if (j > 0 && !unexplained[j - 1].isNaN()) unexplained[j - 1] else 0.0).toFloat(),
                (if (grid.steps[j].isNaN()) 0.0 else grid.steps[j]).toFloat(),
                (if (grid.heartRate[j].isNaN()) usualHeartRate else grid.heartRate[j]).toFloat()
            )
        }
    }

    /** The rows `i - 35` .. `i` of [steps], oldest first, as a view without copies. Null when there are not enough. */
    fun sequence(steps: Array<FloatArray>, i: Int): Array<FloatArray>? {
        if (i < STEPS - 1 || i >= steps.size) return null
        return Array(STEPS) { steps[i - STEPS + 1 + it] }
    }

    /**
     * The context of step [i], in this order:
     * - 0..3: the mean BG 30, 60, 90 and 120 minutes after the same time of day, over the last 7 days;
     * - 4..7: the mean BG change from that time to the same 4 points;
     * - 8: how many of the 7 days had BG at that time and at all 4 points (0-7);
     * - 9, 10: the profile ISF (mg/dl per U) and IC (g per U);
     * - 11..14: the linear "no more insulin" prediction at 30, 60, 90 and 120 minutes.
     *
     * A day without BG at one of those points is skipped. With no day found the mean is BG now and the change 0.
     * [live] must be `Effects.atLive(i)`.
     */
    fun context(grid: HistoryGrid, live: Effects, linear: LinearModel, i: Int): FloatArray {
        val out = FloatArray(CONTEXT)
        val sumBg = DoubleArray(KNOTS.size)
        val sumChange = DoubleArray(KNOTS.size)
        var days = 0
        for (d in 1..DAYS) {
            val base = i - d * STEPS_PER_DAY
            if (base < 0) break
            val baseBg = grid.bg[base]
            if (baseBg.isNaN() || KNOTS.any { grid.bg[base + it].isNaN() }) continue
            for (k in KNOTS.indices) {
                val value = grid.bg[base + KNOTS[k]]
                sumBg[k] += value
                sumChange[k] += value - baseBg
            }
            days++
        }
        for (k in KNOTS.indices) {
            out[k] = (if (days > 0) sumBg[k] / days else grid.bg[i]).toFloat()
            out[KNOTS.size + k] = (if (days > 0) sumChange[k] / days else 0.0).toFloat()
        }
        out[8] = days.toFloat()
        out[9] = grid.isf[i].toFloat()
        out[10] = grid.ic[i].toFloat()
        for (k in KNOTS.indices) out[11 + k] = linear.delta(grid, live, i, KNOTS[k]).toFloat()
        return out
    }

    /** The real BG change at +5 .. +120 minutes after step [i], divided by [TARGET_SCALE]. Null when BG is missing. */
    fun target(grid: HistoryGrid, i: Int): FloatArray? {
        val future = Samples.futureBg(grid, i) ?: return null
        val now = grid.bg[i]
        if (now.isNaN()) return null
        return FloatArray(HORIZONS) { ((future[it] - now) / TARGET_SCALE).toFloat() }
    }
}
