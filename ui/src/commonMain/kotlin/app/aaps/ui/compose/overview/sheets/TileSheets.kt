package app.aaps.ui.compose.overview.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcPluginInsulin
import app.aaps.core.ui.compose.icons.IcPumpBattery
import app.aaps.core.ui.compose.icons.IcSmb
import app.aaps.core.ui.compose.icons.IcTarget
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.statusLevelToColor
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.extensions.directionToIcon
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.overview.chips.BolusRecommendation
import app.aaps.ui.compose.overview.chips.BolusRecommendationKind
import app.aaps.ui.compose.overview.chips.isWarning
import app.aaps.ui.compose.overview.chips.toColor
import app.aaps.ui.compose.overview.toColor
import app.aaps.ui.compose.overview.chips.toIcon
import app.aaps.ui.compose.overview.statusLights.StatusItem
import app.aaps.ui.compose.overview.statusLights.StatusUiState
import kotlin.math.roundToInt

/*
 * autoISF build: the sheets the overview tiles open (the design the user approved for every tile). Each sheet has a
 * "...Body" that draws it from a ready state, used by the screenshot tests, and a "...Sheet" that reads the state when
 * it opens and puts the body in a bottom sheet. A button closes the sheet and then calls its action, which asks for
 * the PIN and the confirmation (see TileSheetSource).
 */

/** Reads a sheet state once, when the sheet opens. */
@Composable
private fun <T> loaded(load: suspend () -> T): T? = produceState<T?>(null) { value = load() }.value

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> LoadingSheet(load: suspend () -> T, onDismiss: () -> Unit, body: @Composable (T) -> Unit) {
    val state = loaded(load)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        state?.let { body(it) }
    }
}

private val PERCENT_CHOICES = listOf(50, 80, 120, 150)
private val TBR_MINUTES = listOf(30, 60, 120, 240)
private val PAUSE_MINUTES = listOf(60, 120, 180)
private val DISCONNECT_MINUTES = listOf(15, 30, 60)
private val PROFILE_PERCENT = listOf(80, 90, 100, 110, 120)
private val PROFILE_MINUTES = listOf(0, 60, 120, 240)
private val CARB_STEPS = listOf(5, 10, 20, 50)
private val CARB_TIMES = listOf(0, 15, 30, -15)
private val INSULIN_STEPS = listOf(0.5, 1.0, 2.0)

@Composable
private fun durationText(minutes: Int): String = when {
    minutes == 0       -> stringResource(UiStrings.sheet_no_end)
    minutes % 60 == 0  -> stringResource(UiStrings.sheet_hours, minutes / 60)
    else               -> stringResource(CoreUiStrings.format_mins, minutes)
}

@Composable
private fun offsetText(minutes: Int): String = when {
    minutes == 0 -> stringResource(UiStrings.sheet_now)
    minutes > 0  -> stringResource(UiStrings.sheet_later, minutes)
    else         -> stringResource(UiStrings.sheet_earlier, -minutes)
}

private fun Double.roundTo(step: Double): Double = (this / step).roundToInt() * step

/** The value with as many decimals as [step] needs: 132, 7.2, 1.50. Digits only, the unit is a resource. */
private fun Double.text(step: Double): String {
    val decimals = when {
        step >= 1.0 -> 0
        step >= 0.1 -> 1
        else        -> 2
    }
    if (decimals == 0) return roundToInt().toString()
    var factor = 1
    repeat(decimals) { factor *= 10 }
    val scaled = (this * factor).roundToInt()
    val whole = scaled / factor
    val fraction = (scaled % factor).toString().padStart(decimals, '0')
    return "$whole.$fraction"
}

// ----- BG

