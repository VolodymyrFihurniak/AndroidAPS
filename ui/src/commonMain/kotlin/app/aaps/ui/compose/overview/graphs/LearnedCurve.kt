package app.aaps.ui.compose.overview.graphs

import androidx.compose.runtime.Immutable
import app.aaps.core.interfaces.overview.graph.GraphDataPoint

/** autoISF build: a forecast of the personal prediction older than this is not drawn */
const val LEARNED_MAX_AGE_MS = 15 * 60_000L

/**
 * autoISF build: the personal prediction as the BG graph draws it, in the units of the graph. Each list starts at BG
 * now; [upper] and [lower] are the curve plus and minus the typical error of the model at each point.
 */
@Immutable
data class LearnedCurve(
    val center: List<GraphDataPoint>,
    val upper: List<GraphDataPoint>,
    val lower: List<GraphDataPoint>,
    /** False while the model is not better than AutoISF */
    val ready: Boolean
)
