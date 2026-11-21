package app.aaps.plugins.aps.learned.train

import app.aaps.plugins.aps.learned.grid.HistoryGrid
import app.aaps.plugins.aps.learned.model.CARB_HOURS
import app.aaps.plugins.aps.learned.model.Effects
import app.aaps.plugins.aps.learned.model.HORIZONS
import app.aaps.plugins.aps.learned.model.LearnedModel
import app.aaps.plugins.aps.learned.model.LinearFeatures
import app.aaps.plugins.aps.learned.model.LinearModel
import app.aaps.plugins.aps.learned.model.Metrics
import app.aaps.plugins.aps.learned.model.ResidualNetwork
import app.aaps.plugins.aps.learned.model.RidgeAccumulator
import app.aaps.plugins.aps.learned.model.RidgeFit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * autoISF build, personal prediction: learns a [LearnedModel] from a [HistoryGrid].
 *
 * 1. Takes the valid steps of the window ([Samples.valid]); the last [Samples.TEST_DAYS] days are the test days.
 * 2. Fits the linear part by ridge regression, once on the learning days and once on all days.
 * 3. Trains the network on what the linear part leaves over, on the learning days, and stops when the test days stop
 *    getting better.
 * 4. Measures the model, "BG stays the same" and AutoISF on the test days, as they would be live: nothing given after
 *    the sample time is known.
 * 5. Trains the network again on all days for the same number of epochs. That model is the one used.
 *
 * Writes one line per step to [log], short progress to [progress], and formats BG values with [bg]. Checks for
 * cancellation often, so "Stop" works within a moment.
 */
