# Personal Prediction v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two curves (expected, from a GRU; no more insulin, with the insulin effect learned on quiet samples), each
measured against AutoISF, computed every minute from the newest reading.

**Architecture:** v1 code in `plugins/aps/.../learned` stays the base. The grid gets an anchored mode and 1-minute
phases; a plain Kotlin GRU learns the expected curve from precomputed step features; the linear part fits the insulin
weights on quiet samples first. `LearnedForecast` carries both curves to the graph and the Insulin dialog.

**Tech Stack:** Kotlin Multiplatform (commonMain), coroutines (parallel batch gradient), kotlinx.serialization, Compose,
Vico 3.3.1.

**Spec:** `docs/superpowers/specs/2026-11-21-personal-prediction-v2-design.md` (and v1:
`docs/superpowers/specs/2026-11-21-learned-prediction-design.md`)

## Global Constraints

- Plain Kotlin in `commonMain`, no ML library, no Rust, no new module dependency.
- The model never changes insulin delivery; the loop never waits for it.
- Dose advice rules of the safety fix stay: larger of personal and profile ISF; no "missing" while BG falls more than
  3 mg/dl in 5 min; ready only vs AutoISF on the same moments with coverage >= 50 %.
- GRU: 32 units, 36 steps (3 h) of 5-minute steps, 11 step inputs, 24 outputs (/50), at most 25 epochs, patience 4,
  at most 100 000 samples, time limit of the whole training 20 minutes.
- Quiet sample: no carbs from 36 steps before to 24 steps after, and `bg[i] - bg[i-3] <= 0`. Insulin pull x10, ISF
  multiplier 0.5-2.0.
- Strings: English `values` + Ukrainian `values-uk-rUA` (fork strings). Simple English, explicit imports, theme values.
- Gradle: `--no-daemon -Dorg.gradle.workers.max=3`, `LC_ALL=C.UTF-8`, log redirected, never two at once, no edits while
  it runs. Commit + push both branches after each task (user approved).

APS-TEST:
`./gradlew --console=plain --no-daemon -Dorg.gradle.workers.max=3 :plugins:aps:testAndroidHostTest --tests "app.aaps.plugins.aps.learned.*"`

## Review Focus

1. **5-minute sensor**: no phases, phase 0 only; nothing breaks. Test in Task 1.
2. **First week of history**: the 7-day inputs with no days found give BG now, change 0 and a count 0. Test in Task 4.
3. **Newest reading older than 10 minutes**: no live forecast, also in anchored mode. Test in Task 6.
4. **No stored AutoISF runs**: neither curve is ready. Test in Task 5.
5. **Saved v2 model after the update**: not read (version 3), learned again. Test in Task 5.

---

### Task 1: Anchored grid and 1-minute phases

**Files:** Modify `learned/grid/HistoryGridBuilder.kt`; Create `learned/grid/Phases.kt`; Test `commonTest/.../grid/HistoryGridBuilderTest.kt`, `PhasesTest.kt`

**Interfaces:**
- Produces: `HistoryGridBuilder.build(input, from, to, timeZone, alignToStep: Boolean = true)` - with `false` the
  first step is exactly `from`. `object Phases { fun isOneMinuteSensor(bg: List<BgReading>): Boolean; fun offsets(bg: List<BgReading>): List<Long> }`
  - median gap of the sorted readings <= 2 min -> offsets 0, 60 000 ... 240 000 ms, else only 0.

- [ ] **Step 1: Failing tests:** `not aligned starts exactly at from` (from = t0 + 3 min -> `grid.start == from`,
  `grid.timeOf(1) == from + STEP_MS`); `one minute sensor gets five phases` (readings every minute -> offsets `[0, 60000, 120000, 180000, 240000]`);
  `five minute sensor gets one phase` (Review Focus 1: readings every 5 min -> `[0]`); `no readings get one phase`.
- [ ] **Step 2:** APS-TEST, expect FAIL (no parameter / no `Phases`).
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** APS-TEST, expect PASS.
- [ ] **Step 5:** Commit "Personal prediction v2: anchored grid and 1-minute phases".

### Task 2: Insulin weights from quiet samples

**Files:** Modify `learned/model/RidgeFit.kt`, `learned/model/LinearModel.kt`, `learned/train/Samples.kt`, `learned/train/Trainer.kt`; Modify `commonTest/.../SyntheticHistory.kt`; Test `RidgeFitTest.kt`, `TrainerTest.kt`, `LinearModelTest.kt`