@Composable
internal fun BgSheetBody(
    bgInfo: BgInfoData?,
    timeAgoText: String,
    sensorName: String,
    state: BgSheetState,
    commandsAllowed: Boolean,
    onSave: (Double) -> Unit,
    onCalibrate: (Double) -> Unit,
    onHistory: () -> Unit
) {
    val color = bgInfo?.bgRange?.toColor() ?: MaterialTheme.colorScheme.outline
    var value by remember(state) { mutableDoubleStateOf(state.startValue) }
    val unit = stringResource(if (state.units == GlucoseUnit.MGDL) CoreUiStrings.mgdl else CoreUiStrings.mmol)
    TileSheetBody(
        icon = bgInfo?.trendArrow?.directionToIcon() ?: ElementType.BG_CHECK.icon(),
        iconColor = color,
        title = stringResource(UiStrings.sheet_bg_title),
        status = listOf(sensorName, timeAgoText).filter { it.isNotEmpty() }.joinToString(stringResource(UiStrings.sheet_separator))
    ) {
        SheetCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                Text(text = bgInfo?.bgText ?: "---", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = color)
                SheetTitle(
                    title = listOfNotNull(unit, bgInfo?.trendDescription).joinToString(stringResource(UiStrings.sheet_separator)),
                    subtitle = bgInfo?.let { stringResource(UiStrings.bg_tile_deltas, it.deltaText ?: "-", it.shortAvgDeltaText ?: "-", it.longAvgDeltaText ?: "-") }.orEmpty(),
                    titleColor = color
                )
            }
            SheetSparkline(state.lastHourMgdl, color)
            SheetCaption(state.inRangePercent?.let { stringResource(UiStrings.sheet_bg_last_hour, it) } ?: stringResource(UiStrings.activity_last_hour))
        }
        SheetCard {
            SheetTitle(stringResource(UiStrings.sheet_bg_meter), stringResource(UiStrings.sheet_bg_meter_sub))
            SheetStepper(
                value = value.text(state.step), unit = unit, enabled = commandsAllowed,
                onMinus = { value = (value - state.step).coerceAtLeast(state.step).roundTo(state.step) },
                onPlus = { value = (value + state.step).roundTo(state.step) }
            )
            SheetButtons {
                Button(onClick = { onSave(value) }, enabled = commandsAllowed, modifier = Modifier.weight(1f)) { Text(stringResource(UiStrings.sheet_save)) }
                if (state.canCalibrate)
                    OutlinedButton(onClick = { onCalibrate(value) }, enabled = commandsAllowed, modifier = Modifier.weight(1f)) { Text(stringResource(CoreUiStrings.calibration)) }
            }
        }
        SheetLink(stringResource(UiStrings.sheet_bg_history), onHistory)
    }
}

// ----- Target

@Composable
internal fun TargetSheetBody(state: TargetSheetState, commandsAllowed: Boolean, onStart: (String) -> Unit, onCancel: () -> Unit, onCustom: () -> Unit, onAll: () -> Unit) {
    TileSheetBody(
        icon = IcTarget,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = stringResource(UiStrings.target_tile),
        status = if (state.profileTargetText.isNotEmpty()) stringResource(UiStrings.sheet_target_now, state.profileTargetText) else ""
    ) {
        if (state.runningText.isNotEmpty())
            SheetCard(MaterialTheme.colorScheme.primaryContainer) {
                SheetTitleRow(stringResource(UiStrings.sheet_target_running), state.runningText) {
                    OutlinedButton(onClick = onCancel, enabled = commandsAllowed) { Text(stringResource(CoreUiStrings.cancel)) }
                }
            }
        SheetCard {
            SheetTitle(stringResource(UiStrings.sheet_target_temp), stringResource(UiStrings.sheet_target_temp_sub))
            // Two cards in a row: the first three presets and "Custom"
            val cards: List<Triple<String, String, () -> Unit>> = state.presets.take(PRESET_CARDS).map { Triple(it.name, it.text, { onStart(it.id) }) } +
                Triple(stringResource(UiStrings.sheet_target_custom), stringResource(UiStrings.sheet_target_custom_sub), onCustom)
            val running = state.presets.take(PRESET_CARDS).indexOfFirst { it.running }
            cards.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small), modifier = Modifier.fillMaxWidth()) {
                    pair.forEachIndexed { col, (name, text, action) ->
                        val selected = row * 2 + col == running
                        OutlinedButton(onClick = action, enabled = commandsAllowed, modifier = Modifier.weight(1f)) {
                            SheetTitle(
                                name, text, Modifier.fillMaxWidth(),
                                titleColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (pair.size == 1) Row(Modifier.weight(1f)) {}
                }
            }
        }
        if (state.profileTargetText.isNotEmpty())
            SheetCard {
                SheetValue(stringResource(UiStrings.sheet_target_profile_now), state.profileTargetText)
                if (state.nextChangeTime != null && state.nextChangeText != null)
                    SheetValue(stringResource(UiStrings.sheet_target_from, state.nextChangeTime), state.nextChangeText)
            }
        SheetLink(stringResource(UiStrings.sheet_target_all), onAll)
    }
}

