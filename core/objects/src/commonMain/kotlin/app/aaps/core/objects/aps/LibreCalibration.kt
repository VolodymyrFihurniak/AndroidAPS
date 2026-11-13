package app.aaps.core.objects.aps

import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.interfaces.Preferences

/**
 * autoISF: a short stop of SMB and of the AutoISF weights after a new Libre calibration.
 *
 * The user switches [BooleanKey.FslCalibrationTrigger] on before the calibration settings are changed.
 * The next reading or loop run takes that as the start of the calibration, writes the start time and
 * switches the trigger off again. For [DURATION_MINUTES] after the start, SMB and the AutoISF weights
 * are not used, because the values jump while the new calibration settles. [BooleanKey.FslCalibrationEnd]
 * ends the stop early.
 */
object LibreCalibration {

    /** How long a calibration stops SMB and the AutoISF weights. */
    const val DURATION_MINUTES = 20L

    /** Takes the switch of the user as the start of a calibration. Call it before [remainingMinutes]. */
    fun handleTrigger(preferences: Preferences, now: Long) {
        if (preferences.get(BooleanKey.FslCalibrationTrigger)) {
            preferences.put(LongNonKey.FslCalibrationStart, now)
            preferences.put(BooleanKey.FslCalibrationTrigger, false)
            preferences.put(BooleanKey.FslCalibrationEnd, false)
        }
    }

    /** Minutes left of the calibration time. Zero or less when there is no calibration running. */
    fun remainingMinutes(preferences: Preferences, now: Long): Long =
        DURATION_MINUTES - (now - preferences.get(LongNonKey.FslCalibrationStart)) / 60000

    /** True while a calibration stops SMB and the AutoISF weights. */
    fun stopsSmb(preferences: Preferences, now: Long): Boolean =
        remainingMinutes(preferences, now) > 0 && !preferences.get(BooleanKey.FslCalibrationEnd)
}