**Interfaces:**
- Produces: `RidgeFit.solve(acc, prior, nonNegative, lambda = 1e-3, pullScale: DoubleArray? = null, fixed: Map<Int, Double> = emptyMap())`
  (`pullScale[k]` multiplies the pull of feature k; fixed weights are not fitted, their part is taken out of `X^T y`);
  `Samples.isQuiet(grid: HistoryGrid, i: Int): Boolean`; `LinearModel.MIN_ISF_MULTIPLIER = 0.5`, `MAX_ISF_MULTIPLIER = 2.0`;
  `SyntheticHistory.generate(days, seed, unannouncedMeals: Boolean = false)` - with `true`, two extra meals a day that
  are eaten but not entered, so the loop's SMBs come while BG rises.
- Fit: stage 1 accumulator over quiet samples, insulin features pull x10; take `ins*`. Stage 2 over all samples with
  `fixed = ins*`. Log `Quiet samples: N`.

- [ ] **Step 1: Failing tests:** `RidgeFitTest.fixed weights stay and the rest fits around them`
  (y = 2 x0 + 3 x1, fixed {0: 2.0} -> theta[0] == 2.0, theta[1] within 0.02 of 3); `stronger pull keeps a feature nearer its prior`;
  `LinearModelTest.isf multiplier is clamped` now 0.5..2.0; `TrainerTest.unannounced meals do not make insulin look weak`:
  60 days with `unannouncedMeals = true`, the fitted `isfMultiplier(PI)` within 15 % of the true 1.2 (and first
  reproduce the problem: with the stage-1 filter off - a private flag `quietInsulinFit = false` - it is below 0.9).
- [ ] **Step 2:** APS-TEST, expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** APS-TEST, expect PASS (all v1 trainer tests too).
- [ ] **Step 5:** Commit "Personal prediction v2: insulin effect from quiet samples".

### Task 3: GRU network

**Files:** Create `learned/model/GruNetwork.kt`; Test `commonTest/.../model/GruNetworkTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  @Serializable data class GruState(val steps: Int, val inputs: Int, val context: Int, val units: Int, val dense: Int, val outputs: Int, val parameters: List<Double>)
  class GruNetwork(steps: Int = 36, inputs: Int = 11, context: Int, units: Int = 32, dense: Int = 32, outputs: Int = 24, seed: Int = 1) {
      fun predict(sequence: Array<FloatArray>, context: FloatArray): FloatArray
      fun loss(count: Int, sample: (Int) -> GruSample): Double
      fun gradient(count: Int, sample: (Int) -> GruSample): DoubleArray
      suspend fun trainEpoch(count: Int, sample: (Int) -> GruSample, batch: Int = 64, lr: Double = 1e-3, epoch: Int, threads: Int)
      fun parameters(): DoubleArray; fun setParameters(p: DoubleArray); fun toState(): GruState
      companion object { fun fromState(s: GruState): GruNetwork }
  }
  class GruSample(val sequence: Array<FloatArray>, val context: FloatArray, val target: FloatArray, val weight: Float)
  ```
- Cell: `z = s(Wz x + Uz h + bz)`, `r = s(Wr x + Ur h + br)`, `n = tanh(Wn x + Un (r*h) + bn)`, `h' = (1-z)*n + z*h`, h0 = 0.
  Head: `tanh(Wd [h_T, context] + bd)` then linear to outputs. Weighted MSE over outputs. Backprop through all steps.
  Samples come from a provider, built when their batch is: 100 000 stored sequences would need hundreds of MB. A
  sequence is an array of references to the precomputed step rows (no copy). `trainEpoch` splits each batch into `threads` parts, computes the gradients with `async(Dispatchers.Default)`, sums,
  one Adam step. Xavier start, seeded.

- [ ] **Step 1: Failing tests:** `gru gradient check` (steps 4, inputs 3, context 2, units 3, dense 3, outputs 2; numeric
  vs analytic relative error < 1e-3 for every parameter); `gru learns the sum of a sequence` (inputs 1, steps 6, target
  = sum/6, 400 samples, 40 epochs -> loss < 0.2 x start); `state round trip gives the same outputs`; `parallel gradient equals single`
  (threads 1 vs 3 give the same parameters after one epoch within 1e-9).
- [ ] **Step 2:** APS-TEST, expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** APS-TEST, expect PASS.
- [ ] **Step 5:** Commit "Personal prediction v2: GRU network".

### Task 4: Expected-curve features

