package app.aaps.ui.compose.overview.sheets

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.ui.compose.overview.chips.BolusRecommendation
import app.aaps.ui.compose.overview.statusLights.StatusUiState

/** autoISF build: the tiles that open a sheet. The activity tile has its own sheet, shown by the tiles. */
enum class TileSheetKind { BG, TARGET, BOLUS, CARBS, IOB, BASAL, LOOP, SUPPLIES, PROFILE, PUMP }

/**
 * Shows the sheet of [kind], or nothing. Every button closes the sheet first, then acts: an action asks for the PIN and
 * the confirmation, and a link opens a screen. [commandsAllowed] tells for an element whether its actions are allowed
 * here (an unpaired client or a role below it sees the sheet with the buttons off).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TileSheetHost(
    kind: TileSheetKind?,
    tileSheets: TileSheetActions,
    bgInfo: BgInfoData?,
    timeAgoText: String,
    bolusRecommendation: BolusRecommendation,
    basalIcon: ImageVector,
    sensitivityText: String,
    statusState: StatusUiState,
    pumpName: String,
    pumpIcon: ImageVector?,
    showPump: Boolean,
    commandsAllowed: (ElementType) -> Boolean,
    onNavigate: (NavigationRequest) -> Unit,
    onIobDetails: () -> Unit,
    onBasalDay: () -> Unit,
    onStatusLights: () -> Unit,
    onDismiss: () -> Unit
) {
    // Close first, then act
    fun then(action: () -> Unit): () -> Unit = { onDismiss(); action() }
    fun go(element: ElementType): () -> Unit = then { onNavigate(NavigationRequest.Element(element)) }

    when (kind) {
        null                  -> Unit

        TileSheetKind.BG       -> LoadingSheet(tileSheets::bgSheet, onDismiss) { state ->
            BgSheetBody(
                bgInfo, timeAgoText, statusState.sensorStatus?.label.orEmpty(), state, commandsAllowed(ElementType.BG_CHECK),
                onSave = { v -> onDismiss(); tileSheets.saveBgCheck(v, state.units) },
                onCalibrate = { v -> onDismiss(); tileSheets.calibrate(v, state.units) },
                onHistory = go(ElementType.HISTORY_BROWSER)
            )
        }

        TileSheetKind.TARGET   -> LoadingSheet(tileSheets::targetSheet, onDismiss) { state ->
            TargetSheetBody(
                state, commandsAllowed(ElementType.TEMP_TARGET_MANAGEMENT),
                onStart = { id -> onDismiss(); tileSheets.startTempTarget(id) },
                onCancel = then { tileSheets.cancelTempTarget() },
                onCustom = go(ElementType.TEMP_TARGET_MANAGEMENT),
                onAll = go(ElementType.TEMP_TARGET_MANAGEMENT)
            )
        }

        TileSheetKind.BOLUS    -> LoadingSheet(tileSheets::bolusSheet, onDismiss) { state ->
            BolusSheetBody(
                bolusRecommendation, state, commandsAllowed(ElementType.INSULIN),
                onGive = { insulin, recordOnly -> onDismiss(); tileSheets.giveBolus(insulin, recordOnly) },
                onCalculator = go(ElementType.BOLUS_WIZARD)
            )
        }

        TileSheetKind.CARBS    -> LoadingSheet(tileSheets::carbsSheet, onDismiss) { state ->
            CarbsSheetBody(
                state, commandsAllowed(ElementType.CARBS),
                onEnter = { grams, offset, hypo -> onDismiss(); tileSheets.enterCarbs(grams, offset, hypo) },
                onCalculator = go(ElementType.BOLUS_WIZARD)
            )
        }

        TileSheetKind.IOB      -> LoadingSheet(tileSheets::iobSheet, onDismiss) { state ->
            IobSheetBody(state, onDetails = then(onIobDetails))
        }

        TileSheetKind.BASAL    -> LoadingSheet(tileSheets::basalSheet, onDismiss) { state ->
            BasalSheetBody(
                basalIcon, state, commandsAllowed(ElementType.TEMP_BASAL),
                onStart = { percent, minutes -> onDismiss(); tileSheets.startTempBasal(percent, minutes) },
                onCancel = then { tileSheets.cancelTempBasal() },
                onDay = then(onBasalDay)
            )
        }

        TileSheetKind.LOOP     -> LoadingSheet(tileSheets::loopSheet, onDismiss) { state ->
            LoopSheetBody(
                state, sensitivityText, commandsAllowed(ElementType.RUNNING_MODE),
                onMode = { mode, minutes -> onDismiss(); tileSheets.setRunningMode(mode, minutes) },
                onRunNow = then { tileSheets.runLoopNow() },
                onDetails = state.apsClassName?.let { name -> then { onNavigate(NavigationRequest.Plugin(name)) } } ?: go(ElementType.RUNNING_MODE)
            )
        }

        TileSheetKind.SUPPLIES -> {
            // The same buttons the status row had, each only where it applies
            fun change(element: ElementType, applies: Boolean): (() -> Unit)? =
                if (applies && commandsAllowed(element)) go(element) else null
            ModalBottomSheet(onDismissRequest = onDismiss) {
                SuppliesSheetBody(
                    statusState,
                    onSensor = change(ElementType.SENSOR_INSERT, true),
                    onCannula = change(ElementType.CANNULA_CHANGE, statusState.showFill),
                    onInsulin = change(ElementType.FILL, statusState.showFill),
                    onBattery = change(ElementType.BATTERY_CHANGE, statusState.showPumpBatteryChange),
                    onSettings = then(onStatusLights)
                )
            }
        }

        TileSheetKind.PROFILE  -> LoadingSheet(tileSheets::profileSheet, onDismiss) { state ->
            ProfileSheetBody(
                state, commandsAllowed(ElementType.PROFILE_MANAGEMENT),
                onSwitch = { name, percent, minutes -> onDismiss(); tileSheets.switchProfile(name, percent, minutes) },
                onView = then { onNavigate(NavigationRequest.Element(ElementType.PROFILE_MANAGEMENT)) }
            )
        }

        TileSheetKind.PUMP     -> LoadingSheet(tileSheets::pumpSheet, onDismiss) { state ->
            PumpSheetBody(
                pumpName, pumpIcon, statusState, state, commandsAllowed(ElementType.RUNNING_MODE),
                onRefresh = then { tileSheets.refreshPump() },
                onHistory = go(ElementType.TREATMENTS),
                onDisconnect = then { tileSheets.setRunningMode(RM.Mode.DISCONNECTED_PUMP, PUMP_DISCONNECT_MINUTES) },
                onPumpScreen = if (showPump) go(ElementType.PUMP) else null
            )
        }
    }
}
