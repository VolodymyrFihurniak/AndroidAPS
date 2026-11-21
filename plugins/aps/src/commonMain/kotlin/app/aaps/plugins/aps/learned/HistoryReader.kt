package app.aaps.plugins.aps.learned

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.utils.MidnightUtils
import app.aaps.plugins.aps.learned.grid.BgReading
import app.aaps.plugins.aps.learned.grid.CarbEntry
import app.aaps.plugins.aps.learned.grid.ExtendedSegment
import app.aaps.plugins.aps.learned.grid.HistoryInput
import app.aaps.plugins.aps.learned.grid.InsulinDose
import app.aaps.plugins.aps.learned.grid.ProfileTimeline
import app.aaps.plugins.aps.learned.grid.ProfileValues
import app.aaps.plugins.aps.learned.grid.TempBasalSegment
import app.aaps.plugins.aps.learned.train.StoredRun
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlin.math.min

/**
 * autoISF build, personal prediction: reads the history from the database into a [HistoryInput].
 *
 * Long ranges are read in chunks of [CHUNK_MS], so two years of 1-minute BG never sit in memory as database objects
 * at once: each chunk becomes plain values before the next one is read.
 */
@SingleIn(AppScope::class)
@Inject
class HistoryReader(private val persistenceLayer: PersistenceLayer) {

    /** Everything from [from] to [to]. Temp basals and extended boluses that started before [from] are included. */
    suspend fun read(from: Long, to: Long): HistoryInput = read(from, to, liveAt = null)

    /**
     * The last 12 hours before [now], for a live forecast, and the BG of the 7 days before, for the context of the
     * expected curve. Nothing after [now] is read, except the running temp basal, which keeps its planned end. The
     * target of a running temp target replaces the profile target.
     *
     * The older BG is read again at most once an hour: with a 1-minute sensor a forecast is made every minute, and
     * 7 days are about 10 000 readings.
     */
    suspend fun readLive(now: Long): HistoryInput {
        val liveFrom = now - LIVE_BACK_MS
        val input = read(liveFrom, now, liveAt = now)
        return input.copy(bg = olderBg(now, liveFrom) + input.bg)
    }

    private val olderBgLock = Mutex()
    private var olderBg: List<BgReading> = emptyList()
    private var olderBgReadAt: Long? = null

    /** BG from [CONTEXT_BACK_MS] before [now] up to [liveFrom] */
    private suspend fun olderBg(now: Long, liveFrom: Long): List<BgReading> = olderBgLock.withLock {
        val readAt = olderBgReadAt
        if (readAt == null || now < readAt || now - readAt >= OLDER_BG_REFRESH_MS) {
            // Up to the live part of an hour later, so the two parts meet until the next read
            val readings = mutableListOf<BgReading>()
            chunks(now - CONTEXT_BACK_MS, liveFrom + OLDER_BG_REFRESH_MS) { start, end ->
                persistenceLayer.getBgReadingsDataFromTimeToTime(start, end, true)
                    .filter { it.isValid }
                    .mapTo(readings) { BgReading(it.timestamp, it.value) }
            }
            olderBg = readings.distinctBy { it.timestamp }
            olderBgReadAt = now
        }
        val from = now - CONTEXT_BACK_MS
        olderBg.filter { it.timestamp >= from && it.timestamp < liveFrom }
    }

    /** The stored loop runs of [from]..[to], one day at a time, as [StoredRun] with their main curve */
    suspend fun storedRuns(from: Long, to: Long): List<StoredRun> {
        val out = mutableListOf<StoredRun>()
        var start = from
        while (start < to) {
            val end = min(to, start + DAY_MS)
            for (run in persistenceLayer.getApsResults(start, end)) {
                val rt = run.algorithmResult ?: continue
                val predictions = rt.predBGs ?: continue
                val cob = predictions.COB?.takeIf { (rt.COB ?: 0.0) > 0.0 && it.size > 1 }
                val curve = cob ?: predictions.UAM?.takeIf { it.size > 1 } ?: predictions.IOB?.takeIf { it.size > 1 } ?: continue
                // Point 0 of an oref curve is BG now
                out += StoredRun(run.date, curve.drop(1).map { it.toDouble() })
            }
            start = end
            yield()
        }
        return out
    }

