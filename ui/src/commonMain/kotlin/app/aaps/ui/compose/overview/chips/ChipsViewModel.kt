package app.aaps.ui.compose.overview.chips

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.SensitivityOverview
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.tempTargets.ttPresets
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.widget.WidgetUpdater
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.convertedToAbsolute
import app.aaps.core.objects.extensions.round
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.extensions.displayText
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.overview.sheets.TileSheetSource
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Stable
@AssistedInject
class ChipsViewModel(
    @Assisted cache: OverviewDataCache,
    private val iobCobCalculator: IobCobCalculator,
    private val loop: Loop,
    private val config: Config,
    private val persistenceLayer: PersistenceLayer,
    private val sensitivityOverview: SensitivityOverview,
    private val rh: TextResolver,
    private val decimalFormatter: DecimalFormatter,
    private val rxBus: RxBus,
    private val preferences: Preferences,
    private val activePlugin: ActivePlugin,
    private val widgetUpdater: WidgetUpdater,
    private val profileFunction: ProfileFunction,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
    private val dateUtil: DateUtil,
    private val profileUtil: ProfileUtil,
    private val bolusRecommendationSource: BolusRecommendationSource,
    /** autoISF build: what the tile sheets read and do; the activity sheet starts its target through it too */
    val tileSheets: TileSheetSource
) : ViewModel() {

    @AssistedFactory
    interface Factory {

        fun create(cache: OverviewDataCache): ChipsViewModel
    }

    private val iobCobTicker = flow {
        while (true) {
            emit(Unit)
            delay(150_000L) // 2.5 minutes
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val iobUiState: StateFlow<IobUiState> = iobCobTicker.combine(cache.iobGraphFlow) { _, _ ->
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        val total = bolusIob.iob + basalIob.basaliob
        IobUiState(
            text = rh.gs(InterfacesStrings.format_insulin_units, total),
            iobTotal = total
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = IobUiState()
    )

    val cobUiState: StateFlow<CobUiState> = iobCobTicker.combine(cache.cobGraphFlow) { _, _ ->
        val cobInfo = iobCobCalculator.getCobInfo("ChipsViewModel COB")
        var cobText = cobInfo.displayText(rh, decimalFormatter)
            ?: rh.gs(CoreUiStrings.value_unavailable_short)
        var carbsReq = 0

        val constraintsProcessed = loop.lastRun?.constraintsProcessed
        val lastRun = loop.lastRun
        if (config.APS && constraintsProcessed != null && lastRun != null) {
            if (constraintsProcessed.carbsReq > 0) {
                val lastCarbsTime = persistenceLayer.getNewestCarbs()?.timestamp ?: 0L
                if (lastCarbsTime < lastRun.lastAPSRun) {
                    cobText = rh.gs(CoreUiStrings.value_with_unit, cobText, rh.gs(CoreUiStrings.value_with_unit, constraintsProcessed.carbsReq.toString(), rh.gs(CoreUiStrings.required)))
                }
                carbsReq = constraintsProcessed.carbsReq
            }
        }

        CobUiState(text = cobText, carbsReq = carbsReq, cobValue = cobInfo.displayCob ?: 0.0)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CobUiState()
    )

    // The IOB graph is published before the loop runs in the same calculation chain, so on its
    // own it would show the previous loop's ratio and variable ISF. Predictions are published
    // right after the loop ran on the master, and right after a device status came in on a
    // client, so they carry the fresh values on both sides.
    val sensitivityUiState: StateFlow<SensitivityUiState> = combine(iobCobTicker, cache.iobGraphFlow, cache.predictionsFlow) { _, _, _ ->
        buildSensitivityUiState()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SensitivityUiState()
    )

    // autoISF: the exercise mode button. The preference is read when the flow is collected, not when
    // the view model is built. The ticker re-checks the active APS now and then: a plugin switch is
    // rare and does not change the preference.
    // autoISF build: the activity tile also shows the running target, the effect on basal and ISF, and the steps and
    // heart rate of the last hour, so it follows the temp target too.
    val exerciseModeUiState: StateFlow<ExerciseModeUiState> = combine(
        iobCobTicker,
        flow { emitAll(preferences.observe(BooleanKey.ApsAutoIsfHighTtRaisesSens)) },
        cache.tempTargetFlow
    ) { _, enabled, _ ->
        buildExerciseModeUiState(enabled)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ExerciseModeUiState()
    )

    // autoISF build: the basal card. Every 30 s, so the time left of a TBR, a new profile segment and old pump data
    // show in time, and at once when a TBR, the running mode or the profile changes.
    private val basalTicker = flow {
        while (true) {
            emit(Unit)
            delay(30_000L)
        }
    }

    val basalUiState: StateFlow<BasalUiState> = combine(basalTicker, cache.tbrFlow, cache.runningModeFlow, cache.profileFlow) { _, _, _, _ ->
        buildBasalUiState()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BasalUiState()
    )

    private suspend fun buildBasalUiState(): BasalUiState {
        val now = dateUtil.now()
        val profile = profileFunction.getProfile()
        val tb = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        val tbr = if (profile != null && tb != null) BasalTbr(rate = tb.convertedToAbsolute(now, profile), start = tb.timestamp, durationMs = tb.duration) else null
        // On a client the pump is the master's, known from its device status
        val pumpDataTime = if (config.AAPSCLIENT) processedDeviceStatusData.pumpData?.clock ?: 0L else activePlugin.activePump.lastDataTime.value
        // A TBR the loop asked for in its last run that the pump did not set (open loop, or the command failed)
        val lastRun = loop.lastRun
        val request = lastRun?.constraintsProcessed?.takeIf { config.APS && it.isTempBasalRequested && lastRun.tbrSetByPump?.success != true }
        val requestedRate = request?.let { if (it.usePercent && profile != null) profile.getBasal(now) * it.percent / 100.0 else it.rate }
        val card = basalCardState(
            BasalSnapshot(
                now = now,
                profileBasal = profile?.getBasal(now),
                tbr = tbr,
                profileSwitchPercentage = profile?.percentage ?: 100,
                profileSwitchTimeshiftHours = profile?.timeshift ?: 0,
                runningMode = persistenceLayer.getRunningModeActiveAtOrNull(now)?.mode,
                pumpDataTime = pumpDataTime,
                maxDataAgeMs = basalDataMaxAgeMs(preferences.get(IntKey.AlertsPumpUnreachableThreshold)),
                requestedRate = requestedRate,
                requestedAt = lastRun?.lastAPSRun ?: 0L
            )
        )
        return basalUiState(card)
    }

    private companion object {

        const val LOOP_OLD_MS = 15 * 60_000L
    }

    private fun rateText(rate: Double): String = rh.gs(CoreUiStrings.pump_base_basal_rate, rate)

    private fun basalUiState(card: BasalCardState): BasalUiState {
        val unknown = rh.gs(UiStrings.basal_unknown)
        val percentText = card.percentOfProfile?.let { rh.gs(UiStrings.basal_percent_of_profile, it) } ?: unknown
        val stateText = when (card.status) {
            BasalStatus.PROFILE           -> card.pendingRate?.let { rh.gs(UiStrings.basal_requested_short, rateText(it)) } ?: rh.gs(UiStrings.basal_by_profile)
            BasalStatus.TBR               -> card.pendingRate?.let { rh.gs(UiStrings.basal_requested_short, rateText(it)) }
                ?: rh.gs(UiStrings.basal_tbr_left, card.tbrRemainingMinutes ?: 0)
            BasalStatus.SUSPENDED_BY_PUMP -> rh.gs(UiStrings.basal_suspended_by_pump)
            BasalStatus.PUMP_DISCONNECTED -> rh.gs(UiStrings.basal_pump_disconnected)
            BasalStatus.NO_RECENT_DATA    -> rh.gs(UiStrings.basal_no_recent_data)
            BasalStatus.NO_PROFILE        -> rh.gs(UiStrings.basal_no_profile)
        }
        var statusText = when (card.status) {
            BasalStatus.PROFILE, BasalStatus.TBR -> rh.gs(UiStrings.basal_status_join, percentText, stateText)
            else                                 -> stateText
        }
        card.profileSwitchPercentage?.let { statusText = rh.gs(UiStrings.basal_status_join, statusText, rh.gs(UiStrings.basal_profile_switch_short, it)) }
        return BasalUiState(
            visible = true,
            rateText = card.rate?.let { rateText(it) } ?: unknown,
            statusText = statusText,
            direction = card.direction,
            isWarning = card.status != BasalStatus.PROFILE && card.status != BasalStatus.TBR
        )
    }

    // autoISF build: the statistics of today. Every 5 minutes is enough for numbers of a whole day.
    private val statsTicker = flow {
        while (true) {
            emit(Unit)
            delay(5 * 60_000L)
        }
    }

    val todayStatsUiState: StateFlow<TodayStatsUiState> = statsTicker.combine(cache.bgInfoFlow) { _, _ -> buildTodayStats() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TodayStatsUiState()
        )

    private suspend fun buildTodayStats(): TodayStatsUiState {
        val now = dateUtil.now()
        val readings = persistenceLayer.getBgReadingsDataFromTimeToTime(dateUtil.beginOfDay(now), now, false)
        // The range of the overview, the one the BG value is coloured by
        val low = profileUtil.convertToMgdl(preferences.get(UnitDoubleKey.OverviewLowMark), profileUtil.units)
        val high = profileUtil.convertToMgdl(preferences.get(UnitDoubleKey.OverviewHighMark), profileUtil.units)
        val stats = todayStats(readings.map { it.value }, low, high) ?: return TodayStatsUiState()
        return TodayStatsUiState(
            visible = true,
            timeInRangeText = rh.gs(UiStrings.today_percent_whole, stats.timeInRangePercent),
            timeInRange = stats.timeInRangePercent / 100f,
            cvText = rh.gs(UiStrings.today_percent_one_decimal, stats.cvPercent),
            // The bar of CV is full at 50 %, where it is far too high (36 % is the usual limit)
            cvFraction = (stats.cvPercent / 50.0).toFloat().coerceIn(0f, 1f),
            averageText = profileUtil.fromMgdlToStringInUnits(stats.averageMgdl),
            // Full at the top of the range
            averageFraction = (stats.averageMgdl / high).toFloat().coerceIn(0f, 1f),
            unitText = rh.gs(if (profileUtil.units == GlucoseUnit.MGDL) CoreUiStrings.mgdl else CoreUiStrings.mmol)
        )
    }

    // autoISF build: the loop card, when the loop last ran
    val loopUiState: StateFlow<LoopUiState> = combine(basalTicker, cache.predictionsFlow) { _, _ ->
        val lastRun = if (config.AAPSCLIENT) processedDeviceStatusData.openApsTimestamp.takeIf { it > 0 } else loop.lastRun?.lastAPSRun
        LoopUiState(
            lastRunText = lastRun?.let { dateUtil.minAgo(rh, it) } ?: "",
            // The loop runs every 5 minutes at most; older than 15 means it stopped
            isOld = lastRun == null || dateUtil.now() - lastRun > LOOP_OLD_MS
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LoopUiState()
    )

    // autoISF build: the Bolus card, from the last algorithm run (see BolusRecommendation). Again on a new reading, when
    // IOB or the predictions change (a loop run), when the running mode changes, and every 2.5 minutes.
    val bolusRecommendation: StateFlow<BolusRecommendation> =
        combine(iobCobTicker, cache.bgInfoFlow, cache.iobGraphFlow, cache.predictionsFlow, cache.runningModeFlow) { _, _, _, _, _ ->
            bolusRecommendationSource.current()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BolusRecommendation()
        )

    /** autoISF: switches exercise mode, as the button of aisf did. The widget shows the mode too. */
    fun toggleExerciseMode() {
        preferences.put(BooleanKey.ApsAutoIsfHighTtRaisesSens, !preferences.get(BooleanKey.ApsAutoIsfHighTtRaisesSens))
        widgetUpdater.update("ExerciseMode")
    }

    private fun activityPreset(): TTPreset? = preferences.ttPresets().firstOrNull { it.reason == TT.Reason.ACTIVITY }

    private suspend fun buildExerciseModeUiState(enabled: Boolean): ExerciseModeUiState {
        val visible = exerciseModeApplies(config, activePlugin)
        if (!visible) return ExerciseModeUiState(visible = false, enabled = enabled)
        val now = dateUtil.now()
        val tt = persistenceLayer.getTemporaryTargetActiveAt(now)
        val preset = activityPreset()
        val runningText = tt?.let {
            rh.gs(UiStrings.activity_target_left, profileUtil.fromMgdlToStringWithUnits(it.lowTarget), dateUtil.untilString(it.timestamp + it.duration, rh, withParentheses = false))
        }.orEmpty()
        val presetText = preset?.let {
            rh.gs(UiStrings.activity_target_preset, profileUtil.fromMgdlToStringWithUnits(it.targetValue), rh.gs(CoreUiStrings.format_mins, (it.duration / 60_000L).toInt()))
        }.orEmpty()
        // The same rule as the algorithm (DetermineBasalAutoISF): above the normal target of 100 mg/dL the ratio is
        // c / (c + target - 100), with c = the half basal exercise target - 100, at most min(1.5, autosens max)
        val runningHigh = tt?.lowTarget?.takeIf { it > Constants.NORMAL_TARGET_MGDL }
        val effectTarget = runningHigh ?: preset?.targetValue?.takeIf { it > Constants.NORMAL_TARGET_MGDL }
        val ratio = effectTarget?.let { sensitivityRatioAt(it) }
        // Steps and heart rate of the last hour, only when a watch or another source sends them
        val hourAgo = now - T.hours(1).msecs()
        val steps = persistenceLayer.getStepsCountFromTime(now - T.mins(15).msecs()).maxByOrNull { it.timestamp }?.steps60min
        val heartRates = persistenceLayer.getHeartRatesFromTime(hourAgo)
        return ExerciseModeUiState(
            visible = true,
            enabled = enabled,
            targetRunning = tt != null,
            activityRunning = tt?.reason == TT.Reason.ACTIVITY,
            runningTargetText = runningText,
            presetText = presetText,
            effectTargetText = effectTarget?.let { profileUtil.fromMgdlToStringWithUnits(it) }.orEmpty(),
            basalPercent = ratio?.let { (it * 100).roundToInt() },
            isfFactorText = ratio?.let { decimalFormatter.to1Decimal(1.0 / it) }.orEmpty(),
            effectNow = enabled && runningHigh != null && ratio != null,
            steps = steps,
            heartRate = heartRates.takeIf { it.isNotEmpty() }?.map { it.beatsPerMinute }?.average()?.roundToInt()
        )
    }

    private fun sensitivityRatioAt(targetMgdl: Double): Double? {
        val halfBasalTarget = profileUtil.convertToMgdlDetect(preferences.get(UnitDoubleKey.ApsAutoIsfHalfBasalExerciseTarget))
        val c = halfBasalTarget - Constants.NORMAL_TARGET_MGDL
        if (c <= 0.0) return null
        val ratio = c / (c + targetMgdl - Constants.NORMAL_TARGET_MGDL)
        return (ratio * 100).roundToInt() / 100.0
    }

    /** Starts the activity temp target of the presets, with the protection and confirmation of every sheet action. */
    fun startActivityTarget() {
        val preset = activityPreset() ?: return
        tileSheets.startTempTarget(preset.id)
    }

    /** Cancels the running temp target, the same way. */
    fun cancelTempTarget() = tileSheets.cancelTempTarget()

    private suspend fun buildSensitivityUiState(): SensitivityUiState {
        // Worked out in one place for the chip, its dialog and the watch - see SensitivityOverview
        val data = sensitivityOverview.build()
        return SensitivityUiState(
            asText = data.asText,
            isfFrom = data.isfFrom,
            isfTo = data.isfTo,
            isfStep = data.isfStep,
            dialogText = data.lines.joinToString("\n"),
            ratio = data.ratio,
            isEnabled = data.isEnabled,
            hasData = data.hasData
        )
    }

    fun showIobInfo() {
        viewModelScope.launch {
            val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
            val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
            val total = bolusIob.iob + basalIob.basaliob
            val message = listOf(
                CoreUiStrings.bolus_iob_label to bolusIob.iob,
                CoreUiStrings.treatments_wizard_basaliob_label to basalIob.basaliob,
                CoreUiStrings.iob to total
            ).joinToString("\n") { (label, insulin) ->
                rh.gs(InterfacesStrings.confirmation_line, rh.gs(label), rh.gs(InterfacesStrings.format_insulin_units, insulin))
            }
            rxBus.send(
                EventShowDialog.Ok(
                    title = rh.gs(CoreUiStrings.iob),
                    message = message
                )
            )
        }
    }
}
