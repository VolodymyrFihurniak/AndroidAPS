package app.aaps.plugins.aps.activity

import app.aaps.core.interfaces.aps.PhoneActivitySensor
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PermissionGroup
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * iOS does not give the steps of the phone to the autoISF activity monitor yet.
 *
 * It answers zero steps and "not moved", so the monitor says that the phone is not carried and does
 * not change the sensitivity. That is visible in the log of the loop, not a silent wrong value.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosPhoneActivitySensor(
    private val aapsLogger: AAPSLogger
) : PhoneActivitySensor {

    override val startTime: Long = 0L

    override fun start() {
        aapsLogger.debug(LTag.APS, "iOS has no phone steps for the activity monitor")
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
