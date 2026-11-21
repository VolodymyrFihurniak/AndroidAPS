package app.aaps.ui.compose.overview.graphs

import app.aaps.core.interfaces.overview.graph.GraphConfig
import app.aaps.core.interfaces.overview.graph.SecondaryGraph
import app.aaps.core.interfaces.overview.graph.SeriesType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Pins the stored format of the overview graph configuration.
 *
 * This is user configuration in preferences, and it still has to read documents written by older
 * versions - the secondary graph entries used to be bare arrays of series names before they gained a
 * height. These were written against the `org.json` implementation and kept while it moved to
 * kotlinx.serialization, so they say the swap changed nothing that is stored.
 */
class GraphConfigRepositoryImplTest {

    @Test
    fun `a configuration survives a round trip`() {
        val config = GraphConfig(
            bgOverlays = listOf(SeriesType.ACTIVITY, SeriesType.PREDICTIONS),
            iobOverlays = listOf(SeriesType.ACTIVITY),
            bgHeight = 200,
            iobHeight = 150,
            secondaryGraphs = listOf(SecondaryGraph(listOf(SeriesType.COB), 180))
        )

        val restored = GraphConfigRepositoryImpl.fromJson(GraphConfigRepositoryImpl.toJson(config))

        assertThat(restored).isEqualTo(config)
    }

    @Test
    fun `the legacy shape, a bare array of series names, still reads`() {
        // Before secondary graphs had their own height, each entry was just the list of series.
        // BGI, not COB: a graph with COB alone is moved into the BG graph (autoISF build, see below)
        val legacy = """{"secondaryGraphs":[["BGI"]]}"""

        val restored = GraphConfigRepositoryImpl.fromJson(legacy)

        assertThat(restored.secondaryGraphs).hasSize(1)
        assertThat(restored.secondaryGraphs.first().series).containsExactly(SeriesType.BGI)
        assertThat(restored.secondaryGraphs.first().height).isEqualTo(GraphConfig.DEFAULT_GRAPH_HEIGHT_DP)
    }

    @Test
    fun `a series name this version does not know is skipped, not fatal`() {
        // Forward compatibility: a newer build may have written a series this one cannot name.
        val restored = GraphConfigRepositoryImpl.fromJson("""{"secondaryGraphs":[{"series":["BGI","NOT_A_SERIES"]}]}""")

        assertThat(restored.secondaryGraphs.first().series).containsExactly(SeriesType.BGI)
    }

    @Test
    fun `IOB is dropped from a configurable graph`() {
        // IOB has its own fixed graph now; an older document may still list it here.
        val restored = GraphConfigRepositoryImpl.fromJson("""{"secondaryGraphs":[{"series":["IOB","BGI"]}]}""")

        assertThat(restored.secondaryGraphs.first().series).containsExactly(SeriesType.BGI)
    }

    @Test
    fun `a graph left with no series at all is dropped`() {
        val restored = GraphConfigRepositoryImpl.fromJson("""{"secondaryGraphs":[{"series":["IOB"]}]}""")

        assertThat(restored.secondaryGraphs).isEmpty()
    }

    @Test
    fun `a repeated series is kept once, and at most two are kept`() {
        val restored = GraphConfigRepositoryImpl.fromJson(
            """{"secondaryGraphs":[{"series":["COB","COB","ACTIVITY","DEVIATIONS"]}]}"""
        )

        assertThat(restored.secondaryGraphs.first().series).containsExactly(SeriesType.COB, SeriesType.ACTIVITY).inOrder()
    }

    @Test
    fun `heights are clamped to the allowed range`() {
        val restored = GraphConfigRepositoryImpl.fromJson("""{"bgHeight":10,"iobHeight":100000}""")

        assertThat(restored.bgHeight).isEqualTo(GraphConfig.DEFAULT_GRAPH_HEIGHT_DP)
        assertThat(restored.iobHeight).isEqualTo(GraphConfig.MAX_GRAPH_HEIGHT_DP)
    }

    @Test
    fun `missing overlays fall back to the defaults`() {
        val restored = GraphConfigRepositoryImpl.fromJson("{}")

        assertThat(restored.bgOverlays).containsExactlyElementsIn(GraphConfig.DEFAULT_BG_OVERLAYS).inOrder()
        assertThat(restored.iobOverlays).containsExactly(SeriesType.ACTIVITY, SeriesType.BASAL)
    }

    // autoISF build: the basal band became a layer that can be switched off

    @Test
    fun `a config saved before the basal layer keeps showing the basal`() {
        // The user had switched the activity off; the basal was always shown then
        val restored = GraphConfigRepositoryImpl.fromJson("""{"iobOverlays":[]}""")

        assertThat(restored.iobOverlays).containsExactly(SeriesType.BASAL)
    }

