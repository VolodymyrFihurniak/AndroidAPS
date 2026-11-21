package app.aaps.ui.compose.overview.graphs

import androidx.compose.runtime.Immutable
import app.aaps.core.interfaces.overview.graph.GraphDataPoint
import app.aaps.core.interfaces.overview.graph.SeriesType

/** autoISF build: a forecast of the personal prediction older than this is not drawn */
const val LEARNED_MAX_AGE_MS = 15 * 60_000L

/**
 * autoISF build: the two curves of the personal prediction as the BG graph draws them, in the units of the graph.
 * Each list starts at BG now, and is empty when that curve is not there.
 */
@Immutable
data class LearnedCurve(
    /** If no more insulin is given. Drawn dashed. */
    val noInsulin: List<GraphDataPoint>,
    /** False while this curve is not better than AutoISF */
    val noInsulinReady: Boolean,
    /** v2: where BG really goes, the loop included. Drawn solid, with a band of its typical error at each point. */
    val expected: List<GraphDataPoint>,
    val expectedUpper: List<GraphDataPoint>,
    val expectedLower: List<GraphDataPoint>,
    val expectedReady: Boolean
) {

    /** Only the curves whose layer is in [bgOverlays]; null when none is left */
    fun shownIn(bgOverlays: List<SeriesType>): LearnedCurve? {
        val noInsulinShown = SeriesType.LEARNED_PREDICTION in bgOverlays && noInsulin.isNotEmpty()
        val expectedShown = SeriesType.LEARNED_EXPECTED in bgOverlays && expected.isNotEmpty()
        if (!noInsulinShown && !expectedShown) return null
        return copy(
            noInsulin = if (noInsulinShown) noInsulin else emptyList(),
            expected = if (expectedShown) expected else emptyList(),
            expectedUpper = if (expectedShown) expectedUpper else emptyList(),
            expectedLower = if (expectedShown) expectedLower else emptyList()
        )
    }
}
