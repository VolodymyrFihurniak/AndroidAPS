package app.aaps.plugins.aps.learned

import app.aaps.core.interfaces.aps.LearnedForecast
import app.aaps.core.interfaces.aps.LearnedPoint
import app.aaps.plugins.aps.learned.grid.HistoryGrid
import app.aaps.plugins.aps.learned.grid.HistoryGridBuilder
import app.aaps.plugins.aps.learned.grid.HistoryInput
import app.aaps.plugins.aps.learned.grid.STEP_MS
import app.aaps.plugins.aps.learned.model.CARB_HOURS
import app.aaps.plugins.aps.learned.model.Effects
import app.aaps.plugins.aps.learned.model.GruNetwork
import app.aaps.plugins.aps.learned.model.HORIZONS
import app.aaps.plugins.aps.learned.model.LearnedModel
import app.aaps.plugins.aps.learned.model.ResidualNetwork
import app.aaps.plugins.aps.learned.train.Samples
import app.aaps.plugins.aps.learned.train.Scaling
import app.aaps.plugins.aps.learned.train.SequenceFeatures
import kotlinx.datetime.TimeZone
import kotlin.math.min

/**
 * autoISF build, personal prediction: the curves of a [LearnedModel] from one step of a live grid.
 *
 * "No more insulin": the linear part sees all insulin in the grid, which in a live grid is what was given plus the
 * planned rest of the running temp basal. The network sees only what was known at the step, as in training. So the
 * curve says what happens if nothing more is given.
 *
 * "Expected" (v2): the GRU sees the last 3 hours and the same time of day on the last 7 days, and gives where BG really
 * goes, the loop included, as it learned from the history.
 */
object Forecaster {

    private const val MIN_BG = 39.0
    private const val MAX_BG = 400.0

    /** A reading older than this gives no live forecast */
    const val MAX_BG_AGE_MS = 10 * 60_000L

    /** Steps of a live grid before the newest reading: 7 days for the context of the expected curve, and 1 hour more */
    const val LIVE_BACK_STEPS = 7 * 288 + 12

    /** A live grid goes this far after the newest reading, for the planned rest of the running temp basal */
    private const val LIVE_AHEAD_MS = 2 * 3_600_000L

    /**
     * The live grid for [input]: it has a step exactly at the newest reading [newestBg], so with a 1-minute sensor
     * every new reading gives a new forecast that starts at it, not at the last whole 5 minutes.
     */
    fun liveGrid(input: HistoryInput, newestBg: Long, timeZone: TimeZone): HistoryGrid =
        HistoryGridBuilder.build(input, newestBg - LIVE_BACK_STEPS * STEP_MS, newestBg + LIVE_AHEAD_MS, timeZone, alignToStep = false)

    /**
     * The step a live forecast starts at: the one nearest to the newest reading [newestBg], so that reading is the BG
     * of the step whatever its offset to the 5-minute grid. On a grid from [liveGrid] it is [LIVE_BACK_STEPS]. Null
     * without a reading in the last [MAX_BG_AGE_MS].
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

        val expected = expectedCurve(model, grid, effects, i, bg, time)

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
            modelTrainedAt = model.trainedAt,
            profileIsfMgdl = grid.isf[i],
            deltaMgdl = if (i > 0 && !grid.bg[i - 1].isNaN()) bg - grid.bg[i - 1] else null,
            expectedPoints = expected ?: emptyList(),
            expectedTypicalErrorMgdl = if (expected != null) model.expectedTypicalErrorMgdl else emptyList(),
            expectedReady = expected != null && model.expectedReady
        )
    }

    /** The expected curve from step [i], null when the model has no GRU or the grid has fewer than 36 steps before [i] */
    private fun expectedCurve(model: LearnedModel, grid: HistoryGrid, effects: Effects, i: Int, bg: Double, time: Long): List<LearnedPoint>? {
        val state = model.expected ?: return null
        if (model.expectedStepMean.size != SequenceFeatures.STEP_INPUTS || model.expectedContextMean.size != SequenceFeatures.CONTEXT) return null
        val rows = SequenceFeatures.steps(grid, effects)
        val sequence = SequenceFeatures.sequence(rows, i) ?: return null
        val stepScaling = Scaling(model.expectedStepMean.toFloatArray(), model.expectedStepStd.toFloatArray())
        sequence.forEach { stepScaling.apply(it) }
        val context = SequenceFeatures.context(grid, effects.atLive(i), model.linear, i)
        Scaling(model.expectedContextMean.toFloatArray(), model.expectedContextStd.toFloatArray()).apply(context)
        val change = GruNetwork.fromState(state).predict(sequence, context)
        return (1..HORIZONS).map { h ->
            LearnedPoint(time + h * STEP_MS, (bg + change[h - 1] * SequenceFeatures.TARGET_SCALE).coerceIn(MIN_BG, MAX_BG))
        }
    }
}
