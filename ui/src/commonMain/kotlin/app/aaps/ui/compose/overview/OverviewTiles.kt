package app.aaps.ui.compose.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalClientRole
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcPluginInsulin
import app.aaps.core.ui.compose.icons.IcPumpBattery
import app.aaps.core.ui.compose.icons.IcPumpCartridge
import app.aaps.core.ui.compose.icons.IcQuestion
import app.aaps.core.ui.compose.icons.IcSmb
import app.aaps.core.ui.compose.icons.IcTarget
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.statusLevelToColor
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.extensions.directionToIcon
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.components.bgSpokenDescription
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.overview.chips.BasalDetailsSheet
import app.aaps.ui.compose.overview.chips.BasalUiState
import app.aaps.ui.compose.overview.chips.BolusRecommendation
import app.aaps.ui.compose.overview.chips.BolusRecommendationKind
import app.aaps.ui.compose.overview.chips.CobUiState
import app.aaps.ui.compose.overview.chips.ExerciseModeChip
import app.aaps.ui.compose.overview.chips.ExerciseModeUiState
import app.aaps.ui.compose.overview.chips.IobUiState
import app.aaps.ui.compose.overview.chips.LoopUiState
import app.aaps.ui.compose.overview.chips.SceneBadge
import app.aaps.ui.compose.overview.chips.SensitivityUiState
import app.aaps.ui.compose.overview.chips.isWarning
import app.aaps.ui.compose.overview.chips.toColor
import app.aaps.ui.compose.overview.chips.toIcon
import app.aaps.ui.compose.overview.chips.toTbrState
import app.aaps.ui.compose.overview.chips.ttReasonIcon
import app.aaps.ui.compose.overview.chips.ttReasonIconColor
import app.aaps.ui.compose.overview.statusLights.StatusItem
import app.aaps.ui.compose.overview.statusLights.StatusUiState

/**
 * autoISF build: the top of the overview as tiles, the design the user chose ("variant C"). Every tile shows one value
 * with its icon and is the button for what belongs to it:
 *
 * - BG (wide): value, trend, age, the changes over 5, 15 and 40 minutes. Opens the BG check.
 * - Target: the temp target, or the profile target when none runs. Opens the temp targets.
 * - Bolus: the advice of the last loop run. Opens the Insulin dialog, where the dose is entered and confirmed.
 * - Carbs (COB), IOB, temp basal, loop (running mode), sensor.
 * - Profile and pump (two wide tiles): the profile with its change and time left; the pump with its reservoir and the
 *   age of cannula and insulin. The pump battery is shown only when it is critical.
 *
 * The sensor tile opens the status sheet (sensor change, fill, battery change, status light settings), which were in
 * the status row before.
 */
