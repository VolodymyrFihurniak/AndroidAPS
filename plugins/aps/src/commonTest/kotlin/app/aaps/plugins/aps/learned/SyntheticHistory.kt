package app.aaps.plugins.aps.learned

import app.aaps.core.data.model.ICfg
import app.aaps.plugins.aps.learned.grid.BgReading
import app.aaps.plugins.aps.learned.grid.CarbEntry
import app.aaps.plugins.aps.learned.grid.HistoryInput
import app.aaps.plugins.aps.learned.grid.InsulinDose
import app.aaps.plugins.aps.learned.grid.ProfileTimeline
import app.aaps.plugins.aps.learned.grid.ProfileValues
import app.aaps.plugins.aps.learned.grid.STEP_MS
import app.aaps.plugins.aps.learned.grid.TempBasalSegment
import app.aaps.plugins.aps.learned.model.CarbKernel
import app.aaps.plugins.aps.learned.model.InsulinKernel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.random.Random

/**
 * A made-up body for the tests, in UTC, simulated in 5-minute steps:
 * - personal ISF = profile ISF (50) times `1 - 0.2 cos(time of day)`: 0.8 at midnight, 1.2 at noon
 * - carbs act 1.1 times the profile IC (10) says, absorbed in 2 hours
 * - BG rises 6 mg/dl per hour from 04:00 to 07:00 on its own
 * - three meals a day with a bolus, SMBs above 150 mg/dl, a zero temp below 85 mg/dl
 */
object SyntheticHistory {

    val insulin = ICfg("Rapid", peak = 75, dia = 8.0, concentration = 1.0)
    val profile = ProfileValues(isfMgdl = 50.0, ic = 10.0, basal = 0.8, targetMgdl = 100.0, iCfg = insulin)
    const val CARB_FACTOR = 1.1

    fun trueIsfMultiplier(angle: Double) = 1.0 - 0.2 * cos(angle)

    class Result(val input: HistoryInput, val start: Long, val end: Long)

    fun generate(days: Int, seed: Int = 7): Result {
        val random = Random(seed)
        val start = 1_767_225_600_000L // 2026-01-01 00:00 UTC
        val steps = days * 288
        val insulinKernel = InsulinKernel(insulin)
        val carbKernel = CarbKernel(2)
        val units = DoubleArray(steps)
        val grams = DoubleArray(steps)
        val doses = mutableListOf<InsulinDose>()
        val carbs = mutableListOf<CarbEntry>()
        val tempBasals = mutableListOf<TempBasalSegment>()
        val readings = mutableListOf<BgReading>()
        val meals = listOf(8 * 12 to 60.0, 13 * 12 to 70.0, 19 * 12 to 50.0)
        val mealSteps = mutableMapOf<Int, Double>()
        for (day in 0 until days) for ((step, size) in meals) {
            mealSteps[day * 288 + step + random.nextInt(-6, 7)] = size * random.nextDouble(0.8, 1.2)
        }
        var bg = 110.0
        var lastSmb = -100
        var zeroTempUntil = -1
        for (k in 0 until steps) {
            val time = start + k * STEP_MS
            readings += BgReading(time, bg)
            mealSteps[k]?.let { g ->
                grams[k] += g
                carbs += CarbEntry(time, g, 0L)
                val bolus = g / profile.ic
                units[k] += bolus
                doses += InsulinDose(time, bolus, insulin)
            }
            if (bg > 150.0 && k - lastSmb >= 3 && iob(units, insulinKernel, k) < 2.0) {
                units[k] += 0.3
                doses += InsulinDose(time, 0.3, insulin)
                lastSmb = k
            }
            if (bg < 85.0 && k > zeroTempUntil) {
                tempBasals += TempBasalSegment(time, time + 6 * STEP_MS, 0.0, isAbsolute = true)
                zeroTempUntil = k + 5
            }
            if (k <= zeroTempUntil) units[k] -= profile.basal / 12.0

            val angle = 2 * PI * (k % 288) / 288.0
            var acting = 0.0
            for (j in max(0, k - insulinKernel.steps)..k) acting += units[j] * (insulinKernel.acted(k + 1 - j) - insulinKernel.acted(k - j))
            var absorbed = 0.0
            for (j in max(0, k - carbKernel.steps)..k) absorbed += grams[j] * (carbKernel.absorbed(k + 1 - j) - carbKernel.absorbed(k - j))
            val hour = (k % 288) / 12
            val drift = if (hour in 4..6) 6.0 / 12.0 else 0.0
            bg += -profile.isfMgdl * trueIsfMultiplier(angle) * acting +
                CARB_FACTOR * profile.isfMgdl / profile.ic * absorbed +
                drift + random.nextDouble(-1.0, 1.0)
            bg = bg.coerceIn(45.0, 380.0)
        }
        val input = HistoryInput(readings, doses, tempBasals, emptyList(), carbs, ProfileTimeline { profile }, emptyList(), emptyList())
        return Result(input, start, start + steps * STEP_MS)
    }

    private fun iob(units: DoubleArray, kernel: InsulinKernel, k: Int): Double {
        var sum = 0.0
        for (j in max(0, k - kernel.steps)..k) sum += units[j] * (1.0 - kernel.acted(k - j))
        return sum
    }
}