    @Test
    fun `a basal layer switched off stays off`() {
        val config = GraphConfig(iobOverlays = listOf(SeriesType.ACTIVITY))

        val json = GraphConfigRepositoryImpl.toJson(config)

        assertThat(GraphConfigRepositoryImpl.fromJson(json).iobOverlays).containsExactly(SeriesType.ACTIVITY)
        // An older version reads the old key, which never holds the basal layer
        assertThat(json).contains(""""iobOverlays":["ACTIVITY"]""")
    }

    @Test
    fun `an empty overlay list is kept empty, not replaced by the default`() {
        // Written explicitly means the user turned them all off - that is not the same as absent.
        val restored = GraphConfigRepositoryImpl.fromJson("""{"bgLayers":[],"modelLayer":false}""")

        assertThat(restored.bgOverlays).isEmpty()
    }

    // autoISF build: IOB, COB, the basal and the line became layers of the BG graph

    @Test
    fun `a config saved before the BG layers gets IOB, COB and the basal in the BG graph`() {
        val restored = GraphConfigRepositoryImpl.fromJson("""{"bgOverlays":["PREDICTIONS"]}""")

        assertThat(restored.bgOverlays)
            .containsExactly(SeriesType.PREDICTIONS, SeriesType.IOB, SeriesType.COB, SeriesType.BASAL, SeriesType.LEARNED_PREDICTION).inOrder()
    }

    @Test
    fun `a config saved before the BG layers loses its graph with COB alone, and keeps the others`() {
        val restored = GraphConfigRepositoryImpl.fromJson(
            """{"secondaryGraphs":[{"series":["COB"]},{"series":["COB","DEVIATIONS"]},{"series":["BGI"]}]}"""
        )

        assertThat(restored.secondaryGraphs.map { it.series })
            .containsExactly(listOf(SeriesType.COB, SeriesType.DEVIATIONS), listOf(SeriesType.BGI)).inOrder()
    }

    @Test
    fun `the BG layers survive a round trip, and the old key holds none of them`() {
        val config = GraphConfig(bgOverlays = listOf(SeriesType.PREDICTIONS, SeriesType.COB, SeriesType.BG_LINE))

        val json = GraphConfigRepositoryImpl.toJson(config)
        val restored = GraphConfigRepositoryImpl.fromJson(json)

        assertThat(restored.bgOverlays).containsExactly(SeriesType.PREDICTIONS, SeriesType.COB, SeriesType.BG_LINE).inOrder()
        assertThat(json).contains(""""bgOverlays":["PREDICTIONS"]""")
    }

    @Test
    fun `a COB graph added after the BG layers stays`() {
        val config = GraphConfig(secondaryGraphs = listOf(SecondaryGraph(listOf(SeriesType.COB))))

        val restored = GraphConfigRepositoryImpl.fromJson(GraphConfigRepositoryImpl.toJson(config))

        assertThat(restored.secondaryGraphs.map { it.series }).containsExactly(listOf(SeriesType.COB))
    }

    // autoISF build: the layer of the personal prediction

    @Test
    fun `model layer is on for a config saved before it`() {
        val restored = GraphConfigRepositoryImpl.fromJson("""{"bgLayers":["PREDICTIONS","COB"]}""")

        assertThat(restored.bgOverlays).containsExactly(SeriesType.PREDICTIONS, SeriesType.COB, SeriesType.LEARNED_PREDICTION).inOrder()
    }

    @Test
    fun `model layer switched off stays off`() {
        val config = GraphConfig(bgOverlays = listOf(SeriesType.PREDICTIONS))

        val restored = GraphConfigRepositoryImpl.fromJson(GraphConfigRepositoryImpl.toJson(config))

        assertThat(restored.bgOverlays).containsExactly(SeriesType.PREDICTIONS)
    }

    @Test
    fun `old key does not get the model layer`() {
        val config = GraphConfig(bgOverlays = listOf(SeriesType.PREDICTIONS, SeriesType.LEARNED_PREDICTION))

        val json = GraphConfigRepositoryImpl.toJson(config)

        assertThat(json).contains(""""bgOverlays":["PREDICTIONS"]""")
        assertThat(GraphConfigRepositoryImpl.fromJson(json).bgOverlays).containsExactly(SeriesType.PREDICTIONS, SeriesType.LEARNED_PREDICTION).inOrder()
    }

    @Test
    fun `the model layer is on by default`() {
        assertThat(GraphConfig.DEFAULT_BG_OVERLAYS).contains(SeriesType.LEARNED_PREDICTION)
    }
}
