package app.aaps.plugins.automation.states

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Reorder
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.states.compose.AutomationStatesScreen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * autoISF: shows the automation states and lets the user add, change and delete them.
 * The states themselves are kept by [AutomationStateService].
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(252)
class AutomationStatePlugin(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    notificationManager: NotificationManager,
    private val automationStateService: AutomationStateService
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .icon(Icons.Filled.Reorder)
        .pluginName(AutomationStrings.automation_states)
        .description(AutomationStrings.description_automation_states)
        .composeContent { plugin -> AutomationStatesScreen((plugin as AutomationStatePlugin).service) }
        .setDefault(),
    aapsLogger, rh, notificationManager
) {

    val service: AutomationStateService get() = automationStateService
}
