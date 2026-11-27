package app.aaps.plugins.aps.learned.train

import app.aaps.plugins.aps.learned.grid.HistoryGrid
import app.aaps.plugins.aps.learned.model.Effects
import app.aaps.plugins.aps.learned.model.GruNetwork
import app.aaps.plugins.aps.learned.model.GruSample
import app.aaps.plugins.aps.learned.model.GruState
import app.aaps.plugins.aps.learned.model.HORIZONS
import app.aaps.plugins.aps.learned.model.LinearModel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * autoISF build, personal prediction v2: trains the GRU of the expected curve, the curve of where BG really goes with
 * the loop.
 *
 * It learns from the valid steps of every phase grid, at most [Settings.maxSamples] of them, each kept with the same
 * chance.
 * The validation days stop the training once they stop getting better; the test days only measure it. Then the GRU is
 * trained again on all days for the same number of epochs. Each of the two loops stops after [LOOP_LIMIT], so a slow phone still gets a model inside the
 * time limit of the whole training.
 */
class ExpectedCurveTrainer(
    private val settings: Settings,
    private val threads: Int,
    private val timeSource: TimeSource,
    private val log: (String) -> Unit,
    private val progress: (String) -> Unit,
    private val bg: (Double) -> String
) {

    /**
     * What the GRU learns from. The step rows of each grid are made once and scaled in place. A sample is a phase
     * (an index into [grids]) and a step; its sequence, context and target are made when its batch is built. [role]
     * says what each sample is for: [LEARN], [VALIDATE], [TEST], or [EDGE] for one whose next 2 hours reach into the
     * next part (only the final training on all days uses it).
     */
    class Data(
        val grids: List<HistoryGrid>,
        val effects: List<Effects>,
        val rows: List<Array<FloatArray>>,
        val stepScaling: Scaling,
        val phase: IntArray,
        val step: IntArray,
        val weight: FloatArray,
        val role: ByteArray
    ) {

        fun indicesOf(role: Byte): List<Int> = this.role.indices.filter { this.role[it] == role }
    }

    /**
     * The size of the training. The defaults are the ones the plugin uses; tests that do not look at the expected curve
     * make it small, so they run in seconds.
     */
    data class Settings(val maxSamples: Int = 100_000, val maxEpochs: Int = 25, val units: Int = 32)

    /** A trained GRU, the scaling of its context values, and the epoch it was best at */
    class Fit(val network: GruNetwork, val contextScaling: Scaling, val bestEpoch: Int)

    /** The samples of [grids] from [windowStart]; those from [validationFrom] on validate, those from [testFrom] on test */
    suspend fun prepare(grids: List<HistoryGrid>, now: Long, windowStart: Long, validationFrom: Long, testFrom: Long): Data {
        progress("Expected curve: preparing")
        val effects = grids.map { Effects(it) }
        val rows = grids.mapIndexed { p, grid ->
            yield()
            SequenceFeatures.steps(grid, effects[p])
        }
        val stepScaling = Scaling.of(rows[0])
        rows.forEach { stepScaling.apply(it) }
        // Count first, then keep each with the same chance, so a long history does not fill the memory
        var count = 0
        forEachValid(grids, windowStart) { _, _ -> count++ }
        val chance = min(1.0, settings.maxSamples.toDouble() / max(1, count))
        val random = Random(SEED)
        val phase = ArrayList<Int>()
        val step = ArrayList<Int>()
        forEachValid(grids, windowStart) { p, i ->
            if (random.nextDouble() < chance) {
                phase += p
                step += i
            }
        }
        val weight = FloatArray(phase.size) { Samples.weight(now - grids[phase[it]].timeOf(step[it])).toFloat() }
        val role = ByteArray(phase.size) { roleOf(grids[phase[it]].timeOf(step[it]), validationFrom, testFrom) }
        log("Expected curve: ${phase.size} of $count samples from ${grids.size} phases")
        return Data(grids, effects, rows, stepScaling, phase.toIntArray(), step.toIntArray(), weight, role)
    }

    /**
     * Trains on the learning days and stops when the validation days stop getting better. Null with too few samples.
     * Starts from [start] when it has the same size; it must be a GRU that never saw the validation and test days.
     */
    suspend fun learn(data: Data, linear: LinearModel, start: GruState? = null): Fit? {
        val learn = data.indicesOf(LEARN)
        val validation = data.indicesOf(VALIDATE)
        if (learn.isEmpty() || validation.isEmpty() || data.indicesOf(TEST).isEmpty()) {
            log("Expected curve: not enough data")
            return null
        }
        val contexts = contexts(data, linear)
        val contextScaling = Scaling.of(Array(learn.size) { contexts[learn[it]] })
        contextScaling.apply(contexts)
        val network = newNetwork(start)
        if (start != null && network.parameters().contentEquals(start.parameters.toDoubleArray())) log("Expected curve: starts from the last model")
        fun validationLoss() = network.loss(validation.size) { sample(data, contexts, validation[it]) }
        var bestLoss = validationLoss()
        var bestEpoch = 0
        var best = network.parameters()
        val start = timeSource.markNow()
        for (epoch in 1..settings.maxEpochs) {
            currentCoroutineContext().ensureActive()
            network.trainEpoch(learn.size, { sample(data, contexts, learn[it]) }, batch = BATCH, lr = LEARNING_RATE, epoch = epoch, threads = threads)
            val loss = validationLoss()
            progress("Expected curve: epoch $epoch of at most ${settings.maxEpochs}")
            if (loss < bestLoss) {
                bestLoss = loss
                bestEpoch = epoch
                best = network.parameters()
            } else if (epoch - bestEpoch >= PATIENCE) break
            if (epoch < settings.maxEpochs && start.elapsedNow() >= LOOP_LIMIT) {
                log("Expected curve: time limit reached after $epoch epochs")
                break
            }
        }
        network.setParameters(best)
        // The loss is the sum over the 24 points of the squared error divided by 50
        log("Expected curve: best epoch $bestEpoch, validation error ${bg(sqrt(bestLoss / HORIZONS) * SequenceFeatures.TARGET_SCALE)}")
        return Fit(network, contextScaling, bestEpoch)
    }

    /** Trains a GRU on all days for [epochs] epochs, or until the time limit, from [start] when it has the same size */
    suspend fun final(data: Data, linear: LinearModel, epochs: Int, start: GruState? = null): Fit {
        progress("Expected curve: final training")
        val contexts = contexts(data, linear)
        val contextScaling = Scaling.of(contexts)
        contextScaling.apply(contexts)
        val network = newNetwork(start)
        if (start != null && network.parameters().contentEquals(start.parameters.toDoubleArray())) log("Expected curve: final training starts from the last model")
        val start = timeSource.markNow()
        for (epoch in 1..epochs) {
            currentCoroutineContext().ensureActive()
            network.trainEpoch(data.phase.size, { sample(data, contexts, it) }, batch = BATCH, lr = LEARNING_RATE, epoch = epoch, threads = threads)
            if (epoch < epochs && start.elapsedNow() >= LOOP_LIMIT) {
                log("Expected curve: final training reached the time limit after $epoch of $epochs epochs")
                break
            }
        }
        return Fit(network, contextScaling, epochs)
    }

    /** The predicted BG change in mg/dl at +5 .. +120 minutes after step [i] of phase [p], as known at that step */
    fun predict(data: Data, fit: Fit, linear: LinearModel, p: Int, i: Int): DoubleArray? {
        val sequence = SequenceFeatures.sequence(data.rows[p], i) ?: return null
        val context = SequenceFeatures.context(data.grids[p], data.effects[p].atLive(i), linear, i)
        fit.contextScaling.apply(context)
        val out = fit.network.predict(sequence, context)
        return DoubleArray(HORIZONS) { out[it] * SequenceFeatures.TARGET_SCALE }
    }

    private suspend fun forEachValid(grids: List<HistoryGrid>, windowStart: Long, block: (Int, Int) -> Unit) {
        grids.forEachIndexed { p, grid ->
            for (i in max(Samples.LEAD_STEPS, grid.indexOf(windowStart)) until grid.size) {
                if (i % CHECK_EVERY == 0) yield()
                if (Samples.valid(grid, i)) block(p, i)
            }
        }
    }

    /** The context of every sample, made with [linear] */
    private suspend fun contexts(data: Data, linear: LinearModel): Array<FloatArray> {
        val out = arrayOfNulls<FloatArray>(data.phase.size)
        for (n in out.indices) {
            if (n % CHECK_EVERY == 0) yield()
            val p = data.phase[n]
            val i = data.step[n]
            out[n] = SequenceFeatures.context(data.grids[p], data.effects[p].atLive(i), linear, i)
        }
        return Array(out.size) { out[it]!! }
    }

    private fun sample(data: Data, contexts: Array<FloatArray>, n: Int): GruSample {
        val p = data.phase[n]
        val i = data.step[n]
        // Only valid steps are samples: they have 36 rows before them and BG for the next 2 hours
        return GruSample(
            sequence = SequenceFeatures.sequence(data.rows[p], i)!!,
            context = contexts[n],
            target = SequenceFeatures.target(data.grids[p], i)!!,
            weight = data.weight[n]
        )
    }

    /** A new GRU, with the parameters of [start] when it has exactly this size */
    private fun newNetwork(start: GruState? = null): GruNetwork {
        val network = GruNetwork(
            steps = SequenceFeatures.STEPS, inputs = SequenceFeatures.STEP_INPUTS, context = SequenceFeatures.CONTEXT,
            units = settings.units, dense = settings.units, outputs = HORIZONS, seed = SEED
        )
        val same = start != null && start.steps == network.steps && start.inputs == network.inputs && start.context == network.context &&
            start.units == network.units && start.dense == network.dense && start.outputs == network.outputs
        if (same) network.setParameters(start.parameters.toDoubleArray())
        return network
    }

    companion object {

        const val LEARN: Byte = 0
        const val VALIDATE: Byte = 1
        const val TEST: Byte = 2
        const val EDGE: Byte = 3

        /** What a sample at [time] is for; a sample needs the BG of the next 2 hours, which must not reach the next part */
        fun roleOf(time: Long, validationFrom: Long, testFrom: Long): Byte {
            val end = time + HORIZONS * 5 * 60_000L
            return when {
                time >= testFrom       -> TEST
                time >= validationFrom -> if (end < testFrom) VALIDATE else EDGE
                else                   -> if (end < validationFrom) LEARN else EDGE
            }
        }

        const val PATIENCE = 4
        const val MIN_FINAL_EPOCHS = 3
        val LOOP_LIMIT = 6.minutes

        private const val BATCH = 64
        private const val LEARNING_RATE = 1e-3
        private const val SEED = 1
        private const val CHECK_EVERY = 500
    }
}
