package app.aaps.ui.compose.overview.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsSpacing
import kotlin.math.roundToInt

/*
 * autoISF build: the parts every tile sheet is built from, so all sheets look the same (the design the user approved,
 * the activity sheet being the first): a header with the icon in the colour of the tile, the title and a short state
 * on the right; then cards, first what is now, then what can be done; a link to the full screen at the end.
 * One filled button per card for the main action; a cancel or second action has an outline; values are picked with
 * chips, and the picked chip is in the primary colour.
 */

/** A tile sheet: [TileSheetBody] in a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TileSheet(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    status: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        TileSheetBody(icon, iconColor, title, status, content)
    }
}

/** The body of a tile sheet, apart from the bottom sheet so a screenshot test can draw it. */
@Composable
internal fun TileSheetBody(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    status: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        modifier = Modifier.padding(start = AapsSpacing.large, end = AapsSpacing.large, bottom = AapsSpacing.large)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor)
            Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (status.isNotEmpty())
                Text(text = status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
        }
        content()
    }
}

/** One card of a sheet. [color] marks a card that is in effect now (primary container). */
@Composable
internal fun SheetCard(color: Color = MaterialTheme.colorScheme.surfaceContainerHigh, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = color, shape = MaterialTheme.shapes.medium) {
        Column(
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.small),
            modifier = Modifier
                .fillMaxWidth()
                .padding(AapsSpacing.medium),
            content = content
        )
    }
}

/** The title of a card with a grey line under it. */
@Composable
internal fun SheetTitle(title: String, subtitle: String = "", modifier: Modifier = Modifier, titleColor: Color = Color.Unspecified, subtitleColor: Color? = null) {
    Column(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = titleColor)
        if (subtitle.isNotEmpty())
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A small grey line, e.g. "Last hour". */
@Composable
internal fun SheetCaption(text: String, color: Color? = null) {
    if (text.isEmpty()) return
    Text(text = text, style = MaterialTheme.typography.labelMedium, color = color ?: MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A card title with a button (or switch) on its right. */
@Composable
internal fun SheetTitleRow(title: String, subtitle: String = "", subtitleColor: Color? = null, titleColor: Color = Color.Unspecified, trailing: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
        SheetTitle(title, subtitle, Modifier.weight(1f), titleColor, subtitleColor)
        trailing()
    }
}

/** A name on the left, its value on the right. */
@Composable
internal fun SheetValue(label: String, value: String, valueColor: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = valueColor ?: MaterialTheme.colorScheme.onSurface)
    }
}

/** Chips to pick one of [options]; [selected] is -1 when none is picked (chips that act at once). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SheetChips(options: List<String>, selected: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
        options.forEachIndexed { index, text ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                enabled = enabled,
                label = { Text(text) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

/** A value with "−" and "+" on its sides. */
@Composable
internal fun SheetStepper(value: String, unit: String, enabled: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
        OutlinedButton(onClick = onMinus, enabled = enabled) { Text("−") }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center, modifier = Modifier.weight(1f)) {
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AapsSpacing.small, bottom = AapsSpacing.extraSmall)
            )
        }
        OutlinedButton(onClick = onPlus, enabled = enabled) { Text("+") }
    }
}

/** A thin bar, e.g. the age of a sensor or the time of a temp basal. */
@Composable
internal fun SheetBar(fraction: Float, color: Color) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        color = color,
        trackColor = color.copy(alpha = 0.25f),
        drawStopIndicator = {},
        modifier = Modifier.fillMaxWidth()
    )
}

/** A switch with its title, e.g. the hypo target of the carbs sheet. */
@Composable
internal fun SheetSwitch(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    SheetTitleRow(title, subtitle) { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) }
}

/** The link to the full screen at the end of a sheet. */
@Composable
internal fun SheetLink(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("$text ›") }
}

/** A line of values, e.g. BG of the last hour; the last point is a dot. */
@Composable
internal fun SheetSparkline(values: List<Double>, color: Color) {
    if (values.size < 2) return
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(SPARKLINE_HEIGHT)
    ) {
        val max = values.max()
        val min = values.min()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val pad = 4.dp.toPx()
        fun point(i: Int) = Offset(
            x = i * (size.width - 2 * pad) / (values.size - 1) + pad,
            y = (size.height - pad - (values[i] - min) / span * (size.height - 2 * pad)).toFloat()
        )
        val path = Path().apply {
            moveTo(point(0).x, point(0).y)
            for (i in 1 until values.size) lineTo(point(i).x, point(i).y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, radius = 3.dp.toPx(), center = point(values.size - 1))
    }
}

/**
 * A time chart of [timeline]: the area under the values, a line for now and the full hours as labels under it, with
 * the clock time of the first and the last point at the ends.
 */