private const val PRESET_CARDS = 3

// ----- Bolus

@Composable
internal fun BolusSheetBody(
    advice: BolusRecommendation,
    state: BolusSheetState,
    commandsAllowed: Boolean,
    onGive: (Double, Boolean) -> Unit,
    onCalculator: () -> Unit
) {
    var amount by remember(state) { mutableDoubleStateOf(0.0) }
    var recordOnly by remember { mutableStateOf(false) }
    val warning = AapsTheme.generalColors.statusWarning
    TileSheetBody(
        icon = ElementType.INSULIN.icon(),
        iconColor = ElementType.INSULIN.color(),
        title = stringResource(UiStrings.bolus_card),
        status = if (state.lastBolusText.isNotEmpty()) stringResource(UiStrings.sheet_last, state.lastBolusText) else ""
    ) {
        // The advice of the loop. Only open loop (or a loop without SMB) gets a button: in closed loop the loop gives
        // the insulin itself, and a manual bolus on top would be given twice.
        val suggest = advice.kind == BolusRecommendationKind.SUGGEST
        SheetCard(if (suggest) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
            val (title, note) = when (advice.kind) {
                BolusRecommendationKind.SUGGEST       -> stringResource(UiStrings.sheet_advice, stringResource(InterfacesStrings.format_insulin_units, advice.dose)) to ""
                BolusRecommendationKind.LOOP_GIVES    ->
                    stringResource(UiStrings.sheet_advice, stringResource(InterfacesStrings.format_insulin_units, advice.insulinNeeded)) to stringResource(UiStrings.sheet_advice_loop)
                BolusRecommendationKind.NONE          -> stringResource(UiStrings.bolus_rec_card_none) to stringResource(UiStrings.bolus_rec_card_in_range)
                BolusRecommendationKind.CARBS         -> stringResource(UiStrings.bolus_rec_card_carbs) to stringResource(InterfacesStrings.format_carbs, advice.carbs)
                BolusRecommendationKind.LOW_NOW       -> stringResource(UiStrings.bolus_rec_card_no_bolus) to stringResource(UiStrings.bolus_rec_card_low_now)
                BolusRecommendationKind.LOW_PREDICTED -> stringResource(UiStrings.bolus_rec_card_no_bolus) to stringResource(UiStrings.bolus_rec_card_low_predicted)
                BolusRecommendationKind.NO_DATA       -> stringResource(UiStrings.bolus_rec_card_no_data) to ""
            }
            SheetTitle(title, state.reasonText, titleColor = if (advice.kind.isWarning()) warning else Color.Unspecified)
            SheetCaption(note, if (advice.kind.isWarning()) warning else MaterialTheme.colorScheme.primary)
            if (suggest)
                Button(onClick = { onGive(advice.dose, false) }, enabled = commandsAllowed, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(UiStrings.sheet_give, stringResource(InterfacesStrings.format_insulin_units, advice.dose)))
                }
        }
        SheetCard {
            SheetTitle(stringResource(UiStrings.sheet_other_dose))
            SheetStepper(
                value = amount.text(state.step.coerceAtMost(0.01)),
                unit = stringResource(UiStrings.sheet_units), enabled = commandsAllowed,
                onMinus = { amount = (amount - state.step).coerceAtLeast(0.0).roundTo(state.step) },
                onPlus = { amount = (amount + state.step).coerceAtMost(state.maxBolus).roundTo(state.step) }
            )
            SheetChips(INSULIN_STEPS.map { "+" + (if (it % 1.0 == 0.0) it.roundToInt().toString() else it.text(0.1)) } + stringResource(UiStrings.sheet_record_only), if (recordOnly) INSULIN_STEPS.size else -1, commandsAllowed) { i ->
                if (i < INSULIN_STEPS.size) amount = (amount + INSULIN_STEPS[i]).coerceAtMost(state.maxBolus).roundTo(state.step)
                else recordOnly = !recordOnly
            }
        }
        SheetButtons {
            OutlinedButton(onClick = onCalculator, enabled = commandsAllowed, modifier = Modifier.weight(1f)) { Text(stringResource(UiStrings.sheet_calculator)) }
            OutlinedButton(onClick = { onGive(amount, recordOnly) }, enabled = commandsAllowed && amount > 0.0, modifier = Modifier.weight(1f)) {
                Text(stringResource(if (recordOnly) UiStrings.sheet_record else UiStrings.sheet_give, stringResource(InterfacesStrings.format_insulin_units, amount)))
            }
        }
    }
}

