package app.aaps.ui.compose.overview.graphs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/** autoISF build: the ranges the graph card offers, in minutes. 24 h is the whole loaded range. */
internal val GRAPH_CARD_RANGES_MINUTES = listOf(180.0, 360.0, 720.0, 1440.0)

/** autoISF build: the layers of the BG graph and of the strip under it, in the order of the switches of the graph card. */
internal val GRAPH_CARD_LAYERS = listOf(
    SeriesType.PREDICTIONS, SeriesType.BG_PARABOLA, SeriesType.LEARNED_EXPECTED, SeriesType.LEARNED_PREDICTION,
    SeriesType.IOB, SeriesType.COB, SeriesType.BASAL, SeriesType.ACTIVITY
)

/**
 * autoISF build: the top of the graph card on a phone, from the user's concept: the title with a button to the history
 * browser, the buttons for the time range, and one row of switches for the layers. Each switch is the legend of its
 * layer too: it has the colour and the line of the layer, and a tap shows or hides it. The legend used to be two rows
 * of labels that could not be changed, and one curve (activity) was not in it at all.
 *
 * [compact] puts the range buttons and the history button in one row without the title, for a turned phone and a
 * tablet, where the graph needs the height.
 */
@Composable
internal fun GraphCardHeader(
    rangeMinutes: Double,
    onRangeSelected: (Double) -> Unit,
    bgOverlays: List<SeriesType>,
    onOpenHistory: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** autoISF build: true while the personal prediction plugin is on; only then its two layers have switches */
    learnedAvailable: Boolean = false,
    /** autoISF build: shows or hides a layer. Null in simple mode: the switches then only show what is drawn. */
    onToggleLayer: ((SeriesType) -> Unit)? = null,
    compact: Boolean = false
) {
    val historyButton: @Composable () -> Unit = {
        if (onOpenHistory != null) {
            IconButton(onClick = onOpenHistory) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = stringResource(UiStrings.graph_card_history),
                    // Set here: the card is not a Surface, so the content colour would be the default black in dark mode
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (!compact) {
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = AapsSpacing.medium)
                    .weight(1f)
            )
            historyButton()
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        modifier = (if (compact) modifier else Modifier).fillMaxWidth()
    ) {
        for (minutes in GRAPH_CARD_RANGES_MINUTES) {
            RangeButton(
                label = stringResource(UiStrings.graph_range_hours, (minutes / 60).toInt()),
                selected = minutes == rangeMinutes,
                onClick = { onRangeSelected(minutes) },
                modifier = Modifier.weight(1f)
            )
        }
        if (compact) historyButton()
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        for (layer in GRAPH_CARD_LAYERS) {
            if (layer in LEARNED_LAYERS && !learnedAvailable) continue
            LayerSwitch(
                style = layerStyle(layer),
                label = layerLabel(layer),
                selected = layer in bgOverlays,
                onClick = onToggleLayer?.let { toggle -> { toggle(layer) } }
            )
        }
    }
}

private val LEARNED_LAYERS = setOf(SeriesType.LEARNED_EXPECTED, SeriesType.LEARNED_PREDICTION)

private data class LayerStyle(val color: Color, val mark: LegendMark)

@Composable
private fun layerStyle(layer: SeriesType): LayerStyle = when (layer) {
    SeriesType.PREDICTIONS        -> LayerStyle(AapsTheme.generalColors.iobPrediction, LegendMark.DASHED)
    SeriesType.BG_PARABOLA        -> LayerStyle(AapsTheme.generalColors.bgParabola, LegendMark.LINE)
    SeriesType.LEARNED_EXPECTED   -> LayerStyle(AapsTheme.generalColors.learnedPrediction, LegendMark.LINE)
    SeriesType.LEARNED_PREDICTION -> LayerStyle(AapsTheme.generalColors.learnedPrediction, LegendMark.DASHED)
    SeriesType.IOB                -> LayerStyle(AapsTheme.generalColors.iobPrediction, LegendMark.SQUARE)
    SeriesType.COB                -> LayerStyle(AapsTheme.generalColors.cobPrediction, LegendMark.SQUARE)
    SeriesType.BASAL              -> LayerStyle(AapsTheme.elementColors.tempBasal, LegendMark.SQUARE)
    SeriesType.ACTIVITY           -> LayerStyle(AapsTheme.elementColors.activity, LegendMark.LINE)
    else                          -> LayerStyle(MaterialTheme.colorScheme.onSurfaceVariant, LegendMark.LINE)
}

@Composable
private fun layerLabel(layer: SeriesType): String = when (layer) {
    SeriesType.PREDICTIONS        -> stringResource(UiStrings.graph_layer_predictions)
    SeriesType.BG_PARABOLA        -> stringResource(UiStrings.graph_layer_parabola)
    SeriesType.LEARNED_EXPECTED   -> stringResource(UiStrings.graph_layer_gru)
    SeriesType.LEARNED_PREDICTION -> stringResource(UiStrings.graph_layer_linear)
    SeriesType.IOB                -> stringResource(CoreUiStrings.iob)
    SeriesType.COB                -> stringResource(CoreUiStrings.cob)
    SeriesType.BASAL              -> stringResource(UiStrings.basal_card)
    SeriesType.ACTIVITY           -> stringResource(UiStrings.graph_layer_activity)
    else                          -> layer.name
}

/** autoISF build: one switch of a layer. Off, its mark is faded, so the row still reads as the legend of what is drawn. */
@Composable
private fun LayerSwitch(style: LayerStyle, label: String, selected: Boolean, onClick: (() -> Unit)?) {
    FilterChip(
        selected = selected,
        onClick = { onClick?.invoke() },
        enabled = onClick != null || selected,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = { LegendMarkIcon(if (selected) style.color else style.color.copy(alpha = 0.35f), style.mark) }
    )
}

/**
 * autoISF build: one range button ("3 h"). Not a FilterChip: a chip keeps about 32 dp of padding around its label,
 * so with a big system font on a narrow screen (Samsung "display size" and "font size") "12 h" broke into two lines.
 * Here the label always stays on one line and gets smaller when it does not fit.
 */
@Composable
private fun RangeButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelLarge
    Surface(
        selected = selected,
        onClick = onClick,
        shape = FilterChipDefaults.shape,
        color = if (selected) colors.secondaryContainer else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier
            .heightIn(min = FilterChipDefaults.Height)
            .semantics { role = Role.RadioButton }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = AapsSpacing.small)) {
            BasicText(
                text = label,
                style = labelStyle.copy(
                    color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = RANGE_LABEL_MIN_SIZE, maxFontSize = labelStyle.fontSize, stepSize = 1.sp)
            )
        }
    }
}

/** The smallest size a range label shrinks to before it is cut. */
private val RANGE_LABEL_MIN_SIZE = 9.sp

private enum class LegendMark { LINE, DASHED, SQUARE }

@Composable
private fun LegendMarkIcon(color: Color, mark: LegendMark) {
    Canvas(modifier = Modifier.size(width = 16.dp, height = 10.dp)) {
        val y = size.height / 2
        when (mark) {
            LegendMark.SQUARE -> drawRect(color)
            LegendMark.LINE   -> drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            LegendMark.DASHED -> drawLine(
                color, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))
            )
        }
    }
}
