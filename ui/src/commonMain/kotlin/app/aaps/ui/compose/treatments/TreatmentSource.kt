package app.aaps.ui.compose.treatments

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.UE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import kotlin.math.abs

/**
 * autoISF build: who gave a bolus or set a temp basal, for the treatment history.
 *
 * Worked out from the user entry log, which records the source of each action. There is no database field for it: a
 * bolus or a TBR has no source of its own, so the user entry of the same action is found by its time (and, for a
 * bolus, its amount).
 */
sealed interface TreatmentSource {

    /** The loop: an SMB or a temp basal it set */
    data object Algorithm : TreatmentSource

    /** An automation rule */
    data object Automation : TreatmentSource

    /**
     * A paired AAPSClient, or Nightscout: both write their actions as [Sources.NSClient]. [name] is the device name of
     * the client, empty for a record from Nightscout or for a client command from before the name was recorded.
     */
    data class Client(val name: String) : TreatmentSource

    /** The user on this phone (or its watch, or SMS), with the screen or tool it came from */
    data class User(val via: Sources) : TreatmentSource
}

/**
 * autoISF build: finds the [TreatmentSource] of boluses and temp basals in [entries]. Built once for a list, because the
 * history holds weeks of records: the entries are sorted once and each look-up is a binary search.
 */
class TreatmentSources(entries: List<UE>) {

    private val bolusEntries = entries.filter { it.action in BOLUS_ACTIONS }.sortedBy { it.timestamp }
    private val tempBasalEntries = entries.filter { it.action == Action.TEMP_BASAL }.sortedBy { it.timestamp }

    /**
     * Who gave [bolus]. An SMB is always the loop. Else the nearest bolus entry in time whose insulin matches; an entry
     * without an amount counts too. Null when no entry fits, as for a bolus read from pump history.
     */
    fun ofBolus(bolus: BS): TreatmentSource? {
        if (bolus.type == BS.Type.SMB) return TreatmentSource.Algorithm
        return bolusEntries.around(bolus.timestamp)
            .filter { entry -> entry.insulin()?.let { abs(it - bolus.amount) < AMOUNT_TOLERANCE } ?: true }
            .minByOrNull { abs(it.timestamp - bolus.timestamp) }
            ?.let { sourceOf(it) }
    }

    /** Who set the temp basal that started at [timestamp]: the nearest temp basal entry in time. */
    fun ofTempBasal(timestamp: Long): TreatmentSource? =
        tempBasalEntries.around(timestamp).minByOrNull { abs(it.timestamp - timestamp) }?.let { sourceOf(it) }

    /** The entries from [ENTRY_BEFORE_MS] before [timestamp] to [ENTRY_AFTER_MS] after it, from a list sorted by time */
    private fun List<UE>.around(timestamp: Long): List<UE> {
        val from = timestamp - ENTRY_BEFORE_MS
        val to = timestamp + ENTRY_AFTER_MS
        var low = 0
        var high = size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (this[mid].timestamp < from) low = mid + 1 else high = mid
        }
        val out = mutableListOf<UE>()
        var i = low
        while (i < size && this[i].timestamp <= to) out += this[i++]
        return out
    }

    private fun sourceOf(entry: UE): TreatmentSource = when (entry.source) {
        Sources.Loop       -> TreatmentSource.Algorithm
        Sources.Automation -> TreatmentSource.Automation
        Sources.NSClient   -> TreatmentSource.Client(entry.values.filterIsInstance<ValueWithUnit.SimpleString>().firstOrNull()?.value.orEmpty())
        else               -> TreatmentSource.User(entry.source)
    }

    private fun UE.insulin(): Double? = values.filterIsInstance<ValueWithUnit.Insulin>().firstOrNull()?.value

    private companion object {

        /** User entries of a bolus, through any of the dialogs, the loop or a client */
        val BOLUS_ACTIONS = setOf(Action.BOLUS, Action.TREATMENT, Action.BOLUS_ADVISOR, Action.SUPERBOLUS_TBR, Action.SMB)

        /**
         * How far before the record its user entry may be. The entry is written when the command is accepted, the
         * record when the pump has delivered, which can take minutes on a slow pump or a long queue.
         */
        const val ENTRY_BEFORE_MS = 10 * 60_000L

        /** How far after the record the entry may be, for records written a little out of order */
        const val ENTRY_AFTER_MS = 2 * 60_000L

        /** The pump may deliver a little less or round to its step */
        const val AMOUNT_TOLERANCE = 0.051
    }
}