// ----- Carbs

@Composable
internal fun CarbsSheetBody(state: CarbsSheetState, commandsAllowed: Boolean, onEnter: (Int, Int, Boolean) -> Unit, onCalculator: () -> Unit) {
    var grams by remember(state) { mutableIntStateOf(0) }
    var offset by remember { mutableIntStateOf(0) }
    var hypo by remember { mutableStateOf(false) }
    val carbsColor = ElementType.CARBS.color()
    TileSheetBody(
        icon = ElementType.CARBS.icon(),
        iconColor = carbsColor,
        title = stringResource(InterfacesStrings.carbs),
        status = if (state.cobText.isNotEmpty()) stringResource(UiStrings.sheet_cob, state.cobText) else ""
    ) {
        if (state.cobGrams != null && state.lastMealGrams != null && state.lastMealGrams > 0) {
            SheetCard {
                SheetValue(
                    stringResource(UiStrings.sheet_carbs_absorbing),
                    stringResource(UiStrings.sheet_of, state.cobText, stringResource(InterfacesStrings.format_carbs, state.lastMealGrams))
                )
                SheetBar((state.cobGrams / state.lastMealGrams).toFloat(), carbsColor)
                SheetCaption(stringResource(UiStrings.sheet_carbs_last_meal, state.lastMealText))
            }
        }
        SheetCard {
            SheetTitle(stringResource(UiStrings.sheet_carbs_enter))
            SheetStepper(
                value = grams.toString(), unit = stringResource(UiStrings.sheet_grams), enabled = commandsAllowed,
                onMinus = { grams = (grams - 1).coerceAtLeast(0) },
                onPlus = { grams = (grams + 1).coerceAtMost(state.maxCarbs) }
            )
            SheetChips(CARB_STEPS.map { "+$it" }, -1, commandsAllowed) { grams = (grams + CARB_STEPS[it]).coerceAtMost(state.maxCarbs) }
            SheetCaption(stringResource(UiStrings.sheet_when))
            SheetChips(CARB_TIMES.map { offsetText(it) }, CARB_TIMES.indexOf(offset), commandsAllowed) { offset = CARB_TIMES[it] }
            SheetSwitch(stringResource(UiStrings.sheet_carbs_hypo), state.hypoTargetText, hypo, commandsAllowed) { hypo = it }
        }
        SheetButtons {
            Button(onClick = { onEnter(grams, offset, hypo) }, enabled = commandsAllowed && grams > 0, modifier = Modifier.weight(1f)) {
                Text(stringResource(UiStrings.sheet_carbs_save, stringResource(InterfacesStrings.format_carbs, grams)))
            }
            OutlinedButton(onClick = onCalculator, enabled = commandsAllowed, modifier = Modifier.weight(1f)) { Text(stringResource(UiStrings.sheet_calculator)) }
        }
    }
}

// ----- IOB

@Composable
internal fun IobSheetBody(state: IobSheetState, onDetails: () -> Unit) {
    val insulinColor = ElementType.INSULIN.color()
    TileSheetBody(
        icon = IcSmb,
        iconColor = AapsTheme.generalColors.iobPrediction,
        title = stringResource(UiStrings.sheet_iob_title),
        status = stringResource(UiStrings.sheet_iob_status, state.totalText)
    ) {
        SheetCard {
            SheetValue(stringResource(UiStrings.sheet_iob_bolus), state.bolusIobText, insulinColor)
            SheetValue(stringResource(UiStrings.sheet_iob_basal), state.basalIobText, AapsTheme.elementColors.tempBasal)
            SheetValue(stringResource(UiStrings.sheet_iob_activity_now), state.activityText)
        }
        if (state.curve.size > 1)
            SheetCard {
                SheetCaption(stringResource(UiStrings.sheet_iob_curve))
                SheetSparkline(state.curve, insulinColor)
                Row {
                    SheetCaption(stringResource(UiStrings.sheet_now))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                        if (state.inTwoHoursText.isNotEmpty()) SheetCaption(stringResource(UiStrings.sheet_iob_in_2h, state.inTwoHoursText))
                    }
                    SheetCaption(stringResource(UiStrings.sheet_hours, 4))
                }
            }
        if (state.lastDoses.isNotEmpty())
            SheetCard {
                SheetCaption(stringResource(UiStrings.sheet_iob_last))
                state.lastDoses.forEach { (dose, ago) -> SheetValue(dose, ago) }
            }
        SheetLink(stringResource(UiStrings.sheet_iob_details), onDetails)
    }
}

