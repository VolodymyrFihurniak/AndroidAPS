package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.chips.ChipsViewModel
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
    runningModeSceneManaged: Boolean = false,
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
    val iobUiState by chipsViewModel.iobUiState.collectAsStateWithLifecycle()
    val cobUiState by chipsViewModel.cobUiState.collectAsStateWithLifecycle()
    val todayStatsUiState by chipsViewModel.todayStatsUiState.collectAsStateWithLifecycle()

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
                // autoISF build: the cards of the user's concept, shared with the landscape layouts
                OverviewMainCards(
                    profileName = profileName,
                    isProfileModified = isProfileModified,
                    profileProgress = profileProgress,
                    profileSceneManaged = profileSceneManaged,
                    tempTargetText = tempTargetText,
                    tempTargetState = tempTargetState,
                    tempTargetProgress = tempTargetProgress,
                    tempTargetReason = tempTargetReason,
                    tempTargetSceneManaged = tempTargetSceneManaged,
                    runningMode = runningMode,
                    runningModeText = runningModeText,
                    runningModeSceneManaged = runningModeSceneManaged,
                    graphViewModel = graphViewModel,
                    chipsViewModel = chipsViewModel,
                    manageViewModel = manageViewModel,
                    statusViewModel = statusViewModel,
                    statusLightsDef = statusLightsDef,
                    onNavigate = onNavigate,
                    onIobChipClick = onIobChipClick,
                    commandsAllowed = commandsAllowed
                )

                GraphsSection(
                    graphViewModel = graphViewModel, isSimpleMode = isSimpleMode, iobText = iobUiState.text, cobText = cobUiState.text,
                    overlaidBarsHeight = overlaidBarsHeight,
                    bgExtraHeight = bgExtraHeight,
                    cardStyle = true,
                    // autoISF build: no title row, as in the tiles design: the ranges and the history button in one row, so the graph gets the height
                    compactHeader = true,
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