**Files:** Create `learned/train/SequenceFeatures.kt`; Test `commonTest/.../train/SequenceFeaturesTest.kt`

**Interfaces:**
- Consumes: `HistoryGrid`, `Effects`, `LinearModel`.
- Produces:
  ```kotlin
  object SequenceFeatures {
      const val STEPS = 36; const val STEP_INPUTS = 11; const val CONTEXT = 15
      /** Per step: bg, bg change, net insulin, insulin acting next step, IOB, carbs, carbs absorbed next step (3 h), COB, temp basal ratio, sin, cos */
      fun steps(grid: HistoryGrid, effects: Effects): Array<FloatArray>      // once per grid, size x 11, NaN BG -> last BG
      fun sequence(steps: Array<FloatArray>, i: Int): Array<FloatArray>?     // rows i-35..i, null when i < 35
      /** 7-day mean BG at 30/60/90/120 min ahead and change (8), days found (1), profile ISF and IC (2), linear live deltas at 6/12/18/24 (4) */
      fun context(grid: HistoryGrid, live: Effects, linear: LinearModel, i: Int): FloatArray
      fun target(grid: HistoryGrid, i: Int): FloatArray?                    // 24 changes /50 from Samples.futureBg
  }
  ```

- [ ] **Step 1: Failing tests:** `step features use nothing after the step` (a dose at step 50 does not change rows <= 49);
  `seven day context averages the same time of day` (BG +10 each day at the same time -> mean as computed by hand, days 7);
  `first week has no days` (Review Focus 2: days 0, mean = BG now, change 0); `sequence needs 36 steps`.
- [ ] **Step 2:** APS-TEST, expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** APS-TEST, expect PASS.
- [ ] **Step 5:** Commit "Personal prediction v2: features of the expected curve".

### Task 5: Trainer and model v3

**Files:** Modify `learned/train/Trainer.kt`, `learned/model/LearnedModel.kt`; Test `TrainerTest.kt`

**Interfaces:**
- Consumes: Tasks 1-4.
- Produces: `Trainer.train(grids: List<HistoryGrid>, now, windowDays, autoIsfRuns): LearnedModel?` (grids[0] = phase 0;
  the linear part and the residual network use grids[0], the GRU uses all); the old single-grid call stays as
  `train(grid, ...) = train(listOf(grid), ...)`. `LearnedModel` version 3 with `expected: GruState?`,
  `expectedContextMean/Std`, `expectedStepMean/Std: List<Float>`, `expectedMetrics: Metrics?`,
  `expectedOnAutoIsfMoments: Metrics?`, `expectedTypicalErrorMgdl: List<Double>`, `expectedReady: Boolean`,
  `phases: Int`, `trainingSeconds: Map<String, Int>` (keys "linear", "network", "gru", "measure").
- GRU samples: all valid (phase, step) pairs, kept with chance `min(1, 100000 / count)` (seeded); test days by time;
  early stop on test loss, patience 4, at most 25 epochs; final GRU retrained on all for the best epoch count (>= 3);
  `threads` = `Runtime`-free: a constructor parameter, default 4. Log each part with its seconds.

- [ ] **Step 1: Failing tests:** `expected curve beats BG stays and the no more insulin curve` (60 days,
  unannounced meals, runs = BG stays: `expectedMetrics.mae60 < naive.mae60` and `< model.mae60`, `expectedReady`);
  `no stored AutoISF runs means neither curve is ready` (Review Focus 4); `a version 2 model is not read` (Review Focus 5);
  `json round trip with the gru`; `the log gives the seconds of each part`.
- [ ] **Step 2:** APS-TEST, expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** APS-TEST, expect PASS. Note the test time.
- [ ] **Step 5:** Commit "Personal prediction v2: trainer with the expected curve, model v3".

### Task 6: Forecaster, plugin, every minute

**Files:** Modify `core/interfaces/.../aps/LearnedPrediction.kt`, `learned/Forecaster.kt`, `learned/LearnedPredictionPlugin.kt`, `learned/TrainingSchedule.kt`; Test `ForecasterTest.kt`, `LearnedPredictionPluginTest.kt`

**Interfaces:**
- Produces: `LearnedForecast` + `expectedPoints: List<LearnedPoint> = emptyList()`, `expectedTypicalErrorMgdl: List<Double> = emptyList()`,
  `expectedReady: Boolean = false`. `Forecaster.forecast(model, grid, liveIndex)` fills both. `TrainingSchedule.TIME_LIMIT_MS = 20 min`.
