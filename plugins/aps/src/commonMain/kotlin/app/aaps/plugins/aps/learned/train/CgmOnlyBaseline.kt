package app.aaps.plugins.aps.learned.train

import app.aaps.plugins.aps.learned.grid.HistoryGrid
import app.aaps.plugins.aps.learned.model.HORIZONS
import app.aaps.plugins.aps.learned.model.RidgeAccumulator
import app.aaps.plugins.aps.learned.model.RidgeFit
import kotlinx.coroutines.yield
import kotlin.math.cos
import kotlin.math.sin

/**
 * autoISF build, personal prediction: a yardstick that reads only BG, no insulin and no carbs. One ridge fit per point
 * of the curve on 8 values: the BG change over 5, 15, 30 and 60 minutes, BG minus its mean of the last 24 hours, BG,
 * and sin and cos of the time of day.
 *
 * The idea is the "adaptive" forecast of JugglucoNG (github.com/ctqvva/JugglucoNG, GPL-3.0; written again here, no code
 * copied). On two public data sets it beat "BG stays the same" for each of 48 people. A curve of this plugin that does
 * not beat it does not earn its size. It is only measured on the test days: never drawn, never used for a dose.
 */
object CgmOnlyBaseline {

    /** The 8 values and a constant */
    const val FEATURES = 9

    /** 24 hours of 5-minute steps */
    private const val DAY_STEPS = 288

    /** The 24-hour mean needs BG on at least half of the day */
    private const val MIN_DAY_VALUES = DAY_STEPS / 2

    private const val CHECK_EVERY = 500

    /** The fitted weights, one row of [FEATURES] per point of the curve */
    class Fit(val weights: Array<DoubleArray>)

    /** Per step of [grid]: the mean BG of the 24 hours up to and including it, NaN with too few readings */
    fun dayMeans(grid: HistoryGrid): DoubleArray {
        val n = grid.size
        val sum = DoubleArray(n + 1)
        val count = IntArray(n + 1)
        for (j in 0 until n) {
            val value = grid.bg[j]
            val present = !value.isNaN()
            sum[j + 1] = sum[j] + if (present) value else 0.0
            count[j + 1] = count[j] + if (present) 1 else 0
        }
        return DoubleArray(n) { j ->
            val from = maxOf(0, j + 1 - DAY_STEPS)
            val values = count[j + 1] - count[from]
            if (values < MIN_DAY_VALUES) Double.NaN else (sum[j + 1] - sum[from]) / values
        }
    }

    /** The values at step [i] into [out]; false when BG now, 5, 15, 30 or 60 minutes ago or the 24-hour mean is missing */
    fun features(grid: HistoryGrid, dayMeans: DoubleArray, i: Int, out: DoubleArray): Boolean {
        if (i < 12) return false
        val now = grid.bg[i]
        val mean = dayMeans[i]
        val b5 = grid.bg[i - 1]
        val b15 = grid.bg[i - 3]
        val b30 = grid.bg[i - 6]
        val b60 = grid.bg[i - 12]
        if (now.isNaN() || mean.isNaN() || b5.isNaN() || b15.isNaN() || b30.isNaN() || b60.isNaN()) return false
        val angle = grid.dayAngle[i]
        out[0] = 1.0
        out[1] = now - b5
        out[2] = now - b15
        out[3] = now - b30
        out[4] = now - b60
        out[5] = now - mean
        out[6] = now
        out[7] = sin(angle)
        out[8] = cos(angle)
        return true
    }

    /**
     * Fits on [steps] of [grid]; [futures] holds the BG of the next 2 hours of each step, [weights] the recency weight
     * per step. Null when no step had all its values.
     */
    suspend fun fit(grid: HistoryGrid, dayMeans: DoubleArray, steps: List<Int>, futures: Map<Int, DoubleArray>, weights: DoubleArray): Fit? {
        val acc = Array(HORIZONS) { RidgeAccumulator(FEATURES) }
        val x = DoubleArray(FEATURES)
        var rows = 0
        steps.forEachIndexed { n, i ->
            if (n % CHECK_EVERY == 0) yield()
            if (!features(grid, dayMeans, i, x)) return@forEachIndexed
            val future = futures.getValue(i)
            for (h in 0 until HORIZONS) acc[h].add(x, future[h] - grid.bg[i], weights[i])
            rows++
        }
        if (rows == 0) return null
        val prior = DoubleArray(FEATURES)
        return Fit(Array(HORIZONS) { RidgeFit.solve(acc[it], prior, emptySet()) })
    }

    /** The BG change at +5 .. +120 minutes after step [i], mg/dl; null when its values are missing */
    fun predict(fit: Fit, grid: HistoryGrid, dayMeans: DoubleArray, i: Int): DoubleArray? {
        val x = DoubleArray(FEATURES)
        if (!features(grid, dayMeans, i, x)) return null
        return DoubleArray(HORIZONS) { h ->
            val w = fit.weights[h]
            var y = 0.0
            for (k in 0 until FEATURES) y += w[k] * x[k]
            y
        }
    }
}
