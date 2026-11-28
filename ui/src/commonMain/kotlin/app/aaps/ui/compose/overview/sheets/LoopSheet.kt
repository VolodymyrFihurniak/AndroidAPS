package app.aaps.ui.compose.overview.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcAs
import app.aaps.core.ui.compose.icons.IcLoopDisconnected
import app.aaps.core.ui.compose.icons.IcLoopPaused
import app.aaps.core.ui.compose.icons.IcLoopReconnect
import app.aaps.core.ui.compose.icons.IcSmb
import app.aaps.core.ui.compose.icons.IcTbrHigh
import app.aaps.core.ui.compose.icons.IcTbrLow
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.overview.chips.toColor
import app.aaps.ui.compose.overview.chips.toIcon
import kotlin.math.abs

/*
 * autoISF build: the Loop sheet (the design the user approved). From the top: the mode that is in effect when it is not
 * a running loop (a pause, a pump that is off, a loop that is off), the three loop modes as tiles, a pause and a pump off
 * with chips, and the last run with the runs of the last hour. Every button asks for the PIN and the confirmation as
 * before (see TileSheetSource); "No end" is the running mode "Loop disabled", which has no duration.
 */

private val PAUSE_MINUTES = listOf(60, 120, 180)
private val DISCONNECT_MINUTES = listOf(15, 30, 60)

private val ICON_ROW = 18.dp
private val ICON_GROUP = 20.dp
private val ICON_TILE = 28.dp
private val ICON_BUTTON = 20.dp
private val ICON_PLAY = 16.dp
private val TILE_MIN_HEIGHT = 88.dp

/** From this font scale on the chips of a group are two in a row. */
private const val BIG_FONT_SCALE = 1.15f
private const val CHIPS_BIG_FONT = 2

/** A control the user may not use (a client without the right) is drawn like a disabled one. */
private const val NOT_ALLOWED_ALPHA = 0.38f

/** The last run and its hour, when the loop does not run: the numbers are old. */
private const val OLD_RUN_ALPHA = 0.6f
private const val OLD_STRIP_ALPHA = 0.45f

/** The note under the title of the in-effect card, and the border of the chip "No end". */
private const val NOTE_ALPHA = 0.75f
private const val ACCENT_BORDER_ALPHA = 0.45f

/** The strip of runs: the height, the profile line from the top, and the height of the bar for 50 % more or less. */
private val STRIP_HEIGHT = 56.dp
private val STRIP_PROFILE_Y = 30.dp
private val STRIP_REACH_50 = 18.dp
private val STRIP_REACH_MAX = 26.dp
private val STRIP_REACH_MIN = 2.dp
private val STRIP_SMB_Y = 6.dp
private val STRIP_SMB_RADIUS = 4.dp
private const val STRIP_BAR_ALPHA = 0.55f
private const val STRIP_FLAT_ALPHA = 0.5f

@Composable
private fun RM.Mode.label(): String = stringResource(
    when (this) {
        RM.Mode.CLOSED_LOOP       -> CoreUiStrings.closedloop
        RM.Mode.OPEN_LOOP         -> CoreUiStrings.openloop
        RM.Mode.CLOSED_LOOP_LGS   -> CoreUiStrings.lowglucosesuspend
        RM.Mode.DISABLED_LOOP     -> CoreUiStrings.disabled_loop
        RM.Mode.SUPER_BOLUS       -> CoreUiStrings.superbolus
        RM.Mode.DISCONNECTED_PUMP -> InterfacesStrings.pump_disconnected
        RM.Mode.SUSPENDED_BY_PUMP -> CoreUiStrings.pump_suspended
        RM.Mode.SUSPENDED_BY_USER -> InterfacesStrings.loopsuspended
        RM.Mode.SUSPENDED_BY_DST  -> CoreUiStrings.loop_suspended_by_dst
        RM.Mode.RESUME            -> CoreUiStrings.resumeloop
    }
)

