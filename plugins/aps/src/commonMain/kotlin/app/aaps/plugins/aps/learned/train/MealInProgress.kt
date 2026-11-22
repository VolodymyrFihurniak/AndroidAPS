package app.aaps.plugins.aps.learned.train

import app.aaps.plugins.aps.learned.model.HORIZONS

/**
 * autoISF build, personal prediction: a meal that goes on at a moment, as oref's UAM sees it. When BG rose faster than
 * insulin and entered carbs explain in the last 30 minutes, the carbs still act: the "no more insulin" curve lets that
 * rise go on, fading to 0. Without it the curve went down while BG rose from a meal that was not entered.
 *
 * The rise now is the line through the last [WINDOW_STEPS] unexplained changes, at the last one. It fades to 0 as fast
 * as it already slows down, but not faster than in [MIN_FADE_STEPS] and not slower than in [MAX_FADE_STEPS]; if it
 * does not slow down, in [DEFAULT_FADE_STEPS].
 */
object MealInProgress {

    /** 30 minutes */
    const val WINDOW_STEPS = 6

    /** Below 1 mg/dl per 5 minutes there is no meal going on */
    const val MIN_RATE_MGDL_PER_STEP = 1.0

    const val MIN_FADE_STEPS = 6
    const val DEFAULT_FADE_STEPS = 12
    const val MAX_FADE_STEPS = 24

    /**
     * The extra BG change at +1 .. +24 steps after moment [i], mg/dl; all 0 without a meal going on. [unexplained] is
     * from [UnannouncedMeals.unexplained]: the changes of the steps before [i] are known at [i], not the one from [i].
     */
    fun rise(unexplained: DoubleArray, i: Int): DoubleArray {
        val out = DoubleArray(HORIZONS)
        // The changes that end at i - WINDOW_STEPS + 1 .. i
        var sumX = 0.0
        var sumY = 0.0
        var sumXX = 0.0
        var sumXY = 0.0
        var count = 0
        for (k in 0 until WINDOW_STEPS) {
            val j = i - WINDOW_STEPS + k
            if (j < 0 || j >= unexplained.size) continue
            val y = unexplained[j]
            if (y.isNaN()) continue
            sumX += k
            sumY += y
            sumXX += k * k
            sumXY += k * y
            count++
        }
        if (count * 3 < WINDOW_STEPS * 2) return out
        val meanX = sumX / count
        val meanY = sumY / count
        val spread = sumXX / count - meanX * meanX
        val slope = if (spread > 0.0) (sumXY / count - meanX * meanY) / spread else 0.0
        val rate = meanY + slope * (WINDOW_STEPS - 1 - meanX)
        if (meanY < MIN_RATE_MGDL_PER_STEP || rate <= 0.0) return out
        val fade = (if (slope < 0.0) rate / -slope else DEFAULT_FADE_STEPS.toDouble()).coerceIn(MIN_FADE_STEPS.toDouble(), MAX_FADE_STEPS.toDouble())
        var total = 0.0
        for (h in 1..HORIZONS) {
            total += rate * (1.0 - h / fade).coerceAtLeast(0.0)
            out[h - 1] = total
        }
        return out
    }
}
