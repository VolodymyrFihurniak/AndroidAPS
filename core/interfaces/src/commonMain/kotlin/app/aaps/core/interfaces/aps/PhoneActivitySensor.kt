package app.aaps.core.interfaces.aps

import app.aaps.core.interfaces.plugin.PermissionGroup

/**
 * Steps and movement seen by the sensors of the phone itself (autoISF activity monitor).
 *
 * The step counts are kept in blocks of 5 minutes. The block that is running now is not counted yet,
 * so [recentSteps5Min] is the last full block, [recentSteps10Min] the block before it and so on.
 *
 * Only Android has these sensors. Other platforms answer zero steps and "not moved", so the activity
 * monitor reports that the phone is not carried and does not change the sensitivity.
 */
interface PhoneActivitySensor {

    /** Start to listen to the step counter and the movement sensor. It is safe to call it more than once. */
    fun start()

    /** Time when the sensors started to listen, in milliseconds since the epoch, or 0 when they did not start. */
    val startTime: Long

    /** Steps in the last full 5 minute block. */
    fun recentSteps5Min(): Int

    /** Steps in the 5 minute block before [recentSteps5Min]. */
    fun recentSteps10Min(): Int

    /** Steps in the 5 minute block before [recentSteps10Min]. */
    fun recentSteps15Min(): Int

    /** Steps in the last 30 minutes. */
    fun recentSteps30Min(): Int

    /** Steps in the last 60 minutes. */
    fun recentSteps60Min(): Int

    /** Steps in the last 180 minutes. */
    fun recentSteps180Min(): Int

    /** True when the phone was moved in the last 15 minutes, which means that it is carried. */
    fun phoneMoved(): Boolean

    /** Runtime permissions the sensors need. Empty where the platform needs none. */
    fun permissionGroups(): List<PermissionGroup>
}
