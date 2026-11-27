package app.aaps.plugins.aps.learned.train

import app.aaps.plugins.aps.learned.grid.HistoryGrid
import app.aaps.plugins.aps.learned.nearestTo
import app.aaps.plugins.aps.learned.model.CARB_HOURS
import app.aaps.plugins.aps.learned.model.Effects
import app.aaps.plugins.aps.learned.model.HORIZONS
import app.aaps.plugins.aps.learned.model.LearnedModel
import app.aaps.plugins.aps.learned.model.LinearFeatures
import app.aaps.plugins.aps.learned.model.LinearModel
import app.aaps.plugins.aps.learned.model.LowCheck
import app.aaps.plugins.aps.learned.model.Metrics
import app.aaps.plugins.aps.learned.model.Range
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
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * autoISF build, personal prediction: learns a [LearnedModel] from [HistoryGrid]s, one per 1-minute phase of the
 * history (only one with a 5-minute sensor). The "no more insulin" curve learns from the first grid, the expected
 * curve from all of them.
 *
 * 1. Takes the valid steps of the window ([Samples.valid]); the last [Samples.TEST_DAYS] days are the test days, the
 *    [Samples.VALIDATION_DAYS] days before them the validation days, the rest the learning days. A step whose next 2
 *    hours reach into the next part is left out, so no part sees the BG of the next one.
 * 2. Fits the linear part by ridge regression, once on the learning days and once on all days.
 * 3. Trains the network on what the linear part leaves over, on the learning days, and stops when the validation days
 *    stop getting better.
 * 4. Trains the GRU of the expected curve the same way ([ExpectedCurveTrainer]). It starts from the GRU of the last
 *    model only when that one never saw the validation and test days.
 * 5. Measures both curves, "BG stays the same" and AutoISF on the test days, as they would be live: nothing given
 *    after the sample time is known.
 * 6. Trains the network and the GRU again on all days for the same number of epochs. Those are the ones used.
 *
 * Writes one line per step to [log], short progress to [progress], and formats BG values with [bg]. Checks for
 * cancellation often, so "Stop" works within a moment. The GRU splits each batch over [threads]. [timeSource] measures
 * the time of each part.
 */