@Composable
private fun RM.Mode?.description(): String = stringResource(
    when (this) {
        RM.Mode.CLOSED_LOOP     -> UiStrings.sheet_loop_mode_closed
        RM.Mode.OPEN_LOOP       -> UiStrings.sheet_loop_mode_open
        RM.Mode.CLOSED_LOOP_LGS -> UiStrings.sheet_loop_mode_lgs
        else                    -> UiStrings.sheet_loop_mode_pick
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LoopSheetBody(
    state: LoopSheetState,
    autosensText: String,
    commandsAllowed: Boolean,
    onMode: (RM.Mode, Int) -> Unit,
    onRunNow: () -> Unit,
    onDetails: () -> Unit
) {
    val mode = state.mode
    TileSheetBody(
        icon = mode?.toIcon() ?: ElementType.RUNNING_MODE.icon(),
        iconColor = mode?.toColor() ?: MaterialTheme.colorScheme.outline,
        title = stringResource(CoreUiStrings.loop),
        status = if (state.lastRunText.isNotEmpty()) stringResource(UiStrings.sheet_loop_run, state.lastRunText) else mode?.label().orEmpty()
    ) {
        if (mode != null && state.inEffect != null)
            InEffectCard(mode, state.inEffect, state.canResume, commandsAllowed) { onMode(RM.Mode.RESUME, 0) }
        if (state.loopModes.isNotEmpty())
            SheetCard {
                SheetTitle(stringResource(UiStrings.sheet_loop_mode), mode.description())
                Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium), modifier = Modifier.height(IntrinsicSize.Min)) {
                    state.loopModes.forEach { loopMode ->
                        ModeTile(loopMode, selected = loopMode == mode, enabled = commandsAllowed, modifier = Modifier.weight(1f)) {
                            if (loopMode != mode) onMode(loopMode, 0)
                        }
                    }
                }
            }
        if (state.canSuspend || state.canDisconnect)
            SheetCard {
                Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
                    if (state.canSuspend) {
                        val extend = mode == RM.Mode.SUSPENDED_BY_USER
                        ChipGroup(
                            icon = IcLoopPaused, iconColor = RM.Mode.DISABLED_LOOP.toColor(),
                            title = stringResource(if (extend) UiStrings.sheet_loop_pause_more else UiStrings.sheet_loop_pause_group),
                            subtitle = stringResource(if (extend) UiStrings.sheet_loop_more_sub else UiStrings.sheet_loop_pause_sub),
                            labels = PAUSE_MINUTES.map { stringResource(UiStrings.sheet_hours, it / 60) },
                            endLabel = if (state.canStop) stringResource(UiStrings.sheet_loop_no_end) else null,
                            enabled = commandsAllowed,
                            onPick = { onMode(RM.Mode.SUSPENDED_BY_USER, PAUSE_MINUTES[it]) },
                            onEnd = { onMode(RM.Mode.DISABLED_LOOP, 0) }
                        )
                    }
                    if (state.canSuspend && state.canDisconnect) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (state.canDisconnect) {
                        val extend = mode == RM.Mode.DISCONNECTED_PUMP
                        ChipGroup(
                            icon = IcLoopDisconnected, iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            title = stringResource(if (extend) UiStrings.sheet_loop_disconnect_more else UiStrings.sheet_loop_disconnect_group),
                            subtitle = stringResource(if (extend) UiStrings.sheet_loop_more_sub else UiStrings.sheet_loop_disconnect_sub),
                            labels = DISCONNECT_MINUTES.map { minutesText(it) },
                            endLabel = null,
                            enabled = commandsAllowed,
                            onPick = { onMode(RM.Mode.DISCONNECTED_PUMP, DISCONNECT_MINUTES[it]) },
                            onEnd = {}
                        )
                    }
                }
            }
        if (state.lastRun != null || state.canRunNow)
            LastRunCard(state, autosensText, commandsAllowed, onRunNow)
        SheetLink(stringResource(if (state.apsClassName != null) UiStrings.sheet_loop_details else UiStrings.sheet_loop_modes), onDetails)
    }
}

@Composable
private fun minutesText(minutes: Int): String =
    if (minutes % 60 == 0) stringResource(UiStrings.sheet_hours, minutes / 60) else stringResource(CoreUiStrings.format_mins, minutes)

/** What is in effect now instead of the loop: the mode, how long it lasts, and the button that ends it. */
@Composable
private fun InEffectCard(mode: RM.Mode, inEffect: LoopInEffect, canResume: Boolean, commandsAllowed: Boolean, onResume: () -> Unit) {
    SheetCard(MaterialTheme.colorScheme.primaryContainer) {
        SheetTitle(mode.label(), inEffect.detailText, subtitleColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = NOTE_ALPHA))
        inEffect.leftFraction?.let { SheetBar(it, MaterialTheme.colorScheme.primary) }
        if (canResume)
            Button(onClick = onResume, enabled = commandsAllowed, modifier = Modifier
                .fillMaxWidth()
                .padding(top = AapsSpacing.medium)) {
                Icon(IcLoopReconnect, contentDescription = null, modifier = Modifier.size(ICON_BUTTON))
                Spacer(Modifier.width(AapsSpacing.medium))
                Text(stringResource(if (mode == RM.Mode.DISCONNECTED_PUMP) CoreUiStrings.pump_reconnect else CoreUiStrings.resumeloop))
            }
    }
}

