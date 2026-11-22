package app.aaps.plugins.aps.learned

import app.aaps.core.interfaces.aps.LearnedForecast
import app.aaps.plugins.aps.learned.grid.BgReading
import app.aaps.plugins.aps.learned.model.Metrics
import app.aaps.plugins.aps.learned.train.StoredRun
import kotlin.math.abs

/**
 * autoISF build, personal prediction: one forecast of the plugin, as kept for the live check. BG now and each curve at
 * 30, 60 and 120 minutes, mg/dl; [gru] is null when the model had no GRU.
 */
data class KeptForecast(val timestamp: Long, val bg: Double, val linear: List<Double>, val gru: List<Double>?) {

    companion object {

        /** Null when the forecast has no point at 30, 60 or 120 minutes */
        fun of(forecast: LearnedForecast): KeptForecast? {
            val linear = LIVE_MINUTES.map { forecast.at(it) ?: return null }
            val gru = LIVE_MINUTES.map { forecast.expectedAt(it) }.takeIf { values -> values.all { it != null } }?.map { it!! }
            return KeptForecast(forecast.timestamp, forecast.bgMgdl, linear, gru)
        }
    }
}

/** The horizons of the live check, minutes */
val LIVE_MINUTES = listOf(30, 60, 120)

/**
 * autoISF build, personal prediction: the live check. How close the plugin's own forecasts came to the real BG 30, 60
 * and 120 minutes later: on all moments, and on the moments with a kept AutoISF prediction next to AutoISF itself.
 * "BG stays the same" is the guess any model has to beat.
 *
 * A model may give a dose once it is better than AutoISF at 60 minutes on at least [MIN_PAIRED_MOMENTS] such moments.
 * It counts the forecasts of the plugin, whichever model made them: a model is trained again every day.
 */
data class LiveScore(
    /** Moments with the real BG at all three points */
    val moments: Int,
    val gru: Metrics?,
    val linear: Metrics?,
    val naive: Metrics?,
    /** Of them, the moments with a kept AutoISF prediction */
    val pairedMoments: Int,
    val pairedGru: Metrics?,
    val pairedLinear: Metrics?,
    val pairedAutoIsf: Metrics?,
    val pairedNaive: Metrics?
) {

    /** Enough moments with an AutoISF prediction to decide whether a model may give a dose */
    val enough: Boolean get() = pairedMoments >= MIN_PAIRED_MOMENTS

    val linearReady: Boolean get() = enough && beats(pairedLinear)

    val gruReady: Boolean get() = enough && beats(pairedGru)

    private fun beats(metrics: Metrics?) = metrics != null && pairedAutoIsf != null && metrics.mae60 < pairedAutoIsf.mae60

    companion object {

        /** 3 days of moments with an AutoISF prediction, half of their 5-minute steps */
        const val MIN_PAIRED_MOMENTS = 3 * 144

        /** The live check looks at the forecasts of the last 7 days */
        const val WINDOW_MS = 7 * 86_400_000L

        /** A real BG reading this close to the time a forecast was for counts */
        private const val BG_MATCH_MS = 180_000L

        /** An AutoISF prediction this close to the time of a forecast counts */
        private const val RUN_MATCH_MS = 300_000L

        /** [forecasts], [runs] and [bg] each sorted by time */
        fun compute(forecasts: List<KeptForecast>, runs: List<StoredRun>, bg: List<BgReading>): LiveScore {
            val all = Sums()
            val paired = Sums()
            for (forecast in forecasts) {
                val actual = LIVE_MINUTES.map { minutes ->
                    bg.nearestTo(forecast.timestamp + minutes * 60_000L, BG_MATCH_MS) { it.timestamp }?.mgdl ?: Double.NaN
                }
                if (actual.any { it.isNaN() }) continue
                all.add(forecast, actual, null)
                val run = runs.nearestTo(forecast.timestamp, RUN_MATCH_MS) { it.timestamp } ?: continue
                paired.add(forecast, actual, listOf(run.at30, run.at60, run.at120))
            }
            return LiveScore(
                moments = all.count, gru = all.gru.metrics(), linear = all.linear.metrics(), naive = all.naive.metrics(),
                pairedMoments = paired.count, pairedGru = paired.gru.metrics(), pairedLinear = paired.linear.metrics(),
                pairedAutoIsf = paired.autoIsf.metrics(), pairedNaive = paired.naive.metrics()
            )
        }
    }

    /** Sums of the absolute errors at the three points, and how many were added */
    private class Sums {

        var count = 0
        val linear = Errors()
        val gru = Errors()
        val naive = Errors()
        val autoIsf = Errors()

        fun add(forecast: KeptForecast, actual: List<Double>, autoIsfValues: List<Double>?) {
            count++
            linear.add(forecast.linear, actual)
            forecast.gru?.let { gru.add(it, actual) }
            naive.add(List(LIVE_MINUTES.size) { forecast.bg }, actual)
            autoIsfValues?.let { autoIsf.add(it, actual) }
        }
    }

    private class Errors {

        private val sum = DoubleArray(LIVE_MINUTES.size)
        private var count = 0

        fun add(predicted: List<Double>, actual: List<Double>) {
            for (k in sum.indices) sum[k] += abs(predicted[k] - actual[k])
            count++
        }

        fun metrics(): Metrics? = if (count == 0) null else Metrics(sum[0] / count, sum[1] / count, sum[2] / count)
    }
}

/** The item nearest to [time] in a list sorted by [timeOf], or null when none is within [maxGapMs] */
internal fun <T> List<T>.nearestTo(time: Long, maxGapMs: Long, timeOf: (T) -> Long): T? {
    if (isEmpty()) return null
    var low = 0
    var high = size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (timeOf(this[mid]) < time) low = mid + 1 else high = mid
    }
    return listOfNotNull(getOrNull(low - 1), getOrNull(low))
        .filter { abs(timeOf(it) - time) <= maxGapMs }
        .minByOrNull { abs(timeOf(it) - time) }
}
