package app.aaps.plugins.aps.learned.grid

import app.aaps.core.data.model.ICfg

/** autoISF build, personal prediction: the length of one step of the grid the model works on */
const val STEP_MS = 5 * 60_000L

/** One CGM reading, mg/dl */
data class BgReading(val timestamp: Long, val mgdl: Double)

/** A bolus or an SMB, with the insulin curve it was given with */
data class InsulinDose(val timestamp: Long, val units: Double, val iCfg: ICfg)

/** A temp basal from [start] to [end]: [rate] in U/h when [isAbsolute], else in % of the profile basal */
data class TempBasalSegment(val start: Long, val end: Long, val rate: Double, val isAbsolute: Boolean)

/** An extended bolus from [start] to [end]. It is always insulin on top of the basal. */
data class ExtendedSegment(val start: Long, val end: Long, val unitsPerHour: Double)

/** Carbs, spread over [durationMs] when that is not 0 */
data class CarbEntry(val timestamp: Long, val grams: Double, val durationMs: Long)

/** A value of a watch or phone at a time: steps in the last 5 minutes, or the heart rate in beats per minute */
data class ActivityReading(val timestamp: Long, val value: Double)

/** The profile at one moment: ISF and target in mg/dl, IC in g/U, basal in U/h, and the insulin of the profile */
data class ProfileValues(val isfMgdl: Double, val ic: Double, val basal: Double, val targetMgdl: Double, val iCfg: ICfg)

/** The profile that was active at a time, null when there was none */
fun interface ProfileTimeline {

    fun valuesAt(timestamp: Long): ProfileValues?
}

/**
 * Everything the personal prediction learns from, as plain lists. Read from the database by `HistoryReader`, or
 * made up by the tests.
 */
data class HistoryInput(
    val bg: List<BgReading>,
    val doses: List<InsulinDose>,
    val tempBasals: List<TempBasalSegment>,
    val extended: List<ExtendedSegment>,
    val carbs: List<CarbEntry>,
    val profile: ProfileTimeline,
    /** Times of a sensor change or start */
    val sensorStarts: List<Long>,
    /** Times of a calibration */
    val calibrations: List<Long>,
    /** Steps in the 5 minutes before each time, from a watch or the phone; empty without them */
    val steps: List<ActivityReading> = emptyList(),
    /** Heart rate, from a watch; empty without one */
    val heartRate: List<ActivityReading> = emptyList()
)
