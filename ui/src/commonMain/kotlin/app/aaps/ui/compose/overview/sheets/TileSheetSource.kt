package app.aaps.ui.compose.overview.sheets

import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.iobCalc
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
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
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
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
import app.aaps.ui.compose.overview.chips.BasalSnapshot
import app.aaps.ui.compose.overview.chips.BasalStatus
import app.aaps.ui.compose.overview.chips.basalCardState
import app.aaps.ui.compose.overview.chips.basalDataMaxAgeMs
import app.aaps.ui.compose.tempTarget.toTTPresetsWithDisplayName
import dev.zacsweers.metro.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * autoISF build: what the sheets of the overview tiles read and do. A sheet reads its state when it opens and again every
 * few seconds while it is open, and calls an action when the user presses a button.
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
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
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
        val profile = profileFunction.getProfile()
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        val total = bolusIob.iob + basalIob.basaliob
        val step = T.mins(STEP_MINUTES).msecs()
        val from = now - (profile?.let { T.mins((it.iCfg.dia * 60).toLong()).msecs() } ?: T.hours(DEFAULT_DIA_HOURS).msecs())
        // Every dose that may still act: boluses and SMBs of the user and the loop, temp basals, extended boluses
        val boluses = persistenceLayer.getBolusesFromTime(from, true).filter { it.type != BS.Type.PRIMING && it.amount > 0 }
        val rows = mutableListOf<Triple<Long, String, String>>()
        boluses.forEach { bolus ->
            val name = if (bolus.type == BS.Type.SMB) rh.gs(UiStrings.sheet_smb, insulin(bolus.amount)) else rh.gs(UiStrings.sheet_bolus, insulin(bolus.amount))
            rows += Triple(bolus.timestamp, name, twoParts(dateUtil.minAgo(rh, bolus.timestamp), rh.gs(UiStrings.sheet_left, insulin(bolus.iobCalc(now).iobContrib))))
        }
        persistenceLayer.getTemporaryBasalsStartingFromTime(from, true).forEach { tb ->
            rows += Triple(tb.timestamp, tbrName(tb), twoParts(dateUtil.minAgo(rh, tb.timestamp), minutes((tb.duration / 60_000L).toInt())))
        }
        persistenceLayer.getExtendedBolusesStartingFromTime(from, true).forEach { eb ->
            rows += Triple(eb.timestamp, rh.gs(UiStrings.sheet_extended, insulin(eb.amount)), twoParts(dateUtil.minAgo(rh, eb.timestamp), minutes((eb.duration / 60_000L).toInt())))
        }
        val doses = rows.sortedByDescending { it.first }.take(LAST_DOSES).map { it.second to it.third }
        // IOB from the last bolus or SMB until now, and the array of the algorithm from now on (DIA + 30 min)
        val lastDose = boluses.maxOfOrNull { it.timestamp }
        val past = mutableListOf<Double>()
        if (profile != null && lastDose != null) {
            var t = lastDose
            while (t < now) {
                past += iobCobCalculator.calculateFromTreatmentsAndTemps(t, profile).iob
                t += step
            }
        }
        val future = profile?.let { iobCobCalculator.calculateIobArrayInDia(it).map { iob -> iob.iob } }.orEmpty()
        // The insulin acts until the IOB stays under IOB_ZERO
        val lastActive = future.indexOfLast { abs(it) >= IOB_ZERO }
        val end = if (lastActive in 0 until future.lastIndex) now + (lastActive + 1) * step else null
        val shown = future.take(if (lastActive >= 0) (lastActive + 2).coerceAtMost(future.size) else 1)
        val start = lastDose?.takeIf { past.isNotEmpty() } ?: now
        val last = now + (shown.size - 1).coerceAtLeast(0) * step
        val nowIndex = past.size
        val timeline = SheetTimeline(
            values = past + shown,
            nowIndex = nowIndex,
            ticks = hourTicks(start, last, if (last - start > T.hours(6).msecs()) 2 else 1) { t -> if (t <= now) ((t - start) / step).toInt() else nowIndex + ((t - now) / step).toInt() },
            startText = dateUtil.timeString(start),
            endText = dateUtil.timeString(end ?: last)
        )
        return IobSheetState(
            totalText = insulin(total),
            bolusIobText = insulin(bolusIob.iob),
            basalIobText = insulin(basalIob.basaliob),
            activityText = rh.gs(UiStrings.sheet_iob_activity, decimalFormatter.to3Decimal(bolusIob.activity + basalIob.activity)),
            timeline = timeline,
            lastDoses = doses,
            actsUntilText = end?.let { twoParts(dateUtil.timeString(it), rh.gs(UiStrings.sheet_left, dateUtil.untilString(it, rh, withParentheses = false))) }.orEmpty()
        )
    }

    private fun tbrName(tb: TB): String =
        if (tb.isAbsolute) rh.gs(UiStrings.sheet_tbr_rate, rh.gs(CoreUiStrings.pump_base_basal_rate, tb.rate)) else rh.gs(UiStrings.sheet_tbr_percent, tb.rate.roundToInt())

    /** The full hours (local time) from [from] to [to], every [everyHours]; [index] gives the point of a time. */
    private fun hourTicks(from: Long, to: Long, everyHours: Int, index: (Long) -> Int): List<SheetTimeTick> {
        val hour = T.hours(1).msecs()
        val midnight = dateUtil.beginOfDay(from)
        var t = midnight
        while (t <= from) t += hour
        val ticks = mutableListOf<SheetTimeTick>()
        while (t < to) {
            if (((t - midnight) / hour) % everyHours == 0L) ticks += SheetTimeTick(index(t), dateUtil.timeString(t))
            t += hour
        }
        return ticks
    }

    override suspend fun basalSheet(): BasalSheetState {
        val now = dateUtil.now()
        val profile = profileFunction.getProfile()
        val pump = activePlugin.activePump
        val tbr = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        val step = T.mins(STEP_MINUTES).msecs()
        val midnight = dateUtil.beginOfDay(now)
        val chartStart = now - T.hours(CHART_PAST_HOURS).msecs()
        val chartEnd = now + T.hours(CHART_FUTURE_HOURS).msecs()
        // A temp basal can start up to its duration before the time asked; 12 hours covers every pump
        val tbrs = persistenceLayer.getTemporaryBasalsStartingFromTime(minOf(chartStart, midnight) - T.hours(12).msecs(), true)
        fun tbAt(t: Long): TB? = tbrs.lastOrNull { it.timestamp <= t && t < it.end }
        fun givenAt(t: Long, p: Double): Double = tbAt(t)?.let { if (it.isAbsolute) it.rate else p * it.rate / 100.0 } ?: p

        if (profile == null)
            return BasalSheetState(canSetTempBasal = pump.pumpDescription.isTempBasalCapable, loopMayChange = loop.runningMode() == RM.Mode.CLOSED_LOOP)

        // The chart: 5 minute points on a grid that holds now
        val given = mutableListOf<Double?>()
        val profileRates = mutableListOf<Double>()
        var t = now - ((now - chartStart) / step) * step
        val first = t
        while (t <= chartEnd) {
            val p = profile.getBasal(t)
            profileRates += p
            given += if (t <= now) givenAt(t, p) else tbAt(t)?.takeIf { tbr != null }?.let { givenAt(t, p) }
            t += step
        }
        val nowIndex = ((now - first) / step).toInt()
        // Today: the given basal against the profile, in 5 minute steps
        var givenToday = 0.0
        var profileToday = 0.0
        t = midnight
        while (t < now) {
            val p = profile.getBasal(t)
            givenToday += givenAt(t, p) * STEP_MINUTES / 60.0
            profileToday += p * STEP_MINUTES / 60.0
            t += step
        }
        // The next change of the profile basal, on the 5 minute grid of the clock
        val rateNow = profile.getBasal(now)
        var next: Long? = null
        t = (now / step + 1) * step
        while (t <= now + T.hours(24).msecs() && next == null) {
            if (abs(profile.getBasal(t) - rateNow) > 0.001) next = t
            t += step
        }
        // The same rules as the basal tile: the pump may be suspended, disconnected, or silent for too long
        val card = basalCardState(
            BasalSnapshot(
                now = now,
                profileBasal = rateNow,
                tbr = null,
                runningMode = persistenceLayer.getRunningModeActiveAtOrNull(now)?.mode,
                pumpDataTime = if (config.AAPSCLIENT) processedDeviceStatusData.pumpData?.clock ?: 0L else pump.lastDataTime.value,
                maxDataAgeMs = basalDataMaxAgeMs(preferences.get(IntKey.AlertsPumpUnreachableThreshold))
            )
        )
        val warning = when (card.status) {
            BasalStatus.SUSPENDED_BY_PUMP -> rh.gs(UiStrings.basal_suspended_by_pump)
            BasalStatus.PUMP_DISCONNECTED -> rh.gs(UiStrings.basal_pump_disconnected)
            BasalStatus.NO_RECENT_DATA    -> card.dataAgeMinutes?.let { rh.gs(UiStrings.sheet_basal_pump_old, it.toInt()) } ?: rh.gs(UiStrings.basal_details_pump_never)
            else                          -> ""
        }
        val leftMin = tbr?.let { ((it.timestamp + it.duration - now) / 60_000L).toInt().coerceAtLeast(0) } ?: 0
        val totalMin = tbr?.let { (it.duration / 60_000L).toInt() } ?: 0
        val changes = tbrs.filter { it.timestamp >= chartStart }.sortedByDescending { it.timestamp }.take(LAST_CHANGES).map { tb ->
            twoParts(dateUtil.timeString(tb.timestamp), tbrName(tb)) to minutes((tb.duration / 60_000L).toInt())
        }
        return BasalSheetState(
            profileBasalText = rh.gs(CoreUiStrings.pump_base_basal_rate, rateNow),
            rateText = decimalFormatter.to2Decimal(if (card.status == BasalStatus.SUSPENDED_BY_PUMP) 0.0 else tbr?.convertedToAbsolute(now, profile) ?: rateNow),
            percentText = tbr?.let { rh.gs(UiStrings.sheet_percent, it.convertedToPercent(now, profile)) }.orEmpty(),
            detailText = if (tbr != null) twoParts(rh.gs(UiStrings.sheet_basal_temp), rh.gs(UiStrings.sheet_minutes_left_of, leftMin, totalMin))
            else rh.gs(UiStrings.sheet_basal_profile_only),
            warningText = warning,
            runningProgress = if (tbr != null && totalMin > 0) 1f - leftMin.toFloat() / totalMin else null,
            nextChangeText = if (tbr == null) next?.let { rh.gs(UiStrings.sheet_basal_next, dateUtil.timeString(it), rh.gs(CoreUiStrings.pump_base_basal_rate, profile.getBasal(it))) }.orEmpty() else "",
            profileRate = rateNow,
            chart = BasalChart(
                given = given, profile = profileRates, nowIndex = nowIndex,
                ticks = hourTicks(first, chartEnd, CHART_TICK_HOURS) { time -> ((time - first) / step).toInt() }
            ),
            todayGivenText = insulin(givenToday),
            todayProfileText = insulin(profileToday),
            todayDifferenceText = insulin(givenToday - profileToday),
            changes = changes,
            loopMayChange = loop.runningMode() == RM.Mode.CLOSED_LOOP,
            canSetTempBasal = pump.pumpDescription.isTempBasalCapable
        )
    }

    override suspend fun loopSheet(): LoopSheetState {
        val now = dateUtil.now()
        val record = loop.runningModeRecord()
        val mode = record.mode
        val allowed = loop.allowedNextModes()
        val profile = profileFunction.getProfile()
        val run = loop.lastRun
        val result = run?.constraintsProcessed ?: run?.request
        val lastRunTime = if (config.AAPSCLIENT) null else run?.lastAPSRun
        val lastRun = if (config.AAPSCLIENT) null else result?.let {
            val share = requestedPercent(it, profile?.getBasal(now))
            LoopLastRun(
                timeText = lastRunTime?.let { time -> dateUtil.timeString(time) }.orEmpty(),
                tbrText = if (!it.isTempBasalRequested) "" else {
                    val amount = if (it.usePercent) rh.gs(UiStrings.sheet_percent, it.percent) else rh.gs(CoreUiStrings.pump_base_basal_rate, it.rate)
                    if (it.duration > 0) twoParts(amount, minutes(it.duration)) else amount
                },
                tbrAboveProfile = share?.let { s -> if (s > 100) true else if (s < 100) false else null },
                smbText = if (it.smb > 0.0) insulin(it.smb) else ""
            )
        }
        val inEffect = when {
            mode.isLoopRunning()          -> null
            record.isTemporary()          -> {
                val end = record.timestamp + record.duration
                LoopInEffect(
                    detailText = twoParts(rh.gs(UiStrings.sheet_left, dateUtil.untilString(end, rh, withParentheses = false)), rh.gs(UiStrings.sheet_loop_until, dateUtil.timeString(end))),
                    leftFraction = ((end - now).toFloat() / record.duration).coerceIn(0f, 1f)
                )
            }

            mode == RM.Mode.DISABLED_LOOP -> LoopInEffect(detailText = rh.gs(UiStrings.sheet_loop_off_sub))
            else                          -> LoopInEffect()
        }
        val stripStart = now - RUN_STRIP_MINUTES * 60_000L
        val runStrip = if (config.AAPSCLIENT) LoopRunStrip() else buildRunStrip(
            // Older runs too: a temp basal asked for before the strip starts can still run in it
            results = persistenceLayer.getApsResults(stripStart - T.hours(1).msecs(), now),
            now = now,
            basalAt = { time -> profile?.getBasal(time) },
            hours = hourTicks(stripStart, now, 1) { time -> ((time - stripStart) / 60_000L).toInt() },
            startText = dateUtil.timeString(stripStart),
            endText = dateUtil.timeString(now)
        )
        return LoopSheetState(
            mode = mode,
            lastRunText = lastRunTime?.let { dateUtil.minAgo(rh, it) }.orEmpty(),
            loopModes = listOf(RM.Mode.CLOSED_LOOP, RM.Mode.OPEN_LOOP, RM.Mode.CLOSED_LOOP_LGS).filter { it == mode || it in allowed },
            canSuspend = RM.Mode.SUSPENDED_BY_USER in allowed,
            canDisconnect = RM.Mode.DISCONNECTED_PUMP in allowed,
            canStop = RM.Mode.DISABLED_LOOP in allowed,
            canResume = RM.Mode.RESUME in allowed,
            inEffect = inEffect,
            lastRun = lastRun,
            runStrip = runStrip,
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

        const val LAST_DOSES = 8
        const val LAST_CHANGES = 4
        const val DEFAULT_DIA_HOURS = 5L

        /** IOB below this counts as gone, U */
        const val IOB_ZERO = 0.05

        /** The points of the charts, minutes apart */
        const val STEP_MINUTES = 5L

        /** The basal chart: hours before and after now, and a label every 3 hours */
        const val CHART_PAST_HOURS = 6L
        const val CHART_FUTURE_HOURS = 3L
        const val CHART_TICK_HOURS = 3
    }
}