- Plugin live: grid built with `alignToStep = false` from `newest - 12 h` so the last step is the newest reading;
  `liveIndex` = that last step; no reading in 10 min -> null. Training: one grid per `Phases.offsets(input.bg)`.

- [ ] **Step 1: Failing tests:** `forecast has the expected curve` (model with a GRU -> 24 expected points from +5 min);
  `anchored live grid starts at the newest reading` (newest 2 min ago -> forecast timestamp == newest);
  `old newest reading gives no forecast` (Review Focus 3: 11 min -> null).
- [ ] **Step 2:** APS-TEST, expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** APS-TEST + `:app:testFullDebugUnitTest --tests "*PluginLifetimeWorkScanTest"`, expect PASS.
- [ ] **Step 5:** Commit "Personal prediction v2: both curves every minute".

### Task 7: Graph, two layers

**Files:** Modify `core/interfaces/.../graph/GraphConfiguration.kt` (`SeriesType.LEARNED_EXPECTED`), `ui/.../graphs/GraphConfigRepositoryImpl.kt` (key `expectedLayer`, absent = on), `GraphsSection.kt`, `SecondaryGraphCompose.kt`, `GraphViewModel.kt`, `LearnedCurve.kt` (+ `expected`, `expectedUpper`, `expectedLower`, `expectedReady`), `BgGraphCompose.kt` (layer 5b: expected solid 2 dp + band; no more insulin dashed 2 dp), `GraphCardHeader.kt` (two legend entries), `GraphInspect.kt` (two rows); strings `core/ui` (+uk): `learned_expected_shortname` "Model: expected", `learned_no_insulin_shortname` "Model: no more insulin", `graph_series_learned_expected`; Test `GraphConfigRepositoryImplTest.kt`

- [ ] **Step 1: Failing tests:** `expected layer is on for a config saved before it`; `expected layer switched off stays off`; `old key holds neither model layer`.
- [ ] **Step 2:** `:ui:testAndroidHostTest --tests "*GraphConfigRepositoryImplTest"`, expect FAIL.
- [ ] **Step 3:** Implement; snapshot of the BG graph with both curves (temporary test, PNG to the scratchpad, then delete).
- [ ] **Step 4:** `:ui:testAndroidHostTest`, expect PASS.
- [ ] **Step 5:** Commit "Personal prediction v2: two graph layers".

### Task 8: Insulin dialog with both curves

**Files:** Modify `ui/.../insulinDialog/ModelAdvice.kt`, `ModelAdviceCard.kt`; strings `ui` (+uk): `model_adv_in_both` "In %1$s: %2$s expected, %3$s with no more insulin"; Test `ModelAdviceTest.kt`

- [ ] **Step 1: Failing test:** `a low on the expected curve says do not bolus` (no-more-insulin points 120, expected point 65 -> LOW_PREDICTED with 65).
- [ ] **Step 2:** `:ui:testAndroidHostTest --tests "*ModelAdviceTest"`, expect FAIL.
- [ ] **Step 3:** Implement (lowest point over both curves; card lines with both values when the expected curve is there).
- [ ] **Step 4:** Same, expect PASS.
- [ ] **Step 5:** Commit "Personal prediction v2: both curves in the Insulin dialog".

### Task 9: Plugin screen

**Files:** Modify `learned/compose/LearnedPredictionScreen.kt`; strings `plugins/aps` (+uk): `learned_row_expected` "Expected",
`learned_row_no_insulin` "No more insulin", `learned_state_ready_expected`/`learned_state_not_ready_expected` (one line per curve),
`learned_phases` "1-minute phases used: %1$d", `learned_training_time` "Training took %1$s"

- [ ] **Step 1:** Rows Expected / No more insulin / AutoISF / BG stays on the same moments; a ready line per curve; phases and the time of each part.
- [ ] **Step 2:** Snapshot (temporary test, then delete); `:plugins:aps:testAndroidHostTest --tests "app.aaps.plugins.aps.learned.*"` PASS.
- [ ] **Step 3:** Commit "Personal prediction v2: plugin screen".

### Task 10: Full check, README, push

- [ ] **Step 1:** CI suite with `--continue`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 2:** iOS compile of `plugins:aps`, `ui`, `core:interfaces`, `core:ui`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3:** README: v2 section (two curves, GRU, every minute, quiet insulin fit, measuring).
- [ ] **Step 4:** Commit "README: personal prediction v2" and push both branches.
