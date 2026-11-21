package app.aaps.ui.compose.overview.chips

import androidx.compose.runtime.Immutable
import app.aaps.core.interfaces.aps.RT
import kotlin.math.floor

/**
 * autoISF build: what the Bolus card and the Insulin dialog advise, worked out from the last run of the algorithm, not
 * from the bolus wizard. The wizard counts a negative IOB as insulin that is missing, and so it advised 0.4 U at 3.9
 * mmol/l. The algorithm already weighs IOB, COB, the trend and its predictions, so its own numbers are used, with a
 * guard against lows in front of them.
 *
 * The advice is only shown. A dose is never given from it: in open loop a button fills the amount field of the
 * Insulin dialog, and the user still confirms.
 */
enum class BolusRecommendationKind {

    /** No algorithm run in the last [BolusRecommendationInput.MAX_RUN_AGE_MS], or no BG */
    NO_DATA,

    /** BG is below the low mark now */
    LOW_NOW,

    /** The algorithm asks for carbs, see [BolusRecommendation.carbs] */
    CARBS,

    /** A prediction curve goes below the low mark in the next 2 hours, see [BolusRecommendation.minPredictedMgdl] */
    LOW_PREDICTED,

    /** The predictions end in range, no insulin is needed */
    NONE,

    /** Closed loop with SMB: the loop gives [BolusRecommendation.insulinNeeded] itself, no manual bolus */
    LOOP_GIVES,

    /** Open loop, or a loop without SMB: [BolusRecommendation.dose] may be given by hand */
    SUGGEST
}

/** The kinds that say not to bolus, shown in the warning colour */
fun BolusRecommendationKind.isWarning(): Boolean =
    this == BolusRecommendationKind.CARBS || this == BolusRecommendationKind.LOW_NOW || this == BolusRecommendationKind.LOW_PREDICTED

/** Everything the advice is made from. BG values in mg/dl. */
data class BolusRecommendationInput(
    /** The newest BG, null when it is too old */
    val bgMgdl: Double?,
    /** The low mark of the overview */
    val lowMgdl: Double,
    /** The last algorithm run, null when there is none */
    val result: RT?,
    /** How old that run is */
    val resultAgeMs: Long,
    /** True in closed loop with SMB on: then the loop gives the insulin itself */
    val loopGivesSmb: Boolean,
    /** The highest bolus the constraints allow */
    val maxBolus: Double,
    /** The step of the pump */
    val bolusStep: Double
) {

    companion object {

        /** An older run no longer tells what is needed now. The loop runs every 5 minutes. */
        const val MAX_RUN_AGE_MS = 15 * 60_000L

        /** The low guard looks this far ahead in the prediction curves (5 minutes a point) */
        const val LOW_GUARD_POINTS = 24

        /** The point of the curve shown as "in 1 hour" */
        const val ONE_HOUR_POINT = 12
    }
}

/** The advice, with the numbers the dialog shows under it. */
@Immutable
data class BolusRecommendation(
    val kind: BolusRecommendationKind = BolusRecommendationKind.NO_DATA,
    /** The dose to give by hand, only for [BolusRecommendationKind.SUGGEST] */
    val dose: Double = 0.0,
    /** What the algorithm still needs, after the SMB of its run */
    val insulinNeeded: Double = 0.0,
    val carbs: Int = 0,
    val carbsWithinMinutes: Int = 0,
    val minPredictedMgdl: Double? = null,
    val eventualMgdl: Double? = null,
    val inOneHourMgdl: Double? = null,
    /** The ISF the algorithm used, mg/dl per U */
    val isfMgdl: Double? = null,
    val resultAgeMs: Long = 0L
)

/** The advice for [input]. The order of the checks is the order of the guards: lows first. */
fun bolusRecommendationOf(input: BolusRecommendationInput): BolusRecommendation {
    val bg = input.bgMgdl
    if (bg != null && bg < input.lowMgdl) return BolusRecommendation(BolusRecommendationKind.LOW_NOW)
    val rt = input.result
    if (bg == null || rt == null || input.resultAgeMs > BolusRecommendationInput.MAX_RUN_AGE_MS) return BolusRecommendation(BolusRecommendationKind.NO_DATA)

    val curves = listOfNotNull(rt.predBGs?.IOB, rt.predBGs?.COB, rt.predBGs?.UAM, rt.predBGs?.ZT).filter { it.isNotEmpty() }
    val minPredicted = curves.mapNotNull { it.take(BolusRecommendationInput.LOW_GUARD_POINTS).minOrNull() }.minOrNull()?.toDouble()
    // The curve the algorithm leans on: COB while carbs are on board, else UAM, else IOB
    val main = (rt.predBGs?.COB?.takeIf { (rt.COB ?: 0.0) > 0.0 } ?: rt.predBGs?.UAM ?: rt.predBGs?.IOB).orEmpty()
    val needed = ((rt.insulinReq ?: 0.0) - (rt.units ?: 0.0)).coerceAtLeast(0.0)
    val details = BolusRecommendation(
        insulinNeeded = needed,
        minPredictedMgdl = minPredicted,
        eventualMgdl = rt.eventualBG,
        inOneHourMgdl = main.getOrNull(BolusRecommendationInput.ONE_HOUR_POINT)?.toDouble(),
        isfMgdl = rt.variable_sens,
        resultAgeMs = input.resultAgeMs
    )

    val carbs = rt.carbsReq ?: 0
    if (carbs > 0) return details.copy(kind = BolusRecommendationKind.CARBS, carbs = carbs, carbsWithinMinutes = rt.carbsReqWithin ?: 0)
    if (minPredicted != null && minPredicted < input.lowMgdl) return details.copy(kind = BolusRecommendationKind.LOW_PREDICTED)
    if (needed < input.bolusStep) return details.copy(kind = BolusRecommendationKind.NONE)
    if (input.loopGivesSmb) return details.copy(kind = BolusRecommendationKind.LOOP_GIVES)
    val dose = floorToStep(minOf(needed, input.maxBolus), input.bolusStep)
    return if (dose < input.bolusStep) details.copy(kind = BolusRecommendationKind.NONE)
    else details.copy(kind = BolusRecommendationKind.SUGGEST, dose = dose)
}

/** Down to the step of the pump, never up: an advice may give less, not more. */
private fun floorToStep(value: Double, step: Double): Double =
    if (step <= 0.0) value else floor(value / step + 1e-9) * step
