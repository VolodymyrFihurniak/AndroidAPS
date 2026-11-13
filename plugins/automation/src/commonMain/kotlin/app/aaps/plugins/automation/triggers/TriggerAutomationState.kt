package app.aaps.plugins.automation.triggers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Reorder
import app.aaps.core.interfaces.automation.AutomationStateInterface
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.InputDropdownMenu
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * autoISF: true when an automation state has a given value.
 *
 * The states and their possible values are kept by [AutomationStateInterface]. The editor reads the
 * lists from there, this class only keeps the chosen name and value.
 */
class TriggerAutomationState(deps: TriggerDeps, val automationStateService: AutomationStateInterface) : Trigger(deps) {

    var stateName = InputDropdownMenu()
    var stateValue = InputDropdownMenu()

    constructor(deps: TriggerDeps, automationStateService: AutomationStateInterface, other: TriggerAutomationState) : this(deps, automationStateService) {
        stateName = InputDropdownMenu(other.stateName.value)
        stateValue = InputDropdownMenu(other.stateValue.value)
    }

    override suspend fun shouldRun(): Boolean {
        if (automationStateService.inState(stateName.value, stateValue.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("stateName", stateName.value)
            put("stateValue", stateValue.value)
        }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        stateName.value = d.lenientString("stateName", "")
        stateValue.value = d.lenientString("stateValue", "")
        return this
    }

    override fun friendlyName(): TextRef = AutomationStrings.check_state_name

    override fun friendlyDescription(): String =
        rh.gs(AutomationStrings.check_state_description, stateName.value, stateValue.value)

    override fun composeIcon() = Icons.Filled.Reorder

    override fun duplicate(): Trigger = TriggerAutomationState(deps, automationStateService, this)
}
