package app.aaps.plugins.aps.learned.model

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.plugins.aps.learned.grid.HistoryGrid
import app.aaps.plugins.aps.learned.grid.STEP_MS
import kotlin.math.max
import kotlin.math.min

/** autoISF build, personal prediction: the model looks this many steps ahead (24 steps = 120 minutes) */
const val HORIZONS = 24

/** The absorption times the carb part of the model mixes */
val CARB_HOURS = listOf(1, 2, 3, 4, 6)

/** Carbs start to act this many minutes after they are eaten */
const val CARB_DELAY_MIN = 10

/** How far back the "last carbs" and "last bolus" inputs look (72 steps = 6 hours) */
const val LOOK_BACK_STEPS = 72

/**
 * The part of 1 U of [iCfg] that has acted `k` steps after it was given: 0 before and at delivery, 1 after the
 * insulin duration. From [ICfg.iobCalcForTreatment], so it is the same curve the loop uses.
 */
class InsulinKernel(iCfg: ICfg) {

    private val acted: DoubleArray

    /** Steps after which all of it has acted */
    val steps: Int

    init {
        val unit = BS(timestamp = 0L, amount = 1.0, type = BS.Type.NORMAL, iCfg = iCfg)
        // The curve ends at the insulin duration, but never before the floor iobCalcForTreatment applies
        var last = 0
        val values = mutableListOf(0.0)
        var k = 1
        while (k < MAX_STEPS) {
            val left = iCfg.iobCalcForTreatment(unit, k * STEP_MS).iobContrib
            values += 1.0 - left
            last = k
            if (left == 0.0) break
            k++
        }
        steps = last
        acted = values.toDoubleArray()
    }

    fun acted(k: Int): Double = when {
        k <= 0      -> 0.0
        k >= steps  -> 1.0
        else        -> acted[k]
    }

    private companion object {

        /** 24 hours, far above any insulin duration */
        const val MAX_STEPS = 288
    }
}

/** The part of the carbs absorbed `k` steps after they were eaten: in a straight line over [hours], after the delay */
class CarbKernel(val hours: Int) {

    fun absorbed(k: Int): Double = ((k * 5.0 - CARB_DELAY_MIN) / (hours * 60.0)).coerceIn(0.0, 1.0)

    /** Steps after which all carbs are absorbed */
    val steps: Int = (CARB_DELAY_MIN + hours * 60 + 4) / 5
}

/**
 * The insulin and carb effects on a [HistoryGrid], as sums over the doses with fixed kernels.
 *
 * With [liveIndex], every dose and every carb entry after that step is ignored. The network inputs use this in
 * training and live alike, so they only see what was known at that moment. Without it, all doses in the grid count:
 * in training that is the insulin that was really given, in a live grid it is the planned rest of the running temp
 * basal.
 */
class Effects private constructor(
    private val grid: HistoryGrid,
    private val liveIndex: Int?,
    private val insulinKernels: List<InsulinKernel>,
    private val carbKernels: Map<Int, CarbKernel>
) {

    constructor(grid: HistoryGrid, liveIndex: Int? = null) :
        this(grid, liveIndex, grid.curves.map { InsulinKernel(it) }, CARB_HOURS.associateWith { CarbKernel(it) })

    private val lastDose = min(grid.size - 1, liveIndex ?: Int.MAX_VALUE)

    /** The same grid seen from step [index]: what was given after it is ignored. Shares the kernels, so it is cheap. */
    fun atLive(index: Int): Effects = Effects(grid, index, insulinKernels, carbKernels)

    /** Units that act between step [i] and step `i + h` */
    fun insulinActing(i: Int, h: Int): Double {
        var sum = 0.0
        for (c in grid.curves.indices) {
            val kernel = insulinKernels[c]
            val units = grid.insulin[c]
            for (j in max(0, i - kernel.steps)..min(lastDose, i + h)) {
                val u = units[j]
                if (u != 0.0) sum += u * (kernel.acted(i + h - j) - kernel.acted(i - j))
            }
        }
        return sum
    }

    /** Grams absorbed between step [i] and step `i + h` with the kernel of [hours] */
    fun carbsAbsorbed(i: Int, h: Int, hours: Int): Double {
        val kernel = carbKernel(hours)
        var sum = 0.0
        for (j in max(0, i - kernel.steps)..min(lastDose, i + h)) {
            val g = grid.carbs[j]
            if (g != 0.0) sum += g * (kernel.absorbed(i + h - j) - kernel.absorbed(i - j))
        }
        return sum
    }

    /** Insulin on board at step [i] */
    fun iob(i: Int): Double {
        var sum = 0.0
        for (c in grid.curves.indices) {
            val kernel = insulinKernels[c]
            val units = grid.insulin[c]
            for (j in max(0, i - kernel.steps)..min(lastDose, i)) {
                val u = units[j]
                if (u != 0.0) sum += u * (1.0 - kernel.acted(i - j))
            }
        }
        return sum
    }

    /** Carbs on board at step [i] with the kernel of [hours] */
    fun cob(i: Int, hours: Int = 3): Double {
        val kernel = carbKernel(hours)
        var sum = 0.0
        for (j in max(0, i - kernel.steps)..min(lastDose, i)) {
            val g = grid.carbs[j]
            if (g != 0.0) sum += g * (1.0 - kernel.absorbed(i - j))
        }
        return sum
    }

    /** Steps since the last carbs at or before [i], null when none in [LOOK_BACK_STEPS] */
    fun lastCarbsStepsAgo(i: Int): Int? = lastCarbsStep(i)?.let { i - it }

    /** Grams of the last carbs at or before [i], 0 when none in [LOOK_BACK_STEPS] */
    fun lastCarbsGrams(i: Int): Double = lastCarbsStep(i)?.let { grid.carbs[it] } ?: 0.0

    /** Steps since the last step with at least [minUnits] at or before [i], null when none in [LOOK_BACK_STEPS] */
    fun lastBolusStepsAgo(i: Int, minUnits: Double = 0.5): Int? {
        for (j in min(i, lastDose) downTo max(0, i - LOOK_BACK_STEPS)) {
            if (grid.insulin.sumOf { it[j] } >= minUnits) return i - j
        }
        return null
    }

    private fun lastCarbsStep(i: Int): Int? {
        for (j in min(i, lastDose) downTo max(0, i - LOOK_BACK_STEPS)) if (grid.carbs[j] > 0.0) return j
        return null
    }

    private fun carbKernel(hours: Int) = carbKernels[hours] ?: CarbKernel(hours)
}
