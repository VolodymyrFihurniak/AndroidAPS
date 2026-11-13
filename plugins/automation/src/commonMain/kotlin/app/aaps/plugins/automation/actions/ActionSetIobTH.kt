package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerticalAlignTop
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.utils.lenientInt
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.InputIobTH
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import app.aaps.core.data.ue.Action as UeAction

/** autoISF: sets the IOB threshold `iob_threshold_percent`. */
class ActionSetIobTH(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val preferences: Preferences,
    private val uel: UserEntryLogger
) : Action(aapsLogger, rh, pumpEnactResultProvider) {

    var newIobTH = InputIobTH()

    override fun friendlyName(): TextRef = AutomationStrings.autoisf_iobTH_percent
    override fun shortDescription(): String = rh.gs(AutomationStrings.automate_set_iobTH_percent, newIobTH.value)
    override fun composeIcon() = Icons.Filled.VerticalAlignTop
    override fun elementType() = ElementType.INSULIN

    override suspend fun doAction(): PumpEnactResult {
        val currentIobTH = preferences.get(IntKey.ApsAutoIsfIobThPercent)
        return if (currentIobTH != newIobTH.value) {
            uel.log(
                UeAction.IOB_TH_SET,
                Sources.Automation,
                title + ": " + rh.gs(AutomationStrings.automate_set_iobTH_percent, newIobTH.value),
                ValueWithUnit.Percent(newIobTH.value)
            )
            preferences.put(IntKey.ApsAutoIsfIobThPercent, newIobTH.value)
            pumpEnactResultProvider().success(true).comment(AutomationStrings.weight_new)
        } else {
            pumpEnactResultProvider().success(false).comment(AutomationStrings.weight_old)
        }
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = buildJsonObject { put("percentage", newIobTH.value) }
        return buildJsonObject {
            put("type", this@ActionSetIobTH::class.simpleName)
            put("data", data)
        }.toString()
    }

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        newIobTH.value = o.lenientInt("percentage", 100)
        return this
    }

    override fun isValid(): Boolean = newIobTH.value in InputIobTH.MIN..InputIobTH.MAX
}
