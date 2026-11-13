package app.aaps.core.data.model

import app.aaps.core.data.time.systemUtcOffsetAt

/**
 * AutoISF key values of one loop run, kept for the AutoISF graphs.
 *
 * Each factor is the ratio autoISF used for that part of the calculation, 1.0 means "no change".
 */
data class AIV(
    override var id: Long = 0,
    /** Milliseconds since the epoch. Time of the loop run that calculated the values. */
    override var timestamp: Long,
    var acceIsf: Double,
    var bgIsf: Double,
    var ppIsf: Double,
    /** Place holder, not calculated yet. */
    var driftIsf: Double,
    var duraIsf: Double,
    var finalIsf: Double,
    var iobThEffective: Double,
    var utcOffset: Long = systemUtcOffsetAt(timestamp),
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    override var ids: IDs = IDs()
) : HasIDs, TimeStamped {

    fun contentEqualsTo(other: AIV): Boolean {
        return this === other || (
            timestamp == other.timestamp &&
                acceIsf == other.acceIsf &&
                bgIsf == other.bgIsf &&
                ppIsf == other.ppIsf &&
                driftIsf == other.driftIsf &&
                duraIsf == other.duraIsf &&
                finalIsf == other.finalIsf &&
                iobThEffective == other.iobThEffective &&
                isValid == other.isValid)
    }

    companion object {

        /** Values of a run where autoISF changed nothing. */
        fun neutral(timestamp: Long): AIV = AIV(
            timestamp = timestamp,
            acceIsf = 1.0,
            bgIsf = 1.0,
            ppIsf = 1.0,
            driftIsf = 1.0,
            duraIsf = 1.0,
            finalIsf = 1.0,
            iobThEffective = 0.0
        )
    }
}
