package app.aaps.plugins.aps.learned.train

import app.aaps.plugins.aps.learned.grid.HistoryGrid
import app.aaps.plugins.aps.learned.model.LinearFeatures
import kotlin.math.abs

/**
 * autoISF build, personal prediction: the band of typical error depends on how fast BG changes now. A forecast while
 * BG is steady is much surer than one while it rises or falls fast, after a meal or a bolus. The test days are measured
 * in three groups, and a live forecast shows the band of the group it is in.
 */
object ErrorBands {

    /** BG changes by less than 1 mg/dl per 5 minutes (12 per hour) on average over 15 minutes */
    const val STEADY = 0

    /** By 1 to 3 mg/dl per 5 minutes */
    const val MODERATE = 1

    /** By 3 mg/dl per 5 minutes or more (36 per hour) */
    const val FAST = 2

    const val COUNT = 3

    /** A group needs this many test moments; with fewer, the band of all moments is used */
    const val MIN_MOMENTS = 50

    private const val MODERATE_FROM = 1.0
    private const val FAST_FROM = 3.0

    /** The group of step [i] */
    fun of(grid: HistoryGrid, i: Int): Int {
        val change = abs(LinearFeatures.slope(grid, i, 3))
        return when {
            change < MODERATE_FROM -> STEADY
            change < FAST_FROM     -> MODERATE
            else                   -> FAST
        }
    }

    /** The band of group [group] when it was measured, else [all] */
    fun pick(byGroup: List<List<Double>>, group: Int, all: List<Double>): List<Double> =
        byGroup.getOrNull(group)?.takeIf { it.isNotEmpty() } ?: all
}
