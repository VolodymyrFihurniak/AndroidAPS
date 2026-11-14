package app.aaps.workflow

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoopReadingTest {

    private val gridTime = 1_700_000_100_000L

    private fun reading(timestamp: Long, value: Double) =
        GV(timestamp = timestamp, raw = null, value = value, trendArrow = TrendArrow.FLAT, noise = null, sourceSensor = SourceSensor.LIBRE_2)

    private val buckets = listOf(InMemoryGlucoseValue(timestamp = gridTime, value = 120.0))

    @Test
    fun oneMinuteDataGivesTheReadingNewerThanTheNewestBucket() {
        val newest = newestReadingAfterBuckets(listOf(reading(gridTime + 120_000L, 124.0)), buckets, fiveMinData = false)

        assertEquals(gridTime + 120_000L, newest?.timestamp)
        assertEquals(124.0, newest?.value)
        assertEquals(SourceSensor.LIBRE_2, newest?.sourceSensor)
    }

    @Test
    fun fiveMinuteDataGivesNothing() {
        // The newest bucket is the newest reading already
        assertNull(newestReadingAfterBuckets(listOf(reading(gridTime + 120_000L, 124.0)), buckets, fiveMinData = true))
        // Not known yet: nothing either
        assertNull(newestReadingAfterBuckets(listOf(reading(gridTime + 120_000L, 124.0)), buckets, fiveMinData = null))
    }

    @Test
    fun aReadingNotNewerThanTheBucketOrNotValidGivesNothing() {
        assertNull(newestReadingAfterBuckets(listOf(reading(gridTime, 124.0)), buckets, fiveMinData = false))
        assertNull(newestReadingAfterBuckets(listOf(reading(gridTime - 20_000L, 124.0)), buckets, fiveMinData = false))
        assertNull(newestReadingAfterBuckets(listOf(reading(gridTime + 60_000L, 38.0)), buckets, fiveMinData = false))
        assertNull(newestReadingAfterBuckets(emptyList(), buckets, fiveMinData = false))
        assertNull(newestReadingAfterBuckets(listOf(reading(gridTime + 60_000L, 124.0)), emptyList(), fiveMinData = false))
    }

    @Test
    fun theReadingIsMovedLikeTheNewestBucket() {
        val bucket = InMemoryGlucoseValue(timestamp = gridTime, value = 120.0, smoothed = 117.0)
        val newest = InMemoryGlucoseValue(timestamp = gridTime + 60_000L, value = 124.0)

        // The smoothing moved the bucket 3 down
        assertEquals(121.0, withSmoothingOffsetOf(newest, bucket).recalculated)
    }

    @Test
    fun theCalibrationIsKeptAndTheOffsetAddedToIt() {
        val bucket = InMemoryGlucoseValue(timestamp = gridTime, value = 120.0, calibrated = 110.0, smoothed = 112.0)
        val newest = InMemoryGlucoseValue(timestamp = gridTime + 60_000L, value = 124.0, calibrated = 114.0)

        assertEquals(116.0, withSmoothingOffsetOf(newest, bucket).recalculated)
    }

    @Test
    fun withoutSmoothingNothingIsMoved() {
        val newest = InMemoryGlucoseValue(timestamp = gridTime + 60_000L, value = 124.0)

        assertEquals(124.0, withSmoothingOffsetOf(newest, InMemoryGlucoseValue(timestamp = gridTime, value = 120.0)).recalculated)
        assertEquals(124.0, withSmoothingOffsetOf(newest, null).recalculated)
    }
}
