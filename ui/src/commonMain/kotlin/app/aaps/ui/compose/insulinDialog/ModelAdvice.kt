package app.aaps.ui.compose.insulinDialog

import androidx.compose.runtime.Immutable
import app.aaps.core.interfaces.aps.LearnedForecast
import kotlin.math.roundToInt
import kotlin.math.truncate

/**
 * autoISF build: what the personal prediction (experimental) advises in the Insulin dialog. The same guards against
 * lows as the advice of the loop come first. The advice is only shown; there is no button that uses it.
 */
enum class ModelAdviceKind {

    /** No forecast in the last [MODEL_FORECAST_MAX_AGE_MS] */
    NO_DATA,

    /** BG is below the low mark now */
    LOW_NOW,

    /** The model was not better than AutoISF on the test days, so it gives no dose */
    NOT_READY,

    /** A point of the model curve goes below the low mark in the next 2 hours */
    LOW_PREDICTED,

    /** [ModelAdvice.units] of insulin are missing */
    MISSING,

    /** [ModelAdvice.units] of insulin too much, about [ModelAdvice.carbs] grams would cover it */
    TOO_MUCH,

    /** Neither missing nor too much */
    NONE
}

/** A forecast older than this gives no advice */
const val MODEL_FORECAST_MAX_AGE_MS = 15 * 60_000L

@Immutable
data class ModelAdvice(
    val kind: ModelAdviceKind,
    /** Units missing or too much, rounded towards 0 to the pump step, never below 0 */
    val units: Double = 0.0,
    /** Grams that would cover the insulin that is too much */
    val carbs: Int = 0,
    /** The lowest point of the curve, for [ModelAdviceKind.LOW_PREDICTED] */
    val minPredictedMgdl: Double? = null,
    /** The forecast the advice comes from, for the numbers under it */
    val forecast: LearnedForecast? = null,
    /** True in closed loop with SMB: the loop may give part of the missing insulin itself */
    val loopMayGive: Boolean = false
)

/** The advice of the model. The order of the checks is the order of the guards: lows first. */
fun modelAdviceOf(forecast: LearnedForecast?, bgMgdl: Double?, lowMgdl: Double, bolusStep: Double, closedLoopSmb: Boolean, now: Long): ModelAdvice {
    if (bgMgdl != null && bgMgdl < lowMgdl) return ModelAdvice(ModelAdviceKind.LOW_NOW)
    if (forecast == null || now - forecast.timestamp > MODEL_FORECAST_MAX_AGE_MS) return ModelAdvice(ModelAdviceKind.NO_DATA)
    if (!forecast.ready) return ModelAdvice(ModelAdviceKind.NOT_READY, forecast = forecast)
    val lowest = forecast.points.minOfOrNull { it.mgdl }
    if (lowest != null && lowest < lowMgdl) return ModelAdvice(ModelAdviceKind.LOW_PREDICTED, minPredictedMgdl = lowest, forecast = forecast)
    if (forecast.isfMgdl <= 0.0) return ModelAdvice(ModelAdviceKind.NO_DATA)
    val need = towardsZero((forecast.eventualMgdl - forecast.targetMgdl) / forecast.isfMgdl, bolusStep)
    return when {
        need >= bolusStep  -> ModelAdvice(ModelAdviceKind.MISSING, units = need, forecast = forecast, loopMayGive = closedLoopSmb)
        need <= -bolusStep -> ModelAdvice(
            ModelAdviceKind.TOO_MUCH, units = -need, carbs = (-need * forecast.ic).roundToInt(), forecast = forecast
        )

        else               -> ModelAdvice(ModelAdviceKind.NONE, forecast = forecast)
    }
}

/** To the pump step, towards 0: an advice may say less, not more */
private fun towardsZero(value: Double, step: Double): Double =
    if (step <= 0.0) value else truncate(value / step + if (value >= 0) 1e-9 else -1e-9) * step