class Trainer(
    private val log: (String) -> Unit,
    private val progress: (String) -> Unit,
    private val bg: (Double) -> String
) {

    suspend fun train(grid: HistoryGrid, now: Long, windowDays: Int, autoIsfRuns: List<StoredRun>): LearnedModel? {
        val effects = Effects(grid)
        val first = max(Samples.LEAD_STEPS, grid.indexOf(now - windowDays * DAY_MS))
        val candidates = (first until grid.size).toList()
        val samples = candidates.filter { Samples.valid(grid, it) }
        if (samples.size < Samples.MIN_SAMPLES) {
            log("Not enough data: ${samples.size} samples, at least ${Samples.MIN_SAMPLES} needed")
            return null
        }
        val testFrom = grid.indexOf(now - Samples.TEST_DAYS * DAY_MS)
        val learn = samples.filter { it < testFrom }
        val test = samples.filter { it >= testFrom }
        if (learn.size < Samples.MIN_SAMPLES / 2 || test.isEmpty()) {
            log("Not enough data: ${learn.size} samples to learn from and ${test.size} to test")
            return null
        }
        log("Training started: $windowDays days, ${samples.size} samples, ${candidates.size - samples.size} dropped (gaps, sensor start)")

        val weights = DoubleArray(grid.size)
        samples.forEach { weights[it] = Samples.weight(now - grid.timeOf(it)) }
        val futures = HashMap<Int, DoubleArray>(samples.size * 2)
        samples.forEach { futures[it] = Samples.futureBg(grid, it)!! }

        // Linear part, both fits in one pass
        progress("Linear fit")
        val accLearn = RidgeAccumulator(LinearFeatures.SIZE)
        val accAll = RidgeAccumulator(LinearFeatures.SIZE)
        val x = DoubleArray(LinearFeatures.SIZE)
        samples.forEachIndexed { n, i ->
            if (n % CHECK_EVERY == 0) yield()
            val future = futures.getValue(i)
            for (h in 1..HORIZONS) {
                LinearFeatures.fill(grid, effects, i, h, x)
                val y = future[h - 1] - grid.bg[i]
                accAll.add(x, y, weights[i])
                if (i < testFrom) accLearn.add(x, y, weights[i])
            }
        }
        val linearLearn = LinearModel(RidgeFit.solve(accLearn, LinearFeatures.PRIOR, LinearFeatures.NON_NEGATIVE).toList())
        val linearAll = LinearModel(RidgeFit.solve(accAll, LinearFeatures.PRIOR, LinearFeatures.NON_NEGATIVE).toList())
        logLinear(linearAll)

        // Network on the learning days, early stop on the test days
        val learnSet = networkSet(grid, effects, linearLearn, learn, futures, weights)
        val testSet = networkSet(grid, effects, linearLearn, test, futures, weights)
        val scaling = Scaling.of(learnSet.x)
        scaling.apply(learnSet.x)
        scaling.apply(testSet.x)
        val network = ResidualNetwork(inputs = Samples.INPUTS, seed = SEED)
        var bestLoss = testSet.loss(network)
        var bestEpoch = 0
        var best = network.parameters()
        for (epoch in 1..MAX_EPOCHS) {
            currentCoroutineContext().ensureActive()
            network.trainEpoch(learnSet.x, learnSet.y, learnSet.w, BATCH, LEARNING_RATE, epoch)
            val loss = testSet.loss(network)
            progress("Network: epoch $epoch of at most $MAX_EPOCHS")
            if (loss < bestLoss) {
                bestLoss = loss
                bestEpoch = epoch
                best = network.parameters()
            } else if (epoch - bestEpoch >= PATIENCE) break
            yield()
        }
        network.setParameters(best)
        log("Network: best epoch $bestEpoch, test error ${bg(sqrt(bestLoss) * LearnedModel.OUTPUT_SCALE)}")

        // Measure on the test days, as live
        progress("Measuring")
        val evaluation = evaluate(grid, effects, linearLearn, network, scaling, test, futures, autoIsfRuns)
        val autoIsfText = evaluation.autoIsf?.let { "AutoISF ${bg(it.mae60)}" } ?: "AutoISF no stored runs"
        val ready = if (evaluation.coverage >= MIN_COVERAGE && evaluation.autoIsf != null) evaluation.model.mae60 < evaluation.autoIsf.mae60
        else evaluation.model.mae60 < evaluation.naive.mae60
        log(
            "Test days: 60 min error model ${bg(evaluation.model.mae60)}, $autoIsfText, BG stays ${bg(evaluation.naive.mae60)}" +
                if (ready) " -> ready" else " -> not ready"
        )
        if (evaluation.clampHits > 0) log("Warning: the network hit its limit of ${bg(Samples.MAX_RESIDUAL)} ${evaluation.clampHits} times")

        // The model used: the linear part of all days, the network trained again on all days
        progress("Final training")
        val allSet = networkSet(grid, effects, linearAll, samples, futures, weights)
        val finalScaling = Scaling.of(allSet.x)
        finalScaling.apply(allSet.x)
        val finalNetwork = ResidualNetwork(inputs = Samples.INPUTS, seed = SEED)
        repeat(max(MIN_FINAL_EPOCHS, bestEpoch)) { epoch ->
            currentCoroutineContext().ensureActive()
            finalNetwork.trainEpoch(allSet.x, allSet.y, allSet.w, BATCH, LEARNING_RATE, epoch + 1)
            yield()
        }

        return LearnedModel(
            trainedAt = now,
            windowDays = windowDays,
            samples = samples.size,
            linear = linearAll,
            network = finalNetwork.toState(),
            inputMean = finalScaling.mean.toList(),
            inputStd = finalScaling.std.toList(),
            model = evaluation.model,
            autoIsf = evaluation.autoIsf,
            naive = evaluation.naive,
            autoIsfCoverage = evaluation.coverage,
            typicalErrorMgdl = evaluation.perHorizon.toList(),
            ready = ready,
            clampHits = evaluation.clampHits
        )
    }

    private fun logLinear(linear: LinearModel) {
        val raw = DAY_PARTS.map { linear.rawIsfMultiplier(angleOfHour(it)) }
        log(
            "Linear fit: ISF x${raw[0].fmt()} night, x${raw[1].fmt()} morning, x${raw[2].fmt()} day, x${raw[3].fmt()} evening; " +
                "carbs x${linear.carbFactor().fmt()}, mostly ${mainCarbHours(linear)} h; " +
                "drift ${DAY_PARTS.joinToString(", ") { "${it}:00 ${bg(linear.driftPerHour(angleOfHour(it)))}/h" }}"
        )
        if (raw.any { it < LinearModel.MIN_ISF_MULTIPLIER || it > LinearModel.MAX_ISF_MULTIPLIER })
            log("Warning: the ISF multiplier is outside ${LinearModel.MIN_ISF_MULTIPLIER}..${LinearModel.MAX_ISF_MULTIPLIER} and is limited")
    }

    private fun mainCarbHours(linear: LinearModel): Int {
        val weights = linear.carbWeights()
        return CARB_HOURS[weights.indices.maxByOrNull { weights[it] } ?: 0]
    }

    /** Inputs (live, as known at the step) and targets (what the linear part left over with the real insulin) */
    private suspend fun networkSet(
        grid: HistoryGrid, effects: Effects, linear: LinearModel, steps: List<Int>, futures: Map<Int, DoubleArray>, weights: DoubleArray
    ): NetworkSet {
        val x = Array(steps.size) { FloatArray(Samples.INPUTS) }
        val y = Array(steps.size) { FloatArray(Samples.KNOTS.size) }
        val w = FloatArray(steps.size)
        steps.forEachIndexed { n, i ->
            if (n % CHECK_EVERY == 0) yield()
            Samples.networkInputs(grid, effects.atLive(i), linear, i, x[n])
            val future = futures.getValue(i)
            Samples.KNOTS.forEachIndexed { k, h ->
                y[n][k] = ((future[h - 1] - grid.bg[i] - linear.delta(grid, effects, i, h)) / LearnedModel.OUTPUT_SCALE).toFloat()
            }
            w[n] = weights[i].toFloat()
        }
        return NetworkSet(x, y, w)
    }

    private class Evaluation(
        val model: Metrics, val naive: Metrics, val autoIsf: Metrics?, val coverage: Double, val perHorizon: DoubleArray, val clampHits: Int
    )

    private suspend fun evaluate(
        grid: HistoryGrid, effects: Effects, linear: LinearModel, network: ResidualNetwork, scaling: Scaling,
        test: List<Int>, futures: Map<Int, DoubleArray>, runs: List<StoredRun>
    ): Evaluation {
        val sortedRuns = runs.sortedBy { it.timestamp }
        val modelSum = DoubleArray(HORIZONS)
        val naiveSum = DoubleArray(HORIZONS)
        val autoIsfSum = DoubleArray(HORIZONS)
        val autoIsfCount = IntArray(HORIZONS)
        var covered = 0
        var clampHits = 0
        val inputs = FloatArray(Samples.INPUTS)
        val knots = DoubleArray(Samples.KNOTS.size)
        test.forEachIndexed { n, i ->
            if (n % CHECK_EVERY == 0) yield()
            val live = effects.atLive(i)
            Samples.networkInputs(grid, live, linear, i, inputs)
            scaling.apply(inputs)
            val out = network.predict(inputs)
            for (k in knots.indices) {
                val raw = out[k] * LearnedModel.OUTPUT_SCALE.toDouble()
                if (abs(raw) > Samples.MAX_RESIDUAL) clampHits++
                knots[k] = raw.coerceIn(-Samples.MAX_RESIDUAL, Samples.MAX_RESIDUAL)
            }
            val future = futures.getValue(i)
            val run = nearestRun(sortedRuns, grid.timeOf(i))
            if (run != null) covered++
            for (h in 1..HORIZONS) {
                val actual = future[h - 1]
                val predicted = (grid.bg[i] + linear.delta(grid, live, i, h) + Samples.residualAt(knots, h)).coerceIn(MIN_BG, MAX_BG)
                modelSum[h - 1] += abs(predicted - actual)
                naiveSum[h - 1] += abs(grid.bg[i] - actual)
                run?.mainCurve?.getOrNull(h - 1)?.let {
                    autoIsfSum[h - 1] += abs(it - actual)
                    autoIsfCount[h - 1]++
                }
            }
        }
        val perHorizon = DoubleArray(HORIZONS) { modelSum[it] / test.size }
        val naive = DoubleArray(HORIZONS) { naiveSum[it] / test.size }
        val autoIsf = if (autoIsfCount.all { it > 0 }) DoubleArray(HORIZONS) { autoIsfSum[it] / autoIsfCount[it] } else null
        return Evaluation(
            model = metrics(perHorizon),
            naive = metrics(naive),
            autoIsf = autoIsf?.let { metrics(it) },
            coverage = covered.toDouble() / test.size,
            perHorizon = perHorizon,
            clampHits = clampHits
        )
    }

    private fun nearestRun(runs: List<StoredRun>, time: Long): StoredRun? {
        if (runs.isEmpty()) return null
        var low = 0
        var high = runs.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (runs[mid].timestamp < time) low = mid + 1 else high = mid
        }
        return listOfNotNull(runs.getOrNull(low - 1), runs.getOrNull(low))
            .filter { abs(it.timestamp - time) <= RUN_MATCH_MS }
            .minByOrNull { abs(it.timestamp - time) }
    }

    private fun metrics(perHorizon: DoubleArray) = Metrics(perHorizon[5], perHorizon[11], perHorizon[23])

    private class NetworkSet(val x: Array<FloatArray>, val y: Array<FloatArray>, val w: FloatArray) {

        fun loss(network: ResidualNetwork) = network.loss(x, y, w)
    }

    companion object {

        private const val DAY_MS = 86_400_000L
        private const val CHECK_EVERY = 500
        private const val SEED = 1
        private const val MAX_EPOCHS = 60
        private const val PATIENCE = 5
        private const val MIN_FINAL_EPOCHS = 5
        private const val BATCH = 256
        private const val LEARNING_RATE = 1e-3f
        private const val MIN_COVERAGE = 0.5
        private const val RUN_MATCH_MS = 5 * 60_000L
        private const val MIN_BG = 39.0
        private const val MAX_BG = 400.0

        /** The hours shown for night, morning, day and evening, also on the plugin screen */
        val DAY_PARTS = listOf(3, 9, 15, 21)

        fun angleOfHour(hour: Int): Double = 2 * PI * hour / 24.0

        private fun Double.fmt(): String = ((this * 100).roundToInt() / 100.0).toString()
    }
}