/** One loop mode: its icon in the colour of the mode when it runs, and its name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeTile(mode: RM.Mode, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = content,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = TILE_MIN_HEIGHT)
            .alpha(if (enabled) 1f else NOT_ALLOWED_ALPHA)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium - AapsSpacing.extraSmall),
            modifier = Modifier.padding(start = AapsSpacing.small, end = AapsSpacing.small, top = AapsSpacing.large, bottom = AapsSpacing.medium + AapsSpacing.extraSmall)
        ) {
            Icon(mode.toIcon(), contentDescription = null, tint = if (selected) mode.toColor() else content, modifier = Modifier.size(ICON_TILE))
            Text(mode.label(), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

/** A group of chips with an icon and a title: pause the loop, or disconnect the pump. [endLabel] is the red chip for a pause with no end. */
@Composable
private fun ChipGroup(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    labels: List<String>,
    endLabel: String?,
    enabled: Boolean,
    onPick: (Int) -> Unit,
    onEnd: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium - AapsSpacing.extraSmall)) {
        // The icon is centred on the first line of the title, also with a big font
        val titleLine = with(LocalDensity.current) { MaterialTheme.typography.titleSmall.lineHeight.toDp() }
        Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium), verticalAlignment = Alignment.Top) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.height(titleLine)) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(ICON_GROUP))
            }
            SheetTitle(title, subtitle, Modifier.weight(1f))
        }
        // One row of chips; with a big font two chips in a row, so no text has to wrap. A row is as high as its highest chip.
        val perRow = if (LocalDensity.current.fontScale > BIG_FONT_SCALE) CHIPS_BIG_FONT else labels.size + (if (endLabel != null) 1 else 0)
        val chips = labels.mapIndexed { index, text -> LoopChip(text, null) { onPick(index) } } + listOfNotNull(endLabel?.let { LoopChip(it, RM.Mode.DISABLED_LOOP.toColor(), onEnd) })
        Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
            chips.chunked(perRow).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium), modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)) {
                    line.forEach { chip -> TimeChip(chip.text, enabled, Modifier.weight(1f), chip.accent, chip.onClick) }
                    // The last row keeps the width of the chips above it
                    repeat(perRow - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One chip of a [ChipGroup]; [accent] colours the chip that turns the loop off. */
private class LoopChip(val text: String, val accent: Color?, val onClick: () -> Unit)

/** A chip that acts at once, as wide as its share of the row; [accent] colours the text and the border of the chip that turns the loop off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeChip(text: String, enabled: Boolean, modifier: Modifier, accent: Color? = null, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxHeight(),
        label = { Text(text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        colors = if (accent == null) FilterChipDefaults.filterChipColors()
        else FilterChipDefaults.filterChipColors(labelColor = accent, disabledLabelColor = accent.copy(alpha = NOT_ALLOWED_ALPHA)),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = false,
            borderColor = accent?.copy(alpha = ACCENT_BORDER_ALPHA) ?: MaterialTheme.colorScheme.outlineVariant
        )
    )
}

/** The last run: what it asked for, the runs of the last hour, and the button to run now. The numbers are dimmed when the loop does not run. */
@Composable
private fun LastRunCard(state: LoopSheetState, autosensText: String, commandsAllowed: Boolean, onRunNow: () -> Unit) {
    val running = state.mode?.isLoopRunning() ?: true
    val last = state.lastRun
    SheetCard {
        SheetCaption(if (last != null && last.timeText.isNotEmpty()) stringResource(UiStrings.sheet_loop_last_at, last.timeText) else stringResource(UiStrings.sheet_loop_last))
        if (last != null)
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall), modifier = Modifier.alpha(if (running) 1f else OLD_RUN_ALPHA)) {
                if (last.tbrText.isNotEmpty())
                    RunValue(
                        icon = if (last.tbrAboveProfile == false) IcTbrLow else IcTbrHigh,
                        color = when (last.tbrAboveProfile) {
                            true  -> AapsTheme.generalColors.basalIncrease.copy(alpha = 1f)
                            false -> AapsTheme.generalColors.basalDecrease.copy(alpha = 1f)
                            null  -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        label = stringResource(UiStrings.sheet_loop_tbr), value = last.tbrText
                    )
                if (last.smbText.isNotEmpty())
                    RunValue(IcSmb, MaterialTheme.colorScheme.tertiary, stringResource(UiStrings.sheet_loop_smb), last.smbText)
                if (last.tbrText.isEmpty() && last.smbText.isEmpty())
                    RunValue(null, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(UiStrings.sheet_loop_decision), stringResource(UiStrings.sheet_no_change))
                if (autosensText.isNotEmpty())
                    RunValue(IcAs, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(UiStrings.sheet_loop_autosens), autosensText)
            }
        if (!state.runStrip.isEmpty) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = AapsSpacing.small))
            val more = AapsTheme.generalColors.basalIncrease.copy(alpha = 1f)
            val less = AapsTheme.generalColors.basalDecrease.copy(alpha = 1f)
            val smb = MaterialTheme.colorScheme.tertiary
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium + AapsSpacing.extraSmall)) {
                SheetCaption(stringResource(UiStrings.sheet_loop_last_hour))
                Spacer(Modifier.weight(1f))
                SheetCaption(stringResource(UiStrings.sheet_loop_more), more)
                SheetCaption(stringResource(UiStrings.sheet_loop_less), less)
                SheetCaption(stringResource(UiStrings.sheet_loop_smb), smb)
            }
            RunStrip(state.runStrip, dimmed = !running)
        }
        if (state.canRunNow)
            OutlinedButton(onClick = onRunNow, enabled = commandsAllowed, modifier = Modifier
                .fillMaxWidth()
                .padding(top = AapsSpacing.small)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(ICON_PLAY))
                Spacer(Modifier.width(AapsSpacing.medium))
                Text(stringResource(UiStrings.sheet_loop_run_now))
            }
    }
}