class Trainer(
    private val log: (String) -> Unit,
    private val progress: (String) -> Unit,
    private val bg: (Double) -> String,
    private val expectedCurve: ExpectedCurveTrainer.Settings = ExpectedCurveTrainer.Settings(),
    private val threads: Int = 4,
    private val timeSource: TimeSource = TimeSource.Monotonic
) {

    suspend fun train(grid: HistoryGrid, now: Long, windowDays: Int, autoIsfRuns: List<StoredRun>): LearnedModel? =
        train(listOf(grid), now, windowDays, autoIsfRuns)

    /** [grids] holds one grid per phase, phase 0 first. The GRU starts from the one of [previous] when it fits. */
    suspend fun train(grids: List<HistoryGrid>, now: Long, windowDays: Int, autoIsfRuns: List<StoredRun>, previous: LearnedModel? = null): LearnedModel? {
        val grid = grids.first()
        val effects = Effects(grid)
        val first = max(Samples.LEAD_STEPS, grid.indexOf(now - windowDays * DAY_MS))
        val verdicts = (first until grid.size).map { it to Samples.reject(grid, it) }
        val samples = verdicts.filter { it.second == null }.map { it.first }
        val dropped = verdicts.mapNotNull { it.second }.filter { it != Samples.Reject.EDGE }.groupingBy { it }.eachCount()
        log(
            "Dropped: " + if (dropped.isEmpty()) "none"
            else dropped.entries.sortedByDescending { it.value }.joinToString(", ") { (reason, count) -> "$count ${reason.label()}" }
        )
        if (samples.size < Samples.MIN_SAMPLES) {
            log("Not enough data: ${samples.size} samples, at least ${Samples.MIN_SAMPLES} needed")
            return null
        }
        val testFromMs = now - Samples.TEST_DAYS * DAY_MS
        val validationFromMs = testFromMs - Samples.VALIDATION_DAYS * DAY_MS
        val testFrom = grid.indexOf(testFromMs)
        val validationFrom = grid.indexOf(validationFromMs)
        // A step learns or validates only when its next 2 hours end before the next part starts
        val learn = samples.filter { it + HORIZONS < validationFrom }
        val validation = samples.filter { it >= validationFrom && it + HORIZONS < testFrom }
        val test = samples.filter { it >= testFrom }
        if (learn.size < Samples.MIN_SAMPLES / 2 || validation.isEmpty() || test.isEmpty()) {
            log("Not enough data: ${learn.size} samples to learn from, ${validation.size} to validate and ${test.size} to test")
            return null
        }
        log("Training started: $windowDays days, ${samples.size} samples" + if (grids.size > 1) ", ${grids.size} phases of 1 minute" else "")
        log(
            "Split: ${learn.size} to learn, ${validation.size} to validate (${Samples.VALIDATION_DAYS} days), ${test.size} to test " +
                "(${Samples.TEST_DAYS} days), ${samples.size - learn.size - validation.size - test.size} left out at the edges"
        )
        val times = PARTS.associateWith { Duration.ZERO }.toMutableMap()

        val weights = DoubleArray(grid.size)
        samples.forEach { weights[it] = Samples.weight(now - grid.timeOf(it)) }
        val futures = HashMap<Int, DoubleArray>(samples.size * 2)
        samples.forEach { futures[it] = Samples.futureBg(grid, it)!! }

        // Linear part, both fits in one pass
        progress("Linear fit")
        val linearStart = timeSource.markNow()
        val accLearn = RidgeAccumulator(LinearFeatures.SIZE)
        val accAll = RidgeAccumulator(LinearFeatures.SIZE)
        val x = DoubleArray(LinearFeatures.SIZE)
        // Meals that were not entered are left out: in them insulin comes while BG keeps rising
        val unexplained = UnannouncedMeals.unexplained(grid, effects)
        val meals = UnannouncedMeals.find(grid, effects)
        val mealsUpTo = IntArray(grid.size + 1).also { count -> for (j in 0 until grid.size) count[j + 1] = count[j] + if (meals[j]) 1 else 0 }
        var rows = 0
        var mealRows = 0
        samples.forEachIndexed { n, i ->
            if (n % CHECK_EVERY == 0) yield()
            val future = futures.getValue(i)
            for (h in 1..HORIZONS) {
                rows++
                // The steps i .. i + h - 1 make the BG change of this row
                if (mealsUpTo[i + h] - mealsUpTo[i] > 0) {
                    mealRows++
                    continue
                }
                LinearFeatures.fill(grid, effects, i, h, x)
                val y = future[h - 1] - grid.bg[i]
                accAll.add(x, y, weights[i])
                if (i + HORIZONS < validationFrom) accLearn.add(x, y, weights[i])
            }
        }
        val linearLearn = LinearModel(RidgeFit.solve(accLearn, LinearFeatures.PRIOR, LinearFeatures.NON_NEGATIVE).toList())
        val linearAll = LinearModel(RidgeFit.solve(accAll, LinearFeatures.PRIOR, LinearFeatures.NON_NEGATIVE).toList())
        log("Meals not entered: ${mealStarts(meals)} found, ${if (rows > 0) (mealRows * 100L / rows) else 0} % of the rows left out of the linear fit")
        logLinear(linearAll)
        // The yardstick that reads only BG, on the same learning days
        val dayMeans = CgmOnlyBaseline.dayMeans(grid)
        val cgmOnly = CgmOnlyBaseline.fit(grid, dayMeans, learn, futures, weights)
        times.add(LINEAR, linearStart)

        // Network on the learning days, early stop on the validation days
        val networkStart = timeSource.markNow()
        val learnSet = networkSet(grid, effects, unexplained, linearLearn, learn, futures, weights)
        val validationSet = networkSet(grid, effects, unexplained, linearLearn, validation, futures, weights)
        val scaling = Scaling.of(learnSet.x)
        scaling.apply(learnSet.x)
        scaling.apply(validationSet.x)
        val network = ResidualNetwork(inputs = Samples.INPUTS, seed = SEED)
        var bestLoss = validationSet.loss(network)
        var bestEpoch = 0
        var best = network.parameters()
        for (epoch in 1..MAX_EPOCHS) {
            currentCoroutineContext().ensureActive()
            network.trainEpoch(learnSet.x, learnSet.y, learnSet.w, BATCH, LEARNING_RATE, epoch)
            val loss = validationSet.loss(network)
            progress("Network: epoch $epoch of at most $MAX_EPOCHS")
            if (loss < bestLoss) {
                bestLoss = loss
                bestEpoch = epoch
                best = network.parameters()
            } else if (epoch - bestEpoch >= PATIENCE) break
            yield()
        }
        network.setParameters(best)
        log("Network: best epoch $bestEpoch, validation error ${bg(sqrt(bestLoss) * LearnedModel.OUTPUT_SCALE)}")
        times.add(NETWORK, networkStart)

        // GRU of the expected curve on the learning days of all phases, early stop on the validation days. The GRU of
        // the last model learned from all days up to its training, so it saw most of today's test days: measured from
        // it, the test error would look better than it is.
        val gruStart = timeSource.markNow()
        val windowStart = now - windowDays * DAY_MS
        val gru = ExpectedCurveTrainer(expectedCurve, threads, timeSource, log, progress, bg)
        val gruData = gru.prepare(grids, now, windowStart, validationFromMs, testFromMs)
        val unseen = previous?.takeIf { it.trainedAt <= validationFromMs }?.expected
        if (previous?.expected != null && unseen == null) log("Expected curve: measured from a new start, the last model saw the test days")
        val gruFit = gru.learn(gruData, linearLearn, unseen)
        times.add(GRU, gruStart)

        // Measure on the test days, as live
        progress("Measuring")
        val measureStart = timeSource.markNow()
        val expected: ((Int) -> DoubleArray?)? = gruFit?.let { fit -> { i -> gru.predict(gruData, fit, linearLearn, 0, i) } }
        val cgm: ((Int) -> DoubleArray?)? = cgmOnly?.let { fit -> { i -> CgmOnlyBaseline.predict(fit, grid, dayMeans, i) } }
        val evaluation = evaluate(grid, effects, unexplained, linearLearn, network, scaling, test, futures, autoIsfRuns, expected, cgm, testFromMs)
        // Ready only when it beats AutoISF on the same moments, and those cover at least half of the test days.
        // Without enough stored AutoISF runs there is nothing fair to compare with, so it is not ready.
        // And it must not see fewer of the lows coming than AutoISF, once there are enough lows to tell.
        val autoIsf = evaluation.autoIsf
        val enoughRuns = evaluation.coverage >= MIN_COVERAGE && autoIsf != null
        val ready = enoughRuns && evaluation.modelOnAutoIsf.beats(autoIsf) && evaluation.seesLows(LearnedModel.LINEAR)
        val expectedReady = enoughRuns && evaluation.expectedOnAutoIsf.beats(autoIsf) && evaluation.seesLows(LearnedModel.EXPECTED)
        logEvaluation(evaluation, ready, expectedReady)
        times.add(MEASURE, measureStart)

        // The model used: the linear part of all days, the network trained again on all days
        progress("Final training")
        val finalStart = timeSource.markNow()
        val allSet = networkSet(grid, effects, unexplained, linearAll, samples, futures, weights)
        val finalScaling = Scaling.of(allSet.x)
        finalScaling.apply(allSet.x)
        val finalNetwork = ResidualNetwork(inputs = Samples.INPUTS, seed = SEED)
        repeat(max(MIN_FINAL_EPOCHS, bestEpoch)) { epoch ->
            currentCoroutineContext().ensureActive()
            finalNetwork.trainEpoch(allSet.x, allSet.y, allSet.w, BATCH, LEARNING_RATE, epoch + 1)
            yield()
        }
        times.add(NETWORK, finalStart)

        // And the GRU trained again on all days
        val finalGruStart = timeSource.markNow()
        val finalGru = gruFit?.let { gru.final(gruData, linearAll, max(ExpectedCurveTrainer.MIN_FINAL_EPOCHS, it.bestEpoch), previous?.expected) }
        times.add(GRU, finalGruStart)
        val seconds = times.mapValues { it.value.inWholeSeconds.toInt() }
        log(
            "Time: linear ${seconds[LINEAR]} s, network ${seconds[NETWORK]} s, expected curve ${seconds[GRU]} s, " +
                "measuring ${seconds[MEASURE]} s"
        )

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
            modelOnAutoIsfMoments = evaluation.modelOnAutoIsf,
            naiveOnAutoIsfMoments = evaluation.naiveOnAutoIsf,
            autoIsfCoverage = evaluation.coverage,
            typicalErrorMgdl = evaluation.perHorizon.toList(),
            ready = ready,
            clampHits = evaluation.clampHits,
            expected = finalGru?.network?.toState(),
            expectedStepMean = if (finalGru != null) gruData.stepScaling.mean.toList() else emptyList(),
            expectedStepStd = if (finalGru != null) gruData.stepScaling.std.toList() else emptyList(),
            expectedContextMean = finalGru?.contextScaling?.mean?.toList() ?: emptyList(),
            expectedContextStd = finalGru?.contextScaling?.std?.toList() ?: emptyList(),
            expectedMetrics = evaluation.expected,
            expectedOnAutoIsfMoments = evaluation.expectedOnAutoIsf,
            expectedTypicalErrorMgdl = evaluation.expectedPerHorizon?.toList() ?: emptyList(),
            expectedReady = expectedReady && finalGru != null,
            phases = grids.size,
            trainingSeconds = seconds,
            typicalErrorByChange = evaluation.perHorizonByChange,
            expectedTypicalErrorByChange = evaluation.expectedPerHorizonByChange,
            cgmOnly = evaluation.cgmOnly,
            cgmOnlyOnAutoIsfMoments = evaluation.cgmOnlyOnAutoIsf,
            lowsOnAutoIsfMoments = evaluation.pairedLows,
            lows = evaluation.lows,
            versusAutoIsf60 = evaluation.versusAutoIsf60
        )
    }

    private fun MutableMap<String, Duration>.add(part: String, start: TimeMark) {
        this[part] = getValue(part) + start.elapsedNow()
    }

    private fun mealStarts(meals: BooleanArray): Int = meals.indices.count { meals[it] && (it == 0 || !meals[it - 1]) }

    private fun Metrics?.beats(autoIsf: Metrics?): Boolean = this != null && autoIsf != null && mae60 < autoIsf.mae60

    private fun logEvaluation(evaluation: Evaluation, ready: Boolean, expectedReady: Boolean) {
        // Which test days have stored runs: a gap shows whether the database lost them or the loop did not run
        log("AutoISF runs on the test days, part of the moments per day (oldest first): " +
                evaluation.coveragePerDay.joinToString(", ") { "${(it * 100).roundToInt()} %" })
        val expected = evaluation.expected
        log(
            "Test days, all moments: 60 min error " + (if (expected != null) "expected ${bg(expected.mae60)}, " else "") +
                "no more insulin ${bg(evaluation.model.mae60)}, " + (evaluation.cgmOnly?.let { "BG only ${bg(it.mae60)}, " } ?: "") +
                "BG stays ${bg(evaluation.naive.mae60)}"
        )
        val autoIsf = evaluation.autoIsf
        val pairedModel = evaluation.modelOnAutoIsf
        if (autoIsf == null || pairedModel == null) {
            log("Test days: no stored AutoISF runs -> neither curve is ready")
        } else {
            val percent = (evaluation.coverage * 100).roundToInt()
            val pairedExpected = evaluation.expectedOnAutoIsf
            val verdict = when {
                evaluation.coverage < MIN_COVERAGE -> "neither curve is ready (too few AutoISF runs)"
                pairedExpected != null             -> "expected curve ${readyText(expectedReady)}, no more insulin curve ${readyText(ready)}"
                else                               -> "no more insulin curve ${readyText(ready)}"
            }
            log(
                "Test days, $percent % of moments with an AutoISF run: 60 min error " +
                    (if (pairedExpected != null) "expected ${bg(pairedExpected.mae60)}, " else "") +
                    "no more insulin ${bg(pairedModel.mae60)}, " + (evaluation.cgmOnlyOnAutoIsf?.let { "BG only ${bg(it.mae60)}, " } ?: "") +
                    "AutoISF ${bg(autoIsf.mae60)} -> $verdict"
            )
            if (evaluation.versusAutoIsf60.isNotEmpty()) log(
                "Test days, 60 min error minus AutoISF, 95 % range by days: " +
                    evaluation.versusAutoIsf60.entries.joinToString(", ") { (curve, range) ->
                        "${curveName(curve)} ${signed(range.mean)} (${signed(range.low)} .. ${signed(range.high)})" + if (range.surelyBelowZero) "" else " not sure"
                    }
            )
            val pairedLows = evaluation.pairedLows
            if (pairedLows.isNotEmpty()) log(
                "Test days, lows below ${bg(LowCheck.LOW_MGDL)} at 30 or 60 min, moments with an AutoISF run: ${pairedLows.values.first().lows} lows; seen coming " +
                    pairedLows.entries.joinToString(", ") { (curve, check) -> "${curveName(curve)} ${check.caught} with ${check.falseWarnings} false warnings" }
            )
            for (curve in listOf(LearnedModel.EXPECTED, LearnedModel.LINEAR))
                if (pairedLows.containsKey(curve) && !evaluation.seesLows(curve)) log("Test days: the ${curveName(curve)} curve sees fewer lows coming than AutoISF -> not ready")
        }
        if (evaluation.clampHits > 0) log("Warning: the network hit its limit of ${bg(Samples.MAX_RESIDUAL)} ${evaluation.clampHits} times")
    }

    private fun readyText(ready: Boolean) = if (ready) "ready" else "not ready"

    private fun signed(value: Double): String = (if (value >= 0) "+" else "-") + bg(abs(value))

    private fun curveName(curve: String): String = when (curve) {
        LearnedModel.EXPECTED -> "expected"
        LearnedModel.LINEAR   -> "no more insulin"
        LearnedModel.CGM_ONLY -> "BG only"
        LearnedModel.AUTOISF  -> "AutoISF"
        else                  -> "BG stays"
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
        val rawDrift = DAY_PARTS.map { linear.rawDriftPerHour(angleOfHour(it)) }
        if (rawDrift.any { abs(it) > LinearModel.MAX_DRIFT_MGDL_PER_HOUR })
            log("Warning: BG on its own came out as ${rawDrift.joinToString(", ") { bg(it) }} per hour and is limited to ${bg(LinearModel.MAX_DRIFT_MGDL_PER_HOUR)}")
    }

    private fun mainCarbHours(linear: LinearModel): Int {
        val weights = linear.carbWeights()
        return CARB_HOURS[weights.indices.maxByOrNull { weights[it] } ?: 0]
    }

    /** Inputs (live, as known at the step) and targets (what the linear part left over with the real insulin) */
    /** [unexplained] from [UnannouncedMeals.unexplained]: the network learns what is left after the meal in progress too */
    private suspend fun networkSet(
        grid: HistoryGrid, effects: Effects, unexplained: DoubleArray, linear: LinearModel, steps: List<Int>, futures: Map<Int, DoubleArray>, weights: DoubleArray
    ): NetworkSet {
        val x = Array(steps.size) { FloatArray(Samples.INPUTS) }
        val y = Array(steps.size) { FloatArray(Samples.KNOTS.size) }
        val w = FloatArray(steps.size)
        steps.forEachIndexed { n, i ->
            if (n % CHECK_EVERY == 0) yield()
            Samples.networkInputs(grid, effects.atLive(i), linear, i, x[n])
            val future = futures.getValue(i)
            val meal = MealInProgress.rise(unexplained, i)
            Samples.KNOTS.forEachIndexed { k, h ->
                y[n][k] = ((future[h - 1] - grid.bg[i] - linear.delta(grid, effects, i, h) - meal[h - 1]) / LearnedModel.OUTPUT_SCALE).toFloat()
            }
            w[n] = weights[i].toFloat()
        }
        return NetworkSet(x, y, w)
    }

    private class Evaluation(
        val model: Metrics, val naive: Metrics, val autoIsf: Metrics?, val modelOnAutoIsf: Metrics?, val naiveOnAutoIsf: Metrics?,
        val coverage: Double, val perHorizon: DoubleArray, val clampHits: Int,
        val expected: Metrics?, val expectedOnAutoIsf: Metrics?, val expectedPerHorizon: DoubleArray?,
        /** Per test day, oldest first: the part of its moments with a stored AutoISF run */
        val coveragePerDay: List<Double>,
        /** Per group of [ErrorBands]: the mean error per point, empty for a group with too few moments */
        val perHorizonByChange: List<List<Double>>,
        val expectedPerHorizonByChange: List<List<Double>>,
        val cgmOnly: Metrics?,
        val cgmOnlyOnAutoIsf: Metrics?,
        /** Keys from [LearnedModel.CURVES] */
        val pairedLows: Map<String, LowCheck>,
        val lows: Map<String, LowCheck>,
        val versusAutoIsf60: Map<String, Range>
    ) {

        /**
         * False when [curve] saw a smaller part of the lows coming than AutoISF did on the same moments, and there were
         * at least [MIN_LOWS] of them; with fewer the test days cannot tell.
         */
        fun seesLows(curve: String): Boolean {
            val own = pairedLows[curve] ?: return true
            val autoIsf = pairedLows[LearnedModel.AUTOISF] ?: return true
            if (own.lows < MIN_LOWS) return true
            return own.caught >= autoIsf.caught
        }
    }

    /**
     * [expected] gives the BG change of the expected curve at step `i`, mg/dl, as known at that step; [cgm] the same for
     * the yardstick that reads only BG (null where its values are missing: then it counts as "BG stays the same").
     */
    private suspend fun evaluate(
        grid: HistoryGrid, effects: Effects, unexplained: DoubleArray, linear: LinearModel, network: ResidualNetwork, scaling: Scaling,
        test: List<Int>, futures: Map<Int, DoubleArray>, runs: List<StoredRun>, expected: ((Int) -> DoubleArray?)?,
        cgm: ((Int) -> DoubleArray?)?, testStart: Long
    ): Evaluation {
        val dayMoments = IntArray(Samples.TEST_DAYS)
        val dayCovered = IntArray(Samples.TEST_DAYS)
        val groupMoments = IntArray(ErrorBands.COUNT)
        val groupModelSum = Array(ErrorBands.COUNT) { DoubleArray(HORIZONS) }
        val groupExpectedSum = Array(ErrorBands.COUNT) { DoubleArray(HORIZONS) }
        val sortedRuns = runs.sortedBy { it.timestamp }
        val modelSum = DoubleArray(HORIZONS)
        val naiveSum = DoubleArray(HORIZONS)
        val expectedSum = DoubleArray(HORIZONS)
        val cgmSum = DoubleArray(HORIZONS)
        // The same on the moments with a stored AutoISF run only, so they are compared fairly
        val pairedModelSum = DoubleArray(HORIZONS)
        val pairedNaiveSum = DoubleArray(HORIZONS)
        val pairedExpectedSum = DoubleArray(HORIZONS)
        val pairedCgmSum = DoubleArray(HORIZONS)
        val autoIsfSum = DoubleArray(HORIZONS)
        // Per test day, the 60-minute error of each curve minus that of AutoISF, summed, for the ranges by day
        val versus = mapOf(LearnedModel.EXPECTED to DoubleArray(Samples.TEST_DAYS), LearnedModel.LINEAR to DoubleArray(Samples.TEST_DAYS), LearnedModel.CGM_ONLY to DoubleArray(Samples.TEST_DAYS))
        val lows = LowCounter()
        val pairedLows = LowCounter()
        var covered = 0
        var clampHits = 0
        val inputs = FloatArray(Samples.INPUTS)
        val knots = DoubleArray(Samples.KNOTS.size)
        val curves = HashMap<String, DoubleArray>()
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
            val meal = MealInProgress.rise(unexplained, i)
            val run = sortedRuns.nearestTo(grid.timeOf(i), RUN_MATCH_MS) { it.timestamp }
            val day = ((grid.timeOf(i) - testStart) / DAY_MS).toInt().coerceIn(0, Samples.TEST_DAYS - 1)
            dayMoments[day]++
            val group = ErrorBands.of(grid, i)
            groupMoments[group]++
            if (run != null) {
                covered++
                dayCovered[day]++
            }
            // Valid test steps always have 36 rows before them, so a GRU always gives a curve here
            val expectedChange = expected?.invoke(i)
            val cgmChange = cgm?.invoke(i)
            val now = grid.bg[i]
            val modelCurve = DoubleArray(HORIZONS) { (now + linear.delta(grid, live, i, it + 1) + meal[it] + Samples.residualAt(knots, it + 1)).coerceIn(MIN_BG, MAX_BG) }
            val cgmCurve = DoubleArray(HORIZONS) { (now + (cgmChange?.get(it) ?: 0.0)).coerceIn(MIN_BG, MAX_BG) }
            curves.clear()
            curves[LearnedModel.LINEAR] = modelCurve
            if (expectedChange != null) curves[LearnedModel.EXPECTED] = DoubleArray(HORIZONS) { (now + expectedChange[it]).coerceIn(MIN_BG, MAX_BG) }
            if (cgm != null) curves[LearnedModel.CGM_ONLY] = cgmCurve
            curves[LearnedModel.NAIVE] = DoubleArray(HORIZONS) { now }
            for (h in 1..HORIZONS) {
                val actual = future[h - 1]
                val modelError = abs(modelCurve[h - 1] - actual)
                val naiveError = abs(now - actual)
                val expectedError = curves[LearnedModel.EXPECTED]?.let { abs(it[h - 1] - actual) } ?: 0.0
                val cgmError = abs(cgmCurve[h - 1] - actual)
                modelSum[h - 1] += modelError
                naiveSum[h - 1] += naiveError
                expectedSum[h - 1] += expectedError
                cgmSum[h - 1] += cgmError
                groupModelSum[group][h - 1] += modelError
                groupExpectedSum[group][h - 1] += expectedError
                // Only at 30, 60 and 120 minutes, the points of the metrics
                run?.atStep(h)?.let {
                    val autoIsfError = abs(it - actual)
                    autoIsfSum[h - 1] += autoIsfError
                    pairedModelSum[h - 1] += modelError
                    pairedNaiveSum[h - 1] += naiveError
                    pairedExpectedSum[h - 1] += expectedError
                    pairedCgmSum[h - 1] += cgmError
                    if (h == 12) {
                        versus.getValue(LearnedModel.LINEAR)[day] += modelError - autoIsfError
                        versus.getValue(LearnedModel.EXPECTED)[day] += expectedError - autoIsfError
                        versus.getValue(LearnedModel.CGM_ONLY)[day] += cgmError - autoIsfError
                    }
                }
            }
            // Lows at 30 or 60 minutes, the points every curve has, AutoISF included
            val low = future[5] < LowCheck.LOW_MGDL || future[11] < LowCheck.LOW_MGDL
            for ((curve, values) in curves) {
                val warns = values[5] < LowCheck.LOW_MGDL || values[11] < LowCheck.LOW_MGDL
                lows.add(curve, low, warns)
                if (run != null) pairedLows.add(curve, low, warns)
            }
            if (run != null) pairedLows.add(LearnedModel.AUTOISF, low, run.at30 < LowCheck.LOW_MGDL || run.at60 < LowCheck.LOW_MGDL)
        }
        val perHorizon = DoubleArray(HORIZONS) { modelSum[it] / test.size }
        val naive = DoubleArray(HORIZONS) { naiveSum[it] / test.size }
        val expectedPerHorizon = if (expected != null) DoubleArray(HORIZONS) { expectedSum[it] / test.size } else null
        fun paired(sum: DoubleArray) = if (covered > 0) metrics(DoubleArray(HORIZONS) { sum[it] / covered }) else null
        val versusAutoIsf60 = if (covered == 0) emptyMap() else versus
            .filterKeys { (it != LearnedModel.EXPECTED || expected != null) && (it != LearnedModel.CGM_ONLY || cgm != null) }
            .mapNotNull { (curve, differences) -> DayResampling.range(differences, dayCovered)?.let { curve to it } }
            .toMap()
        return Evaluation(
            model = metrics(perHorizon),
            naive = metrics(naive),
            autoIsf = paired(autoIsfSum),
            modelOnAutoIsf = paired(pairedModelSum),
            naiveOnAutoIsf = paired(pairedNaiveSum),
            coverage = covered.toDouble() / test.size,
            perHorizon = perHorizon,
            clampHits = clampHits,
            expected = expectedPerHorizon?.let { metrics(it) },
            expectedOnAutoIsf = if (expected != null) paired(pairedExpectedSum) else null,
            expectedPerHorizon = expectedPerHorizon,
            coveragePerDay = List(Samples.TEST_DAYS) { if (dayMoments[it] > 0) dayCovered[it].toDouble() / dayMoments[it] else 0.0 },
            perHorizonByChange = byGroup(groupModelSum, groupMoments),
            expectedPerHorizonByChange = if (expected != null) byGroup(groupExpectedSum, groupMoments) else emptyList(),
            cgmOnly = if (cgm != null) metrics(DoubleArray(HORIZONS) { cgmSum[it] / test.size }) else null,
            cgmOnlyOnAutoIsf = if (cgm != null) paired(pairedCgmSum) else null,
            pairedLows = if (covered > 0) pairedLows.result() else emptyMap(),
            lows = lows.result(),
            versusAutoIsf60 = versusAutoIsf60
        )
    }

    /** Counts per curve for [LowCheck] */
    private class LowCounter {

        private val counts = LinkedHashMap<String, IntArray>()

        fun add(curve: String, low: Boolean, warns: Boolean) {
            val c = counts.getOrPut(curve) { IntArray(4) }
            if (low) c[0]++
            if (low && warns) c[1]++
            if (warns) c[2]++
            if (warns && !low) c[3]++
        }

        fun result(): Map<String, LowCheck> =
            LearnedModel.CURVES.mapNotNull { curve -> counts[curve]?.let { curve to LowCheck(it[0], it[1], it[2], it[3]) } }.toMap()
    }

    /** The mean per point of each group, an empty list for a group with fewer than [ErrorBands.MIN_MOMENTS] */
    private fun byGroup(sums: Array<DoubleArray>, moments: IntArray): List<List<Double>> = sums.indices.map { group ->
        if (moments[group] < ErrorBands.MIN_MOMENTS) emptyList() else sums[group].map { it / moments[group] }
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
        /** Below this part of the test moments with a stored AutoISF run, neither curve is compared, so neither is ready */
        const val MIN_COVERAGE = 0.5
        /** With fewer lows on the moments with an AutoISF run, the test days cannot tell which curve sees them better */
        const val MIN_LOWS = 20
        private const val RUN_MATCH_MS = 5 * 60_000L
        private const val MIN_BG = 39.0
        private const val MAX_BG = 400.0

        /** The parts of the training whose time is measured */
        private const val LINEAR = "linear"
        private const val NETWORK = "network"
        private const val GRU = "gru"
        private const val MEASURE = "measure"
        private val PARTS = listOf(LINEAR, NETWORK, GRU, MEASURE)

        /** The hours shown for night, morning, day and evening, also on the plugin screen */
        val DAY_PARTS = listOf(3, 9, 15, 21)

        fun angleOfHour(hour: Int): Double = 2 * PI * hour / 24.0

        private fun Samples.Reject.label(): String = when (this) {
            Samples.Reject.EDGE                  -> "at the edge"
            Samples.Reject.NO_PROFILE            -> "no profile"
            Samples.Reject.NO_BG_NOW             -> "no BG"
            Samples.Reject.NO_BG_BEFORE          -> "no BG 5 to 30 min before"
            Samples.Reject.SENSOR_OR_CALIBRATION -> "sensor start or calibration"
            Samples.Reject.GAPS_AHEAD            -> "BG gaps in the next 2 h"
        }

        private fun Double.fmt(): String = ((this * 100).roundToInt() / 100.0).toString()
    }
}
