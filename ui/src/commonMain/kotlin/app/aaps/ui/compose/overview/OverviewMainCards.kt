package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.aapsClient.AapsClientStatusCard
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.chips.TodayStatsRow
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.graphs.GraphsSection
import app.aaps.ui.compose.overview.statusLights.StatusViewModel

/**
 * autoISF build: the cards of the main screen of the user's concept, the same in every layout (phone portrait, phone
 * landscape, tablet). On top a card with what the user sets (profile, temp target, exercise), then the main BG card with
 * IOB and COB, the cards basal, bolus and loop, the status of sensor and pump with the Pump button, and in AAPSClient
 * the client status. Before, only the portrait layout had these cards and a turned phone showed the old screen.
 */
@Composable
fun OverviewMainCards(
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
    graphViewModel: GraphViewModel,
    chipsViewModel: ChipsViewModel,
    manageViewModel: ManageViewModel,
    statusViewModel: StatusViewModel,
    statusLightsDef: PreferenceSubScreenDef,
    onNavigate: (NavigationRequest) -> Unit,
    onIobChipClick: () -> Unit,
    commandsAllowed: Boolean,
    modifier: Modifier = Modifier
) {
    val config = LocalConfig.current
    val bgInfoState by graphViewModel.bgInfoState.collectAsStateWithLifecycle()
    val sensitivityUiState by chipsViewModel.sensitivityUiState.collectAsStateWithLifecycle()
    val iobUiState by chipsViewModel.iobUiState.collectAsStateWithLifecycle()
    val cobUiState by chipsViewModel.cobUiState.collectAsStateWithLifecycle()
    val exerciseModeUiState by chipsViewModel.exerciseModeUiState.collectAsStateWithLifecycle()
    val basalUiState by chipsViewModel.basalUiState.collectAsStateWithLifecycle()
    val loopUiState by chipsViewModel.loopUiState.collectAsStateWithLifecycle()
    val bolusRecommendation by chipsViewModel.bolusRecommendation.collectAsStateWithLifecycle()
    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()
    val manageState by manageViewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier) {
        // autoISF build: the tiles of the design the user chose, see OverviewTiles
        OverviewTiles(
            bgInfo = bgInfoState.bgInfo,
            timeAgoText = bgInfoState.timeAgoText,
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
            iobUiState = iobUiState,
            cobUiState = cobUiState,
            basalUiState = basalUiState,
            loopUiState = loopUiState,
            sensitivityUiState = sensitivityUiState,
            bolusRecommendation = bolusRecommendation,
            exerciseModeUiState = exerciseModeUiState,
            onExerciseModeClick = chipsViewModel::toggleExerciseMode,
            onStartActivity = chipsViewModel::startActivityTarget,
            onCancelTempTarget = chipsViewModel::cancelTempTarget,
            tileSheets = chipsViewModel.tileSheets,
            statusState = statusState,
            pumpName = manageState.pumpPlugin.pluginDescription.pluginName?.let { stringResource(it) }.orEmpty(),
            pumpIcon = manageState.pumpPlugin.pluginDescription.icon,
            showPump = manageState.showPump,
            statusLightsDef = statusLightsDef,
            commandsAllowed = commandsAllowed,
            onNavigate = onNavigate,
            onIobClick = onIobChipClick,
            modifier = Modifier.padding(horizontal = AapsSpacing.large, vertical = AapsSpacing.medium)
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
    }
}

/**
 * autoISF build: the graphs column of the two column layouts (phone landscape, tablet), with its own scroll. The graphs
 * are cards as in portrait, the statistics of today are under them, and the BG graph takes the height the column leaves
 * free, so there is no empty space under the graphs.
 */
@Composable
fun OverviewGraphsColumn(
    graphViewModel: GraphViewModel,
    chipsViewModel: ChipsViewModel,
    isSimpleMode: Boolean,
    onNavigate: (NavigationRequest) -> Unit,
    overlaidBarsHeight: Dp,
    modifier: Modifier = Modifier
) {
    val iobUiState by chipsViewModel.iobUiState.collectAsStateWithLifecycle()
    val cobUiState by chipsViewModel.cobUiState.collectAsStateWithLifecycle()
    val todayStatsUiState by chipsViewModel.todayStatsUiState.collectAsStateWithLifecycle()
    // Same as in OverviewScreenStacked: the content is measured without the extra height, so adding it changes nothing
    // in the next pass.
    var bgExtraHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            GraphsSection(
                graphViewModel = graphViewModel, isSimpleMode = isSimpleMode, iobText = iobUiState.text, cobText = cobUiState.text,
                overlaidBarsHeight = overlaidBarsHeight,
                bgExtraHeight = bgExtraHeight,
                cardStyle = true,
                // autoISF build: a turned phone and a tablet need the height for the graph, not for a title
                compactHeader = true,
                onOpenHistory = { onNavigate(NavigationRequest.Element(ElementType.HISTORY_BROWSER)) },
                footer = {
                    TodayStatsRow(
                        state = todayStatsUiState,
                        onOpenStatistics = { onNavigate(NavigationRequest.Element(ElementType.STATISTICS)) },
                        modifier = Modifier.padding(horizontal = AapsSpacing.large, vertical = AapsSpacing.medium)
                    )
                },
                modifier = Modifier.onSizeChanged { size ->
                    val contentHeight = with(density) { size.height.toDp() } - bgExtraHeight
                    bgExtraHeight = (viewportHeight - contentHeight).coerceAtLeast(0.dp)
                }
            )
        }
    }
}
