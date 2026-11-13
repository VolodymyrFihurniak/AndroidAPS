package app.aaps.plugins.automation.states

import app.aaps.core.interfaces.automation.AutomationStateInterface
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * autoISF: named states that automations can set and check, for example `Sleeping` = `True`.
 *
 * Each state has a list of possible values and one current value. Both are kept in the preferences as
 * JSON, see [AutomationStateStringKey]. The AutoISF activity monitor reads the state `Sleeping`.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AutomationStateService(
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger
) : AutomationStateInterface {

    private val lock = AapsLock()
    private val json = Json { ignoreUnknownKeys = true }

    private var automationStates: MutableMap<String, String> = load(AutomationStateStringKey.AutomationCurrentStates)
    private var stateValues: MutableMap<String, List<String>> = loadValues(AutomationStateStringKey.AutomationStateValues)

    private val _changes = MutableStateFlow(0)

    /** Counts every change, so a screen can follow it. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    init {
        preferences.registerPreferences(AutomationStateStringKey.entries)
    }

    private fun load(key: AutomationStateStringKey): MutableMap<String, String> =
        try {
            json.decodeFromString<Map<String, String>>(preferences.get(key)).toMutableMap()
        } catch (e: Exception) {
            aapsLogger.error(LTag.AUTOMATION, "Reading automation states failed", e)
            mutableMapOf()
        }

    private fun loadValues(key: AutomationStateStringKey): MutableMap<String, List<String>> =
        try {
            json.decodeFromString<Map<String, List<String>>>(preferences.get(key)).toMutableMap()
        } catch (e: Exception) {
            aapsLogger.error(LTag.AUTOMATION, "Reading automation state values failed", e)
            mutableMapOf()
        }

    private fun saveStates() {
        preferences.put(AutomationStateStringKey.AutomationCurrentStates, json.encodeToString(automationStates.toMap()))
        _changes.value++
    }

    private fun saveValues() {
        preferences.put(AutomationStateStringKey.AutomationStateValues, json.encodeToString(stateValues.toMap()))
        _changes.value++
    }

    override fun inState(stateName: String, state: String): Boolean = lock.withLock {
        automationStates[stateName.trim()] == state.trim()
    }

    override fun setState(stateName: String, state: String) {
        val trimmedName = stateName.trim()
        val trimmedState = state.trim()
        lock.withLock {
            require(stateValues.containsKey(trimmedName)) { "Invalid state name: $trimmedName" }
            require(stateValues[trimmedName]!!.contains(trimmedState)) { "Invalid state value: $trimmedState" }
            automationStates[trimmedName] = trimmedState
            saveStates()
        }
    }

    override fun getState(stateName: String): String = lock.withLock { automationStates[stateName.trim()] ?: "" }

    override fun getAllStates(): List<Pair<String, String>> = lock.withLock {
        stateValues.keys.map { stateName -> Pair(stateName, automationStates[stateName] ?: "") }
    }

    override fun getStateValues(stateName: String): List<String> = lock.withLock { stateValues[stateName.trim()] ?: emptyList() }

    override fun setStateValues(stateName: String, values: List<String>) {
        val trimmedName = stateName.trim()
        val trimmedValues = values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        lock.withLock {
            // A current value that is not in the new list is removed
            val currentState = automationStates[trimmedName]
            if (currentState != null && !trimmedValues.contains(currentState)) {
                automationStates.remove(trimmedName)
                saveStates()
            }
            stateValues[trimmedName] = trimmedValues
            saveValues()
        }
    }

    override fun hasStateValues(stateName: String): Boolean = lock.withLock { stateValues.containsKey(stateName.trim()) }

    override fun deleteState(stateName: String) {
        val trimmedName = stateName.trim()
        lock.withLock {
            automationStates.remove(trimmedName)
            stateValues.remove(trimmedName)
            saveStates()
            saveValues()
        }
    }
}
