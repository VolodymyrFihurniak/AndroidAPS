package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcNoTbr
import app.aaps.core.ui.compose.icons.IcTbrHigh
import app.aaps.core.ui.compose.icons.IcTbrLow
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * autoISF build: the basal card of the overview, in the row of [IobChip] and [CobChip] and in their style.
 *
 * The rate in U/h is the main value. The smaller line under it holds the percent of the profile and the status
 * (by profile, TBR with the time left, profile switch, loop request not set yet), or why the rate is not known.
 * That line scrolls when it does not fit, like the COB text. Tap opens the details (`BasalDetailsSheet`).
 *
 * It replaced the icon-only temp basal chip, whose three icons it still shows.
 *
 * @see BasalChipProfilePreview
 * @see BasalChipTbrPreview
 * @see BasalChipNoDataPreview
 */
@Composable
internal fun BasalChip(
    state: BasalUiState,
    onClick: () -> Unit,
    showIcon: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val color = AapsTheme.elementColors.tempBasal
    val statusColor = if (state.isWarning) AapsTheme.generalColors.statusWarning else MaterialTheme.colorScheme.onSurfaceVariant
    // Only the noun, as in IobChip: the texts below are read as they are
    val chipDescription = stringResource(UiStrings.basal_card)
    val direction = stringResource(state.direction.toDescription())
    // Same guard as IobChip: no 48dp minimum, so the chip keeps the height of the row
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Surface(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
            color = color.copy(alpha = 0.2f),
            modifier = modifier
                .heightIn(min = AapsSpacing.chipHeight)
                .semantics {
                    contentDescription = chipDescription
                    stateDescription = direction
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = AapsSpacing.medium, vertical = AapsSpacing.extraSmall)
            ) {
                if (showIcon) {
                    Icon(
                        imageVector = state.direction.toTbrState().toIcon(),
                        // Decorative: the Surface names the chip and the state says the direction
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(AapsSpacing.chipIconSize)
                    )
                }
                Column(modifier = Modifier.padding(start = if (showIcon) AapsSpacing.medium else 0.dp)) {
                    Text(
                        text = state.rateText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = state.statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }
        }
    }
}

private fun BasalDirection.toTbrState(): TbrState = when (this) {
    BasalDirection.HIGHER -> TbrState.HIGH
    BasalDirection.LOWER  -> TbrState.LOW
    BasalDirection.SAME   -> TbrState.NONE
}

/** What the icon means in words, so a screen reader can tell a higher rate from a lower one. */
private fun BasalDirection.toDescription(): TextRef = when (this) {
    BasalDirection.HIGHER -> CoreUiStrings.tbr_state_above_profile
    BasalDirection.LOWER  -> CoreUiStrings.tbr_state_below_profile
    BasalDirection.SAME   -> CoreUiStrings.tbr_state_none
}

internal fun TbrState.toIcon(): ImageVector = when (this) {
    TbrState.HIGH -> IcTbrHigh
    TbrState.LOW  -> IcTbrLow
    TbrState.NONE -> IcNoTbr
}
