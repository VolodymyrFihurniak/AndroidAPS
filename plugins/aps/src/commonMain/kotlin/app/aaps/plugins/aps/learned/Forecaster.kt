package app.aaps.plugins.aps.learned

import app.aaps.core.interfaces.aps.LearnedForecast
import app.aaps.core.interfaces.aps.LearnedPoint
import app.aaps.plugins.aps.learned.grid.HistoryGrid
import app.aaps.plugins.aps.learned.grid.STEP_MS
import app.aaps.plugins.aps.learned.model.CARB_HOURS
import app.aaps.plugins.aps.learned.model.Effects
import app.aaps.plugins.aps.learned.model.HORIZONS
import app.aaps.plugins.aps.learned.model.LearnedModel
import app.aaps.plugins.aps.learned.model.ResidualNetwork
import app.aaps.plugins.aps.learned.train.Samples
import app.aaps.plugins.aps.learned.train.Scaling
import kotlin.math.min

/**
 * autoISF build, personal prediction: the curve of a [LearnedModel] from one step of a live grid.
 *
 * The linear part sees all insulin in the grid, which in a live grid is what was given plus the planned rest of the
 * running temp basal. The network sees only what was known at the step, as in training. So the curve says what
 * happens if nothing more is given.
 */
object Forecaster {

    private const val MIN_BG = 39.0
    private const val MAX_BG = 400.0

    /** A reading older than this gives no live forecast */
    const val MAX_BG_AGE_MS = 10 * 60_000L

    /**
     * The step a live forecast starts at: the one nearest to the newest reading [newestBg], so that reading is the BG
     * of the step whatever its offset to the 5-minute grid. The step that holds "now" would miss a 5-minute sensor
     * that reads more than 3 minutes after a step. Null without a reading in the last [MAX_BG_AGE_MS].
     */
    fun liveIndex(grid: HistoryGrid, newestBg: Long?, now: Long): Int? {
        if (newestBg == null || now - newestBg > MAX_BG_AGE_MS) return null
        val index = ((newestBg - grid.start + STEP_MS / 2).floorDiv(STEP_MS)).toInt()
        return index.takeIf { it in 0 until grid.size }
    }

    /** Null when BG or the profile is missing at [liveIndex] */
    fun forecast(model: LearnedModel, grid: HistoryGrid, liveIndex: Int): LearnedForecast? {
        val i = liveIndex
        if (i !in 0 until grid.size) return null
        val bg = grid.bg[i]
        if (bg.isNaN() || !grid.hasProfile(i) || grid.isf[i] <= 0.0 || grid.ic[i] <= 0.0) return null

        val effects = Effects(grid)
        val live = effects.atLive(i)
        val inputs = FloatArray(Samples.INPUTS)
        Samples.networkInputs(grid, live, model.linear, i, inputs)
        Scaling(model.inputMean.toFloatArray(), model.inputStd.toFloatArray()).apply(inputs)
        val out = ResidualNetwork.fromState(model.network).predict(inputs)
        val knots = DoubleArray(Samples.KNOTS.size) { k ->
            (out[k] * model.outputScale.toDouble()).coerceIn(-Samples.MAX_RESIDUAL, Samples.MAX_RESIDUAL)
        }

        val time = grid.timeOf(i)
        val points = (1..HORIZONS).map { h ->
            val value = bg + model.linear.delta(grid, effects, i, h) + Samples.residualAt(knots, h)
            LearnedPoint(time + h * STEP_MS, value.coerceIn(MIN_BG, MAX_BG))
        }

        // Where the curve ends once the insulin and carbs still on board at +120 min have acted
        val isfNow = grid.isf[i] * model.linear.isfMultiplier(grid.dayAngle[i])
        val end = min(i + HORIZONS, grid.size - 1)
        val csf = grid.isf[i] / grid.ic[i]
        val weights = model.linear.carbWeights()
        val carbsLeft = CARB_HOURS.indices.sumOf { k -> weights[k] * effects.cob(end, CARB_HOURS[k]) }
        val eventual = (points.last().mgdl - isfNow * effects.iob(end) + csf * carbsLeft).coerceIn(MIN_BG, MAX_BG)

        return LearnedForecast(
            timestamp = time,
            bgMgdl = bg,
            points = points,
            typicalErrorMgdl = model.typicalErrorMgdl,
            eventualMgdl = eventual,
            isfMgdl = isfNow,
            ic = grid.ic[i],
            targetMgdl = grid.target[i],
            ready = model.ready,
            modelTrainedAt = model.trainedAt
        )
    }
}
