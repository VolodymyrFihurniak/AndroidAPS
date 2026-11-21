package app.aaps.plugins.aps.learned

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.LearnedForecast
import app.aaps.core.interfaces.aps.LearnedPrediction
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventBucketedDataCreated
import app.aaps.core.interfaces.sync.SyncLogBuffer
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.aps.ApsStrings
import app.aaps.plugins.aps.learned.compose.LearnedPredictionScreen
import app.aaps.plugins.aps.learned.grid.HistoryGridBuilder
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone

/** autoISF build, personal prediction: what the plugin screen shows */
enum class LearnedState { NO_MODEL, TRAINING, READY, NOT_READY }

data class LearnedStatus(val state: LearnedState, val progress: String? = null, val model: LearnedModel? = null)

/**
 * autoISF build: the personal prediction (experimental). Learns from the history on this phone how BG reacts to
 * insulin, carbs and the time of day, draws its own 2-hour prediction and gives a dose advice in the Insulin dialog.
 *
 * It never changes insulin delivery and the loop never waits for it: training and predictions run on their own
 * coroutines. Training starts when the plugin is switched on without a model, then once a day, or when the user taps
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
    ownPreferences = LearnedIntKey.entries + LearnedStringKey.entries,
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

    private var model: LearnedModel? = null
    private var liveJob: Job? = null
    private var trainingJob: Job? = null
    private var lastAttempt = 0L
    private var lastPredictionLog = 0L

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "learned_prediction_settings",
        title = ApsStrings.learned_prediction,
        items = listOf(LearnedIntKey.TrainingWindowDays),
        icon = pluginDescription.icon
    )

    @OptIn(FlowPreview::class)
    override suspend fun onStart() {
        super.onStart()
        model = LearnedModel.fromJson(preferences.get(LearnedStringKey.ModelJson))
        updateStatus()
        _enabled.value = true
        liveJob?.cancel()
        liveJob = appScope.launch(Dispatchers.Default) {
            refresh()
            rxBus.toFlow(EventBucketedDataCreated::class).debounce(DEBOUNCE_MS).collect { refresh() }
        }
    }

    override suspend fun onStop() {
        liveJob?.cancel()
        trainingJob?.cancel()
        _enabled.value = false
        _forecast.value = null
        super.onStop()
    }

    override suspend fun forecastNow(): LearnedForecast? = if (_enabled.value) computeForecast().also { _forecast.value = it } else null

    /** Starts a training now, unless one runs. The daily rule does not apply. */
    fun trainNow() {
        if (trainingJob?.isActive == true) return
        startTraining()
    }

    /** Stops a running training. The model saved before stays. */
    fun stopTraining() {
        trainingJob?.cancel()
    }

    /** Stops a training and deletes the model */
    fun resetModel() {
        trainingJob?.cancel()
        model = null
        preferences.put(LearnedStringKey.ModelJson, "")
        _forecast.value = null
        log.add("Model deleted")
        updateStatus()
    }

    private suspend fun refresh() {
        _forecast.value = computeForecast()
        if (TrainingSchedule.shouldStart(dateUtil.now(), model, lastAttempt, trainingJob?.isActive == true)) startTraining()
    }

    private suspend fun computeForecast(): LearnedForecast? {
        val current = model ?: return null
        return try {
            val now = dateUtil.now()
            val input = historyReader.readLive(now)
            val grid = HistoryGridBuilder.build(input, now - LIVE_BACK_MS, now + LIVE_AHEAD_MS, TimeZone.currentSystemDefault())
            Forecaster.forecast(current, grid, grid.indexOf(now))?.also { logPrediction(it) }
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
        val values = listOf(30, 60, 120).joinToString(", ") { minutes ->
            "$minutes min ${forecast.at(minutes)?.let { profileUtil.fromMgdlToStringWithUnits(it) } ?: "-"}"
        }
        log.add("Prediction ${dateUtil.timeString(forecast.timestamp)}", values)
    }

    private fun startTraining() {
        lastAttempt = dateUtil.now()
        _status.value = LearnedStatus(LearnedState.TRAINING, null, model)
        trainingJob = appScope.launch(Dispatchers.Default) {
            try {
                val trained = withTimeout(TrainingSchedule.TIME_LIMIT_MS) { train() }
                if (trained != null) {
                    model = trained
                    preferences.put(LearnedStringKey.ModelJson, trained.toJson())
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
        val grid = HistoryGridBuilder.build(input, from, now, TimeZone.currentSystemDefault())
        val runs = historyReader.storedRuns(now - Samples.TEST_DAYS * DAY_MS, now)
        val trainer = Trainer(
            log = { log.add(it) },
            progress = { progress(it) },
            bg = { profileUtil.fromMgdlToStringWithUnits(it) }
        )
        return trainer.train(grid, now, days, runs)
    }

    private fun progress(text: String) {
        _status.value = LearnedStatus(LearnedState.TRAINING, text, model)
    }

    private fun updateStatus(training: Boolean = trainingJob?.isActive == true) {
        val current = model
        _status.value = when {
            training                      -> LearnedStatus(LearnedState.TRAINING, _status.value.progress, current)
            current == null               -> LearnedStatus(LearnedState.NO_MODEL)
            current.ready                 -> LearnedStatus(LearnedState.READY, model = current)
            else                          -> LearnedStatus(LearnedState.NOT_READY, model = current)
        }
    }

    private companion object {

        const val DAY_MS = 86_400_000L
        const val LIVE_BACK_MS = 12 * 3_600_000L
        const val LIVE_AHEAD_MS = 2 * 3_600_000L
        const val DEBOUNCE_MS = 5_000L
        const val PREDICTION_LOG_MS = 15 * 60_000L
    }
}
