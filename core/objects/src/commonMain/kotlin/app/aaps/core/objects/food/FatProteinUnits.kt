package app.aaps.core.objects.food

import kotlin.math.round

/**
 * autoISF build: fat and protein as late carbs (fat protein units, Warsaw method), ported from iAPS
 * (github.com/Artificial-Pancreas/iAPS, MIT licence, `CarbsStorage.swift`).
 *
 * kcal = 9 * fat + 4 * protein. Carb equivalents = kcal / 10 * factor. One FPU = 10 g of equivalents.
 * The equivalents are eaten over 3 h (below 2 FPU), 4 h (2..3), 5 h (3..4) or the time cap (4 and more),
 * starting after a delay.
 */
object FatProteinUnits {

    data class Result(
        /** Carb equivalents in grams, rounded to whole grams */
        val carbs: Int,
        val fpu: Double,
        val delayMinutes: Int,
        val durationHours: Int
    )

    /**
     * @param factor how much of the equivalents count, iAPS default 0.5
     * @return null when there is nothing to add (less than 1 g)
     */
    fun compute(fatGrams: Double, proteinGrams: Double, factor: Double, delayMinutes: Int, timeCapHours: Int): Result? {
        if (fatGrams < 0.0 || proteinGrams < 0.0 || factor <= 0.0) return null
        val kcal = fatGrams * 9.0 + proteinGrams * 4.0
        val equivalents = kcal / 10.0 * factor
        if (equivalents < 1.0) return null
        val fpu = equivalents / 10.0
        val cap = timeCapHours.coerceIn(5, 12)
        val hours = when {
            fpu < 2.0 -> 3
            fpu < 3.0 -> 4
            fpu < 4.0 -> 5
            else      -> cap
        }
        return Result(carbs = round(equivalents).toInt(), fpu = round(fpu * 10.0) / 10.0, delayMinutes = delayMinutes.coerceIn(0, 240), durationHours = hours)
    }
}
