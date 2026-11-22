package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/** autoISF build: the ranges the graph card offers, in minutes. 24 h is the whole loaded range. */
internal val GRAPH_CARD_RANGES_MINUTES = listOf(180.0, 360.0, 720.0, 1440.0)

/**
 * autoISF build: the top of the graph card on a phone, from the user's concept: the title with a button to the history
 * browser, the buttons for the time range, and a legend of what the graphs show now.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GraphCardHeader(
    rangeMinutes: Double,
    onRangeSelected: (Double) -> Unit,
    bgOverlays: List<SeriesType>,
    onOpenHistory: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** autoISF build: the curves of the personal prediction that are drawn, for their legend entries */
    learned: LearnedCurve? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Icon(
            imageVector = Icons.Filled.ShowChart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(AapsSpacing.chipIconSize)
        )
        Text(
            text = stringResource(UiStrings.graph_card_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(start = AapsSpacing.medium)
                .weight(1f)
        )
        if (onOpenHistory != null) {
            IconButton(onClick = onOpenHistory) {
                Icon(imageVector = Icons.Filled.CalendarMonth, contentDescription = stringResource(UiStrings.graph_card_history))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium), modifier = Modifier.fillMaxWidth()) {
        for (minutes in GRAPH_CARD_RANGES_MINUTES) {
            FilterChip(
                selected = minutes == rangeMinutes,
                onClick = { onRangeSelected(minutes) },
                label = { Text(stringResource(UiStrings.graph_range_hours, (minutes / 60).toInt())) },
                modifier = Modifier.weight(1f)
            )
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AapsSpacing.small)
    ) {
        val glucoseMark = if (SeriesType.BG_LINE in bgOverlays) LegendMark.LINE else LegendMark.DOT
        LegendItem(AapsTheme.generalColors.bgInRange, glucoseMark, stringResource(CoreUiStrings.glucose))
        if (SeriesType.PREDICTIONS in bgOverlays) LegendItem(AapsTheme.generalColors.iobPrediction, LegendMark.DASHED, stringResource(CoreUiStrings.predictions_shortname))
        if (SeriesType.BG_PARABOLA in bgOverlays) LegendItem(AapsTheme.generalColors.bgParabola, LegendMark.LINE, stringResource(CoreUiStrings.bg_parabola_shortname))
        // Only the name of each model: whether it is better than AutoISF is on the plugin screen and in the Insulin dialog
        if (learned != null && learned.expected.isNotEmpty())
            LegendItem(AapsTheme.generalColors.learnedPrediction, LegendMark.LINE, stringResource(CoreUiStrings.learned_expected_shortname))
        if (learned != null && learned.noInsulin.isNotEmpty())
            LegendItem(AapsTheme.generalColors.learnedPrediction, LegendMark.DASHED, stringResource(CoreUiStrings.learned_no_insulin_shortname))
        // IOB and COB are areas at the bottom of the BG graph, the basal a band at its top
        if (SeriesType.IOB in bgOverlays) LegendItem(AapsTheme.generalColors.iobPrediction, LegendMark.SQUARE, stringResource(CoreUiStrings.iob))
        if (SeriesType.COB in bgOverlays) LegendItem(AapsTheme.generalColors.cobPrediction, LegendMark.SQUARE, stringResource(CoreUiStrings.cob))
        if (SeriesType.BASAL in bgOverlays) {
            LegendItem(AapsTheme.elementColors.tempBasal.copy(alpha = 0.5f), LegendMark.SQUARE, stringResource(UiStrings.basal_card))
            LegendItem(AapsTheme.elementColors.tempBasal, LegendMark.DASHED, stringResource(CoreUiStrings.profile))
        }
    }
}

private enum class LegendMark { DOT, LINE, DASHED, SQUARE }

@Composable
private fun LegendItem(color: Color, mark: LegendMark, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = 16.dp, height = 10.dp)) {
            val y = size.height / 2
            when (mark) {
                LegendMark.DOT    -> drawCircle(color, radius = size.height / 2, center = Offset(size.width / 2, y))
                LegendMark.SQUARE -> drawRect(color)
                LegendMark.LINE   -> drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                LegendMark.DASHED -> drawLine(
                    color, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = AapsSpacing.small)
        )
    }
}
