package app.aaps.ui.compose.overview.chips

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.model.RM
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcLoopClosed
import app.aaps.core.ui.compose.icons.IcLoopDisabled
import app.aaps.core.ui.compose.icons.IcLoopDisconnected
import app.aaps.core.ui.compose.icons.IcLoopLgs
import app.aaps.core.ui.compose.icons.IcLoopOpen
import app.aaps.core.ui.compose.icons.IcLoopPaused
import app.aaps.core.ui.compose.icons.IcLoopPausedDst
import app.aaps.core.ui.compose.icons.IcLoopPausedPump
import app.aaps.core.ui.compose.icons.IcLoopSuperbolus
import app.aaps.core.ui.compose.loopColor

/**
 * Extension to get theme color for RM.Mode.
 * Thin composable wrapper around the shared [loopColor] mapping.
 */
@Composable
internal fun RM.Mode.toColor(): Color = loopColor(AapsTheme.generalColors)

/**
 * Extension to get Compose icon for RM.Mode
 */
internal fun RM.Mode.toIcon(): ImageVector = when (this) {
    RM.Mode.CLOSED_LOOP       -> IcLoopClosed
    RM.Mode.CLOSED_LOOP_LGS   -> IcLoopLgs
    RM.Mode.OPEN_LOOP         -> IcLoopOpen
    RM.Mode.DISABLED_LOOP     -> IcLoopDisabled
    RM.Mode.SUPER_BOLUS       -> IcLoopSuperbolus
    RM.Mode.DISCONNECTED_PUMP -> IcLoopDisconnected
    RM.Mode.SUSPENDED_BY_PUMP -> IcLoopPausedPump
    RM.Mode.SUSPENDED_BY_DST  -> IcLoopPausedDst
    RM.Mode.SUSPENDED_BY_USER -> IcLoopPaused

    RM.Mode.RESUME            -> IcLoopClosed
}
