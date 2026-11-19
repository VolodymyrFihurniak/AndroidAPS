package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.LocalClientRole
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.overview.chips.BasalDetailsSheet
import app.aaps.ui.compose.overview.chips.BasalUiState
import app.aaps.ui.compose.overview.chips.CobUiState
import app.aaps.ui.compose.overview.chips.ExerciseModeChip
import app.aaps.ui.compose.overview.chips.ExerciseModeUiState
import app.aaps.ui.compose.overview.chips.IobCobChipsRow
import app.aaps.ui.compose.overview.chips.IobUiState
import app.aaps.ui.compose.overview.chips.ProfileChip
import app.aaps.ui.compose.overview.chips.RunningModeChip
import app.aaps.ui.compose.overview.chips.SensitivityUiState
import app.aaps.ui.compose.overview.chips.TempTargetChip

@Composable
fun OverviewChipsColumn(
    runningMode: RM.Mode?,
    runningModeText: String,
    runningModeRemaining: String,
    runningModeProgress: Float,
    runningModeSceneManaged: Boolean = false,
    smbEnabled: Boolean = false,
    profileName: String,
    isProfileModified: Boolean,
    profileProgress: Float,
    profileSceneManaged: Boolean = false,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    tempTargetSceneManaged: Boolean = false,
    basalUiState: BasalUiState,
    iobUiState: IobUiState,
    cobUiState: CobUiState,
    sensitivityUiState: SensitivityUiState,
    onNavigate: (NavigationRequest) -> Unit,
    onIobChipClick: () -> Unit,
    // The command chips (running mode / profile / temp target) open mutating screens — their click is disabled on an
    // unpaired client (same MASTER_OR_PAIRED_CLIENT gate as nav/Manage), while the chip stays visible as status.
    commandsAllowed: Boolean = true,
    // autoISF: the exercise mode button, next to the temp basal chip
    exerciseModeUiState: ExerciseModeUiState = ExerciseModeUiState(),
    onExerciseModeClick: () -> Unit = {},
    /**
     * autoISF build: IOB, COB, basal and sensitivity under the other chips. A phone shows them in a row of their own
     * across the whole width instead (`OverviewMetricsRow`), and passes false.
     */
    showMetrics: Boolean = true,
    /** autoISF build: false on a phone, where the loop card of `OverviewCardRow` shows the running mode. */
    showRunningMode: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        NarrowChips(
            runningMode = runningMode,
            runningModeText = runningModeText,
            runningModeRemaining = runningModeRemaining,
            runningModeProgress = runningModeProgress,
            runningModeSceneManaged = runningModeSceneManaged,
            smbEnabled = smbEnabled,
            profileName = profileName,
            isProfileModified = isProfileModified,
            profileProgress = profileProgress,
            profileSceneManaged = profileSceneManaged,
            tempTargetText = tempTargetText,
            tempTargetState = tempTargetState,
            tempTargetProgress = tempTargetProgress,
            tempTargetReason = tempTargetReason,
            tempTargetSceneManaged = tempTargetSceneManaged,
            onNavigate = onNavigate,
            commandsAllowed = commandsAllowed,
            exerciseModeUiState = exerciseModeUiState,
            onExerciseModeClick = onExerciseModeClick,
            showRunningMode = showRunningMode
        )
        if (showMetrics) MetricChips(basalUiState, iobUiState, cobUiState, sensitivityUiState, onNavigate, onIobChipClick)
    }
}

/** IOB, COB and the basal card in one row, and the sensitivity under them. */
@Composable
private fun MetricChips(
    basalUiState: BasalUiState,
    iobUiState: IobUiState,
    cobUiState: CobUiState,
    sensitivityUiState: SensitivityUiState,
    onNavigate: (NavigationRequest) -> Unit,
    onIobChipClick: () -> Unit
) {
    // autoISF build: the basal card, in the row of IOB and COB, opens its details
    var showBasalDetails by remember { mutableStateOf(false) }
    IobCobChipsRow(
        iobUiState = iobUiState,
        cobUiState = cobUiState,
        onIobChipClick = onIobChipClick,
        basalUiState = basalUiState,
        onBasalChipClick = { showBasalDetails = true }
    )
    if (showBasalDetails) {
        BasalDetailsSheet(
            state = basalUiState,
            onOpenProfiles = { onNavigate(NavigationRequest.Element(ElementType.PROFILE_MANAGEMENT)) },
            onDismiss = { showBasalDetails = false }
        )
    }
    SensitivityChipBlock(
        state = sensitivityUiState,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NarrowChips(
    runningMode: RM.Mode?,
    runningModeText: String,
    runningModeRemaining: String,
    runningModeProgress: Float,
    runningModeSceneManaged: Boolean,
    smbEnabled: Boolean,
    profileName: String,
    isProfileModified: Boolean,
    profileProgress: Float,
    profileSceneManaged: Boolean,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    tempTargetSceneManaged: Boolean,
    onNavigate: (NavigationRequest) -> Unit,
    commandsAllowed: Boolean,
    exerciseModeUiState: ExerciseModeUiState,
    onExerciseModeClick: () -> Unit,
    showRunningMode: Boolean
) {
    val profile: @Composable (Modifier) -> Unit = { modifier ->
        ProfileChip(
            profileName = profileName.ifEmpty { stringResource(CoreUiStrings.no_profile_set) },
            isModified = isProfileModified,
            progress = profileProgress,
            onClick = { onNavigate(NavigationRequest.Element(ElementType.PROFILE_MANAGEMENT)) },
            sceneManaged = profileSceneManaged,
            isNoProfile = profileName.isEmpty(),
            // Stays clickable even on an unpaired client: profile management opens read-only there,
            // so this is a view affordance, not a command one (unlike the running-mode / TT chips).
            modifier = modifier
        )
    }
    val tempTargetAndExercise: @Composable RowScope.() -> Unit = {
        if (tempTargetText.isNotEmpty()) {
            TempTargetChip(
                targetText = tempTargetText,
                state = tempTargetState,
                progress = tempTargetProgress,
                reason = tempTargetReason,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(NavigationRequest.Element(ElementType.TEMP_TARGET_MANAGEMENT)) },
                sceneManaged = tempTargetSceneManaged,
                enabled = commandsAllowed && LocalClientRole.current.allows(ElementType.TEMP_TARGET_MANAGEMENT.role)
            )
        }
        if (exerciseModeUiState.visible) {
            ExerciseModeChip(
                enabled = exerciseModeUiState.enabled,
                onClick = onExerciseModeClick,
                clickable = commandsAllowed
            )
        }
    }
    if (!showRunningMode) {
        // autoISF build: on a phone the running mode is a card, so profile, temp target and exercise fit one row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)
        ) {
            profile(Modifier.weight(1f))
            tempTargetAndExercise()
        }
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (runningModeText.isNotEmpty()) {
            RunningModeChip(
                mode = runningMode,
                text = runningModeText,
                progress = runningModeProgress,
                remaining = runningModeRemaining,
                sceneManaged = runningModeSceneManaged,
                smbEnabled = smbEnabled,
                enabled = commandsAllowed && LocalClientRole.current.allows(ElementType.RUNNING_MODE.role),
                onClick = { onNavigate(NavigationRequest.Element(ElementType.RUNNING_MODE)) }
            )
        }
        profile(Modifier.weight(1f))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)
    ) {
        tempTargetAndExercise()
    }
}

