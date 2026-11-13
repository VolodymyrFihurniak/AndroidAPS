package app.aaps.core.keys

import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey

enum class DoubleNonKey(
    override val key: String,
    override val defaultValue: Double,
    override val exportable: Boolean = true
) : DoubleNonPreferenceKey {

    // autoISF: result of the activity monitor of the last loop run (1.0 = no change).
    // State of this phone, not a setting → not exportable.
    ActivityMonitorRatio("activity_ratio", 1.0, exportable = false),
    // autoISF: last calibrated and last smoothed Libre value from xDrip+ (mg/dL), -1 = none yet.
    // State of this phone, not a setting → not exportable.
    FslLastRaw("fsl_last_raw", -1.0, exportable = false),
    FslLastSmooth("fsl_last_smooth", -1.0, exportable = false),
}