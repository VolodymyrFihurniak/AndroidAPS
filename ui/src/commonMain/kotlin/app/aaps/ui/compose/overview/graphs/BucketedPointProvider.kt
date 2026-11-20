package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore

/**
 * PointProvider for BUCKETED BG data - colors points by range (LOW/IN_RANGE/HIGH).
 * Uses lookup map keyed by x-value to find the original BgDataPoint.
 */
@Immutable
class BucketedPointProvider(
    private val dataLookup: Map<Double, BgDataPoint>,
    lowColor: Color,
    inRangeColor: Color,
    highColor: Color,
    /** autoISF build: in line mode only a low reading gets a dot, the line itself shows the rest */
    private val lowOnly: Boolean = false
) : LineCartesianLayer.PointProvider {

    // Pre-build point components for efficiency
    private val lowPoint = createFilledPoint(lowColor)
    private val inRangePoint = createFilledPoint(inRangeColor)
    private val highPoint = createFilledPoint(highColor)

    private fun createFilledPoint(color: Color) = LineCartesianLayer.Point(
        component = ShapeComponent(
            fill = Fill(color),
            shape = CircleShape
        ),
        size = 6.dp
    )

    override fun getPoint(
        entry: LineCartesianLayerModel.Entry,
        extraStore: ExtraStore
    ): LineCartesianLayer.Point? {
        val dataPoint = dataLookup[entry.x] ?: return if (lowOnly) null else inRangePoint // fallback
        if (lowOnly) return if (dataPoint.range == BgRange.LOW) lowPoint else null

        return when (dataPoint.range) {
            BgRange.LOW      -> lowPoint
            BgRange.IN_RANGE -> inRangePoint
            BgRange.HIGH     -> highPoint
        }
    }

    override fun getLargestPoint(extraStore: ExtraStore): LineCartesianLayer.Point = inRangePoint
}