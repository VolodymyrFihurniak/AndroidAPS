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
