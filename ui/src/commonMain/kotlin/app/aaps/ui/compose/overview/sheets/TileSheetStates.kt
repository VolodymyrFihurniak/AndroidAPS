package app.aaps.ui.compose.overview.sheets

import androidx.compose.runtime.Immutable
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT

/*
 * autoISF build: what the sheets of the overview tiles show. Each sheet reads its state once when it opens (see
 * TileSheetSource); the texts are ready to show, already in the units of the user.
 */

/** The BG sheet: the last hour and the entry of a meter BG. */
@Immutable
data class BgSheetState(
    val units: GlucoseUnit = GlucoseUnit.MGDL,
    /** The readings of the last hour, oldest first, mg/dl */
    val lastHourMgdl: List<Double> = emptyList(),
    /** The share of the last hour in range, null without readings */
    val inRangePercent: Int? = null,
    /** The value the meter field starts with, in the units of the user (the last reading) */
    val startValue: Double = 0.0,
    /** The step of the meter field: 1 mg/dl or 0.1 mmol/l */
    val step: Double = 1.0,
    /** Whether the BG source takes a calibration */
    val canCalibrate: Boolean = false
)

/** One temp target preset as a card of the target sheet. */
@Immutable
data class TargetPresetItem(
    val id: String,
    val name: String,
    val reason: TT.Reason,
    /** "140 mg/dL · 60 min" */
    val text: String,
    /** This preset runs now */
    val running: Boolean
)

@Immutable
data class TargetSheetState(
    val presets: List<TargetPresetItem> = emptyList(),
    /** The running temp target with its time left, empty when none runs */
    val runningText: String = "",
    /** The target of the profile now */
    val profileTargetText: String = "",
    /** The next change of the profile target in the next 24 hours, e.g. "22:00" to "110 mg/dL"; null without one */
    val nextChangeTime: String? = null,
    val nextChangeText: String? = null
)

@Immutable
data class BolusSheetState(
    /** "3.0 U · 2 h ago", empty without a bolus */
    val lastBolusText: String = "",
    /** "BG 132, eventual 168, IOB 1.20 U, COB 22 g" */
    val reasonText: String = "",
    /** The step of the pump for the amount field */
    val step: Double = 0.1,
    /** The largest amount the field takes (the max bolus of the settings) */
    val maxBolus: Double = 10.0
)

@Immutable
data class CarbsSheetState(
    val cobText: String = "",
    /** Carbs still on board and the carbs of the last meal, for the bar; null when unknown */
    val cobGrams: Double? = null,
    val lastMealGrams: Int? = null,
    /** "45 g · 1 h 10 min ago" */
    val lastMealText: String = "",
    /** "120 mg/dL · 60 min" of the hypo preset */
    val hypoTargetText: String = "",
    val maxCarbs: Int = 200
)

@Immutable
data class IobSheetState(
    val totalText: String = "",
    val bolusIobText: String = "",
    val basalIobText: String = "",
    val activityText: String = "",
    /** IOB now and every 5 minutes of the next hours, U */
    val curve: List<Double> = emptyList(),
    /** IOB in 2 hours, U */
    val inTwoHoursText: String = "",
    /** The last doses, newest first: "SMB 0.3 U" to "12 min ago" */
    val lastDoses: List<Pair<String, String>> = emptyList(),
    /** When the IOB is gone: "23:40 · 3 h 10 min left", or empty when there is none */
    val actsUntilText: String = ""
)

@Immutable
data class BasalSheetState(
    val profileBasalText: String = "",
    /** The running temp basal, e.g. "0.94 U/h · 110 %"; empty when none runs */
    val runningText: String = "",
    /** "from the loop · 22 min left of 30" */
    val runningDetail: String = "",
    /** Share of the running temp basal that is done, 0..1 */
    val runningProgress: Float = 0f,
    /** True in closed loop: the loop may change a temp basal of the user */
    val loopMayChange: Boolean = false,
    /** Whether the pump takes temp basals at all */
    val canSetTempBasal: Boolean = true
)

@Immutable
data class LoopSheetState(
    val mode: RM.Mode? = null,
    val lastRunText: String = "",
    /** The loop modes the user may switch to (closed, open, LGS), the running one included */
    val loopModes: List<RM.Mode> = emptyList(),
    val canSuspend: Boolean = false,
    val canDisconnect: Boolean = false,
    /** Suspended or disconnected now: the sheet offers to resume */
    val canResume: Boolean = false,
    /** What the last run decided, e.g. "TBR 110 % · SMB 0.3 U" */
    val decisionText: String = "",
    /** Only on the phone that runs the loop */
    val canRunNow: Boolean = false,
    /** The class of the active APS plugin, for its screen; null on a client */
    val apsClassName: String? = null
)

@Immutable
data class ProfileSheetState(
    val names: List<String> = emptyList(),
    /** The profile that runs now (without percentage) */
    val activeName: String = "",
    val percentage: Int = 100,
    /** "100 %" or "110 % · 1 h 20 min left" */
    val activeText: String = ""
)

@Immutable
data class PumpSheetState(
    /** "connected 1 min ago" */
    val connectionText: String = "",
    val basalText: String = "",
    val lastBolusText: String = "",
    /** Only on the phone that drives the pump */
    val canRefresh: Boolean = false,
    val canDisconnect: Boolean = false
)