// ----- Basal

@Composable
internal fun BasalSheetBody(icon: ImageVector, state: BasalSheetState, commandsAllowed: Boolean, onStart: (Int, Int) -> Unit, onCancel: () -> Unit, onDay: () -> Unit) {
    var percent by remember { mutableIntStateOf(1) }
    var minutes by remember { mutableIntStateOf(1) }
    val basalColor = AapsTheme.elementColors.tempBasal
    TileSheetBody(
        icon = icon,
        iconColor = basalColor,
        title = stringResource(CoreUiStrings.basal),
        status = if (state.profileBasalText.isNotEmpty()) stringResource(UiStrings.sheet_basal_profile, state.profileBasalText) else ""
    ) {
        SheetCard {
            if (state.runningText.isNotEmpty()) {
                SheetTitleRow(state.runningText, state.runningDetail, titleColor = basalColor) {
                    OutlinedButton(onClick = onCancel, enabled = commandsAllowed) { Text(stringResource(CoreUiStrings.cancel)) }
                }
                SheetBar(state.runningProgress, basalColor)
            } else SheetTitle(stringResource(UiStrings.sheet_basal_none), state.profileBasalText)
        }
        if (state.canSetTempBasal)
            SheetCard {
                SheetTitle(stringResource(UiStrings.sheet_basal_own), if (state.loopMayChange) stringResource(UiStrings.sheet_basal_own_sub) else "")
                SheetChips(PERCENT_CHOICES.map { stringResource(UiStrings.sheet_percent, it) }, percent, commandsAllowed) { percent = it }
                SheetCaption(stringResource(UiStrings.sheet_for))
                SheetChips(TBR_MINUTES.map { durationText(it) }, minutes, commandsAllowed) { minutes = it }
                Button(onClick = { onStart(PERCENT_CHOICES[percent], TBR_MINUTES[minutes]) }, enabled = commandsAllowed, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(UiStrings.sheet_basal_start, PERCENT_CHOICES[percent], durationText(TBR_MINUTES[minutes])))
                }
            }
        SheetLink(stringResource(UiStrings.sheet_basal_day), onDay)
    }
}

// ----- Loop

@Composable
private fun RM.Mode.label(): String = when (this) {
    RM.Mode.CLOSED_LOOP       -> stringResource(CoreUiStrings.closedloop)
    RM.Mode.OPEN_LOOP         -> stringResource(CoreUiStrings.openloop)
    RM.Mode.CLOSED_LOOP_LGS   -> stringResource(CoreUiStrings.lowglucosesuspend)
    RM.Mode.DISCONNECTED_PUMP -> stringResource(InterfacesStrings.pump_disconnected)
    RM.Mode.SUSPENDED_BY_USER -> stringResource(InterfacesStrings.loopsuspended)
    else                      -> name
}

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
    TileSheetBody(
        icon = state.mode?.toIcon() ?: ElementType.RUNNING_MODE.icon(),
        iconColor = state.mode?.toColor() ?: MaterialTheme.colorScheme.outline,
        title = stringResource(CoreUiStrings.loop),
        status = if (state.lastRunText.isNotEmpty()) stringResource(UiStrings.sheet_loop_run, state.lastRunText) else state.mode?.label().orEmpty()
    ) {
        if (state.loopModes.isNotEmpty())
            SheetCard {
                SheetTitle(stringResource(UiStrings.sheet_loop_mode))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    state.loopModes.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = mode == state.mode,
                            onClick = { if (mode != state.mode) onMode(mode, 0) },
                            enabled = commandsAllowed,
                            shape = SegmentedButtonDefaults.itemShape(i, state.loopModes.size),
                            label = { Text(mode.label(), maxLines = 1) }
                        )
                    }
                }
            }
        if (state.canSuspend || state.canDisconnect || state.canResume)
            SheetCard {
                SheetTitle(stringResource(UiStrings.sheet_loop_pause))
                if (state.canResume)
                    Button(onClick = { onMode(RM.Mode.RESUME, 0) }, enabled = commandsAllowed, modifier = Modifier.fillMaxWidth()) { Text(stringResource(CoreUiStrings.resumeloop)) }
                if (state.canSuspend)
                    SheetChips(PAUSE_MINUTES.map { stringResource(UiStrings.sheet_loop_suspend, durationText(it)) }, -1, commandsAllowed) { onMode(RM.Mode.SUSPENDED_BY_USER, PAUSE_MINUTES[it]) }
                if (state.canDisconnect)
                    SheetChips(DISCONNECT_MINUTES.map { stringResource(UiStrings.sheet_loop_disconnect, durationText(it)) }, -1, commandsAllowed) { onMode(RM.Mode.DISCONNECTED_PUMP, DISCONNECT_MINUTES[it]) }
            }
        if (state.decisionText.isNotEmpty() || state.canRunNow)
            SheetCard {
                SheetCaption(stringResource(UiStrings.sheet_loop_last))
                if (state.decisionText.isNotEmpty()) SheetValue(stringResource(UiStrings.sheet_loop_decision), state.decisionText)
                if (autosensText.isNotEmpty()) SheetValue(stringResource(UiStrings.sheet_loop_autosens), autosensText)
                if (state.canRunNow)
                    OutlinedButton(onClick = onRunNow, enabled = commandsAllowed, modifier = Modifier.fillMaxWidth()) { Text(stringResource(UiStrings.sheet_loop_run_now)) }
            }
        SheetLink(stringResource(if (state.apsClassName != null) UiStrings.sheet_loop_details else UiStrings.sheet_loop_modes), onDetails)
    }
}

