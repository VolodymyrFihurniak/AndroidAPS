package app.aaps.plugins.aps.learned

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.LearnedForecast
import app.aaps.core.interfaces.aps.LearnedPrediction
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventBucketedDataCreated
import app.aaps.core.interfaces.sync.SyncLogBuffer
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.aps.ApsStrings
import app.aaps.plugins.aps.learned.compose.LearnedPredictionScreen
import app.aaps.plugins.aps.learned.grid.HistoryGrid
import app.aaps.plugins.aps.learned.grid.HistoryGridBuilder
import app.aaps.plugins.aps.learned.grid.HistoryInput
import app.aaps.plugins.aps.learned.grid.Phases
import app.aaps.plugins.aps.learned.grid.STEP_MS
import app.aaps.plugins.aps.learned.keys.LearnedBooleanKey
import app.aaps.plugins.aps.learned.keys.LearnedIntKey
import app.aaps.plugins.aps.learned.keys.LearnedStringKey
import app.aaps.plugins.aps.learned.model.LearnedModel
import app.aaps.plugins.aps.learned.train.Samples
import app.aaps.plugins.aps.learned.train.Trainer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.datetime.TimeZone
import kotlin.math.max
import kotlin.math.roundToInt

/** autoISF build, personal prediction: what the plugin screen shows */
enum class LearnedState { NO_MODEL, TRAINING, READY, NOT_READY }

data class LearnedStatus(val state: LearnedState, val progress: String? = null, val model: LearnedModel? = null)

