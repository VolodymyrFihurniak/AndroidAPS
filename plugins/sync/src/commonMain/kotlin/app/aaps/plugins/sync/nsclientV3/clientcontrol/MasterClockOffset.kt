package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import kotlin.math.abs

/**
 * autoISF build: how far the clock of the master is ahead of the clock of this client, in ms.
 *
 * A command is valid for a few seconds only (`validUntil`), and the master checks that time with its own
 * clock. Phones are often some seconds apart, so a master ahead by 8 s or more answered every command
 * with "expired", and a master behind could apply a command after this client had stopped waiting.
 * So the client gives `validUntil` in the clock of the master.
 *
 * The master signs the time it wrote each ACK. One ACK arriving here gives `masterTime - clientTime`,
 * which is the real offset minus the time the ACK travelled. The travel time only makes a sample
 * smaller, so the largest recent sample is the best estimate, and any error makes a command expire a
 * little earlier, never later.
 */
internal class MasterClockOffset {

    private class Sample(val offset: Long, val receivedAt: Long)

    private val lock = AapsLock()
    private val samples = ArrayDeque<Sample>()

    /** A signed ACK the master wrote at [masterTime] arrived here, live, at [clientTime]. */
    fun addSample(masterTime: Long, clientTime: Long) {
        val offset = masterTime - clientTime
        // Outside of what the master accepts at all (±5 min): an old ACK read again, not a clock
        if (abs(offset) > MAX_OFFSET_MS) return
        lock.withLock {
            samples.addLast(Sample(offset, clientTime))
            while (samples.size > MAX_SAMPLES) samples.removeFirst()
        }
    }

    /**
     * The offset to add to the time of this client to get the time of the master. The largest sample of
     * the last [RECENT_MS], else the newest sample (the clocks drift slowly), else 0 before any ACK.
     */
    fun offset(now: Long): Long = lock.withLock {
        samples.filter { now - it.receivedAt in 0..RECENT_MS }.maxOfOrNull { it.offset }
            ?: samples.lastOrNull()?.offset
            ?: 0L
    }

    companion object {

        const val MAX_SAMPLES = 10
        const val RECENT_MS = 30 * 60 * 1000L
        const val MAX_OFFSET_MS = 4 * 60 * 1000L
    }
}
