package app.aaps.core.interfaces.aps

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.time.T
import kotlin.time.Clock

/**
 * autoISF: the bucketed data the loop works with.
 *
 * With 5 minute data this is a copy of [AutosensDataStore.bucketedData]. With 1 minute data the
 * buckets stay on their 5 minute grid, so the newest bucket can be up to 5 minutes older than the
 * newest reading. Then [AutosensDataStore.newestLoopReading] is put in front, and the glucose status
 * of the loop starts at the newest reading, as in AAPS 3.x. The deltas are worked out from the time
 * of each value, not from its position, so the shorter first step does not change them.
 */
fun AutosensDataStore.loopBucketedDataCopy(): MutableList<InMemoryGlucoseValue>? {
    val data = getBucketedDataTableCopy() ?: return null
    val newest = newestLoopReading ?: return data
    if (data.isNotEmpty() && newest.timestamp > data[0].timestamp) data.add(0, newest.copy())
    return data
}

/**
 * autoISF: the newest value the loop works with, or null when there is none from the last
 * [Constants.OLD_BG_MINUTES] minutes. Like [AutosensDataStore.actualBg], but with 1 minute data it
 * changes with every reading, so the loop runs for every reading.
 */
fun AutosensDataStore.actualLoopBg(): InMemoryGlucoseValue? {
    val bucket = actualBg()
    val newest = newestLoopReading ?: return bucket
    val freshFrom = Clock.System.now().toEpochMilliseconds() - T.mins(Constants.OLD_BG_MINUTES).msecs()
    return if (newest.timestamp > freshFrom && newest.timestamp > (bucket?.timestamp ?: 0L)) newest else bucket
}
