package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Reorder
import app.aaps.core.interfaces.automation.AutomationStateInterface
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.elements.InputDropdownMenu
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** autoISF: sets an automation state to one of its values. */
class ActionSetAutomationState(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    val automationStateService: AutomationStateInterface
) : Action(aapsLogger, rh, pumpEnactResultProvider) {

    var stateName = InputDropdownMenu()
    var stateValue = InputDropdownMenu()

    override fun friendlyName(): TextRef = AutomationStrings.set_state
    override fun shortDescription(): String = rh.gs(AutomationStrings.set_state_description, stateName.value, stateValue.value)
    override fun composeIcon() = Icons.Filled.Reorder

    override fun isValid(): Boolean = stateName.value.isNotEmpty() && stateValue.value.isNotEmpty()

    override suspend fun doAction(): PumpEnactResult =
        try {
            automationStateService.setState(stateName.value, stateValue.value)
            pumpEnactResultProvider().success(true).comment(CoreUiStrings.ok)
        } catch (e: IllegalArgumentException) {
            // The state or the value was deleted after the automation was written
            aapsLogger.error(LTag.AUTOMATION, "Setting automation state failed: ${e.message}")
            pumpEnactResultProvider().success(false).comment(AutomationStrings.automation_state_unknown)
        }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = buildJsonObject {
            put("inputStateName", stateName.value)
            put("inputState", stateValue.value)
        }
        return buildJsonObject {
            put("type", this@ActionSetAutomationState::class.simpleName)
            put("data", data)
        }.toString()
    }

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        stateName.value = o.lenientString("inputStateName", "")
        stateValue.value = o.lenientString("inputState", "")
        return this
    }
}
