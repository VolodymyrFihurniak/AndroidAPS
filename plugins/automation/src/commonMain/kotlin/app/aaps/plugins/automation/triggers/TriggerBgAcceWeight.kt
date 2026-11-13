package app.aaps.plugins.automation.triggers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.utils.lenientDouble
import app.aaps.core.utils.lenientStringOrNull
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputWeight
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** autoISF: compares the `bgAccel_ISF_weight` setting with a value. */
class TriggerBgAcceWeight(deps: TriggerDeps) : Trigger(deps) {

    var acceWeight = InputWeight()
    var comparator: Comparator = Comparator(rh)

    constructor(deps: TriggerDeps, triggerBgAcceWeight: TriggerBgAcceWeight) : this(deps) {
        acceWeight = InputWeight(triggerBgAcceWeight.acceWeight.value)
        comparator = Comparator(rh, triggerBgAcceWeight.comparator.value)
    }

    fun setValue(acceWeight: Double): TriggerBgAcceWeight {
        this.acceWeight.value = acceWeight
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerBgAcceWeight {
        this.comparator.value = comparator
        return this
    }

    override suspend fun shouldRun(): Boolean {
        val actualWeight = preferences.get(DoubleKey.ApsAutoIsfBgAccelWeight)
        if (comparator.value.check(actualWeight, acceWeight.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "set bgAccel_ISF_weight ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "set bgAccel_ISF_weight NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("acce_weight", acceWeight.value)
            put("comparator", comparator.value.toString())
        }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        acceWeight.value = d.lenientDouble("acce_weight")
        comparator.setValue(Comparator.Compare.valueOf(d.lenientStringOrNull("comparator")!!))
        return this
    }

    override fun friendlyName(): TextRef = AutomationStrings.autoisf_acce_weight

    override fun friendlyDescription(): String =
        rh.gs(AutomationStrings.acceweightcompared, rh.gs(comparator.value.stringRes), acceWeight.value.toString())

    override fun composeIcon() = Icons.Filled.Speed
    override fun elementType() = ElementType.AUTOMATION

    override fun duplicate(): Trigger = TriggerBgAcceWeight(deps, this)
}
