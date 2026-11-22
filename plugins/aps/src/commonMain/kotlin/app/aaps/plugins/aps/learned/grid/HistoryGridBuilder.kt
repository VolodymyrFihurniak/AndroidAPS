package app.aaps.plugins.aps.learned.grid

import app.aaps.core.data.model.ICfg
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Instant

/**
 * autoISF build, personal prediction: turns a [HistoryInput] into a [HistoryGrid].
 *
 * Rules, the same for training and for the live prediction:
 * - BG: the reading closest to the step time within [BG_MATCH_MS]. Readings outside [MIN_BG]..[MAX_BG] are errors of
 *   the sensor and are left out.
 * - A bolus or SMB goes to the step of its time.
 * - A temp basal adds, per step, its rate minus the profile basal of that step, times the time it covers. So a temp
 *   basal at the profile basal is 0, and a suspended pump is minus the basal. An extended bolus adds its own rate.
 *   Both use the insulin curve of the profile.
 * - Carbs with a duration are spread evenly over it.
 * - [SENSOR_WARMUP_MS] after a sensor start and [CALIBRATION_MS] after a calibration are excluded from learning.
 */
object HistoryGridBuilder {

    const val BG_MATCH_MS = 3 * 60_000L
    const val MIN_BG = 39.0
    const val MAX_BG = 400.0
    const val SENSOR_WARMUP_MS = 2 * 3_600_000L
    const val CALIBRATION_MS = 30 * 60_000L

    private const val HOUR_MS = 3_600_000.0
    private const val DAY_S = 86_400.0

    /**
     * The grid from [from] to [to], with the local time of day in [timeZone]. With [alignToStep] the first step is
     * [from] down to a whole 5 minutes; without it the first step is exactly [from], so a live grid can end exactly at
     * the newest reading and a 1-minute sensor can be learned in 5 shifted phases (see [Phases]).
     */
    fun build(input: HistoryInput, from: Long, to: Long, timeZone: TimeZone, alignToStep: Boolean = true): HistoryGrid {
        val start = if (alignToStep) from.floorDiv(STEP_MS) * STEP_MS else from
        val size = max(0L, (to - start + STEP_MS - 1) / STEP_MS).toInt()
        val end = start + size * STEP_MS
        fun indexOf(timestamp: Long) = ((timestamp - start).floorDiv(STEP_MS)).toInt()

        val profiles = Array(size) { input.profile.valuesAt(start + it * STEP_MS) }
        val curves = mutableListOf<ICfg>()
        val insulin = mutableListOf<DoubleArray>()
        fun curveOf(iCfg: ICfg): DoubleArray {
            val index = curves.indexOfFirst { it.insulinPeakTime == iCfg.insulinPeakTime && it.insulinEndTime == iCfg.insulinEndTime }
            if (index >= 0) return insulin[index]
            curves += iCfg
            return DoubleArray(size).also { insulin += it }
        }

        for (dose in input.doses) {
            val i = indexOf(dose.timestamp)
            if (dose.units <= 0.0 || i !in 0 until size) continue
            curveOf(dose.iCfg)[i] += dose.units
        }

        // Spreads a piece of time over the steps it covers: block(step, hours covered in that step)
        fun overSteps(from: Long, to: Long, block: (Int, Double) -> Unit) {
            val s = max(from, start)
            val e = min(to, end)
            if (e <= s) return
            for (i in indexOf(s)..indexOf(e - 1)) {
                val stepStart = start + i * STEP_MS
                val covered = min(e, stepStart + STEP_MS) - max(s, stepStart)
                if (covered > 0) block(i, covered / HOUR_MS)
            }
        }

        for (tbr in input.tempBasals) overSteps(tbr.start, tbr.end) { i, hours ->
            val profile = profiles[i] ?: return@overSteps
            val rate = if (tbr.isAbsolute) tbr.rate else profile.basal * tbr.rate / 100.0
            curveOf(profile.iCfg)[i] += (rate - profile.basal) * hours
        }
        for (extended in input.extended) overSteps(extended.start, extended.end) { i, hours ->
            val profile = profiles[i] ?: return@overSteps
            curveOf(profile.iCfg)[i] += extended.unitsPerHour * hours
        }

        val carbs = DoubleArray(size)
        for (entry in input.carbs) {
            if (entry.grams <= 0.0) continue
            if (entry.durationMs <= 0L) {
                val i = indexOf(entry.timestamp)
                if (i in 0 until size) carbs[i] += entry.grams
            } else {
                val perHour = entry.grams / (entry.durationMs / HOUR_MS)
                overSteps(entry.timestamp, entry.timestamp + entry.durationMs) { i, hours -> carbs[i] += perHour * hours }
            }
        }

        val excluded = BooleanArray(size)
        fun exclude(from: Long, length: Long) = overSteps(from, from + length) { i, _ -> excluded[i] = true }
        input.sensorStarts.forEach { exclude(it, SENSOR_WARMUP_MS) }
        input.calibrations.forEach { exclude(it, CALIBRATION_MS) }

        return HistoryGrid(
            start = start,
            size = size,
            bg = bgOnGrid(input.bg, start, size),
            curves = curves,
            insulin = insulin,
            carbs = carbs,
            isf = DoubleArray(size) { profiles[it]?.isfMgdl ?: Double.NaN },
            ic = DoubleArray(size) { profiles[it]?.ic ?: Double.NaN },
            basal = DoubleArray(size) { profiles[it]?.basal ?: Double.NaN },
            target = DoubleArray(size) { profiles[it]?.targetMgdl ?: Double.NaN },
            excluded = excluded,
            dayAngle = DoubleArray(size) { dayAngle(start + it * STEP_MS, timeZone) },
            steps = stepsOnGrid(input.steps, start, size),
            heartRate = meanOnGrid(input.heartRate, start, size)
        )
    }

