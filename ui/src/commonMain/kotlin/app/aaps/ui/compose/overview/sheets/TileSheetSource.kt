package app.aaps.ui.compose.overview.sheets

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.clientcontrol.isNotDeliveryError
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.SmbPause
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.tempTargets.ttDurationMinutes
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.convertedToAbsolute
import app.aaps.core.objects.extensions.convertedToPercent
import app.aaps.core.objects.extensions.round
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.clientcontrol.failText
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.icons.IcProfile
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.navigation.ElementAvailability
import app.aaps.ui.compose.tempTarget.toTTPresetsWithDisplayName
import dev.zacsweers.metro.Inject
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * autoISF build: what the sheets of the overview tiles read and do. A sheet reads its state when it opens and calls an
 * action when the user presses a button.
 *
 * Every action that changes therapy (a dose, carbs, a temp target or temp basal, a profile switch, a running mode)
 * goes the way the dialogs go: the protection of the element first (the PIN, when one is set), then the master
 * prepares it and writes the confirmation, the user confirms that, and only then it is applied. A sheet is a shorter
 * way to the same confirmation, never a way around it.
 */
interface TileSheetActions {

    suspend fun bgSheet(): BgSheetState
    suspend fun targetSheet(): TargetSheetState
    suspend fun bolusSheet(): BolusSheetState
    suspend fun carbsSheet(): CarbsSheetState
    suspend fun iobSheet(): IobSheetState
    suspend fun basalSheet(): BasalSheetState
    suspend fun loopSheet(): LoopSheetState
    suspend fun profileSheet(): ProfileSheetState
    suspend fun pumpSheet(): PumpSheetState

    /** Stores a meter BG, in the units of the user */
    fun saveBgCheck(value: Double, units: GlucoseUnit)
    fun calibrate(value: Double, units: GlucoseUnit)
    fun startTempTarget(presetId: String)
    fun cancelTempTarget()
    fun giveBolus(insulin: Double, recordOnly: Boolean)
    fun enterCarbs(grams: Int, timeOffsetMinutes: Int, hypoTarget: Boolean)
    fun startTempBasal(percent: Int, durationMinutes: Int)
    fun cancelTempBasal()
    fun setRunningMode(mode: RM.Mode, durationMinutes: Int)
    fun runLoopNow()
    fun switchProfile(name: String, percentage: Int, durationMinutes: Int)
    fun refreshPump()
}

