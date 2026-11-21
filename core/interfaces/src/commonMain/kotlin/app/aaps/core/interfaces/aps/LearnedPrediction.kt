package app.aaps.core.interfaces.aps

import kotlinx.coroutines.flow.StateFlow

/** autoISF build, personal prediction: one point of the curve, BG in mg/dl */
data class LearnedPoint(val timestamp: Long, val mgdl: Double)

/**
 * autoISF build, personal prediction: what the model of the user's own history predicts from now on. Two curves:
 * [points] if no more insulin is given than what is given already (and the running temp basal until its end), and
 * [expectedPoints], where BG really goes with the loop acting as it usually does. All BG values in mg/dl.
 */
data class LearnedForecast(
    /** The time of the step the forecast starts at */
    val timestamp: Long,
    /** BG at [timestamp] */
    val bgMgdl: Double,
    /** One point every 5 minutes, from +5 to +120 minutes */
    val points: List<LearnedPoint>,
    /** Mean absolute error of the model on the test days, one per point of [points] */
    val typicalErrorMgdl: List<Double>,
    /** Where BG ends once all insulin and carbs on board have acted */
    val eventualMgdl: Double,
    /** The personal ISF now, mg/dl per U */
    val isfMgdl: Double,
    /** The profile IC now, g per U */
    val ic: Double,
    /** The target the loop uses now */
    val targetMgdl: Double,
    /** True when the model was better than AutoISF on the test days. Only then it gives a dose. */
    val ready: Boolean,
    val modelTrainedAt: Long,
    /** The profile ISF now, mg/dl per U. A dose advice never uses an ISF below it, see the Insulin dialog. */
    val profileIsfMgdl: Double = isfMgdl,
    /** BG change over the last 5 minutes, null when the reading before is missing */
    val deltaMgdl: Double? = null,
    /** v2: the expected curve, one point every 5 minutes from +5 to +120 minutes. Empty when the model has none. */
    val expectedPoints: List<LearnedPoint> = emptyList(),
    /** Mean absolute error of the expected curve on the test days, one per point of [expectedPoints] */
    val expectedTypicalErrorMgdl: List<Double> = emptyList(),
    /** True when the expected curve was better than AutoISF on the test days */
    val expectedReady: Boolean = false
) {

    /** The point [minutes] after [timestamp], for 5, 10, ... 120 */
    fun at(minutes: Int): Double? = points.getOrNull(minutes / 5 - 1)?.mgdl

    /** The point of the expected curve [minutes] after [timestamp], null without that curve */
    fun expectedAt(minutes: Int): Double? = expectedPoints.getOrNull(minutes / 5 - 1)?.mgdl
}

/**
 * autoISF build: the personal prediction plugin, as the overview and the Insulin dialog see it. It never changes
 * insulin delivery.
 */
interface LearnedPrediction {

    /** True while the plugin is switched on */
    val enabled: StateFlow<Boolean>

    /** The newest forecast, null when the plugin is off, has no model or has no current BG */
    val forecast: StateFlow<LearnedForecast?>

    /** A forecast made now, from the newest data. Null for the same reasons as [forecast]. */
    suspend fun forecastNow(): LearnedForecast?
}
