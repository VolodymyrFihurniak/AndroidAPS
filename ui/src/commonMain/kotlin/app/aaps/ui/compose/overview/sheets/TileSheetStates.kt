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
    /** IOB from the last bolus or SMB until it is gone, U */
    val timeline: SheetTimeline = SheetTimeline(),
    /** The doses that still act, newest first: "SMB 0.3 U" to "12 min ago · 0.27 U left" */
    val lastDoses: List<Pair<String, String>> = emptyList(),
    /** When the IOB is gone: "23:40 · 3 h 10 min left", or empty when there is none */
    val actsUntilText: String = ""
)

@Immutable
data class BasalSheetState(
    val profileBasalText: String = "",
    /** The basal rate now as a number, e.g. "0.94"; the unit stands beside it */
    val rateText: String = "",
    /** Share of the profile while a temp basal runs, e.g. "110 %"; empty when none runs */
    val percentText: String = "",
    /** "Temp basal · 22 of 30 min left", or "Profile · no temp basal" */
    val detailText: String = "",
    /** Share of the running temp basal that is done, 0..1, or null when none runs */
    val runningProgress: Float? = null,
    /** When no temp basal runs: the next change of the profile, e.g. "at 22:00 → 0.90 U/h" */
    val nextChangeText: String = "",
    /** The profile basal now, U/h, to show what a percentage gives */
    val profileRate: Double = 0.0,
    /** Given basal (past and the running temp basal) and the profile, U/h, every 5 minutes */
    val chart: BasalChart = BasalChart(),
    val todayGivenText: String = "",
    val todayProfileText: String = "",
    val todayDifferenceText: String = "",
    /** The last temp basals, newest first: "18:42 · TBR 110 %" to "30 min" */
    val changes: List<Pair<String, String>> = emptyList(),
    /** True in closed loop: the loop may change a temp basal of the user */
    val loopMayChange: Boolean = false,
    /** Whether the pump takes temp basals at all */
    val canSetTempBasal: Boolean = true
)

/** A clock time on a time axis: the point index and the label, e.g. 21:00. */
@Immutable
data class SheetTimeTick(val index: Int, val label: String)

/** Values every 5 minutes with the point of now and the full hours. */
@Immutable
data class SheetTimeline(
    val values: List<Double> = emptyList(),
    val nowIndex: Int = 0,
    val ticks: List<SheetTimeTick> = emptyList(),
    /** The clock time of the first and the last point */
    val startText: String = "",
    val endText: String = ""
)

@Immutable
data class BasalChart(
    /** Given rate, null where it is not known yet (the future after the running temp basal) */
    val given: List<Double?> = emptyList(),
    val profile: List<Double> = emptyList(),
    val nowIndex: Int = 0,
    val ticks: List<SheetTimeTick> = emptyList()
)

@Immutable
data class LoopSheetState(
    val mode: RM.Mode? = null,
    val lastRunText: String = "",
    /** The loop modes the user may switch to (closed, open, LGS), the running one included */
    val loopModes: List<RM.Mode> = emptyList(),
    /** A pause may start (or, when one runs, be made longer) */
    val canSuspend: Boolean = false,
    val canDisconnect: Boolean = false,
    /** The loop may be turned off with no end: it stays off until the user turns it on */
    val canStop: Boolean = false,
    /** Suspended or disconnected now: the sheet offers to resume */
    val canResume: Boolean = false,
    /** What a mode that is not a running loop (pause, pump off, loop off) says; null while the loop runs */
    val inEffect: LoopInEffect? = null,
    /** The last run, null when there is none or on a client */
    val lastRun: LoopLastRun? = null,
    /** The last hour of runs, empty without runs */
    val runStrip: LoopRunStrip = LoopRunStrip(),
    /** Only on the phone that runs the loop */
    val canRunNow: Boolean = false,
    /** The class of the active APS plugin, for its screen; null on a client */
    val apsClassName: String? = null
)

/** The card of a mode that is in effect now instead of the loop. */
@Immutable
data class LoopInEffect(
    /** "42 min left · until 15:14", or "No end: it will not turn on by itself"; empty when there is nothing to add */
    val detailText: String = "",
    /** The share of a pause or a pump off that is still to run, 0..1; null when it has no end */
    val leftFraction: Float? = null
)

/** What the last run of the algorithm asked for, ready to show. */
@Immutable
data class LoopLastRun(
    /** The clock time of the run, e.g. "14:32" */
    val timeText: String,
    /** "110 % · 30 min", empty when the run asked for no temp basal */
    val tbrText: String = "",
    /** The temp basal is above (true) or below (false) the profile; null when equal or not known */
    val tbrAboveProfile: Boolean? = null,
    /** "0.30 U", empty without an SMB */
    val smbText: String = ""
)

/** The runs of the last hour in steps of 5 minutes: what the algorithm asked for, not what the pump did. */
@Immutable
data class LoopRunStrip(
    /** Oldest first: the share of the profile basal the algorithm asked for (100 = the profile); null where there was no run */
    val percent: List<Int?> = emptyList(),
    /** The SMB asked for in each step, U */
    val smb: List<Double> = emptyList(),
    /** The minutes the strip covers */
    val spanMinutes: Int = 60,
    /** The clock time of the first and the last step */
    val startText: String = "",
    val endText: String = "",
    /** The full hours inside; the index is the minute from the start */
    val hours: List<SheetTimeTick> = emptyList()
) {
    val isEmpty: Boolean get() = percent.all { it == null } && smb.all { it <= 0.0 }
}

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