/** A value of the last run: [icon] (none keeps the place free), the name on the left and the value on the right. */
@Composable
private fun RunValue(icon: ImageVector?, color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium), modifier = Modifier.heightIn(min = 24.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(ICON_ROW)) else Spacer(Modifier.size(ICON_ROW))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
    }
}

/**
 * The runs of the last hour, like the basal chart: a dashed line for the profile, a bar up (orange) where the algorithm
 * asked for more than the profile and down (blue) where it asked for less, and a diamond (teal) for an SMB.
 */
@Composable
private fun RunStrip(strip: LoopRunStrip, dimmed: Boolean) {
    val more = AapsTheme.generalColors.basalIncrease.copy(alpha = STRIP_BAR_ALPHA)
    val less = AapsTheme.generalColors.basalDecrease.copy(alpha = STRIP_BAR_ALPHA)
    val flat = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = STRIP_FLAT_ALPHA)
    val profile = MaterialTheme.colorScheme.onSurfaceVariant
    val smb = MaterialTheme.colorScheme.tertiary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(STRIP_HEIGHT)
            .alpha(if (dimmed) OLD_STRIP_ALPHA else 1f)
    ) {
        val steps = strip.percent.size.coerceAtLeast(1)
        val slot = size.width / steps
        val gap = 1.dp.toPx()
        val line = STRIP_PROFILE_Y.toPx()
        val reach = STRIP_REACH_MAX.toPx()
        strip.percent.forEachIndexed { i, percent ->
            if (percent != null) {
                val shift = ((percent - 100) / 50f * STRIP_REACH_50.toPx()).coerceIn(-reach, reach)
                val height = maxOf(abs(shift), STRIP_REACH_MIN.toPx())
                drawRect(
                    color = if (percent > 100) more else if (percent < 100) less else flat,
                    topLeft = Offset(i * slot + gap / 2, if (shift >= 0) line - height else line),
                    size = Size(slot - gap, height)
                )
            }
        }
        strip.smb.forEachIndexed { i, units ->
            if (units > 0.0) {
                val centre = Offset(i * slot + slot / 2, STRIP_SMB_Y.toPx())
                val r = STRIP_SMB_RADIUS.toPx()
                val diamond = Path().apply {
                    moveTo(centre.x, centre.y - r)
                    lineTo(centre.x + r, centre.y)
                    lineTo(centre.x, centre.y + r)
                    lineTo(centre.x - r, centre.y)
                    close()
                }
                drawPath(diamond, smb)
            }
        }
        drawLine(profile, Offset(0f, line), Offset(size.width, line), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
    }
    val span = strip.spanMinutes.coerceAtLeast(1)
    SheetTimeLabels(
        listOf(0f to strip.startText) + strip.hours.map { it.index.toFloat() / span to it.label } + (1f to strip.endText)
    )
}
