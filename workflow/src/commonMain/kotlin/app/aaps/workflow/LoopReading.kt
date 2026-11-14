package app.aaps.workflow

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GV
import app.aaps.core.objects.extensions.fromGv

/**
 * autoISF: the newest reading for the loop (see `AutosensDataStore.newestLoopReading`), before it is
 * calibrated.
 *
 * Only for data that comes more often than every 5 minutes ([fiveMinData] is false): with 5 minute
 * data the newest bucket is the newest reading already. Null as well when the reading is not newer
 * than the newest bucket, or not a valid value.
 *
 * @param readings newest first, as `AutosensDataStore.bgReadings`
 * @param buckets newest first, as `AutosensDataStore.bucketedData`
 */
internal fun newestReadingAfterBuckets(readings: List<GV>, buckets: List<InMemoryGlucoseValue>, fiveMinData: Boolean?): InMemoryGlucoseValue? {
    if (fiveMinData != false) return null
    val newest = readings.firstOrNull() ?: return null
    val newestBucket = buckets.firstOrNull() ?: return null
    if (newest.timestamp <= newestBucket.timestamp || newest.value < MIN_VALID_BG) return null
    return InMemoryGlucoseValue.fromGv(newest)
}

/**
 * autoISF: the smoothing works on the buckets only. It can not run a second time for the newest reading:
 * the UKF learns from every new value it is given. So the reading is moved by the same amount as the
 * smoothing moved the newest bucket, and lies on the same curve. Without smoothing nothing is moved.
 */
internal fun withSmoothingOffsetOf(reading: InMemoryGlucoseValue, newestBucket: InMemoryGlucoseValue?): InMemoryGlucoseValue {
    val smoothed = newestBucket?.smoothed ?: return reading
    return reading.copy(smoothed = reading.calibratedOrValue + (smoothed - newestBucket.calibratedOrValue))
}

private const val MIN_VALID_BG = 39.0
