package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/** Height of the mini map under the BG graph. */
private val MINI_MAP_HEIGHT = 36.dp

/**
 * autoISF build: the whole loaded range of BG in a small strip under the BG graph, as in the user's xdripswift. A
 * frame marks the part the BG graph shows now. A tap moves the graph to that time, with an animation; a drag moves it
 * along with the finger.
 *
 * [window] and the x given to [onCenterAt] are in minutes from the start of [timeRange], as everywhere in the graphs.
 */
@Composable
internal fun GraphMiniMap(
    readings: List<BgDataPoint>,
    timeRange: Pair<Long, Long>?,
    /** Read only while drawing, so a scroll redraws the strip without composing the graphs again. */
    window: () -> Pair<Double, Double>?,
    lowMark: Double,
    highMark: Double,
    onCenterAt: (x: Double, animate: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (timeRange == null || readings.isEmpty()) return
    val (minTimestamp, maxTimestamp) = timeRange
    val totalMinutes = (maxTimestamp - minTimestamp) / 60_000.0
    if (totalMinutes <= 0.0) return

    val lowColor = AapsTheme.generalColors.bgLow
    val inRangeColor = AapsTheme.generalColors.bgInRange
    val highColor = AapsTheme.generalColors.bgHigh
    val markColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val windowColor = MaterialTheme.colorScheme.primary
    val currentOnCenterAt by rememberUpdatedState(onCenterAt)
    val description = stringResource(UiStrings.graph_mini_map)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(MINI_MAP_HEIGHT)
            .clip(RoundedCornerShape(AapsSpacing.medium))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { contentDescription = description }
            .pointerInput(totalMinutes) {
                detectTapGestures { offset -> currentOnCenterAt(offset.x / size.width * totalMinutes, true) }
            }
            .pointerInput(totalMinutes) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    currentOnCenterAt((change.position.x / size.width * totalMinutes).coerceIn(0.0, totalMinutes), false)
                }
            }
    ) {
        val minY = minOf(readings.minOf { it.value }, lowMark)
        val maxY = maxOf(readings.maxOf { it.value }, highMark)
        val spanY = (maxY - minY).takeIf { it > 0.0 } ?: 1.0
        val inset = 3.dp.toPx()
        fun xOf(timestamp: Long): Float = ((timestamp - minTimestamp) / 60_000.0 / totalMinutes * size.width).toFloat()
        fun yOf(value: Double): Float = (inset + (1 - (value - minY) / spanY) * (size.height - 2 * inset)).toFloat()

        // The low and the high mark, faint and dashed
        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
        for (mark in listOf(lowMark, highMark)) {
            drawLine(markColor, Offset(0f, yOf(mark)), Offset(size.width, yOf(mark)), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        }

        val radius = 1.2.dp.toPx()
        for (reading in readings) {
            val color = when (reading.range) {
                BgRange.LOW      -> lowColor
                BgRange.IN_RANGE -> inRangeColor
                BgRange.HIGH     -> highColor
            }
            drawCircle(color, radius, Offset(xOf(reading.timestamp), yOf(reading.value)))
        }

        // The part the BG graph shows: a light fill with a bar at each edge
        window()?.let { (start, end) ->
            val left = (start / totalMinutes * size.width).toFloat().coerceIn(0f, size.width)
            val right = (end / totalMinutes * size.width).toFloat().coerceIn(0f, size.width)
            if (right > left) {
                drawRect(windowColor.copy(alpha = 0.15f), Offset(left, 0f), Size(right - left, size.height))
                val bar = 2.dp.toPx()
                drawRect(windowColor, Offset(left, 0f), Size(bar, size.height))
                drawRect(windowColor, Offset(right - bar, 0f), Size(bar, size.height))
            }
        }
    }
}
