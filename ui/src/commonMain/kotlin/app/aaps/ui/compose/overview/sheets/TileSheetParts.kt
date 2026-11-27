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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsSpacing

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

/** Two buttons side by side, as wide as the sheet. */
@Composable
internal fun SheetButtons(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium), modifier = Modifier.fillMaxWidth(), content = content)
}

/** The height of a sparkline. */
private val SPARKLINE_HEIGHT = 40.dp
