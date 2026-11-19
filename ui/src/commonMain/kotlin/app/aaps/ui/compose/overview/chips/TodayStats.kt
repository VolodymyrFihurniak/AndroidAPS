package app.aaps.ui.compose.overview.chips

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * autoISF build: the statistics of today on the overview, worked out from the readings since midnight.
 *
 * Readings below 39 mg/dL are sensor errors and do not count, as in the statistics screen (`TirCalculator`).
 */
data class TodayStats(
    /** Percent of the readings inside the user's range. */
    val timeInRangePercent: Int,
    /** Coefficient of variation in percent: standard deviation / mean. */
    val cvPercent: Double,
    val averageMgdl: Double,
    val count: Int
)

private const val MIN_VALID_MGDL = 39.0

/** Null when there is no valid reading today. */
fun todayStats(valuesMgdl: List<Double>, lowMgdl: Double, highMgdl: Double): TodayStats? {
    val valid = valuesMgdl.filter { it >= MIN_VALID_MGDL }
    if (valid.isEmpty()) return null
    val mean = valid.average()
    val sd = sqrt(valid.sumOf { (it - mean) * (it - mean) } / valid.size)
    val inRange = valid.count { it in lowMgdl..highMgdl }
    return TodayStats(
        timeInRangePercent = (inRange * 100.0 / valid.size).roundToInt(),
        cvPercent = if (mean > 0) sd / mean * 100 else 0.0,
        averageMgdl = mean,
        count = valid.size
    )
}
