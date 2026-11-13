package app.aaps.plugins.automation.states

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

/**
 * autoISF: where the automation states are stored, as JSON. The keys are the same as in aisf 3.2,
 * so states of an older aisf build are found again after the update.
 */
enum class AutomationStateStringKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    /** The current value of each state, `{"Sleeping":"True"}`. */
    AutomationCurrentStates("automation_state_service", "{}"),

    /** The possible values of each state, `{"Sleeping":["True","False"]}`. */
    AutomationStateValues("automation_state_values", "{}"),
}