@Composable
internal fun SheetTimelineChart(timeline: SheetTimeline, color: Color) {
    val values = timeline.values
    if (values.size < 2) return
    val nowColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val last = values.size - 1
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
    ) {
        val max = values.max().coerceAtLeast(0.1)
        val min = values.min().coerceAtMost(0.0)
        val pad = 3.dp.toPx()
        fun x(i: Int) = i * size.width / last
        fun y(v: Double) = (size.height - pad - (v - min) / (max - min) * (size.height - 2 * pad)).toFloat()
        drawLine(gridColor, Offset(0f, y(0.0)), Offset(size.width, y(0.0)))
        timeline.ticks.forEach { tick -> drawLine(gridColor, Offset(x(tick.index), 0f), Offset(x(tick.index), size.height)) }
        val line = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1..last) lineTo(x(i), y(values[i]))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(last), y(0.0))
            lineTo(x(0), y(0.0))
            close()
        }
        drawPath(area, color.copy(alpha = AREA_ALPHA))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        val now = timeline.nowIndex.coerceIn(0, last)
        drawLine(nowColor, Offset(x(now), 0f), Offset(x(now), size.height), strokeWidth = 1.dp.toPx())
        drawCircle(color, radius = 3.dp.toPx(), center = Offset(x(now), y(values[now])))
    }
    SheetTimeLabels(
        listOf(0f to timeline.startText) + timeline.ticks.map { it.index.toFloat() / last to it.label } + (1f to timeline.endText)
    )
}

/** The basal of the last hours: the given rate filled, the profile dashed, a line for now. */
@Composable
internal fun SheetBasalChart(chart: BasalChart, color: Color) {
    val points = chart.profile.size
    if (points < 2) return
    val profileColor = MaterialTheme.colorScheme.onSurfaceVariant
    val nowColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
    ) {
        val max = (chart.given.filterNotNull() + chart.profile).max().coerceAtLeast(0.1) * 1.15
        val width = size.width / (points - 1)
        fun x(i: Int) = i * width
        fun y(v: Double) = (size.height - v / max * size.height).toFloat()
        drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height))
        chart.given.forEachIndexed { i, rate ->
            if (rate != null && i < points - 1) {
                val future = i >= chart.nowIndex
                drawRect(color.copy(alpha = if (future) AREA_ALPHA else BAR_ALPHA), topLeft = Offset(x(i), y(rate)), size = Size(width + 0.5f, size.height - y(rate)))
            }
        }
        val profile = Path().apply {
            moveTo(0f, y(chart.profile[0]))
            for (i in 1 until points) {
                lineTo(x(i), y(chart.profile[i - 1]))
                lineTo(x(i), y(chart.profile[i]))
            }
        }
        drawPath(profile, profileColor, style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
        drawLine(nowColor, Offset(x(chart.nowIndex), 0f), Offset(x(chart.nowIndex), size.height), strokeWidth = 1.dp.toPx())
    }
    SheetTimeLabels(chart.ticks.map { it.index.toFloat() / (points - 1) to it.label })
}

/** Labels under a chart, each centred on its place (0..1 of the width) and kept inside the row; a label that would overlap the one before is left out. */
@Composable
internal fun SheetTimeLabels(labels: List<Pair<Float, String>>) {
    val shown = labels.filter { it.second.isNotEmpty() }
    Layout(
        content = { shown.forEach { (_, text) -> Text(text = text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        modifier = Modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            var right = Int.MIN_VALUE
            val gap = 4.dp.roundToPx()
            placeables.forEachIndexed { i, placeable ->
                val x = (shown[i].first * width - placeable.width / 2f).roundToInt().coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                // The last label (the end) always shows; a tick that runs into it is dropped
                val next = placeables.getOrNull(i + 1)?.let { n -> (shown[i + 1].first * width - n.width / 2f).roundToInt().coerceIn(0, (width - n.width).coerceAtLeast(0)) }
                val hitsLast = i == placeables.size - 2 && next != null && x + placeable.width + gap > next
                if (x >= right + gap && !hitsLast) {
                    placeable.place(x, 0)
                    right = x + placeable.width
                }
            }
        }
    }
}

/** Two buttons side by side, as wide as the sheet. */
@Composable
internal fun SheetButtons(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium), modifier = Modifier.fillMaxWidth(), content = content)
}

/** The height of a time chart. */
private val CHART_HEIGHT = 72.dp

/** The fill of a chart area, and of the given basal. */
private const val AREA_ALPHA = 0.25f
private const val BAR_ALPHA = 0.55f

/** The height of a sparkline. */
private val SPARKLINE_HEIGHT = 40.dp
