package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.aps.PhoneActivitySensor
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PermissionGroup
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * A desktop has no step counter for the autoISF activity monitor.
 *
 * Zero steps and "not moved": the monitor says that the phone is not carried and does not change the
 * sensitivity, which is the honest answer here.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DesktopPhoneActivitySensor(
    private val aapsLogger: AAPSLogger
) : PhoneActivitySensor {

    override val startTime: Long = 0L

    override fun start() {
        aapsLogger.debug(LTag.APS, "Desktop has no phone steps for the activity monitor")
    }

    override fun recentSteps5Min(): Int = 0
    override fun recentSteps10Min(): Int = 0
    override fun recentSteps15Min(): Int = 0
    override fun recentSteps30Min(): Int = 0
    override fun recentSteps60Min(): Int = 0
    override fun recentSteps180Min(): Int = 0
    override fun phoneMoved(): Boolean = false
    override fun permissionGroups(): List<PermissionGroup> = emptyList()
}