@Composable
fun OverviewTiles(
    bgInfo: BgInfoData?,
    timeAgoText: String,
    profileName: String,
    isProfileModified: Boolean,
    profileProgress: Float,
    profileSceneManaged: Boolean,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    tempTargetSceneManaged: Boolean,
    runningMode: RM.Mode?,
    runningModeText: String,
    runningModeSceneManaged: Boolean,
    iobUiState: IobUiState,
    cobUiState: CobUiState,
    basalUiState: BasalUiState,
    loopUiState: LoopUiState,
    sensitivityUiState: SensitivityUiState,
    bolusRecommendation: BolusRecommendation,
    exerciseModeUiState: ExerciseModeUiState,
    onExerciseModeClick: () -> Unit,
    statusState: StatusUiState,
    pumpName: String,
    pumpIcon: ImageVector?,
    showPump: Boolean,
    statusLightsDef: PreferenceSubScreenDef,
    commandsAllowed: Boolean,
    onNavigate: (NavigationRequest) -> Unit,
    onIobClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val role = LocalClientRole.current
    fun allowed(element: ElementType) = commandsAllowed && role.allows(element.role)
    fun open(element: ElementType): (() -> Unit)? = if (allowed(element)) ({ onNavigate(NavigationRequest.Element(element)) }) else null
    var showStatusSheet by rememberSaveable { mutableStateOf(false) }
    var showSensitivityDialog by remember { mutableStateOf(false) }
    var showBasalDetails by remember { mutableStateOf(false) }
    val warning = AapsTheme.generalColors.statusWarning

    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium), modifier = modifier.fillMaxWidth()) {
        TileRow {
            BgTile(bgInfo, timeAgoText, onClick = open(ElementType.BG_CHECK), modifier = Modifier.weight(2f))
            TargetTile(
                text = tempTargetText,
                state = tempTargetState,
                progress = tempTargetProgress,
                reason = tempTargetReason,
                sceneManaged = tempTargetSceneManaged,
                onClick = open(ElementType.TEMP_TARGET_MANAGEMENT),
                // The exercise mode works through a high temp target, so its button is on this tile (a row of its own
                // was an empty line on the phone)
                trailing = if (exerciseModeUiState.visible) ({
                    ExerciseModeChip(enabled = exerciseModeUiState.enabled, onClick = onExerciseModeClick, clickable = commandsAllowed)
                }) else null,
                modifier = Modifier.weight(1f)
            )
        }
        TileRow {
            BolusTile(bolusRecommendation, onClick = open(ElementType.INSULIN), modifier = Modifier.weight(1f))
            Tile(
                icon = ElementType.CARBS.icon(), iconColor = ElementType.CARBS.color(),
                title = stringResource(InterfacesStrings.carbs),
                onClick = open(ElementType.CARBS), modifier = Modifier.weight(1f)
            ) {
                TileValue(cobUiState.text.ifEmpty { stringResource(UiStrings.basal_unknown) })
                TileLine(stringResource(CoreUiStrings.cob))
            }
            Tile(
                // The insulin drop: the syringe is the Bolus tile
                icon = IcSmb, iconColor = AapsTheme.generalColors.iobPrediction,
                title = stringResource(CoreUiStrings.iob),
                onClick = onIobClick, modifier = Modifier.weight(1f)
            ) {
                TileValue(iobUiState.text.ifEmpty { stringResource(UiStrings.basal_unknown) })
            }
        }
        TileRow {
            if (basalUiState.visible) {
                Tile(
                    icon = basalUiState.direction.toTbrState().toIcon(), iconColor = AapsTheme.elementColors.tempBasal,
                    // "Basal", not "Temp basal": the long title did not fit a third of a phone screen
                    title = stringResource(CoreUiStrings.basal),
                    onClick = open(ElementType.TEMP_BASAL), modifier = Modifier.weight(1f)
                ) {
                    TileValue(basalUiState.rateText, AapsTheme.elementColors.tempBasal)
                    // The tile opens the temp basal; its second line opens the basal details, as the basal card did
                    TileLine(
                        basalUiState.statusText,
                        if (basalUiState.isWarning) warning else null,
                        modifier = Modifier.clickable { showBasalDetails = true }
                    )
                }
            }
            Tile(
                icon = runningMode?.toIcon() ?: IcQuestion, iconColor = runningMode?.toColor() ?: MaterialTheme.colorScheme.outline,
                title = stringResource(CoreUiStrings.loop),
                onClick = open(ElementType.RUNNING_MODE),
                sceneManaged = runningModeSceneManaged,
                modifier = Modifier.weight(1f)
            ) {
                TileValue(
                    loopUiState.lastRunText.ifEmpty { stringResource(UiStrings.basal_unknown) },
                    if (loopUiState.isOld) warning else runningMode?.toColor()
                )
                TileLine(runningModeText)
                if (sensitivityUiState.asText.isNotEmpty()) {
                    TileLine(
                        sensitivityUiState.asText,
                        modifier = if (sensitivityUiState.dialogText.isNotEmpty()) Modifier.clickable { showSensitivityDialog = true } else Modifier
                    )
                }
            }
            SensorTile(statusState.sensorStatus, onClick = { showStatusSheet = true }, modifier = Modifier.weight(1f))
        }
        TileRow {
            ProfileTile(
                name = profileName,
                isModified = isProfileModified,
                progress = profileProgress,
                sceneManaged = profileSceneManaged,
                // Stays clickable on an unpaired client: profile management opens read-only there
                onClick = { onNavigate(NavigationRequest.Element(ElementType.PROFILE_MANAGEMENT)) },
                modifier = Modifier.weight(1f)
            )
            PumpTile(
                name = pumpName,
                icon = pumpIcon,
                insulin = statusState.insulinStatus,
                cannula = statusState.cannulaStatus,
                battery = statusState.batteryStatus,
                onClick = if (showPump) ({ onNavigate(NavigationRequest.Element(ElementType.PUMP)) }) else ({ showStatusSheet = true }),
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showStatusSheet) {
        StatusSheet(
            statusState = statusState,
            statusLightsDef = statusLightsDef,
            commandsAllowed = commandsAllowed,
            onNavigate = { showStatusSheet = false; onNavigate(it) },
            onPumpClick = if (showPump) ({ showStatusSheet = false; onNavigate(NavigationRequest.Element(ElementType.PUMP)) }) else null,
            pumpIcon = pumpIcon,
            onDismiss = { showStatusSheet = false }
        )
    }
    if (showBasalDetails) {
        BasalDetailsSheet(
            state = basalUiState,
            onOpenProfiles = { onNavigate(NavigationRequest.Element(ElementType.PROFILE_MANAGEMENT)) },
            onDismiss = { showBasalDetails = false }
        )
    }
    if (showSensitivityDialog) {
        OkCancelDialog(
            title = stringResource(CoreUiStrings.sensitivity),
            message = sensitivityUiState.dialogText,
            onConfirm = { showSensitivityDialog = false },
            onDismiss = { showSensitivityDialog = false }
        )
    }
}

/** One row of tiles, all as high as the highest. */
@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        content = content
    )
}

