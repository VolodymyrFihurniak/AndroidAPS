package app.aaps.core.interfaces.aps

import app.aaps.core.data.iob.InMemoryGlucoseValue

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
