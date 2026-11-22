package app.aaps.plugins.aps.learned

import app.aaps.core.interfaces.aps.LearnedForecast
import app.aaps.core.interfaces.aps.LearnedPoint

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
