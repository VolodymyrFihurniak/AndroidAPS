package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalClientRole
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.icons.IcQuestion
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * autoISF build: the row of cards under the main BG card on a phone, from the concept of the user: basal, bolus and
 * loop. IOB and COB are in the main card, so they are not repeated here. Each card: an icon with its title, the value,
 * and a smaller line under it.
 *
 * - Basal: tap opens the basal details.
 * - Bolus: the advice from the last algorithm run (see [BolusRecommendation]). It is never given from here: tap
 *   opens the Insulin dialog, which shows the advice with its numbers, and the user enters and confirms the dose.
 * - Loop: tap opens the running mode. Its second line is the autosens ratio, which opens the sensitivity dialog.
 */
@Composable
fun OverviewCardRow(
    basalUiState: BasalUiState,
    sensitivityUiState: SensitivityUiState,
    bolusRecommendation: BolusRecommendation,
    loopUiState: LoopUiState,
    runningMode: RM.Mode?,
    runningModeText: String,
    commandsAllowed: Boolean,
    onNavigate: (NavigationRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBasalDetails by remember { mutableStateOf(false) }
    var showSensitivityDialog by remember { mutableStateOf(false) }
    val warning = AapsTheme.generalColors.statusWarning
    val role = LocalClientRole.current
    val runningModeAllowed = commandsAllowed && role.allows(ElementType.RUNNING_MODE.role)
    val insulinAllowed = commandsAllowed && role.allows(ElementType.INSULIN.role)
    Row(
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        // All cards as high as the highest
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        if (basalUiState.visible) {
            MetricCell(
                icon = basalUiState.direction.toTbrState().toIcon(),
                color = AapsTheme.elementColors.tempBasal,
                title = stringResource(UiStrings.basal_card),
                value = basalUiState.rateText,
                label = basalUiState.statusText,
                labelColor = if (basalUiState.isWarning) warning else null,
                onClick = { showBasalDetails = true },
                modifier = Modifier.weight(1.2f)
            )
        }
        BolusCell(
            advice = bolusRecommendation,
            onClick = if (insulinAllowed) ({ onNavigate(NavigationRequest.Element(ElementType.INSULIN)) }) else null,
            modifier = Modifier.weight(1f)
        )
        if (runningModeText.isNotEmpty()) {
            MetricCell(
                icon = runningMode?.toIcon() ?: IcQuestion,
                color = runningMode?.toColor() ?: MaterialTheme.colorScheme.outline,
                title = stringResource(CoreUiStrings.loop),
                value = runningModeText,
                label = loopUiState.lastRunText,
                labelColor = if (loopUiState.isOld) warning else null,
                onClick = if (runningModeAllowed) ({ onNavigate(NavigationRequest.Element(ElementType.RUNNING_MODE)) }) else null,
                // The autosens ratio, from the Sensitivity card this card row had before
                extraLine = sensitivityUiState.asText,
                onExtraClick = if (sensitivityUiState.dialogText.isNotEmpty()) ({ showSensitivityDialog = true }) else null,
                modifier = Modifier.weight(1f)
            )
        }
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

/** One card: the icon with the title, the value, a smaller line under it. Value and line scroll instead of being cut. */
@Composable
private fun MetricCell(
    icon: ImageVector,
    color: Color,
    title: String,
    value: String,
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    labelColor: Color? = null,
    /** autoISF build: one more small line, with its own tap */
    extraLine: String = "",
    onExtraClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val content: @Composable () -> Unit = {
        Column(
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall),
            modifier = Modifier.padding(AapsSpacing.large)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(AapsSpacing.chipIconSize))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = AapsSpacing.medium)
                        .basicMarquee()
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor ?: color,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }
            if (extraLine.isNotEmpty()) {
                Text(
                    text = extraLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .then(if (onExtraClick != null) Modifier.clickable(onClick = onExtraClick) else Modifier)
                        .basicMarquee()
                )
            }
        }
    }
    // The title names the card for a screen reader, it is one of its texts
    val cardModifier = modifier.fillMaxHeight()
    if (onClick != null)
        AapsCard(modifier = cardModifier, onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }) { content() }
    else
        AapsCard(modifier = cardModifier) { content() }
}

/** autoISF build: the Bolus card, see [OverviewCardRow]. The longer words are in the Insulin dialog. */
@Composable
private fun BolusCell(advice: BolusRecommendation, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val (value, label) = when (advice.kind) {
        BolusRecommendationKind.SUGGEST       -> stringResource(InterfacesStrings.format_insulin_units, advice.dose) to stringResource(UiStrings.bolus_rec_card_suggest)
        BolusRecommendationKind.LOOP_GIVES    -> stringResource(InterfacesStrings.format_insulin_units, advice.insulinNeeded) to stringResource(UiStrings.bolus_rec_card_loop_gives)
        BolusRecommendationKind.NONE          -> stringResource(UiStrings.bolus_rec_card_none) to stringResource(UiStrings.bolus_rec_card_in_range)
        BolusRecommendationKind.CARBS         -> stringResource(InterfacesStrings.format_carbs, advice.carbs) to stringResource(UiStrings.bolus_rec_card_carbs)
        BolusRecommendationKind.LOW_NOW       -> stringResource(UiStrings.bolus_rec_card_no_bolus) to stringResource(UiStrings.bolus_rec_card_low_now)
        BolusRecommendationKind.LOW_PREDICTED -> stringResource(UiStrings.bolus_rec_card_no_bolus) to stringResource(UiStrings.bolus_rec_card_low_predicted)
        BolusRecommendationKind.NO_DATA       -> stringResource(UiStrings.basal_unknown) to stringResource(UiStrings.bolus_rec_card_no_data)
    }
    MetricCell(
        icon = ElementType.INSULIN.icon(),
        color = ElementType.INSULIN.color(),
        title = stringResource(UiStrings.bolus_card),
        value = value,
        label = label,
        labelColor = if (advice.kind.isWarning()) AapsTheme.generalColors.statusWarning else null,
        onClick = onClick,
        modifier = modifier
    )
}