@Inject
class TileSheetSource(
    private val persistenceLayer: PersistenceLayer,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val profileRepository: ProfileRepository,
    private val activePlugin: ActivePlugin,
    private val loop: Loop,
    private val iobCobCalculator: IobCobCalculator,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val rh: TextResolver,
    private val decimalFormatter: DecimalFormatter,
    private val batchExecutor: BatchExecutor,
    private val protectionCheck: ProtectionCheck,
    private val rxBus: RxBus,
    private val config: Config,
    private val commandQueue: CommandQueue,
    private val elementAvailability: ElementAvailability,
    private val ch: ConcentrationHelper,
    private val automation: Automation,
    // Unqualified, as in ChipsViewModel: the graph binds the application scope under this name too
    private val appScope: CoroutineScope
) : TileSheetActions {

    private fun insulin(units: Double): String = rh.gs(InterfacesStrings.format_insulin_units, units)
    private fun minutes(min: Int): String = rh.gs(CoreUiStrings.format_mins, min)
    private fun twoParts(a: String, b: String): String = rh.gs(UiStrings.sheet_two_parts, a, b)

    // ----- what the sheets show

    override suspend fun bgSheet(): BgSheetState {
        val now = dateUtil.now()
        val units = profileUtil.units
        val readings = persistenceLayer.getBgReadingsDataFromTimeToTime(now - T.hours(1).msecs(), now, true).map { it.value }
        val low = profileUtil.convertToMgdl(preferences.get(UnitDoubleKey.OverviewLowMark), units)
        val high = profileUtil.convertToMgdl(preferences.get(UnitDoubleKey.OverviewHighMark), units)
        val inRange = if (readings.isEmpty()) null else readings.count { it in low..high } * 100 / readings.size
        val last = readings.lastOrNull()
        return BgSheetState(
            units = units,
            lastHourMgdl = readings,
            inRangePercent = inRange,
            startValue = last?.let { profileUtil.fromMgdlToUnits(it, units) }?.let { if (units == GlucoseUnit.MGDL) it.roundToInt().toDouble() else (it * 10).roundToInt() / 10.0 }
                ?: if (units == GlucoseUnit.MGDL) 100.0 else 5.5,
            step = if (units == GlucoseUnit.MGDL) 1.0 else 0.1,
            canCalibrate = elementAvailability.isAvailable(ElementType.CALIBRATION)
        )
    }

    override suspend fun targetSheet(): TargetSheetState {
        val now = dateUtil.now()
        val running = persistenceLayer.getTemporaryTargetActiveAt(now)
        val presets = preferences.get(StringNonKey.TempTargetPresets).toTTPresetsWithDisplayName(rh).map { preset ->
            TargetPresetItem(
                id = preset.id,
                name = preset.name ?: preset.displayName ?: preset.reason.text,
                reason = preset.reason,
                text = rh.gs(UiStrings.activity_target_preset, profileUtil.fromMgdlToStringWithUnits(preset.targetValue), minutes((preset.duration / 60_000L).toInt())),
                running = running != null && running.reason == preset.reason && running.lowTarget == preset.targetValue
            )
        }
        val runningText = running?.let {
            rh.gs(UiStrings.activity_target_left, profileUtil.fromMgdlToStringWithUnits(it.lowTarget), dateUtil.untilString(it.timestamp + it.duration, rh, withParentheses = false))
        }.orEmpty()
        val profile = profileFunction.getProfile()
        val targetNow = profile?.let { (it.getTargetLowMgdl(now) + it.getTargetHighMgdl(now)) / 2 }
        // The next change of the profile target: looked for in steps of 30 minutes over the next day
        var nextTime: Long? = null
        var nextTarget: Double? = null
        if (profile != null && targetNow != null) {
            val step = T.mins(30).msecs()
            var t = now - now % step + step
            while (t < now + T.hours(24).msecs()) {
                val target = (profile.getTargetLowMgdl(t) + profile.getTargetHighMgdl(t)) / 2
                if (target != targetNow) {
                    nextTime = t
                    nextTarget = target
                    break
                }
                t += step
            }
        }
        return TargetSheetState(
            presets = presets,
            runningText = runningText,
            profileTargetText = targetNow?.let { profileUtil.fromMgdlToStringWithUnits(it) }.orEmpty(),
            nextChangeTime = nextTime?.let { dateUtil.timeString(it) },
            nextChangeText = nextTarget?.let { profileUtil.fromMgdlToStringWithUnits(it) }
        )
    }

    override suspend fun bolusSheet(): BolusSheetState {
        val now = dateUtil.now()
        val last = persistenceLayer.getNewestBolus()
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        val cob = iobCobCalculator.getCobInfo("Bolus sheet").displayCob
        val bg = persistenceLayer.getBgReadingsDataFromTimeToTime(now - T.mins(15).msecs(), now, false).firstOrNull()?.value
        return BolusSheetState(
            lastBolusText = last?.let { twoParts(insulin(it.amount), dateUtil.minAgo(rh, it.timestamp)) }.orEmpty(),
            reasonText = rh.gs(
                UiStrings.sheet_bolus_reason,
                bg?.let { profileUtil.fromMgdlToStringWithUnits(it) } ?: rh.gs(UiStrings.basal_unknown),
                insulin(bolusIob.iob + basalIob.basaliob),
                cob?.let { rh.gs(InterfacesStrings.format_carbs, it.roundToInt()) } ?: rh.gs(UiStrings.basal_unknown)
            ),
            step = ch.bolusStep(1.0).takeIf { it > 0.0 } ?: 0.1,
            maxBolus = preferences.get(DoubleKey.SafetyMaxBolus)
        )
    }

    override suspend fun carbsSheet(): CarbsSheetState {
        val cob = iobCobCalculator.getCobInfo("Carbs sheet").displayCob
        val lastMeal = persistenceLayer.getNewestCarbs()
        return CarbsSheetState(
            cobText = cob?.let { rh.gs(InterfacesStrings.format_carbs, it.roundToInt()) }.orEmpty(),
            cobGrams = cob,
            lastMealGrams = lastMeal?.amount?.roundToInt(),
            lastMealText = lastMeal?.let { twoParts(rh.gs(InterfacesStrings.format_carbs, it.amount.roundToInt()), dateUtil.minAgo(rh, it.timestamp)) }.orEmpty(),
            hypoTargetText = rh.gs(
                UiStrings.activity_target_preset,
                profileUtil.fromMgdlToStringWithUnits(preferences.ttTargetMgdl(TT.Reason.HYPOGLYCEMIA)),
                minutes(preferences.ttDurationMinutes(TT.Reason.HYPOGLYCEMIA))
            ),
            maxCarbs = preferences.get(IntKey.SafetyMaxCarbs)
        )
    }

    override suspend fun iobSheet(): IobSheetState {
        val now = dateUtil.now()
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        val total = bolusIob.iob + basalIob.basaliob
        // IOB of the next hours, every 5 minutes: the array the algorithm uses, from now on
        val curve = profileFunction.getProfile()?.let { profile -> iobCobCalculator.calculateIobArrayInDia(profile).map { it.iob } }.orEmpty()
        val doses = persistenceLayer.getBolusesFromTime(now - T.hours(6).msecs(), false).take(LAST_DOSES).map { bolus ->
            val name = if (bolus.type == BS.Type.SMB) rh.gs(UiStrings.sheet_smb, insulin(bolus.amount)) else rh.gs(UiStrings.sheet_bolus, insulin(bolus.amount))
            name to dateUtil.minAgo(rh, bolus.timestamp)
        }
        return IobSheetState(
            totalText = insulin(total),
            bolusIobText = insulin(bolusIob.iob),
            basalIobText = insulin(basalIob.basaliob),
            activityText = rh.gs(UiStrings.sheet_iob_activity, decimalFormatter.to3Decimal(bolusIob.activity + basalIob.activity)),
            curve = curve.take(CURVE_POINTS),
            inTwoHoursText = curve.getOrNull(TWO_HOURS_POINT)?.let { insulin(it) }.orEmpty(),
            lastDoses = doses
        )
    }

    override suspend fun basalSheet(): BasalSheetState {
        val now = dateUtil.now()
        val profile = profileFunction.getProfile()
        val tbr = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        val pump = activePlugin.activePump
        val runningText = if (tbr != null && profile != null)
            twoParts(rh.gs(CoreUiStrings.pump_base_basal_rate, tbr.convertedToAbsolute(now, profile)), rh.gs(UiStrings.sheet_percent, tbr.convertedToPercent(now, profile)))
        else ""
        val leftMin = tbr?.let { ((it.timestamp + it.duration - now) / 60_000L).toInt().coerceAtLeast(0) } ?: 0
        val totalMin = tbr?.let { (it.duration / 60_000L).toInt() } ?: 0
        return BasalSheetState(
            profileBasalText = profile?.let { rh.gs(CoreUiStrings.pump_base_basal_rate, it.getBasal(now)) }.orEmpty(),
            runningText = runningText,
            runningDetail = if (tbr != null) rh.gs(UiStrings.sheet_minutes_left_of, leftMin, totalMin) else "",
            runningProgress = if (totalMin > 0) 1f - leftMin.toFloat() / totalMin else 0f,
            loopMayChange = loop.runningMode() == RM.Mode.CLOSED_LOOP,
            canSetTempBasal = pump.pumpDescription.isTempBasalCapable
        )
    }

    override suspend fun loopSheet(): LoopSheetState {
        val mode = loop.runningMode()
        val allowed = loop.allowedNextModes()
        val run = loop.lastRun
        val result = run?.constraintsProcessed ?: run?.request
        val lastRun = if (config.AAPSCLIENT) null else run?.lastAPSRun
        val decision = result?.let {
            val parts = buildList {
                if (it.isTempBasalRequested) add(if (it.usePercent) rh.gs(UiStrings.sheet_tbr_percent, it.percent) else rh.gs(UiStrings.sheet_tbr_rate, rh.gs(CoreUiStrings.pump_base_basal_rate, it.rate)))
                if (it.smb > 0.0) add(rh.gs(UiStrings.sheet_smb, insulin(it.smb)))
            }
            if (parts.isEmpty()) rh.gs(UiStrings.sheet_no_change) else parts.joinToString(rh.gs(UiStrings.sheet_separator))
        }.orEmpty()
        return LoopSheetState(
            mode = mode,
            lastRunText = lastRun?.let { dateUtil.minAgo(rh, it) }.orEmpty(),
            loopModes = listOf(RM.Mode.CLOSED_LOOP, RM.Mode.OPEN_LOOP, RM.Mode.CLOSED_LOOP_LGS).filter { it == mode || it in allowed },
            canSuspend = RM.Mode.SUSPENDED_BY_USER in allowed,
            canDisconnect = RM.Mode.DISCONNECTED_PUMP in allowed,
            canResume = RM.Mode.RESUME in allowed,
            decisionText = decision,
            canRunNow = !config.AAPSCLIENT && mode.isLoopRunning(),
            apsClassName = if (config.AAPSCLIENT) null else activePlugin.activeAPS?.let { it::class.simpleName }
        )
    }

    override suspend fun profileSheet(): ProfileSheetState {
        val names = profileRepository.profiles.value.map { it.name }
        val eps = persistenceLayer.getEffectiveProfileSwitchActiveAt(dateUtil.now())
        val percentage = eps?.originalPercentage ?: 100
        val left = eps?.takeIf { it.originalDuration > 0 }?.let { dateUtil.untilString(it.timestamp + it.originalDuration, rh, withParentheses = false) }
        val percentText = rh.gs(UiStrings.sheet_percent, percentage)
        return ProfileSheetState(
            names = names,
            activeName = eps?.originalProfileName ?: profileFunction.getOriginalProfileName(),
            percentage = percentage,
            activeText = left?.let { twoParts(percentText, rh.gs(UiStrings.sheet_left, it)) } ?: percentText
        )
    }

    override suspend fun pumpSheet(): PumpSheetState {
        val now = dateUtil.now()
        val pump = activePlugin.activePump
        val lastData = pump.lastDataTime.value
        val profile = profileFunction.getProfile()
        val tbr = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        val basal = when {
            tbr != null && profile != null -> rh.gs(UiStrings.sheet_basal_tbr, rh.gs(CoreUiStrings.pump_base_basal_rate, tbr.convertedToAbsolute(now, profile)))
            profile != null                -> rh.gs(CoreUiStrings.pump_base_basal_rate, profile.getBasal(now))
            else                           -> ""
        }
        val last = persistenceLayer.getNewestBolus()
        val mode = loop.runningMode()
        return PumpSheetState(
            connectionText = if (lastData > 0) rh.gs(UiStrings.sheet_pump_connected, dateUtil.minAgo(rh, lastData)) else "",
            basalText = basal,
            lastBolusText = last?.let { twoParts(insulin(it.amount), dateUtil.minAgo(rh, it.timestamp)) }.orEmpty(),
            canRefresh = !config.AAPSCLIENT,
            canDisconnect = RM.Mode.DISCONNECTED_PUMP in loop.allowedNextModes() && mode != RM.Mode.DISCONNECTED_PUMP
        )
    }

    // ----- what the buttons do

    override fun saveBgCheck(value: Double, units: GlucoseUnit) {
        val action = BatchAction.TherapyEvent(
            teType = TE.Type.FINGER_STICK_BG_VALUE, timestamp = dateUtil.now(), glucoseMgdl = profileUtil.convertToMgdl(value, units),
            glucoseType = TE.MeterType.FINGER, source = Sources.BgCheck
        )
        run(listOf(action), Sources.BgCheck, rh.gs(CoreUiStrings.careportal_bgcheck), ElementType.BG_CHECK)
    }

    override fun calibrate(value: Double, units: GlucoseUnit) =
        run(listOf(BatchAction.Calibration(profileUtil.convertToMgdl(value, units))), Sources.CalibrationDialog, rh.gs(CoreUiStrings.calibration), ElementType.CALIBRATION)

    override fun startTempTarget(presetId: String) {
        val preset = preferences.get(StringNonKey.TempTargetPresets).toTTPresetsWithDisplayName(rh).find { it.id == presetId } ?: return
        run(
            listOf(BatchAction.TempTarget(preset.reason.text, preset.targetValue, preset.targetValue, (preset.duration / 60_000L).toInt(), 0)),
            Sources.TTDialog, rh.gs(CoreUiStrings.clientcontrol_action_set_temp_target), ElementType.TEMP_TARGET_MANAGEMENT
        )
    }

    override fun cancelTempTarget() =
        run(
            listOf(BatchAction.TempTarget(TT.Reason.CUSTOM.text, 0.0, 0.0, 0, 0)),
            Sources.TTDialog, rh.gs(CoreUiStrings.clientcontrol_action_cancel_temp_target), ElementType.TEMP_TARGET_MANAGEMENT
        )

    override fun giveBolus(insulin: Double, recordOnly: Boolean) {
        if (insulin <= 0.0) return
        val action = BatchAction.Bolus(
            insulin = insulin, carbs = 0, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0,
            recordOnly = recordOnly, notes = "", timestamp = 0L, iCfg = null
        )
        run(listOf(action), Sources.InsulinDialog, rh.gs(InterfacesStrings.bolus), ElementType.INSULIN, icon = IcBolus, dose = !recordOnly, onApplied = {
            automation.removeAutomationEventBolusReminder()
        })
    }

    override fun enterCarbs(grams: Int, timeOffsetMinutes: Int, hypoTarget: Boolean) {
        if (grams <= 0) return
        val actions = buildList {
            if (hypoTarget) {
                val mgdl = preferences.ttTargetMgdl(TT.Reason.HYPOGLYCEMIA)
                add(BatchAction.TempTarget(TT.Reason.HYPOGLYCEMIA.text, mgdl, mgdl, preferences.ttDurationMinutes(TT.Reason.HYPOGLYCEMIA), 0))
            }
            add(
                BatchAction.Bolus(
                    insulin = 0.0, carbs = grams, carbsTimeOffsetMinutes = timeOffsetMinutes, carbsDurationHours = 0,
                    recordOnly = false, notes = "", timestamp = 0L, iCfg = null
                )
            )
        }
        run(actions, Sources.CarbDialog, rh.gs(InterfacesStrings.carbs), ElementType.CARBS, icon = IcCarbs)
    }

    override fun startTempBasal(percent: Int, durationMinutes: Int) {
        appScope.launch {
            val pump = activePlugin.activePump
            val isPercent = pump.pumpDescription.tempBasalStyle and PumpDescription.PERCENT == PumpDescription.PERCENT
            // A pump that takes only absolute rates gets the share of the profile basal now
            val rate = if (isPercent) percent.toDouble()
            else profileFunction.getProfile()?.getBasal(dateUtil.now())?.let { it * percent / 100.0 } ?: return@launch
            run(listOf(BatchAction.TempBasal(rate, isPercent, durationMinutes)), Sources.TempBasalDialog, rh.gs(CoreUiStrings.tempbasal_label), ElementType.TEMP_BASAL, pumpDirect = true)
        }
    }

    override fun cancelTempBasal() =
        run(listOf(BatchAction.CancelTempBasal), Sources.TempBasalDialog, rh.gs(CoreUiStrings.tempbasal_label), ElementType.TEMP_BASAL, pumpDirect = true)

    override fun setRunningMode(mode: RM.Mode, durationMinutes: Int) =
        run(listOf(BatchAction.RunningMode(mode, durationMinutes)), Sources.LoopDialog, rh.gs(CoreUiStrings.loop), ElementType.RUNNING_MODE)

    override fun runLoopNow() {
        if (config.AAPSCLIENT) return
        appScope.launch { loop.invoke("Loop sheet", allowNotification = true) }
    }

    override fun switchProfile(name: String, percentage: Int, durationMinutes: Int) =
        run(
            listOf(BatchAction.ProfileSwitch(percentage, 0, durationMinutes, profileName = name)),
            Sources.ProfileSwitchDialog, rh.gs(CoreUiStrings.careportal_profileswitch), ElementType.PROFILE_MANAGEMENT, icon = IcProfile
        )

    override fun refreshPump() {
        if (config.AAPSCLIENT) return
        appScope.launch { commandQueue.readStatus("Pump sheet") }
    }

    /**
     * The way of every action: protection of [element] (as when its screen opens), prepare on the master, the master's
     * confirmation, commit on OK. [dose] is a dose the pump gives: then the confirmation closes itself before the
     * master's SMB pause ends, and a cancel lets the loop give SMBs again at once.
     */
    private fun run(
        actions: List<BatchAction>,
        source: Sources,
        label: String,
        element: ElementType,
        icon: ImageVector = element.icon(),
        pumpDirect: Boolean = false,
        dose: Boolean = false,
        onApplied: suspend () -> Unit = {}
    ) {
        guarded(element.protection) {
            val closeAt = if (dose) TimeSource.Monotonic.markNow() + SmbPause.MAX_DURATION else null
            appScope.launch {
                when (val prepared = batchExecutor.prepare(actions, source, label)) {
                    is ActionProgress.Prepared ->
                        rxBus.send(
                            EventShowDialog.OkCancel(
                                title = label, message = "", confirmationLines = prepared.lines, icon = icon,
                                onOk = {
                                    appScope.launch {
                                        val result = batchExecutor.commit(prepared.id, source, label, pumpDirect)
                                        if (result is ActionProgress.Applied) onApplied()
                                        if (result is ActionProgress.Rejected) report(result, label)
                                    }
                                },
                                onCancel = { batchExecutor.discard(prepared.id) },
                                closeAt = closeAt
                            )
                        )

                    is ActionProgress.Rejected -> report(prepared, label)
                    else                       -> Unit // Unconfirmed: the app-level modal shows it
                }
            }
        }
    }

    /** A refusal: the offline text, or on the master its own detail. A client's modal has shown the master's text already. */
    private fun report(rejected: ActionProgress.Rejected, title: String) {
        when {
            rejected.reason == FailureReason.NoPendingBolus -> Unit
            rejected.reason.isNotDeliveryError()            -> rxBus.send(EventShowDialog.Ok(title = title, message = rh.gs(rejected.reason.failText())))
            !config.AAPSCLIENT                              -> rxBus.send(EventShowDialog.Ok(title = title, message = rejected.detail ?: rh.gs(rejected.reason.failText())))
        }
    }

    private fun guarded(protection: ProtectionCheck.Protection, action: () -> Unit) {
        if (protection == ProtectionCheck.Protection.NONE) action()
        else protectionCheck.requestProtection(protection) { result -> if (result == ProtectionResult.GRANTED) action() }
    }

    private companion object {

        const val LAST_DOSES = 3

        /** 4 hours of the IOB curve, a point every 5 minutes */
        const val CURVE_POINTS = 49
        const val TWO_HOURS_POINT = 24
    }
}
