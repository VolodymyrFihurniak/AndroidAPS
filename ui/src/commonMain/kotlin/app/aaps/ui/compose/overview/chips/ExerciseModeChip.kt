package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalAapsScale
import app.aaps.core.ui.compose.icons.IcActivity
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * autoISF: the exercise mode button of aisf 3.2 (`exerciseModeCheckboxIcon`).
 *
 * Shows whether exercise mode ("high temp target raises sensitivity") is on, and switches it on a
 * tap. As in aisf, the button has the warning colour while the mode is on: a high temp target then
 * changes the sensitivity.
 *
 * @see ExerciseModeChipOnPreview
 * @see ExerciseModeChipOffPreview
 */
@Composable
fun ExerciseModeChip(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    clickable: Boolean = true
) {
    val iconColor = if (enabled) AapsTheme.generalColors.statusWarning else MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor = iconColor.copy(alpha = 0.2f)
    val haptic = LocalHapticFeedback.current
    val description = stringResource(UiStrings.exercise_mode)
    val state = stringResource(if (enabled) UiStrings.exercise_mode_on else UiStrings.exercise_mode_off)

    // Same as TbrChip: no 48dp minimum size, so the chip is as wide as its icon
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Surface(
            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            enabled = clickable,
            shape = RoundedCornerShape(AapsSpacing.chipCornerRadius),
            color = containerColor,
            modifier = modifier
                .height(AapsSpacing.chipHeight * LocalAapsScale.current)
                .semantics { stateDescription = state }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = AapsSpacing.medium)
            ) {
                Icon(
                    imageVector = IcActivity,
                    contentDescription = description,
                    tint = iconColor,
                    modifier = Modifier.size(AapsSpacing.chipIconSize)
                )
            }
        }
    }
}
