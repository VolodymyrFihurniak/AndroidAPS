package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * autoISF build: IOB, COB, basal and sensitivity in one row across the whole width, each an icon over its value,
 * as the row of AAPS 3.4. Used on a phone, where the column beside the BG circle is too narrow for four values: there
 * the basal rate was cut. Taps do what the chips do: IOB opens its split, basal its details, sensitivity its dialog.
 *
 * The cells share the width by what their values need: the basal rate with its unit is the longest.
 */
@Composable
fun OverviewMetricsRow(
    iobUiState: IobUiState,
    cobUiState: CobUiState,
    basalUiState: BasalUiState,
    sensitivityUiState: SensitivityUiState,
    onIobClick: () -> Unit,
    onNavigate: (NavigationRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBasalDetails by remember { mutableStateOf(false) }
    var showSensitivityDialog by remember { mutableStateOf(false) }
    val warning = AapsTheme.generalColors.statusWarning
    val showSensitivity = sensitivityUiState.asText.isNotEmpty() || sensitivityUiState.isfFrom.isNotEmpty()
    Row(
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small),
        // All cells as high as the highest
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        MetricCell(
            icon = ElementType.INSULIN.icon(),
            color = ElementType.INSULIN.color(),
            value = iobUiState.text,
            label = stringResource(CoreUiStrings.iob),
            onClick = onIobClick,
            modifier = Modifier.weight(1f)
        )
        MetricCell(
            icon = ElementType.COB.icon(),
            color = ElementType.COB.color(),
            value = cobUiState.text,
            label = stringResource(CoreUiStrings.cob),
            labelColor = if (cobUiState.carbsReq > 0) warning else null,
            onClick = null,
            modifier = Modifier.weight(0.9f)
        )
        if (basalUiState.visible) {
            MetricCell(
                icon = basalUiState.direction.toTbrState().toIcon(),
                color = AapsTheme.elementColors.tempBasal,
                value = basalUiState.rateText,
                label = basalUiState.statusText,
                labelColor = if (basalUiState.isWarning) warning else null,
                description = stringResource(UiStrings.basal_card),
                onClick = { showBasalDetails = true },
                modifier = Modifier.weight(1.4f)
            )
        }
        if (showSensitivity) {
            MetricCell(
                icon = selectSensIcon(sensitivityUiState.ratio, sensitivityUiState.isEnabled),
                color = ElementType.SENSITIVITY.color(),
                // The ISF in use is the value, the autosens ratio the line under it
                value = sensitivityUiState.isfTo.ifEmpty { sensitivityUiState.asText },
                label = if (sensitivityUiState.isfTo.isNotEmpty()) sensitivityUiState.asText else stringResource(CoreUiStrings.sensitivity),
                description = stringResource(CoreUiStrings.sensitivity),
                onClick = if (sensitivityUiState.dialogText.isNotEmpty()) ({ showSensitivityDialog = true }) else null,
                modifier = Modifier.weight(1.1f)
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

/** One cell: the icon over the value, a smaller line under it. Both lines scroll instead of being cut. */
@Composable
private fun MetricCell(
    icon: ImageVector,
    color: Color,
    value: String,
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    labelColor: Color? = null,
    description: String? = null
) {
    val haptic = LocalHapticFeedback.current
    val content: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = AapsSpacing.small, vertical = AapsSpacing.small)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(AapsSpacing.chipIconSize))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.basicMarquee()
            )
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.basicMarquee()
                )
            }
        }
    }
    val shape = RoundedCornerShape(AapsSpacing.chipCornerRadius)
    val cellModifier = modifier
        .fillMaxHeight()
        .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
    // Same as the chips: no 48dp minimum, the cell keeps the height of its content
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        if (onClick != null)
            Surface(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
                shape = shape,
                color = color.copy(alpha = 0.15f),
                modifier = cellModifier,
                content = content
            )
        else
            Surface(shape = shape, color = color.copy(alpha = 0.15f), modifier = cellModifier, content = content)
    }
}