    private suspend fun read(from: Long, to: Long, liveAt: Long?): HistoryInput {
        val bg = mutableListOf<BgReading>()
        val doses = mutableListOf<InsulinDose>()
        val carbs = mutableListOf<CarbEntry>()
        val tempBasals = mutableListOf<TempBasalSegment>()
        val extended = mutableListOf<ExtendedSegment>()
        chunks(from, to) { start, end ->
            persistenceLayer.getBgReadingsDataFromTimeToTime(start, end, true)
                .filter { it.isValid }
                .mapTo(bg) { BgReading(it.timestamp, it.value) }
            persistenceLayer.getBolusesFromTimeToTime(start, end, true)
                // Priming fills the cannula and never reaches the body
                .filter { it.isValid && !it.isBasalInsulin && it.type != BS.Type.PRIMING && it.amount > 0.0 }
                .mapTo(doses) { InsulinDose(it.timestamp, it.amount, it.iCfg) }
            persistenceLayer.getCarbsFromTimeToTimeExpanded(start, end, true)
                .filter { it.isValid && it.amount > 0.0 }
                .mapTo(carbs) { CarbEntry(it.timestamp, it.amount, it.duration) }
        }
        // Temp basals and extended boluses by their start, from a day earlier, so the ones running at [from] count
        chunks(from - DAY_MS, to) { start, end ->
            persistenceLayer.getTemporaryBasalsStartingFromTimeToTime(start, end, true)
                .filter { it.isValid && it.timestamp + it.duration > from }
                .mapTo(tempBasals) { TempBasalSegment(it.timestamp, it.timestamp + it.duration, it.rate, it.isAbsolute) }
            persistenceLayer.getExtendedBolusesStartingFromTimeToTime(start, end, true)
                .filter { it.isValid && it.timestamp + it.duration > from && it.duration > 0L }
                .mapTo(extended) { ExtendedSegment(it.timestamp, it.timestamp + it.duration, it.amount / (it.duration / HOUR_MS)) }
        }
        val sensorStarts = persistenceLayer.getTherapyEventDataFromToTime(from - SENSOR_LOOK_BACK_MS, to)
            .filter { it.isValid && (it.type == TE.Type.SENSOR_CHANGE || it.type == TE.Type.SENSOR_STARTED) }
            .map { it.timestamp }
        val calibrations = persistenceLayer.getValidCalibrationEntriesSince(from - SENSOR_LOOK_BACK_MS)
            .filter { it.timestamp < to }
            .map { it.timestamp }
        val switches = (listOfNotNull(persistenceLayer.getEffectiveProfileSwitchActiveAt(from)) +
            persistenceLayer.getEffectiveProfileSwitchesFromTimeToTime(from, to, true))
            .filter { it.isValid }
            .distinctBy { it.timestamp }
            .sortedBy { it.timestamp }
        val tempTarget = liveAt?.let { persistenceLayer.getTemporaryTargetActiveAt(it) }
        return HistoryInput(
            bg = bg,
            doses = doses,
            tempBasals = tempBasals,
            extended = extended,
            carbs = carbs,
            profile = timeline(switches, tempTarget),
            sensorStarts = sensorStarts,
            calibrations = calibrations
        )
    }

    private suspend fun chunks(from: Long, to: Long, block: suspend (Long, Long) -> Unit) {
        var start = from
        while (start < to) {
            val end = min(to, start + CHUNK_MS)
            block(start, end)
            start = end
            yield()
        }
    }

    /** The profile at a time: the newest switch at or before it. A temp target, when given, replaces the target while it runs. */
    private fun timeline(switches: List<EPS>, tempTarget: TT?): ProfileTimeline {
        val profiles = switches.map { ProfileSealed.EPS(it, null) }
        return ProfileTimeline { timestamp ->
            var low = 0
            var high = switches.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (switches[mid].timestamp <= timestamp) low = mid + 1 else high = mid
            }
            val index = low - 1
            if (index < 0) return@ProfileTimeline null
            val profile = profiles[index]
            val seconds = MidnightUtils.secondsFromMidnight(timestamp)
            val target = tempTarget?.takeIf { timestamp >= it.timestamp && timestamp < it.timestamp + it.duration }
                ?.let { (it.lowTarget + it.highTarget) / 2.0 }
                ?: ((profile.getTargetLowMgdl(timestamp) + profile.getTargetHighMgdl(timestamp)) / 2.0)
            ProfileValues(
                isfMgdl = profile.getIsfMgdlTimeFromMidnight(seconds),
                ic = profile.getIcTimeFromMidnight(seconds),
                basal = profile.getBasal(timestamp),
                targetMgdl = target,
                iCfg = switches[index].iCfg
            )
        }
    }

    private companion object {

        const val HOUR_MS = 3_600_000.0
        const val DAY_MS = 86_400_000L
        const val CHUNK_MS = 7 * DAY_MS
        const val LIVE_BACK_MS = 12 * 3_600_000L

        /** 7 days for the context of the expected curve, 2 hours more than the live grid needs */
        const val CONTEXT_BACK_MS = 7 * DAY_MS + 3 * 3_600_000L
        const val OLDER_BG_REFRESH_MS = 3_600_000L

        /** A sensor start or calibration just before the range still excludes the first steps of it */
        const val SENSOR_LOOK_BACK_MS = 3 * 3_600_000L
    }
}