// ----- Sensor and supplies

@Composable
internal fun SuppliesSheetBody(
    status: StatusUiState,
    onSensor: (() -> Unit)?,
    onCannula: (() -> Unit)?,
    onInsulin: (() -> Unit)?,
    onBattery: (() -> Unit)?,
    onSettings: () -> Unit
) {
    TileSheetBody(
        icon = IcCgmInsert,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = stringResource(UiStrings.sheet_supplies),
        status = ""
    ) {
        status.sensorStatus?.let { SupplyCard(it, IcCgmInsert, onSensor) }
        status.cannulaStatus?.let { SupplyCard(it, IcCannulaChange, onCannula) }
        status.insulinStatus?.let { SupplyCard(it, IcPluginInsulin, onInsulin) }
        status.batteryStatus?.let { SupplyCard(it, IcPumpBattery, onBattery) }
        SheetLink(stringResource(CoreUiStrings.statuslights), onSettings)
    }
}

/** One supply: its age (and level), a bar toward the time to change, and "Change". What is due is in the warning colour. */
@Composable
private fun SupplyCard(item: StatusItem, icon: ImageVector, onChange: (() -> Unit)?) {
    val due = item.ageStatus == StatusLevel.WARNING || item.ageStatus == StatusLevel.CRITICAL ||
        item.levelStatus == StatusLevel.WARNING || item.levelStatus == StatusLevel.CRITICAL
    val worst = if (item.ageStatus.ordinal >= item.levelStatus.ordinal) item.ageStatus else item.levelStatus
    val color = if (due) statusLevelToColor(worst) else AapsTheme.generalColors.bgInRange
    val parts = listOfNotNull(item.age.ifEmpty { null }, item.level)
    val detail = parts.joinToString(stringResource(UiStrings.sheet_separator))
    SheetCard {
        SheetTitleRow(
            title = item.label,
            subtitle = if (due) stringResource(UiStrings.sheet_time_to_change, detail) else detail,
            subtitleColor = if (due) color else null
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = if (due) color else MaterialTheme.colorScheme.onSurfaceVariant)
            if (onChange != null) {
                if (due) Button(onClick = onChange) { Text(stringResource(UiStrings.sheet_change)) }
                else OutlinedButton(onClick = onChange) { Text(stringResource(UiStrings.sheet_change)) }
            }
        }
        val fraction = item.agePercent.takeIf { it >= 0f } ?: item.levelPercent.takeIf { it >= 0f }
        if (fraction != null) SheetBar(fraction, color)
    }
}

// ----- Profile

