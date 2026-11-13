package app.aaps.plugins.source

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.DoubleNonKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.aps.LibreCalibration
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.objects.workflow.MetroWorkerCreator
import app.aaps.core.ui.compose.icons.IcXDrip
import app.aaps.core.utils.receivers.DataInbox
import app.aaps.core.utils.receivers.Inbox
import app.aaps.plugins.source.compose.BgSourceComposeContent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(400)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<XDripSource>())
@Inject
class XdripSourcePlugin(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    config: Config,
    notificationManager: NotificationManager
) : AbstractBgSourceWithSensorInsertLogPlugin(
    pluginDescription = PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .composeContent { plugin ->
            BgSourceComposeContent(
                title = rh.gs(R.string.source_xdrip)
            )
        }
        .icon(IcXDrip)
        .pluginName(TextRef.AndroidRes(R.string.source_xdrip))
        .preferencesVisibleInSimpleMode(false)
        .description(TextRef.AndroidRes(R.string.description_source_xdrip)),
    aapsLogger = aapsLogger,
    rh = rh,
    preferences = preferences,
    notificationManager = notificationManager
), BgSource, XDripSource {

    override var sensorBatteryLevel = -1

    // cannot be inner class because of needed injection

    @AssistedInject
    class XdripSourceWorker(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        aapsLogger: AAPSLogger,
        fabricPrivacy: FabricPrivacy,
        private val xdripSourcePlugin: XdripSourcePlugin,
        private val persistenceLayer: PersistenceLayer,
        private val preferences: Preferences,
        private val dateUtil: DateUtil,
        private val dataInbox: DataInbox,
        private val profileUtil: ProfileUtil
    ) : LoggingWorker(context, params, Dispatchers.IO, aapsLogger, fabricPrivacy) {

        /**
         * Metro builds this worker. The parameter names must match [MetroWorkerCreator],
         * because Metro matches assisted parameters by name and not only by type.
         */
        @AssistedFactory
        fun interface Factory : MetroWorkerCreator {

            override fun create(context: Context, params: WorkerParameters): XdripSourceWorker
        }

        fun getSensorStartTime(bundle: Bundle): Long? {
            val now = dateUtil.now()
            var sensorStartTime: Long? = if (preferences.get(BooleanKey.BgSourceCreateSensorChange)) {
                bundle.getLong(Intents.EXTRA_SENSOR_STARTED_AT, 0)
            } else {
                null
            }
            // check start time validity
            sensorStartTime?.let {
                if (abs(it - now) > T.months(1).msecs() || it > now) sensorStartTime = null
            }
            return sensorStartTime
        }

        @SuppressLint("CheckResult")
        override suspend fun doWorkAndLog(): Result {
            // Drain first, unconditionally: drain() clears DataInbox's pending-work gate, so every
            // enqueued worker MUST reach it. If we returned early (plugin disabled) before draining,
            // the gate would stay set and silently block all future enqueues for this slot until
            // the process restarts. Bundles drained while disabled are intentionally discarded.
            val bundles = dataInbox.drain(XdripInbox)
            if (!xdripSourcePlugin.isEnabled()) return Result.success(workDataOf("Result" to "Plugin not enabled"))
            if (bundles.isEmpty()) return Result.success(workDataOf("Result" to "no data"))

            var hadFailure = false
            for ((index, bundle) in bundles.withIndex()) {
                try {
                    processBundle(bundle)
                } catch (e: CancellationException) {
                    // WorkManager stopped this run (e.g. chain overload / run-attempt limit). The
                    // coroutine contract requires CancellationException to propagate, otherwise the
                    // loop keeps fighting a cancelled Job and spams failures for every remaining
                    // bundle. drain() already removed the whole batch, so re-queue this bundle plus
                    // everything not yet processed before propagating — otherwise those readings are
                    // lost. requeue/enqueue are non-suspending, so they complete despite cancellation.
                    dataInbox.requeue(XdripInbox, bundles.subList(index, bundles.size))
                    throw e
                } catch (e: Exception) {
                    // processBundle early-returns on malformed-bundle conditions; anything that
                    // reaches the catch is a real exception (typically a DB write). Surface as
                    // failure so WorkInfo reflects the truth.
                    aapsLogger.error(LTag.BGSOURCE, "Failed processing xDrip bundle", e)
                    hadFailure = true
                }
            }
            return if (hadFailure) Result.failure(workDataOf("Error" to "one or more bundles failed")) else Result.success()
        }

        /**
         * autoISF: the 1 minute values of Libre 2/3 (and G7) come from xDrip+ without a raw value. They are
         * calibrated with the offset and slope of the Libre special settings and smoothed with a 1st order
         * exponential filter. The calibrated value is kept in `raw`, the value from xDrip+ in `noise`, see
         * `GlucoseStatusCalculatorAutoIsf`. All other readings are stored as before.
         */
        private fun calibratedLibreValue(bundle: Bundle): GV? {
            val sourceCGM = bundle.getString(Intents.XDRIP_DATA_SOURCE) ?: ""
            val extraRaw = bundle.getDouble(Intents.EXTRA_RAW, 0.0)
            if (extraRaw != 0.0 || sourceCGM !in listOf("Libre2", "Libre2 Native", "Libre3", "G7")) return null

            val now = dateUtil.now()
            LibreCalibration.handleTrigger(preferences, now)
            val calibrationMinutes = LibreCalibration.remainingMinutes(preferences, now)
            if (LibreCalibration.stopsSmb(preferences, now))
                aapsLogger.debug(LTag.BGSOURCE, "Sensor calibrating for another ${calibrationMinutes}m")

            val fromXdrip = bundle.getDouble(Intents.EXTRA_BG_ESTIMATE, 0.0)
            val offset = preferences.get(DoubleKey.FslCalOffset)
            val slope = preferences.get(DoubleKey.FslCalSlope)
            val factor = preferences.get(DoubleKey.FslSmoothAlpha)
            val lastSmooth = preferences.get(DoubleNonKey.FslLastSmooth)
            val lastTimeRaw = preferences.get(LongNonKey.FslSmoothLastTimeRaw)
            val thisTimeRaw = bundle.getLong(Intents.EXTRA_TIMESTAMP, 0)
            val elapsedMinutes = (thisTimeRaw - lastTimeRaw) / 60000.0
            val offsetMgdl = offset * if (profileUtil.units == GlucoseUnit.MMOL) Constants.MMOLL_TO_MGDL else 1.0
            val calibrated = max(40.0, fromXdrip * slope + offsetMgdl)
            val maxGap = 20.0
            val cgmDelta = if (sourceCGM == "G7") 5.0 else 1.0
            // No smoothing just after the start of a calibration, and less smoothing after a gap
            val effectiveAlpha =
                if (LibreCalibration.DURATION_MINUTES - calibrationMinutes < 2 && !preferences.get(BooleanKey.FslCalibrationEnd)) 1.0
                else min(1.0, factor + (1.0 - factor) * (max(0.0, elapsedMinutes - cgmDelta) / (maxGap - cgmDelta)).pow(2.0))
            // exponential smoothing, see https://en.wikipedia.org/wiki/Exponential_smoothing
            val smooth = if (lastSmooth > 0.0) lastSmooth + effectiveAlpha * (calibrated - lastSmooth) else calibrated
            preferences.put(DoubleNonKey.FslLastRaw, calibrated)
            preferences.put(DoubleNonKey.FslLastSmooth, smooth)
            preferences.put(LongNonKey.FslSmoothLastTimeRaw, thisTimeRaw)
            aapsLogger.debug(
                LTag.BGSOURCE,
                "Calibration json: {\"calibration_offset\":$offset,\"calibration_slope\":$slope,\"smoothFactor\":$factor,\"effectiveAlpha\":$effectiveAlpha" +
                    ",\"calibrationStart\":${preferences.get(LongNonKey.FslCalibrationStart)},\"calibrationIgnore\":${preferences.get(BooleanKey.FslCalibrationEnd)}}"
            )
            return GV(
                timestamp = thisTimeRaw,
                value = smooth,
                raw = calibrated,
                noise = fromXdrip,
                trendArrow = TrendArrow.fromString(bundle.getString(Intents.EXTRA_BG_SLOPE_NAME)),
                sourceSensor = SourceSensor.fromString(sourceCGM)
            )
        }

        private suspend fun processBundle(bundle: Bundle) {
            aapsLogger.debug(LTag.BGSOURCE, "Received xDrip data: $bundle")
            val glucoseValues = mutableListOf<GV>()
            glucoseValues += calibratedLibreValue(bundle) ?: GV(
                timestamp = bundle.getLong(Intents.EXTRA_TIMESTAMP, 0),
                value = round(bundle.getDouble(Intents.EXTRA_BG_ESTIMATE, 0.0)),
                raw = round(bundle.getDouble(Intents.EXTRA_RAW, 0.0)),
                noise = null,
                trendArrow = TrendArrow.fromString(bundle.getString(Intents.EXTRA_BG_SLOPE_NAME)),
                sourceSensor = SourceSensor.fromString(bundle.getString(Intents.XDRIP_DATA_SOURCE) ?: "")
            )
            val newSensorStartTime = getSensorStartTime(bundle)
            // Retrieve last stored sensorStartTime from the database
            val lastTherapyEvent = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)
            val lastStoredSensorStartTime = lastTherapyEvent?.timestamp
            // Decide whether to update sensorStartTime or keep the last stored one
            val finalSensorStartTime = when {
                lastStoredSensorStartTime != null && newSensorStartTime != null &&
                    abs(newSensorStartTime - lastStoredSensorStartTime) <= 300_000 -> {
                    aapsLogger.debug(LTag.BGSOURCE, "Sensor start time is within 5 minutes range, skipping update.")
                    null
                }

                lastStoredSensorStartTime != null && newSensorStartTime != null &&
                    newSensorStartTime < lastStoredSensorStartTime                 -> {
                    aapsLogger.debug(LTag.BGSOURCE, "Sensor start time is older than last stored time, skipping update.")
                    null
                }

                else                                                               -> newSensorStartTime
            }
            // Always update glucoseValues, but use the decided sensorStartTime
            if (glucoseValues[0].timestamp > 0 && glucoseValues[0].value > 0.0) {
                persistenceLayer.insertCgmSourceData(Sources.Xdrip, glucoseValues, emptyList(), finalSensorStartTime)
            } else {
                aapsLogger.warn(LTag.BGSOURCE, "Skipping xDrip bundle: missing glucoseValue")
                return
            }
            xdripSourcePlugin.sensorBatteryLevel = bundle.getInt(Intents.EXTRA_SENSOR_BATTERY, -1)
        }
    }
}

object XdripInbox : Inbox<Bundle>("xdrip-bg", XdripSourcePlugin.XdripSourceWorker::class.java)
