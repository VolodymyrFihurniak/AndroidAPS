package app.aaps.core.interfaces.overview.graph

import app.aaps.core.interfaces.overview.graph.GraphConfig.Companion.MAX_GRAPH_HEIGHT_DP
import kotlinx.coroutines.flow.StateFlow

/**
 * Series types available for all graphs (BG primary and secondary).
 * Each type maps to a data flow in OverviewDataCache.
 * IOB implicitly includes bolus markers, COB implicitly includes carbs markers.
 *
 * Not all types are allowed on all graphs — the UI enforces allowed sets per graph type.
 * BG graph: only BASAL, ACTIVITY.
 * Secondary graphs: all types.
 */
enum class SeriesType {

    IOB,
    ABS_IOB,
    COB,
    BGI,
    DEVIATIONS,
    SENSITIVITY,
    VAR_SENSITIVITY,
    DEV_SLOPE,
    HEART_RATE,
    STEPS,
    ACTIVITY,
    PREDICTIONS,

    // autoISF: the factors of each run (acce, bg, pp, dura, final) and the effective IOB threshold
    AUTOISF,
    IOB_TH,

    // autoISF: the parabola fitted to the last BG values, an overlay of the BG graph only
    BG_PARABOLA,

    // autoISF build: the basal band (profile and given basal) at the top of the IOB graph or of the BG graph
    BASAL,

    // autoISF build: the BG readings drawn as one line instead of dots, a switch of the BG graph only
    BG_LINE,

    // autoISF build: the curve of the personal prediction (experimental), a BG graph layer shown only while its plugin is on
    LEARNED_PREDICTION
}

/**
 * Secondary graph entry: series list + per-graph height (dp).
 * Max 2 series per graph. Height is per-graph user-adjustable.
 */
data class SecondaryGraph(
    val series: List<SeriesType>,
    val height: Int = GraphConfig.DEFAULT_GRAPH_HEIGHT_DP
)

/**
 * Configuration for the overview graphs.
 *
 * Fixed graphs (not removable):
 * - BG graph: blood glucose with optional activity overlay (toggled via [bgOverlays]).
 * - IOB graph: IOB line with bolus markers + flipped basal overlay, with optional activity
 *   overlay (toggled via [iobOverlays]).
 *
 * @param bgOverlays       Overlay toggles for the BG graph: [SeriesType.ACTIVITY], [SeriesType.PREDICTIONS], the
 *   autoISF [SeriesType.BG_PARABOLA], and in the autoISF build [SeriesType.IOB] and [SeriesType.COB] as areas at the
 *   bottom, [SeriesType.BASAL] as a band at the top and [SeriesType.BG_LINE] for a line instead of dots.
 * @param iobOverlays      Overlay toggles for the fixed IOB graph (currently only [SeriesType.ACTIVITY]).
 * @param secondaryGraphs  Ordered list of user-configurable secondary graph configurations.
 *   IOB cannot appear here (it has a dedicated fixed slot). Each graph is a List (not Set) to
 *   preserve selection order:
 *   - list[0] = left axis (start), list[1] = right axis (end).
 *   Max 2 series per graph. FIFO: adding a 3rd deselects the oldest.
 */
data class GraphConfig(
    val bgOverlays: List<SeriesType> = DEFAULT_BG_OVERLAYS,
    val iobOverlays: List<SeriesType> = listOf(SeriesType.ACTIVITY, SeriesType.BASAL),
    val bgHeight: Int = DEFAULT_GRAPH_HEIGHT_DP,
    val iobHeight: Int = DEFAULT_GRAPH_HEIGHT_DP,
    // autoISF build: COB is drawn in the BG graph, so there is no COB graph below it by default
    val secondaryGraphs: List<SecondaryGraph> = emptyList()
) {

    companion object {

        /** autoISF build: IOB, COB and the basal are drawn in the BG graph, as the user asked */
        val DEFAULT_BG_OVERLAYS = listOf(
            SeriesType.ACTIVITY, SeriesType.PREDICTIONS, SeriesType.BG_PARABOLA, SeriesType.IOB, SeriesType.COB, SeriesType.BASAL,
            SeriesType.LEARNED_PREDICTION
        )

        /** Maximum number of secondary graphs allowed */
        const val MAX_SECONDARY_GRAPHS = 5

        /** Default graph height in dp (minimum value — user-adjustable up to [MAX_GRAPH_HEIGHT_DP]) */
        const val DEFAULT_GRAPH_HEIGHT_DP = 100

        /** Maximum graph height in dp (2.5x default) */
        const val MAX_GRAPH_HEIGHT_DP = 250
    }
}

/**
 * Repository for graph configuration persistence.
 * Loads/saves GraphConfig and exposes it as a reactive StateFlow.
 */
interface GraphConfigRepository {

    /** Current graph configuration, updated reactively on changes */
    val graphConfigFlow: StateFlow<GraphConfig>

    /** Update and persist graph configuration */
    fun update(config: GraphConfig)
}
