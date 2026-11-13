package app.aaps.plugins.automation.triggers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerticalAlignTop
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.utils.lenientInt
import app.aaps.core.utils.lenientStringOrNull
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputIobTH
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** autoISF: compares the IOB threshold setting (`iob_threshold_percent`) with a value. */
class TriggerIobTH(deps: TriggerDeps) : Trigger(deps) {

    var iobTHpercent = InputIobTH()
    var comparator: Comparator = Comparator(rh)

    constructor(deps: TriggerDeps, triggerIobTH: TriggerIobTH) : this(deps) {
        iobTHpercent = InputIobTH(triggerIobTH.iobTHpercent.value)
        comparator = Comparator(rh, triggerIobTH.comparator.value)
    }

    fun setValue(iobTHpercent: Int): TriggerIobTH {
        this.iobTHpercent.value = iobTHpercent
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerIobTH {
        this.comparator.value = comparator
        return this
    }

    override suspend fun shouldRun(): Boolean {
        val actualPercent = preferences.get(IntKey.ApsAutoIsfIobThPercent)
        if (comparator.value.check(actualPercent, iobTHpercent.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "set iob_threshold_percent ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "set iob_threshold_percent NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("iobTH_percent", iobTHpercent.value)
            put("comparator", comparator.value.toString())
        }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        iobTHpercent.value = d.lenientInt("iobTH_percent")
        comparator.setValue(Comparator.Compare.valueOf(d.lenientStringOrNull("comparator")!!))
        return this
    }

    override fun friendlyName(): TextRef = AutomationStrings.autoisf_iobTH_percent

    override fun friendlyDescription(): String =
        rh.gs(AutomationStrings.iobTHpercentcompared, rh.gs(comparator.value.stringRes), iobTHpercent.value.toString())

    override fun composeIcon() = Icons.Filled.VerticalAlignTop
    override fun elementType() = ElementType.INSULIN

    override fun duplicate(): Trigger = TriggerIobTH(deps, this)
}
