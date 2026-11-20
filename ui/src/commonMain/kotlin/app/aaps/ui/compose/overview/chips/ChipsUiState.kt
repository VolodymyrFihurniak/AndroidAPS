package app.aaps.ui.compose.overview.chips

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import app.aaps.core.interfaces.overview.IsfStep
import app.aaps.core.interfaces.profile.Profile
import kotlin.math.ceil

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
 * autoISF: the exercise mode button ("high temp target raises sensitivity").
 *
 * [visible] only while the active APS uses the mode (AutoISF, and SMB with the aisf changes).
 */
@Immutable
data class ExerciseModeUiState(
    val visible: Boolean = false,
    val enabled: Boolean = false
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

/** autoISF build: what the bolus calculator says for now, with no new carbs. */
enum class BolusAdviceKind {

    /** No profile or no recent BG: nothing to say */
    UNKNOWN,

    /** A correction bolus, see [BolusAdviceUiState.insulin] */
    INSULIN,

    /** BG goes below the target: carbs, not insulin, see [BolusAdviceUiState.carbs] */
    CARBS,

    /** Nothing to give */
    NONE
}

/**
 * autoISF build: the advice of the Bolus card. It is only shown, never given: a tap opens the bolus wizard, where the
 * user checks it and confirms. [loopMayCover] is set in closed loop, where the loop may give the same insulin as SMB.
 */
@Immutable
data class BolusAdviceUiState(
    val kind: BolusAdviceKind = BolusAdviceKind.UNKNOWN,
    val insulin: Double = 0.0,
    val carbs: Int = 0,
    val loopMayCover: Boolean = false
)

/**
 * autoISF build: the advice from the result of the bolus wizard. [insulin] is the dose after the constraints, already
 * on the step of the pump, so anything above 0 can be given. [carbsEquivalent] is what the wizard asks for when the
 * total is negative, rounded up to whole grams.
 */
internal fun bolusAdviceOf(insulin: Double, carbsEquivalent: Double, loopMayCover: Boolean): BolusAdviceUiState = when {
    insulin > 0.0          -> BolusAdviceUiState(BolusAdviceKind.INSULIN, insulin = insulin, loopMayCover = loopMayCover)
    carbsEquivalent >= 1.0 -> BolusAdviceUiState(BolusAdviceKind.CARBS, carbs = ceil(carbsEquivalent).toInt())
    else                   -> BolusAdviceUiState(BolusAdviceKind.NONE)
}

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
