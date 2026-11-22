package app.aaps.plugins.aps.learned.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.aps.ApsStrings

/** autoISF build, personal prediction: its settings */
enum class LearnedIntKey(
    override val key: String,
    override val defaultValue: Int,
    override val min: Int,
    override val max: Int,
    override val title: TextRef,
    override val defaultedBySM: Boolean = false,
    override val dependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
) : IntPreferenceKey {

    TrainingWindowDays("learned_prediction_window_days", 180, 30, 730, title = ApsStrings.learned_window_days),
}

/** autoISF build, personal prediction: warnings from the forecast, off until the user switches them on */
enum class LearnedBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val defaultedBySM: Boolean = false,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val showInNsClientMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = true
) : BooleanPreferenceKey {

    LowAlert("learned_prediction_low_alert", false, title = ApsStrings.learned_low_alert, summary = ApsStrings.learned_low_alert_summary),
    HighAlert("learned_prediction_high_alert", false, title = ApsStrings.learned_high_alert, summary = ApsStrings.learned_high_alert_summary),
}

/**
 * autoISF build, personal prediction: the trained model, a copy of the AutoISF predictions of the last 15 days to
 * compare with, and the plugin's own forecasts of the last 8 days for the live check. Not exported: they belong to this
 * phone's history.
 */
enum class LearnedStringKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    ModelJson("learned_prediction_model", "", exportable = false),
    AutoIsfRuns("learned_prediction_autoisf_runs", "", exportable = false),
    Forecasts("learned_prediction_forecasts", "", exportable = false),
}