    /**
     * Steps per step of the grid. A reading holds the steps of the 5 minutes before it, so it belongs to the step it
     * ends in; with readings every minute their 5-minute windows overlap, and the largest one of a step is taken.
     */
    private fun stepsOnGrid(readings: List<ActivityReading>, start: Long, size: Int): DoubleArray {
        val out = DoubleArray(size) { Double.NaN }
        for (reading in readings) {
            val i = ((reading.timestamp - 1 - start).floorDiv(STEP_MS)).toInt()
            if (i !in 0 until size || reading.value < 0.0) continue
            out[i] = if (out[i].isNaN()) reading.value else max(out[i], reading.value)
        }
        return out
    }

    /** The mean of the readings in each step of the grid, NaN for a step without one */
    private fun meanOnGrid(readings: List<ActivityReading>, start: Long, size: Int): DoubleArray {
        val sum = DoubleArray(size)
        val count = IntArray(size)
        for (reading in readings) {
            val i = ((reading.timestamp - start).floorDiv(STEP_MS)).toInt()
            if (i !in 0 until size || reading.value <= 0.0) continue
            sum[i] += reading.value
            count[i]++
        }
        return DoubleArray(size) { if (count[it] > 0) sum[it] / count[it] else Double.NaN }
    }

    /** Local time of day of [timestamp] as an angle: 0 at midnight, PI at noon */
    fun dayAngle(timestamp: Long, timeZone: TimeZone): Double {
        val local = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(timeZone)
        val seconds = local.hour * 3600 + local.minute * 60 + local.second
        return 2 * PI * seconds / DAY_S
    }

    private fun bgOnGrid(readings: List<BgReading>, start: Long, size: Int): DoubleArray {
        val valid = readings.filter { it.mgdl in MIN_BG..MAX_BG }.sortedBy { it.timestamp }
        val bg = DoubleArray(size) { Double.NaN }
        var first = 0
        for (i in 0 until size) {
            val time = start + i * STEP_MS
            while (first < valid.size && valid[first].timestamp < time - BG_MATCH_MS) first++
            var best = Long.MAX_VALUE
            var j = first
            while (j < valid.size && valid[j].timestamp <= time + BG_MATCH_MS) {
                val distance = abs(valid[j].timestamp - time)
                if (distance < best) {
                    best = distance
                    bg[i] = valid[j].mgdl
                }
                j++
            }
        }
        return bg
    }
}
