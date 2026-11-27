package app.aaps.ui.compose.overview.chips

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import app.aaps.core.interfaces.overview.IsfStep
import app.aaps.core.interfaces.profile.Profile

/**
 * What the overview chips show.
 *
 * Kept in their own file rather than beside `ChipsViewModel`: these are plain values with no Android
 * in them, and every composable and preview that reads them can then be shared code.
 */
@Immutable
data class IobUiState(
    val text: String = "",
    val iobTotal: Double = 0.0
)

@Immutable
data class CobUiState(
    val text: String = "",
    val carbsReq: Int = 0,
    val cobValue: Double = 0.0
)

/**
 * autoISF: the exercise mode ("high temp target raises sensitivity") and the activity tile of the overview.
 *
 * [visible] only while the active APS uses the mode (AutoISF, and SMB with the aisf changes).
 */
@Immutable
data class ExerciseModeUiState(
    val visible: Boolean = false,
    val enabled: Boolean = false,
    /** A temp target runs now, of any reason */
    val targetRunning: Boolean = false,
    /** The running temp target is an activity one */
    val activityRunning: Boolean = false,
    /** The running temp target with its time left, e.g. "140 mg/dL · 45 min" */
    val runningTargetText: String = "",
    /** The activity preset of the temp targets, e.g. "140 mg/dL · 60 min"; empty when there is none */
    val presetText: String = "",
    /** The target the effect is worked out for (the running high target, else the activity preset) */
    val effectTargetText: String = "",
    /** The share of the profile basal the loop uses at [effectTargetText]; null when a target there changes nothing */
    val basalPercent: Int? = null,
    /** How much larger the ISF is at [effectTargetText], e.g. 1.7 */
    val isfFactorText: String = "",
    /** The effect works now: the mode is on and a high temp target runs */
    val effectNow: Boolean = false,
    /** Steps of the last hour, null without a step source */
    val steps: Int? = null,
    /** Mean heart rate of the last hour, null without a heart rate source */
    val heartRate: Int? = null
)

@Immutable
data class SensitivityUiState(
    val asText: String = "",
    val isfFrom: String = "",
    val isfTo: String = "",
    val isfStep: IsfStep = IsfStep.SAME,
    val dialogText: String = "",
    val ratio: Double = 1.0,
    val isEnabled: Boolean = true,
    val hasData: Boolean = false
)

/**
 * autoISF build: the statistics of today on the overview. [visible] is false while there is no reading today.
 * [timeInRange] is 0..1 for the bar under the value.
 */
@Immutable
data class TodayStatsUiState(
    val visible: Boolean = false,
    val timeInRangeText: String = "",
    val timeInRange: Float = 0f,
    val cvText: String = "",
    val cvFraction: Float = 0f,
    val averageText: String = "",
    val averageFraction: Float = 0f,
    val unitText: String = ""
)

/**
 * autoISF build: the loop card of the overview. [lastRunText] is when the loop last ran (on a client: when the master's
 * last run came), empty when it never did.
 */
@Immutable
data class LoopUiState(
    val lastRunText: String = "",
    val isOld: Boolean = false
)

/**
 * autoISF build: the basal card next to IOB and COB, and the details it opens. See `basalCardState` for the rules.
 *
 * [rateText] is the main value, [statusText] the smaller line under it. [isWarning] marks a state where the rate is
 * not the profile or a TBR (no data, suspended, disconnected), shown in the warning colour.
 */
@Stable
data class BasalUiState(
    val visible: Boolean = false,
    val rateText: String = "",
    val statusText: String = "",
    val direction: BasalDirection = BasalDirection.SAME,
    val isWarning: Boolean = false,
    val profileName: String = "",
    /** Label and value of each line of the details. */
    val details: List<Pair<String, String>> = emptyList(),
    /** For the day graph of the details, null without a profile. */
    val profile: Profile? = null
)
