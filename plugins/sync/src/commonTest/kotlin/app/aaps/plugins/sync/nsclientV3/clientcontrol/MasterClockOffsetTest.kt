package app.aaps.plugins.sync.nsclientV3.clientcontrol

import kotlin.test.Test
import kotlin.test.assertEquals

class MasterClockOffsetTest {

    private val now = 1_700_000_000_000L

    @Test
    fun noAckYetMeansNoOffset() {
        assertEquals(0L, MasterClockOffset().offset(now))
    }

    @Test
    fun theLargestRecentSampleWins() {
        val clock = MasterClockOffset()
        // Master 10 s ahead; the ACKs travelled 300 ms and 1200 ms
        clock.addSample(masterTime = now - 60_000L + 10_000L - 300L, clientTime = now - 60_000L)
        clock.addSample(masterTime = now - 30_000L + 10_000L - 1_200L, clientTime = now - 30_000L)

        assertEquals(9_700L, clock.offset(now))
    }

    @Test
    fun aMasterBehindGivesANegativeOffset() {
        val clock = MasterClockOffset()
        clock.addSample(masterTime = now - 20_000L, clientTime = now)

        assertEquals(-20_000L, clock.offset(now))
    }

    @Test
    fun anOldAckReadAgainIsNotAClock() {
        val clock = MasterClockOffset()
        clock.addSample(masterTime = now - 10 * 60_000L, clientTime = now)

        assertEquals(0L, clock.offset(now))
    }

    @Test
    fun withoutRecentSamplesTheNewestOneIsUsed() {
        val clock = MasterClockOffset()
        clock.addSample(masterTime = now - 3 * 3600_000L + 5_000L, clientTime = now - 3 * 3600_000L)
        clock.addSample(masterTime = now - 2 * 3600_000L + 7_000L, clientTime = now - 2 * 3600_000L)

        assertEquals(7_000L, clock.offset(now))
    }

    @Test
    fun onlyTheLastSamplesAreKept() {
        val clock = MasterClockOffset()
        clock.addSample(masterTime = now - 1_000L + 60_000L, clientTime = now - 1_000L)
        repeat(MasterClockOffset.MAX_SAMPLES) { clock.addSample(masterTime = now + 2_000L, clientTime = now) }

        assertEquals(2_000L, clock.offset(now))
    }
}