@Composable
internal fun ProfileSheetBody(state: ProfileSheetState, commandsAllowed: Boolean, onSwitch: (String, Int, Int) -> Unit, onView: () -> Unit) {
    var name by remember(state) { mutableStateOf(state.activeName) }
    var percent by remember(state) { mutableIntStateOf(PROFILE_PERCENT.indexOf(state.percentage).takeIf { it >= 0 } ?: PROFILE_PERCENT.indexOf(100)) }
    var minutes by remember { mutableIntStateOf(0) }
    TileSheetBody(
        icon = ElementType.PROFILE_MANAGEMENT.icon(),
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = stringResource(CoreUiStrings.profile),
        status = listOf(state.activeName, state.activeText).filter { it.isNotEmpty() }.joinToString(stringResource(UiStrings.sheet_separator))
    ) {
        SheetCard {
            SheetTitle(stringResource(UiStrings.sheet_profile_switch_to))
            state.names.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = item == name, onClick = { name = item }, enabled = commandsAllowed)
                    Text(text = item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (item == state.activeName) SheetCaption(stringResource(UiStrings.sheet_profile_active))
                }
            }
        }
        SheetCard {
            SheetTitle(stringResource(UiStrings.sheet_profile_percent), stringResource(UiStrings.sheet_profile_percent_sub))
            SheetChips(PROFILE_PERCENT.map { stringResource(UiStrings.sheet_percent, it) }, percent, commandsAllowed) { percent = it }
            SheetChips(PROFILE_MINUTES.map { durationText(it) }, minutes, commandsAllowed) { minutes = it }
        }
        SheetButtons {
            Button(
                onClick = { onSwitch(name, PROFILE_PERCENT[percent], PROFILE_MINUTES[minutes]) },
                enabled = commandsAllowed && name.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(UiStrings.sheet_switch)) }
            OutlinedButton(onClick = onView, modifier = Modifier.weight(1f)) { Text(stringResource(UiStrings.sheet_profile_view)) }
        }
    }
}

// ----- Pump

@Composable
internal fun PumpSheetBody(
    pumpName: String,
    pumpIcon: ImageVector?,
    status: StatusUiState,
    state: PumpSheetState,
    commandsAllowed: Boolean,
    onRefresh: () -> Unit,
    onHistory: () -> Unit,
    onDisconnect: () -> Unit,
    onPumpScreen: (() -> Unit)?
) {
    val insulinColor = ElementType.INSULIN.color()
    TileSheetBody(
        icon = pumpIcon ?: ElementType.PUMP.icon(),
        iconColor = ElementType.PUMP.color(),
        title = pumpName.ifEmpty { stringResource(CoreUiStrings.pump) },
        status = state.connectionText
    ) {
        SheetCard {
            status.insulinStatus?.level?.let { level ->
                SheetValue(stringResource(UiStrings.sheet_pump_reservoir), level, status.insulinStatus.levelStatus.takeIf { it != StatusLevel.UNSPECIFIED && it != StatusLevel.NORMAL }?.let { statusLevelToColor(it) })
                status.insulinStatus.levelPercent.takeIf { it >= 0f }?.let { SheetBar(1f - it, insulinColor) }
            }
            status.batteryStatus?.let { battery -> SheetValue(stringResource(UiStrings.sheet_pump_battery), battery.level ?: battery.age) }
            if (state.basalText.isNotEmpty()) SheetValue(stringResource(UiStrings.sheet_pump_basal), state.basalText)
            if (state.lastBolusText.isNotEmpty()) SheetValue(stringResource(UiStrings.sheet_pump_last_bolus), state.lastBolusText)
        }
        SheetButtons {
            if (state.canRefresh) OutlinedButton(onClick = onRefresh, enabled = commandsAllowed, modifier = Modifier.weight(1f)) { Text(stringResource(UiStrings.sheet_pump_refresh)) }
            OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f)) { Text(stringResource(UiStrings.sheet_pump_history)) }
        }
        if (state.canDisconnect)
            SheetCard {
                SheetTitleRow(stringResource(UiStrings.sheet_pump_disconnect), stringResource(UiStrings.sheet_pump_disconnect_sub)) {
                    OutlinedButton(onClick = onDisconnect, enabled = commandsAllowed) { Text(stringResource(CoreUiStrings.format_mins, PUMP_DISCONNECT_MINUTES)) }
                }
            }
        if (onPumpScreen != null) SheetLink(stringResource(UiStrings.sheet_pump_screen), onPumpScreen)
    }
}

internal const val PUMP_DISCONNECT_MINUTES = 30
