package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.utils.lenientDouble
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.InputWeight
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import app.aaps.core.data.ue.Action as UeAction

/** autoISF: sets `bgAccel_ISF_weight`. */
class ActionSetAcceWeight(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val preferences: Preferences,
    private val uel: UserEntryLogger
) : Action(aapsLogger, rh, pumpEnactResultProvider) {

    var newWeight = InputWeight()

    override fun friendlyName(): TextRef = AutomationStrings.autoisf_acce_weight
    override fun shortDescription(): String = rh.gs(AutomationStrings.automate_set_acce_weight, newWeight.value)
    override fun composeIcon() = Icons.Filled.Speed

    override suspend fun doAction(): PumpEnactResult {
        val currentAcceWeight = preferences.get(DoubleKey.ApsAutoIsfBgAccelWeight)
        return if (currentAcceWeight != newWeight.value) {
            uel.log(UeAction.ACCE_WEIGHT_SET, Sources.Automation, title + ": " + rh.gs(AutomationStrings.automate_set_acce_weight, newWeight.value))
            preferences.put(DoubleKey.ApsAutoIsfBgAccelWeight, newWeight.value)
            pumpEnactResultProvider().success(true).comment(AutomationStrings.weight_new)
        } else {
            pumpEnactResultProvider().success(false).comment(AutomationStrings.weight_old)
        }
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = buildJsonObject { put("weight", newWeight.value) }
        return buildJsonObject {
            put("type", this@ActionSetAcceWeight::class.simpleName)
            put("data", data)
        }.toString()
    }

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        newWeight.value = o.lenientDouble("weight")
        return this
    }

    override fun isValid(): Boolean = newWeight.value in InputWeight.MIN..InputWeight.MAX
}
