package app.aaps.implementation.utils

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.ui.extensions.directionToDescription
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class TrendCalculatorImpl(
    private val rh: TextResolver
) : TrendCalculator {

    override fun getTrendArrow(autosensDataStore: AutosensDataStore): TrendArrow? {
        val data = autosensDataStore.getBucketedDataTableCopy() ?: return null
        if (data.isEmpty()) return null
        // autoISF: with 1 minute data the screen shows the newest reading (AutosensDataStore.lastBg), so the
        // arrow follows it every minute, as in AAPS 3.x. It can be only 1 minute after the newest bucket, and
        // a slope over so short a step is mostly noise, so it is compared with a bucket about 5 minutes older.
        val newest = autosensDataStore.newestLoopReading
        if (newest != null && newest.timestamp > data[0].timestamp) {
            val previous = data.firstOrNull { newest.timestamp - it.timestamp >= MIN_TREND_STEP_MS } ?: data[0]
            return direction(newest, previous)
        }
        /* Change 4.0.0 - always calculate from bucketed data
        val glucoseValue = data[0]
        val trend = when {
            glucoseValue.value != glucoseValue.recalculated -> calculateDirection(data) // always recalculate after smoothing
            glucoseValue.trendArrow != TrendArrow.NONE      -> glucoseValue.trendArrow
            else                                            -> calculateDirection(data)
        }
         */
        return calculateDirection(data)
    }

    override fun getTrendDescription(autosensDataStore: AutosensDataStore): String =
        rh.gs(getTrendArrow(autosensDataStore).directionToDescription())

    private fun calculateDirection(readings: MutableList<InMemoryGlucoseValue>): TrendArrow {

        if (readings.size < 2)
            return TrendArrow.NONE
        return direction(readings[0], readings[1])
    }

    private fun direction(current: InMemoryGlucoseValue, previous: InMemoryGlucoseValue): TrendArrow {
        // Avoid division by 0
        val slope =
            if (current.timestamp == previous.timestamp) 0.0
            else (previous.recalculated - current.recalculated) / (previous.timestamp - current.timestamp)

        val slopeByMinute = slope * 60000

        return when {
            slopeByMinute <= -3.5 -> TrendArrow.DOUBLE_DOWN
            slopeByMinute <= -2   -> TrendArrow.SINGLE_DOWN
            slopeByMinute <= -1   -> TrendArrow.FORTY_FIVE_DOWN
            slopeByMinute <= 1    -> TrendArrow.FLAT
            slopeByMinute <= 2    -> TrendArrow.FORTY_FIVE_UP
            slopeByMinute <= 3.5  -> TrendArrow.SINGLE_UP
            slopeByMinute <= 40   -> TrendArrow.DOUBLE_UP
            else                  -> TrendArrow.NONE
        }
    }

    private companion object {

        // autoISF: 5 minutes less the 30 s of jitter the bucketing allows (IRREGULAR_DATA_SEC)
        const val MIN_TREND_STEP_MS = 270_000L
    }
}