/**
 * One tile: a line with the icon and the title (and a ">" when it opens something), then its values. [tint] colours
 * the tile, for a state that needs to be seen (a running temp target, a changed profile).
 */
@Composable
private fun Tile(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    sceneManaged: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val colors = CardDefaults.elevatedCardColors(containerColor = tint?.copy(alpha = TILE_TINT_ALPHA)?.compositeOver(base) ?: base)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall),
            modifier = Modifier.padding(TILE_PADDING)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(TITLE_ICON_SIZE))
                Spacer(Modifier.width(AapsSpacing.small))
                // Small grey title, as in the design: the value is what the eye looks for
                FitText(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (sceneManaged) SceneBadge()
                trailing?.invoke()
                if (onClick != null) Chevron()
            }
            content()
        }
    }
    val cardModifier = modifier.fillMaxHeight()
    if (onClick != null) ElevatedCard(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }, modifier = cardModifier, colors = colors, content = body)
    else ElevatedCard(modifier = cardModifier, colors = colors, content = body)
}

@Composable
private fun TileValue(text: String, color: Color? = null) {
    FitText(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
        color = color ?: MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun TileLine(text: String, color: Color? = null, modifier: Modifier = Modifier) {
    if (text.isEmpty()) return
    FitText(text = text, style = MaterialTheme.typography.labelSmall, color = color ?: MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/**
 * The BG tile has no title line, as in the design: the value large, the trend beside it, and under the trend the age of
 * the reading with the changes over 5, 15 and 40 minutes.
 */
@Composable
private fun BgTile(bgInfo: BgInfoData?, timeAgoText: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val color = bgInfo?.bgRange?.toColor() ?: MaterialTheme.colorScheme.outline
    val unknown = stringResource(UiStrings.basal_unknown)
    val description = bgInfo?.let {
        // One stop for a screen reader: value, range, trend, delta and age in one sentence
        bgSpokenDescription(
            bgText = it.bgText,
            range = it.bgRange,
            trend = it.trendDescription,
            deltaText = it.deltaText,
            timeAgo = timeAgoText,
            isOutdated = it.isOutdated
        )
    } ?: stringResource(CoreUiStrings.no_data)
    val colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    val body: @Composable ColumnScope.() -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
            modifier = Modifier
                .padding(TILE_PADDING)
                .semantics(mergeDescendants = true) { contentDescription = description }
        ) {
            BasicText(
                text = bgInfo?.bgText ?: "---",
                style = AapsTheme.typography.bgValue.copy(
                    color = color,
                    textDecoration = if (bgInfo?.isOutdated == true) TextDecoration.LineThrough else TextDecoration.None
                ),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = BG_MIN_SIZE, maxFontSize = AapsTheme.typography.bgValue.fontSize, stepSize = 2.sp)
            )
            Column(modifier = Modifier.weight(1f)) {
                if (bgInfo == null) {
                    FitText(text = stringResource(CoreUiStrings.no_data), style = MaterialTheme.typography.titleSmall, color = color)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        bgInfo.trendArrow?.let {
                            Icon(imageVector = it.directionToIcon(), contentDescription = null, tint = color, modifier = Modifier.size(AapsSpacing.chipIconSize))
                        }
                        FitText(
                            text = bgInfo.trendDescription,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                            color = color,
                            modifier = Modifier.padding(start = AapsSpacing.small)
                        )
                    }
                    FitText(
                        text = stringResource(
                            UiStrings.bg_tile_age,
                            timeAgoText,
                            stringResource(
                                UiStrings.bg_tile_deltas,
                                bgInfo.deltaText ?: unknown,
                                bgInfo.shortAvgDeltaText ?: unknown,
                                bgInfo.longAvgDeltaText ?: unknown
                            )
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onClick != null) Chevron(modifier = Modifier.align(Alignment.Top))
        }
    }
    val cardModifier = modifier.fillMaxHeight()
    if (onClick != null) ElevatedCard(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }, modifier = cardModifier, colors = colors, content = body)
    else ElevatedCard(modifier = cardModifier, colors = colors, content = body)
}

@Composable
private fun Chevron(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(TITLE_ICON_SIZE)
    )
}

@Composable
private fun TargetTile(
    text: String,
    state: TempTargetChipState,
    progress: Float,
    reason: TT.Reason?,
    sceneManaged: Boolean,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier
) {
    val stateColor = when (state) {
        TempTargetChipState.Active   -> reason.ttReasonIconColor()
        TempTargetChipState.Adjusted -> AapsTheme.generalColors.adjusted
        TempTargetChipState.None     -> null
    }
    // "100 (35 min)": the value on the first line, the time left (in brackets) on the second
    val bracket = text.indexOf(" (")
    val value = if (bracket > 0) text.substring(0, bracket) else text
    val left = if (bracket > 0) text.substring(bracket + 2).removeSuffix(")") else ""
    Tile(
        icon = if (state == TempTargetChipState.Active) reason.ttReasonIcon() else IcTarget,
        iconColor = stateColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
        title = stringResource(UiStrings.target_tile),
        onClick = onClick,
        tint = stateColor,
        sceneManaged = sceneManaged,
        trailing = trailing,
        modifier = modifier
    ) {
        TileValue(value.ifEmpty { stringResource(UiStrings.basal_unknown) }, stateColor)
        TileLine(left)
        if (progress > 0f) TileProgress(progress, stateColor ?: MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun BolusTile(advice: BolusRecommendation, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val (value, label) = when (advice.kind) {
        BolusRecommendationKind.SUGGEST       -> stringResource(InterfacesStrings.format_insulin_units, advice.dose) to stringResource(UiStrings.bolus_rec_card_suggest)
        BolusRecommendationKind.LOOP_GIVES    -> stringResource(InterfacesStrings.format_insulin_units, advice.insulinNeeded) to stringResource(UiStrings.bolus_rec_card_loop_gives)
        BolusRecommendationKind.NONE          -> stringResource(UiStrings.bolus_rec_card_none) to stringResource(UiStrings.bolus_rec_card_in_range)
        BolusRecommendationKind.CARBS         -> stringResource(InterfacesStrings.format_carbs, advice.carbs) to stringResource(UiStrings.bolus_rec_card_carbs)
        BolusRecommendationKind.LOW_NOW       -> stringResource(UiStrings.bolus_rec_card_no_bolus) to stringResource(UiStrings.bolus_rec_card_low_now)
        BolusRecommendationKind.LOW_PREDICTED -> stringResource(UiStrings.bolus_rec_card_no_bolus) to stringResource(UiStrings.bolus_rec_card_low_predicted)
        BolusRecommendationKind.NO_DATA       -> stringResource(UiStrings.basal_unknown) to stringResource(UiStrings.bolus_rec_card_no_data)
    }
    Tile(
        icon = ElementType.INSULIN.icon(), iconColor = ElementType.INSULIN.color(),
        title = stringResource(UiStrings.bolus_card),
        onClick = onClick, modifier = modifier
    ) {
        TileValue(value)
        TileLine(label, if (advice.kind.isWarning()) AapsTheme.generalColors.statusWarning else null)
    }
}

@Composable
private fun SensorTile(sensor: StatusItem?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Tile(
        icon = IcCgmInsert, iconColor = sensor?.let { statusColor(it.ageStatus) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
        title = sensor?.label ?: stringResource(CoreUiStrings.sensor_label),
        onClick = onClick, modifier = modifier
    ) {
        TileValue(sensor?.age?.ifEmpty { null } ?: stringResource(UiStrings.basal_unknown), sensor?.let { statusColor(it.ageStatus) })
        if (sensor?.level != null) TileLine(sensor.level, statusColor(sensor.levelStatus))
    }
}

@Composable
private fun ProfileTile(name: String, isModified: Boolean, progress: Float, sceneManaged: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val noProfile = name.isEmpty()
    val stateColor = when {
        noProfile  -> MaterialTheme.colorScheme.error
        isModified -> AapsTheme.generalColors.inProgress
        else       -> null
    }
    // "Profile A(110%) (1h 20min)": the name on the first line, the time left on the second
    val bracket = name.lastIndexOf(" (")
    val value = if (bracket > 0 && name.endsWith(")")) name.substring(0, bracket) else name
    val left = if (bracket > 0 && name.endsWith(")")) name.substring(bracket + 2).removeSuffix(")") else ""
    Tile(
        icon = ElementType.PROFILE_MANAGEMENT.icon(),
        iconColor = stateColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
        title = stringResource(CoreUiStrings.profile),
        onClick = onClick,
        tint = stateColor,
        sceneManaged = sceneManaged,
        modifier = modifier
    ) {
        // "No profile" in normal letters: the all-caps "NO PROFILE SET" filled the whole tile
        TileValue(value.ifEmpty { stringResource(UiStrings.basal_no_profile) }, stateColor)
        TileLine(left)
        if (progress > 0f) TileProgress(progress, stateColor ?: MaterialTheme.colorScheme.primary)
    }
}

/** The pump: reservoir, age of cannula and insulin. The battery only when it is critical, as a warning. */
@Composable
private fun PumpTile(
    name: String,
    icon: ImageVector?,
    insulin: StatusItem?,
    cannula: StatusItem?,
    battery: StatusItem?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Tile(
        icon = icon ?: ElementType.PUMP.icon(), iconColor = ElementType.PUMP.color(),
        title = name.ifEmpty { stringResource(CoreUiStrings.pump) },
        onClick = onClick, modifier = modifier
    ) {
        StatusLine(IcPumpCartridge, insulin?.level ?: stringResource(UiStrings.basal_unknown), insulin?.let { statusColor(it.levelStatus) }, big = true)
        Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
            cannula?.let { StatusLine(IcCannulaChange, it.age, statusColor(it.ageStatus)) }
            insulin?.let { StatusLine(IcPluginInsulin, it.age, statusColor(it.ageStatus)) }
        }
        // Only a critical battery: the AAPS warning level is 51 %, so a half full battery was always on the tile
        val batteryLow = battery != null && (battery.levelStatus == StatusLevel.CRITICAL || battery.ageStatus == StatusLevel.CRITICAL)
        if (batteryLow) {
            val status = if (battery.levelStatus == StatusLevel.CRITICAL) battery.levelStatus else battery.ageStatus
            StatusLine(IcPumpBattery, battery.level ?: battery.age, statusColor(status))
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, color: Color?, big: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(if (big) AapsSpacing.chipIconSize else STATUS_ICON_SIZE)
        )
        Spacer(Modifier.width(AapsSpacing.small))
        if (big) TileValue(text, color) else TileLine(text, color)
    }
}

@Composable
private fun TileProgress(progress: Float, color: Color) {
    LinearProgressIndicator(
        progress = { progress },
        color = color,
        trackColor = color.copy(alpha = 0.3f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AapsSpacing.extraSmall)
    )
}

private fun StatusLevel.isAlarm() = this == StatusLevel.WARNING || this == StatusLevel.CRITICAL

/** The colour of a status, or null for the normal one (the tile then uses its own). */
@Composable
private fun statusColor(level: StatusLevel): Color? = if (level.isAlarm()) statusLevelToColor(level) else null

/**
 * A text that always stays on one line and gets smaller when it does not fit, down to half of its size. With a big
 * system font on a narrow screen (Samsung "font size" and "display size") texts broke into several lines before.
 */
@Composable
internal fun FitText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        // No line height of its own: with the theme's line height the box was a pixel lower than the text once the
        // text got smaller, and the text was cut at the bottom
        style = style.copy(color = color, lineHeight = TextUnit.Unspecified),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = style.fontSize * FIT_MIN_SHARE, maxFontSize = style.fontSize, stepSize = 1.sp),
        modifier = modifier
    )
}

/** The status sheet: what the status row showed when opened (sensor, insulin, cannula, battery, their buttons). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusSheet(
    statusState: StatusUiState,
    statusLightsDef: PreferenceSubScreenDef,
    commandsAllowed: Boolean,
    onNavigate: (NavigationRequest) -> Unit,
    onPumpClick: (() -> Unit)?,
    pumpIcon: ImageVector?,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        OverviewStatusSection(
            sensorStatus = statusState.sensorStatus,
            insulinStatus = statusState.insulinStatus,
            cannulaStatus = statusState.cannulaStatus,
            batteryStatus = statusState.batteryStatus,
            showFill = statusState.showFill,
            showPumpBatteryChange = statusState.showPumpBatteryChange,
            commandsAllowed = commandsAllowed,
            onNavigate = onNavigate,
            statusLightsDef = statusLightsDef,
            expanded = true,
            onExpandedChange = { if (!it) onDismiss() },
            onPumpClick = onPumpClick,
            pumpIcon = pumpIcon,
            modifier = Modifier.padding(horizontal = AapsSpacing.large, vertical = AapsSpacing.medium)
        )
    }
}

/** How strongly a state colours its tile. */
private const val TILE_TINT_ALPHA = 0.18f

/** How small a [FitText] may get, as a share of its normal size. */
private const val FIT_MIN_SHARE = 0.5f

private val BG_MIN_SIZE = 24.sp
private val STATUS_ICON_SIZE = AapsSpacing.chipIconSize * 0.8f
private val TITLE_ICON_SIZE = AapsSpacing.chipIconSize * 0.75f
private val TILE_PADDING = PaddingValues(horizontal = AapsSpacing.medium, vertical = AapsSpacing.small)
