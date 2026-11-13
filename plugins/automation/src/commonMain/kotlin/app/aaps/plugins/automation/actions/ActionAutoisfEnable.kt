package app.aaps.plugins.automation.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ToggleOn
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.core.data.ue.Action as UeAction

/** autoISF: switches the AutoISF weights (`openapsama_enable_autoISF`) on. */
class ActionAutoisfEnable(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    pumpEnactResultProvider: () -> PumpEnactResult,
    private val preferences: Preferences,
    private val uel: UserEntryLogger
) : Action(aapsLogger, rh, pumpEnactResultProvider) {

    override fun friendlyName(): TextRef = AutomationStrings.enableautoisf
    override fun shortDescription(): String = rh.gs(AutomationStrings.enableautoisf)
    override fun composeIcon() = Icons.Filled.ToggleOn

    override suspend fun doAction(): PumpEnactResult =
        if (!preferences.get(BooleanKey.ApsUseAutoIsfWeights)) {
            uel.log(UeAction.AUTOISF_ENABLED, Sources.Automation, title)
            preferences.put(BooleanKey.ApsUseAutoIsfWeights, true)
            pumpEnactResultProvider().success(true).comment(AutomationStrings.autoisf_enabled)
        } else {
            pumpEnactResultProvider().success(true).comment(AutomationStrings.autoisf_alreadyenabled)
        }

    override fun isValid(): Boolean = true
}
