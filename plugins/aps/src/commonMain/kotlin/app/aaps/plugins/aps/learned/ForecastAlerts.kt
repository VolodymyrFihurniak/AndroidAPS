package app.aaps.plugins.aps.learned

import app.aaps.core.interfaces.aps.LearnedForecast
import app.aaps.core.interfaces.aps.LearnedPoint
import kotlin.math.abs
import kotlin.math.max

/**
 * autoISF build, personal prediction: when the forecast warns of a low or a high. It uses the GRU curve (where BG
 * really goes, the loop included) and the linear curve only without one. A warning is not a dose, so it does not wait
 * for the live check; the user switches it on.
 *
 * Low: a point below the low mark in the next [LOW_WITHIN_MIN] while BG is not low yet (then other alarms speak).
 * High: a point above the high mark in the next [HIGH_WITHIN_MIN] while BG is not high yet.
 */
object ForecastAlerts {

    const val LOW_WITHIN_MIN = 30
    const val HIGH_WITHIN_MIN = 60

    /** The same warning is not given again sooner than this */
    const val REPEAT_MS = 30 * 60_000L

    enum class Kind { LOW, HIGH }

    /** The first point beyond the mark: its BG in mg/dl and how many minutes from the start of the forecast */
    data class Alert(val kind: Kind, val mgdl: Double, val minutes: Int)

    fun low(forecast: LearnedForecast, lowMgdl: Double): Alert? {
        if (forecast.bgMgdl < lowMgdl) return null
        return first(forecast, LOW_WITHIN_MIN) { it.mgdl < lowMgdl }?.let { Alert(Kind.LOW, it.mgdl, minutesOf(forecast, it)) }
    }

    fun high(forecast: LearnedForecast, highMgdl: Double): Alert? {
        if (forecast.bgMgdl > highMgdl) return null
        return first(forecast, HIGH_WITHIN_MIN) { it.mgdl > highMgdl }?.let { Alert(Kind.HIGH, it.mgdl, minutesOf(forecast, it)) }
    }

    private fun first(forecast: LearnedForecast, withinMin: Int, beyond: (LearnedPoint) -> Boolean): LearnedPoint? {
        val curve = forecast.expectedPoints.ifEmpty { forecast.points }
        return curve.firstOrNull { it.timestamp - forecast.timestamp <= withinMin * 60_000L && beyond(it) }
    }

    private fun minutesOf(forecast: LearnedForecast, point: LearnedPoint) = ((point.timestamp - forecast.timestamp) / 60_000L).toInt()
}

/**
 * autoISF build: a forecast low warns only when it is confirmed. Ported from JugglucoNG
 * (github.com/ctqvva/JugglucoNG, GPL-3.0, docs/alerts/forecast-low-confirmation.md) and made simpler.
 *
 * - **Urgent** lows warn at once: BG at most 15 mg/dl above the low mark and the low within 10 minutes, or insulin
 *   that acts in the next 30 minutes would drop BG by at least half the way to the low mark (and at least 10 mg/dl).
 * - **A fall back after a short rise** waits: a quiet baseline (5 minutes within 6 mg/dl), then a rise of at least
 *   15 mg/dl within 3 to 15 minutes, and no insulin on board. A forecast low is then often only the way back to the
 *   baseline. The wait ends when BG goes more than 3 mg/dl below the baseline, or 15 minutes after the peak.
 * - **Every other** low warns when two readings at least one minute apart both see it.
 *
 * A gap of more than 6 minutes between readings, or a run without a forecast low, starts the confirmation again.
 * Not thread safe: the plugin calls it from one place.
 */
class LowConfirmation {

    private data class Reading(val timestamp: Long, val mgdl: Double)

    private val readings = ArrayDeque<Reading>()
    private var firstSeen: Long? = null

    /**
     * @param alert the forecast low of this run, or null
     * @param timestamp time of the newest BG reading
     * @param iob insulin on board in U, null when not known (then no fall back is assumed)
     * @param insulinNext30 units that act in the next 30 minutes, null when not known
     * @return true when the low should warn now
     */
    fun confirm(alert: ForecastAlerts.Alert?, timestamp: Long, bgMgdl: Double, lowMgdl: Double, iob: Double?, insulinNext30: Double?, isfMgdl: Double): Boolean {
        val previous = readings.lastOrNull()
        if (previous == null || timestamp > previous.timestamp) {
            if (previous != null && timestamp - previous.timestamp > MAX_GAP_MS) {
                readings.clear()
                firstSeen = null
            }
            readings.addLast(Reading(timestamp, bgMgdl))
        }
        while (readings.isNotEmpty() && timestamp - readings.first().timestamp > HISTORY_MS) readings.removeFirst()

        if (alert == null || alert.kind != ForecastAlerts.Kind.LOW) {
            firstSeen = null
            return false
        }
        if (isUrgent(alert, bgMgdl, lowMgdl, insulinNext30, isfMgdl)) return true
        val seen = firstSeen ?: timestamp.also { firstSeen = it }
        if (isReturnToBaseline(timestamp, bgMgdl, iob, insulinNext30)) return false
        return timestamp - seen >= CONFIRM_MS
    }

    private fun isUrgent(alert: ForecastAlerts.Alert, bgMgdl: Double, lowMgdl: Double, insulinNext30: Double?, isfMgdl: Double): Boolean {
        if (bgMgdl <= lowMgdl + 15.0 && alert.minutes <= 10) return true
        if (insulinNext30 != null && isfMgdl > 0.0) {
            val drop = insulinNext30 * isfMgdl
            val distance = bgMgdl - lowMgdl
            if (drop >= max(10.0, distance / 2.0)) return true
        }
        return false
    }

    /** A quiet baseline, a rise from it and no insulin on board: the forecast low may only be the way back */
    private fun isReturnToBaseline(now: Long, bgMgdl: Double, iob: Double?, insulinNext30: Double?): Boolean {
        if (iob == null || insulinNext30 == null) return false
        if (abs(iob) > NO_INSULIN_U || insulinNext30 > NO_INSULIN_U) return false
        val list = readings.toList()
        // the newest baseline that has a rise after it
        for (end in list.indices.reversed()) {
            val endTime = list[end].timestamp
            val window = list.filter { it.timestamp in (endTime - BASELINE_MS)..endTime }
            if (window.size < 2 || endTime - window.first().timestamp < BASELINE_MS - TOLERANCE_MS) continue
            if (window.maxOf { it.mgdl } - window.minOf { it.mgdl } > BASELINE_RANGE) continue
            val baseline = window.map { it.mgdl }.average()
            val after = list.drop(end + 1)
            if (after.isEmpty()) continue
            val top = after.maxOf { it.mgdl }
            val peak = after.first { it.mgdl == top }
            val riseMinutes = (peak.timestamp - endTime) / 60_000.0
            if (top - baseline < MIN_RISE || riseMinutes < 3.0 || riseMinutes > 15.0) continue
            if (now - peak.timestamp > EXPIRE_MS) return false
            return bgMgdl >= baseline - 3.0
        }
        return false
    }

    companion object {

        const val HISTORY_MS = 45 * 60_000L
        const val MAX_GAP_MS = 6 * 60_000L
        const val CONFIRM_MS = 60_000L
        const val BASELINE_MS = 5 * 60_000L
        const val TOLERANCE_MS = 30_000L
        const val BASELINE_RANGE = 6.0
        const val MIN_RISE = 15.0
        const val EXPIRE_MS = 15 * 60_000L
        const val NO_INSULIN_U = 0.05
    }
}
