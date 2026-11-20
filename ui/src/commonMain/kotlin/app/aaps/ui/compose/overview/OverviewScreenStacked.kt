package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.aapsClient.AapsClientStatusCard
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.chips.OverviewCardRow
import app.aaps.ui.compose.overview.chips.TodayStatsRow
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.graphs.GraphsSection
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.scenes.ActiveSceneBanner

@Composable
fun OverviewScreenStacked(
    profileName: String,
    isProfileModified: Boolean,
    profileProgress: Float,
    profileSceneManaged: Boolean = false,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    tempTargetSceneManaged: Boolean = false,
    runningMode: RM.Mode?,
    runningModeText: String,
    runningModeRemaining: String,
    runningModeProgress: Float,
    runningModeSceneManaged: Boolean = false,
    smbEnabled: Boolean,
    isSimpleMode: Boolean,
    graphViewModel: GraphViewModel,
    chipsViewModel: ChipsViewModel,
    manageViewModel: ManageViewModel,
    statusViewModel: StatusViewModel,
    statusLightsDef: PreferenceSubScreenDef,
    onNavigate: (NavigationRequest) -> Unit,
    onIobChipClick: () -> Unit,
    paddingValues: PaddingValues,
    activeSceneState: ActiveSceneState? = null,
    sceneExpired: Boolean = false,
    activeSceneChainTargetName: String? = null,
    onEndScene: () -> Unit = {},
    onDismissScene: () -> Unit = {},
    endSceneEnabled: Boolean = true,
    commandsAllowed: Boolean = true,
    formatDuration: (Long) -> String = { ms -> "${(ms / 60000L).toInt()}m" },
    /** The "time to eat" bar, under the scene bar. */
    eatReminderBanner: @Composable () -> Unit = {},
    /** Height of the bars drawn over the content in preview mode (a short window, e.g. split screen); added to the end space. */
    overlaidBarsHeight: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val config = LocalConfig.current
    val bgInfoState by graphViewModel.bgInfoState.collectAsStateWithLifecycle()
    val sensitivityUiState by chipsViewModel.sensitivityUiState.collectAsStateWithLifecycle()
    val iobUiState by chipsViewModel.iobUiState.collectAsStateWithLifecycle()
    val cobUiState by chipsViewModel.cobUiState.collectAsStateWithLifecycle()
    val exerciseModeUiState by chipsViewModel.exerciseModeUiState.collectAsStateWithLifecycle()
    // autoISF build: the basal card next to IOB and COB
    val basalUiState by chipsViewModel.basalUiState.collectAsStateWithLifecycle()
    val loopUiState by chipsViewModel.loopUiState.collectAsStateWithLifecycle()
    val todayStatsUiState by chipsViewModel.todayStatsUiState.collectAsStateWithLifecycle()
    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()

    var statusExpanded by rememberSaveable { mutableStateOf(false) }

    // autoISF build: the BG graph takes the screen height the content leaves free, so there is no empty space under
    // the graphs. The content is measured without that extra height, so adding it changes nothing in the next pass.
    var bgExtraHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier.onSizeChanged { size ->
                    val contentHeight = with(density) { size.height.toDp() } - bgExtraHeight
                    bgExtraHeight = (viewportHeight - contentHeight).coerceAtLeast(0.dp)
                }
            ) {
                ActiveSceneBanner(
                    activeState = activeSceneState,
                    expired = sceneExpired,
                    chainTargetName = activeSceneChainTargetName,
                    onEndClick = onEndScene,
                    onDismiss = onDismissScene,
                    endEnabled = endSceneEnabled,
                    formatDuration = formatDuration
                )
                eatReminderBanner()
                // autoISF build: the main screen of the user's concept. On top a card with what the user sets (profile,
                // temp target, exercise), then the main BG card with IOB and COB, and the cards basal, sensitivity and loop.
                Column(
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                    modifier = Modifier.padding(horizontal = AapsSpacing.large, vertical = AapsSpacing.medium)
                ) {
                    AapsCard(modifier = Modifier.fillMaxWidth()) {
                        OverviewChipsColumn(
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
                            basalUiState = basalUiState,
                            iobUiState = iobUiState,
                            cobUiState = cobUiState,
                            sensitivityUiState = sensitivityUiState,
                            onNavigate = onNavigate,
                            onIobChipClick = onIobChipClick,
                            commandsAllowed = commandsAllowed,
                            exerciseModeUiState = exerciseModeUiState,
                            onExerciseModeClick = chipsViewModel::toggleExerciseMode,
                            // In the cards: IOB and COB in the main card, the rest and the running mode in the card row
                            showMetrics = false,
                            showRunningMode = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AapsSpacing.small, vertical = AapsSpacing.small)
                        )
                    }
                    MainBgCard(
                        bgInfo = bgInfoState.bgInfo,
                        timeAgoText = bgInfoState.timeAgoText,
                        iobText = iobUiState.text,
                        cobText = cobUiState.text,
                        onIobClick = onIobChipClick
                    )
                    OverviewCardRow(
                        basalUiState = basalUiState,
                        sensitivityUiState = sensitivityUiState,
                        loopUiState = loopUiState,
                        runningMode = runningMode,
                        runningModeText = runningModeText,
                        commandsAllowed = commandsAllowed,
                        onNavigate = onNavigate
                    )
                }

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
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = it }
                )

                if (config.AAPSCLIENT) {
                    val nsClientStatus by graphViewModel.nsClientStatusFlow.collectAsStateWithLifecycle()
                    val flavorTint = when {
                        config.AAPSCLIENT3 -> AapsTheme.generalColors.flavorClient3Tint
                        config.AAPSCLIENT2 -> AapsTheme.generalColors.flavorClient2Tint
                        else               -> AapsTheme.generalColors.flavorClient1Tint
                    }
                    AapsClientStatusCard(
                        statusData = nsClientStatus,
                        flavorTint = flavorTint
                    )
                }

                GraphsSection(
                    graphViewModel = graphViewModel, isSimpleMode = isSimpleMode, iobText = iobUiState.text, cobText = cobUiState.text,
                    overlaidBarsHeight = overlaidBarsHeight,
                    bgExtraHeight = bgExtraHeight,
                    cardStyle = true,
                    onOpenHistory = { onNavigate(NavigationRequest.Element(ElementType.HISTORY_BROWSER)) },
                    footer = {
                        // autoISF build: the statistics of today of the user's concept. The treatments are in the bottom menu.
                        TodayStatsRow(
                            state = todayStatsUiState,
                            onOpenStatistics = { onNavigate(NavigationRequest.Element(ElementType.STATISTICS)) },
                            modifier = Modifier.padding(horizontal = AapsSpacing.large, vertical = AapsSpacing.medium)
                        )
                    }
                )
            }
        }
    }
}