/**
 * autoISF build: the personal prediction (experimental). Learns from the history on this phone how BG reacts to
 * insulin, carbs and the time of day, draws its own 2-hour prediction and gives a dose advice in the Insulin dialog.
 *
 * It never changes insulin delivery and the loop never waits for it: training and predictions run on their own
 * coroutines. Training starts when the plugin is switched on without a model, then once a day while the phone is charging (after two days also without), or when the user taps
 * "Train now", and is stopped after [TrainingSchedule.TIME_LIMIT_MS].
 */
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@ContributesBinding(AppScope::class, binding = binding<LearnedPrediction>())
@IntKey(260)
@SingleIn(AppScope::class)
@Inject
class LearnedPredictionPlugin(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    preferences: Preferences,
    notificationManager: NotificationManager,
    private val historyReader: HistoryReader,
    private val runArchive: AutoIsfRunArchive,
    private val forecastArchive: ForecastArchive,
    private val receiverStatusStore: ReceiverStatusStore,
    private val files: LearnedFiles,
    private val rxBus: RxBus,
    private val dateUtil: DateUtil,
    private val profileUtil: ProfileUtil,
    @ApplicationScope private val appScope: CoroutineScope
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.EXPERIMENTAL)
        .icon(Icons.Default.Science)
        .pluginName(ApsStrings.learned_prediction)
        .description(ApsStrings.learned_prediction_description)
        .composeContent { plugin -> LearnedPredictionScreen(plugin as LearnedPredictionPlugin) },
    ownPreferences = LearnedIntKey.entries + LearnedBooleanKey.entries + LearnedStringKey.entries,
    aapsLogger, rh, preferences, notificationManager
), LearnedPrediction {

    /** The training log the plugin screen shows */
    val log = SyncLogBuffer(aapsLogger, LTag.APS)

    private val _enabled = MutableStateFlow(false)
    override val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _forecast = MutableStateFlow<LearnedForecast?>(null)
    override val forecast: StateFlow<LearnedForecast?> = _forecast.asStateFlow()

    private val _status = MutableStateFlow(LearnedStatus(LearnedState.NO_MODEL))
    val status: StateFlow<LearnedStatus> = _status.asStateFlow()

    /** The live check of the plugin's own forecasts, made again every hour; null before the first */
    private val _liveScore = MutableStateFlow<LiveScore?>(null)
    val liveScore: StateFlow<LiveScore?> = _liveScore.asStateFlow()
    private var lastScore = 0L
    private var lastLowAlert = 0L
    private var lastHighAlert = 0L

    /** Days of AutoISF predictions the plugin keeps itself, for its screen */
    private val _autoIsfCopyDays = MutableStateFlow(0)
    val autoIsfCopyDays: StateFlow<Int> = _autoIsfCopyDays.asStateFlow()

    private var model: LearnedModel? = null
    private var liveJob: Job? = null
    private var trainingJob: Job? = null
    private var lastAttempt = 0L
    private var lastPredictionLog = 0L

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "learned_prediction_settings",
        title = ApsStrings.learned_prediction,
        items = listOf(LearnedIntKey.TrainingWindowDays, LearnedBooleanKey.LowAlert, LearnedBooleanKey.HighAlert),
        icon = pluginDescription.icon
    )

    @OptIn(FlowPreview::class)
    override suspend fun onStart() {
        super.onStart()
        model = LearnedModel.fromJson(files.readMoving(LearnedFiles.MODEL, preferences, LearnedStringKey.ModelJson))
        updateStatus()
        _enabled.value = true
        liveJob?.cancel()
        liveJob = appScope.launch(Dispatchers.Default) {
            refresh()
            // A new forecast on every new BG reading (with a 1-minute sensor every minute), and when the data of the
            // graph is calculated again, for example after a bolus
            merge(historyReader.bgChanges(), rxBus.toFlow(EventBucketedDataCreated::class).map { })
                .debounce(DEBOUNCE_MS)
                .collect { refresh() }
        }
    }

    override suspend fun onStop() {
        liveJob?.cancel()
        trainingJob?.cancel()
        _enabled.value = false
        _forecast.value = null
        try {
            runArchive.save()
            forecastArchive.save()
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "Personal prediction: saving the AutoISF runs failed", e)
        }
        super.onStop()
    }

    override suspend fun forecastNow(): LearnedForecast? = if (_enabled.value) computeForecast().also { _forecast.value = it } else null

    /** Starts a training now, unless one runs. The daily rule does not apply. */
    fun trainNow() {
        if (trainingJob?.isActive == true) return
        startTraining()
    }

    /** A plain text report of the model, the checks and the log, for the "Copy report" button */
    fun report(): String = LearnedReport.build(model, _liveScore.value, _autoIsfCopyDays.value, log.entries.value, dateUtil::dateAndTimeString)

    /** Stops a running training. The model saved before stays. */
    fun stopTraining() {
        trainingJob?.cancel()
    }

    /** Stops a training and deletes the model */
    fun resetModel() {
        trainingJob?.cancel()
        model = null
        files.delete(LearnedFiles.MODEL)
        _forecast.value = null
        log.add("Model deleted")
        updateStatus()
    }

    private suspend fun refresh() {
        val forecast = computeForecast()
        _forecast.value = forecast
        try {
            val now = dateUtil.now()
            forecast?.let {
                forecastArchive.add(it, now)
                warn(it, now)
            }
            runArchive.update(now)
            _autoIsfCopyDays.value = runArchive.days(now)
            if (now - lastScore >= SCORE_EVERY_MS) {
                lastScore = now
                _liveScore.value = scoreLive(now)
                updateStatus()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "Personal prediction: keeping or checking the forecasts failed", e)
        }
        if (TrainingSchedule.shouldStart(dateUtil.now(), model, lastAttempt, trainingJob?.isActive == true, receiverStatusStore.isCharging)) startTraining()
    }

    private suspend fun computeForecast(): LearnedForecast? {
        val current = model ?: return null
        return try {
            val now = dateUtil.now()
            val input = historyReader.readLive(now)
            val newest = input.bg.maxOfOrNull { it.timestamp } ?: return null
            if (now - newest > Forecaster.MAX_BG_AGE_MS) return null
            val grid = Forecaster.liveGrid(input, newest, TimeZone.currentSystemDefault())
            val index = Forecaster.liveIndex(grid, newest, now) ?: return null
            // A curve may give a dose only once the live check found it better than AutoISF
            val live = _liveScore.value
            Forecaster.forecast(current, grid, index)
                ?.let { it.copy(ready = live?.linearReady == true, expectedReady = it.expectedPoints.isNotEmpty() && live?.gruReady == true) }
                ?.also { logPrediction(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            aapsLogger.error(LTag.APS, "Personal prediction failed", e)
            null
        }
    }

    private fun logPrediction(forecast: LearnedForecast) {
        if (forecast.timestamp - lastPredictionLog < PREDICTION_LOG_MS) return
        lastPredictionLog = forecast.timestamp
        fun line(at: (Int) -> Double?) = listOf(30, 60, 120).joinToString(", ") { minutes ->
            "$minutes min ${at(minutes)?.let { profileUtil.fromMgdlToStringWithUnits(it) } ?: "-"}"
        }
        if (forecast.expectedPoints.isNotEmpty()) log.add("Expected ${dateUtil.timeString(forecast.timestamp)}", line(forecast::expectedAt))
        log.add("No more insulin ${dateUtil.timeString(forecast.timestamp)}", line(forecast::at))
    }

    private fun startTraining() {
        lastAttempt = dateUtil.now()
        _status.value = LearnedStatus(LearnedState.TRAINING, null, model)
        trainingJob = appScope.launch(Dispatchers.Default) {
            try {
                val trained = withTimeout(TrainingSchedule.TIME_LIMIT_MS) { train() }
                if (trained != null) {
                    model = trained
                    files.write(LearnedFiles.MODEL, trained.toJson())
                    log.add("Model saved")
                    _forecast.value = computeForecast()
                }
            } catch (e: TimeoutCancellationException) {
                log.add("Training stopped", "it took longer than ${TrainingSchedule.TIME_LIMIT_MS / 60_000} minutes")
            } catch (e: CancellationException) {
                log.add("Training stopped")
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.APS, "Personal prediction training failed", e)
                log.add("Training failed", e.message)
            } finally {
                // The job is still active here, so say it explicitly
                updateStatus(training = false)
            }
        }
    }

    private suspend fun train(): LearnedModel? {
        val now = dateUtil.now()
        val days = preferences.get(LearnedIntKey.TrainingWindowDays)
        val from = now - days * DAY_MS - LIVE_BACK_MS
        progress("Reading history")
        val input = historyReader.read(from, now)
        log.add("Data", "${input.bg.size} BG readings, ${input.doses.size} boluses and SMB, ${input.carbs.size} carb entries, ${input.tempBasals.size} temp basals")
        // How much of the window has BG at all: a short history is the usual reason for few samples
        val bgDays = input.bg.map { it.timestamp / DAY_MS }.distinct().size
        val firstBg = input.bg.minOfOrNull { it.timestamp }
        log.add("History", if (firstBg == null) "no BG" else "BG from ${dateUtil.dateString(firstBg)}, on $bgDays of $days days")
        progress("Building the history")
        val grids = phaseGrids(input, from, now)
        logInsulinPerDay(input, grids.first(), days)
        val testFrom = now - Samples.TEST_DAYS * DAY_MS
        val stored = historyReader.storedRuns(testFrom, now)
        val copied = runArchive.runs(testFrom, now)
        log.add(
            "AutoISF runs",
            "${stored.runs.size} with a prediction curve in the database on the test days, ${stored.withoutCurve} without one, ${copied.size} in the copy of this plugin"
        )
        // The same run can be in both: one per minute is enough
        val runs = (copied + stored.runs).distinctBy { it.timestamp / 60_000L }
        val trainer = Trainer(
            log = { log.add(it) },
            progress = { progress(it) },
            bg = { profileUtil.fromMgdlToStringWithUnits(it) }
        )
        return trainer.train(grids, now, days, runs, previous = model)
    }

    /**
     * One grid per 1-minute phase ([Phases]), phase 0 first. Phase 0 covers the whole window; the shifted ones cover at
     * most the last [Phases.MAX_DAYS] days, so a long window does not need 5 times the memory.
     */
    private suspend fun phaseGrids(input: HistoryInput, from: Long, now: Long): List<HistoryGrid> {
        val timeZone = TimeZone.currentSystemDefault()
        val start = from.floorDiv(STEP_MS) * STEP_MS
        val shiftedStart = max(start, (now - Phases.MAX_DAYS * DAY_MS - LIVE_BACK_MS).floorDiv(STEP_MS) * STEP_MS)
        return Phases.offsets(input.bg).map { offset ->
            yield()
            HistoryGridBuilder.build(input, (if (offset == 0L) start else shiftedStart) + offset, now, timeZone, alignToStep = false)
        }
    }

    /**
     * Insulin per day as the model sees it, to compare with the TDD of AAPS. Insulin counted twice (or missing) makes
     * the learned ISF wrong; a phone learned 30 % of the profile ISF, which closed loop data alone did not explain.
     */
    private fun logInsulinPerDay(input: HistoryInput, grid: HistoryGrid, days: Int) {
        if (days <= 0) return
        val boluses = input.doses.sumOf { it.units } / days
        val net = grid.insulin.sumOf { it.sum() } / days - boluses
        val scheduled = (0 until grid.size).sumOf { if (grid.basal[it].isNaN()) 0.0 else grid.basal[it] } * STEP_MS / 3_600_000.0 / days
        fun units(value: Double) = ((value * 10).roundToInt() / 10.0).toString()
        log.add(
            "Insulin per day",
            "about ${units(boluses + net + scheduled)} U: ${units(boluses)} U boluses and SMB, ${units(scheduled)} U profile basal, ${units(net)} U temp basal above or below it"
        )
    }

    private fun progress(text: String) {
        _status.value = LearnedStatus(LearnedState.TRAINING, text, model)
    }

    private fun updateStatus(training: Boolean = trainingJob?.isActive == true) {
        val current = model
        _status.value = when {
            training                            -> LearnedStatus(LearnedState.TRAINING, _status.value.progress, current)
            current == null                     -> LearnedStatus(LearnedState.NO_MODEL)
            _liveScore.value?.linearReady == true -> LearnedStatus(LearnedState.READY, model = current)
            else                                -> LearnedStatus(LearnedState.NOT_READY, model = current)
        }
    }

    /** A warning when the forecast sees a low or a high coming and the user switched that warning on */
    private fun warn(forecast: LearnedForecast, now: Long) {
        fun mark(key: UnitDoubleKey) = profileUtil.convertToMgdl(preferences.get(key), profileUtil.units)
        if (preferences.get(LearnedBooleanKey.LowAlert) && now - lastLowAlert >= ForecastAlerts.REPEAT_MS) {
            ForecastAlerts.low(forecast, mark(UnitDoubleKey.OverviewLowMark))?.let { alert ->
                lastLowAlert = now
                notificationManager.post(
                    NotificationId.LEARNED_LOW_PREDICTED,
                    rh.gs(ApsStrings.learned_alert_low, profileUtil.fromMgdlToStringWithUnits(alert.mgdl), alert.minutes),
                    validMinutes = ALERT_VALID_MIN
                )
            }
        }
        if (preferences.get(LearnedBooleanKey.HighAlert) && now - lastHighAlert >= ForecastAlerts.REPEAT_MS) {
            ForecastAlerts.high(forecast, mark(UnitDoubleKey.OverviewHighMark))?.let { alert ->
                lastHighAlert = now
                notificationManager.post(
                    NotificationId.LEARNED_HIGH_PREDICTED,
                    rh.gs(ApsStrings.learned_alert_high, profileUtil.fromMgdlToStringWithUnits(alert.mgdl), alert.minutes),
                    validMinutes = ALERT_VALID_MIN
                )
            }
        }
    }

    /** The live check of the forecasts of the last [LiveScore.WINDOW_MS] that are at least 2 hours old */
    private suspend fun scoreLive(now: Long): LiveScore {
        val from = now - LiveScore.WINDOW_MS - SCORED_AFTER_MS
        val forecasts = forecastArchive.range(from, now - SCORED_AFTER_MS)
        val score = LiveScore.compute(forecasts, runArchive.runs(from, now), historyReader.bgReadings(from, now))
        log.add("Live check", "${score.moments} forecasts with real BG, ${score.pairedMoments} of them with an AutoISF prediction")
        return score
    }

    private companion object {

        const val DAY_MS = 86_400_000L
        const val LIVE_BACK_MS = 12 * 3_600_000L
        const val DEBOUNCE_MS = 5_000L
        const val PREDICTION_LOG_MS = 15 * 60_000L
        const val SCORE_EVERY_MS = 3_600_000L
        const val ALERT_VALID_MIN = 30

        /** A forecast can be checked once its last point, 2 hours later, has a real BG */
        const val SCORED_AFTER_MS = 2 * 3_600_000L
    }
}
