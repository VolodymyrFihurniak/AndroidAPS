# Personal Prediction (experimental) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A plugin in a new Config Builder category "Experimental" that learns from the local history how BG
reacts to insulin, carbs and time of day, draws its own 2-hour BG prediction as a graph layer, and gives its own
dose advice in the Insulin dialog.

**Architecture:** The database is read once into a compact 5-minute grid (`HistoryGrid`). Everything after
that is pure Kotlin in `commonMain`: insulin and carb effects as convolutions with fixed kernels, a linear model
fitted by ridge regression with a prior, and a small neural network for the rest. The same grid and feature
code serve training and the live prediction. The `ui` module sees only the `LearnedPrediction` interface.

**Tech Stack:** Kotlin Multiplatform (commonMain), Metro DI, kotlinx.serialization, kotlinx-datetime, Compose
Multiplatform, Vico 3.3.1.

**Spec:** `docs/superpowers/specs/2026-11-21-learned-prediction-design.md`

## Global Constraints

- All new code in `commonMain`, pure Kotlin maths, no ML library.
- No new module dependencies.
- Every class injected into a ViewModel has an owner (`@SingleIn(AppScope::class)`), see `ViewModelOwnershipTest`.
- Strings: English in `values/strings.xml`, Ukrainian in `values-uk-rUA/strings.xml` (fork strings only).
- Simple English in code; explicit imports; no fully qualified names inline; theme values in Compose.
- Internal BG unit is mg/dl; text shown to the user uses the user's units (`ProfileUtil.fromMgdlToStringWithUnits`).
- Bash: no `cd && cmd`; gradle with `--no-daemon -Dorg.gradle.workers.max=3`, output redirected to a log,
  `export LC_ALL=C.UTF-8 LANG=C.UTF-8` first; never two gradle runs at once; no edits while gradle runs.
- The model never changes insulin delivery. The loop never waits for the plugin.
- Commit after each task, push to `4.0.0+aisf3.2.1-rc` and `claude/festive-rubin-f2bil0` (user approved).
- Commit trailer:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and
  `Claude-Session: https://claude.ai/code/session_017Cg6omCHdKyhFDgxxhFaYp`

Test command used below (call it **APS-TEST**):

```bash
./gradlew --console=plain --no-daemon -Dorg.gradle.workers.max=3 :plugins:aps:testAndroidHostTest --tests "app.aaps.plugins.aps.learned.*" > /tmp/claude-0/aps-test.log 2>&1; grep -E "BUILD (SUCCESSFUL|FAILED)|^e: |FAILED" /tmp/claude-0/aps-test.log | head -20
```

## Review Focus

1. **Little data** (a few days, a new install): training must not crash; below 2000 samples it logs "not enough
   data" and keeps no model. Test in Task 6.
2. **Percentage profile switch in the window** (e.g. 70 %): net temp basal is measured against the basal of the
   profile active at that time, so a TBR equal to that basal is net 0. Test in Task 3.
3. **DST change** inside the window: grid indexes are UTC, time of day is local per point, nothing shifts or
   crashes. Test in Task 3.
4. **No BG at "now"** (sensor lost): no live forecast, the advice says "no data". Tests in Task 6 and Task 12.
5. **"Stop" during training**: the old model stays saved and keeps predicting. Test in Task 8.

---

### Task 1: Move the log helpers to core

The plugin needs the same log as NSClient. `SyncLogBuffer` and `SyncLogList` move, behaviour unchanged.
`SyncLogLayout` stays in `plugins/sync` (it uses `SyncStrings`).

**Files:**
- Move: `plugins/sync/src/commonMain/kotlin/app/aaps/plugins/sync/log/SyncLogBuffer.kt` ->
  `core/interfaces/src/commonMain/kotlin/app/aaps/core/interfaces/sync/SyncLogBuffer.kt`
- Move: `plugins/sync/src/commonMain/kotlin/app/aaps/plugins/sync/log/SyncLogList.kt` ->
  `core/ui/src/commonMain/kotlin/app/aaps/core/ui/compose/log/SyncLogList.kt`
- Move: `plugins/sync/src/androidHostTest/kotlin/app/aaps/plugins/sync/log/SyncLogBufferTest.kt` ->
  `core/interfaces/src/androidHostTest/...` if that source set exists, else keep it in sync and fix the import
- Modify imports in: `NSClientRepositoryImpl.kt`, `NSClientScreen.kt`, `NSClientScreenPreviews.kt`,
  `SyncLogLayout.kt`, `TidepoolScreen.kt`, `TidepoolRepository.kt`, `XdripScreen.kt`, `XdripMvvmRepository.kt`

**Interfaces:**
- Produces: `app.aaps.core.interfaces.sync.SyncLogBuffer(aapsLogger, tag: LTag, maxEntries: Int = 100)` with
  `entries: StateFlow<List<SyncLogEntry>>`, `add(action, text, json)`, `clear()`;
  `app.aaps.core.ui.compose.log.SyncLogList(entries, dateUtil, modifier)`.

- [ ] **Step 1:** `git mv` the three files, change the package lines, update the KDoc link in `SyncLogBuffer`.
- [ ] **Step 2:** Fix every import (`grep -rn "plugins.sync.log.SyncLog\(Buffer\|List\)"` must return nothing).
- [ ] **Step 3:** Run `:plugins:sync:testAndroidHostTest :core:interfaces:testAndroidHostTest` (redirect to log). Expected: BUILD SUCCESSFUL.
- [ ] **Step 4:** Commit "Move the sync log buffer and list to core, for reuse".

### Task 2: Config Builder category "Experimental"

**Files:**
- Modify: `core/data/src/commonMain/kotlin/app/aaps/core/data/plugin/PluginType.kt` (add `EXPERIMENTAL` last, multi-select, no sync)
- Modify: `core/ui/src/commonMain/kotlin/app/aaps/core/ui/compose/PluginCategoryTitle.kt` (`EXPERIMENTAL -> CoreUiStrings.configbuilder_experimental`)
- Modify: `plugins/configuration/src/commonMain/kotlin/app/aaps/plugins/configuration/configBuilder/ConfigBuilderImpl.kt`
  (`activePluginKey`: `EXPERIMENTAL` with the multi-select `null` group; `logPluginStatus`: `" EXPERIMENTAL"`)
- Modify: `ui/src/commonMain/kotlin/app/aaps/ui/compose/configuration/ConfigurationViewModel.kt`
  (`addCategory(PluginType.EXPERIMENTAL)` after `GENERAL`; `defaultIcon` `Icons.Default.Science` for it;
  category gets a `warningRes` shown under the title)
- Modify: the composable that draws a `ConfigCategoryUiModel` header (find it from `ConfigCategoryUiModel` usages) to show the warning line in `AapsTheme.generalColors.statusWarning`
- Modify: `ui/src/commonMain/kotlin/app/aaps/ui/compose/quickLaunch/QuickLaunchConfigViewModel.kt` (`typeOrder` gets `EXPERIMENTAL` last)
- Strings `core/ui/src/androidMain/res/values/strings.xml` (+ `values-uk-rUA`):
  `configbuilder_experimental` "Experimental" / "Експериментальне";
  `configbuilder_experimental_warning` "Test features. They do not change insulin delivery." / "Тестові функції. Вони не змінюють подачу інсуліну."
- Test: `plugins/configuration/src/androidHostTest/.../ConfigBuilderImplTest.kt`

- [ ] **Step 1: Failing test** in `ConfigBuilderImplTest`: `experimental category has no active plugin key and does not sync` -
  `assertThat(sut.activePluginKey(PluginType.EXPERIMENTAL)).isNull()`; `assertThat(PluginType.EXPERIMENTAL.singleSelect).isFalse()`; `assertThat(PluginType.EXPERIMENTAL.selectionSyncs).isFalse()`.
- [ ] **Step 2:** Run it, expect compile failure (no `EXPERIMENTAL`).
- [ ] **Step 3:** Add the enum value and fix every exhaustive `when` the compiler reports (`grep -rn "PluginType.GENERAL" --include=*.kt` to find the lists).
- [ ] **Step 4:** Run `:plugins:configuration:testAndroidHostTest :ui:testAndroidHostTest`. Expected: PASS.
- [ ] **Step 5:** Commit "Config Builder: new category Experimental".

### Task 3: History grid (pure)

The compact 5-minute grid the whole model works on, built from plain input lists. No database here.

**Files:**
- Create: `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/learned/grid/HistoryInput.kt`
- Create: `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/learned/grid/HistoryGrid.kt`
- Create: `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/learned/grid/HistoryGridBuilder.kt`
- Test: `plugins/aps/src/commonTest/kotlin/app/aaps/plugins/aps/learned/grid/HistoryGridBuilderTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  const val STEP_MS = 5 * 60_000L
  data class BgReading(val timestamp: Long, val mgdl: Double)
  data class InsulinDose(val timestamp: Long, val units: Double, val iCfg: ICfg)        // bolus, SMB
  data class TempBasalSegment(val start: Long, val end: Long, val rate: Double, val isAbsolute: Boolean) // rate U/h or %
  data class ExtendedSegment(val start: Long, val end: Long, val unitsPerHour: Double)  // always net
  data class CarbEntry(val timestamp: Long, val grams: Double, val durationMs: Long)
  data class ProfileValues(val isfMgdl: Double, val ic: Double, val basal: Double, val targetMgdl: Double, val iCfg: ICfg)
  fun interface ProfileTimeline { fun valuesAt(timestamp: Long): ProfileValues? }
  data class HistoryInput(bg, doses, tempBasals, extended, carbs, profile: ProfileTimeline,
                          sensorStarts: List<Long>, calibrations: List<Long>)
  class HistoryGrid(
      val start: Long, val size: Int,
      val bg: DoubleArray,                 // NaN = gap
      val curves: List<ICfg>,              // one per distinct (insulinPeakTime, insulinEndTime)
      val insulin: List<DoubleArray>,      // units delivered in each step, per curve; net for temp basal
      val carbs: DoubleArray,              // grams that start to act in each step
      val isf: DoubleArray, val ic: DoubleArray, val basal: DoubleArray, val target: DoubleArray, // NaN = no profile
      val excluded: BooleanArray,          // 2 h after a sensor start/change, 30 min after a calibration
      val dayAngle: DoubleArray            // local time of day as 0..2*PI
  ) { fun timeOf(index: Int): Long; fun indexOf(timestamp: Long): Int }
  object HistoryGridBuilder { fun build(input: HistoryInput, from: Long, to: Long, timeZone: TimeZone): HistoryGrid }
  ```
- Rules: `from` is floored to a multiple of `STEP_MS`. BG: the reading closest to the grid point within 3 min, values
  outside 39..400 are gaps. A dose goes to the step of its timestamp. A temp basal adds, per step, `(rateAbs - basal) *
  overlapHours`, `rateAbs = if (isAbsolute) rate else basal * rate / 100`, with `basal` and `iCfg` from the profile at
  that step. An extended bolus adds `unitsPerHour * overlapHours`. Carbs with a duration are spread evenly over the
  steps of the duration. Doses with `units <= 0` are skipped.

- [ ] **Step 1: Failing tests:**
  - `closest reading within three minutes, else a gap`: readings at +1 min and +4 min of a point -> point has the first, the next point with nothing within 3 min is `NaN`.
  - `reading outside 39 to 400 is a gap`.
  - `bolus goes to the step of its time`: 2 U at `from + 7 min` -> `insulin[0][1] == 2.0`.
  - `temp basal equal to the profile basal is net zero` (Review Focus 2): profile basal 0.7 (a 70 % switch), absolute TBR 0.7 U/h for 1 h -> all steps 0.0; percent TBR 100 % -> 0.0; absolute 0 U/h for 30 min -> total `-0.35` within 1e-9.
  - `extended bolus is net insulin`: 1.2 U/h for 30 min -> total 0.6.
  - `carbs with a duration are spread`: 30 g over 60 min -> 12 steps of 2.5 g.
  - `sensor start excludes two hours, calibration thirty minutes`.
  - `two insulin curves get two arrays` (different peak).
  - `DST day keeps UTC steps and local time of day` (Review Focus 3): `TimeZone.of("Europe/Kyiv")`, 2026-10-25, 288 steps cover 24 h of real time, `dayAngle` jumps back by one hour at 04:00 local and has no `NaN`.
- [ ] **Step 2:** Run APS-TEST, expect FAIL (classes missing).
- [ ] **Step 3:** Implement the files above.
- [ ] **Step 4:** Run APS-TEST, expect PASS.
- [ ] **Step 5:** Commit "Personal prediction: 5-minute history grid".

### Task 4: Insulin and carb effects (pure)

**Files:**
- Create: `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/learned/model/Effects.kt`
- Test: `plugins/aps/src/commonTest/kotlin/app/aaps/plugins/aps/learned/model/EffectsTest.kt`

**Interfaces:**
- Consumes: `HistoryGrid` (Task 3).
- Produces:
  ```kotlin
  const val HORIZONS = 24                                   // h = 1..24 steps (5..120 min)
  val CARB_HOURS = listOf(1, 2, 3, 4, 6); const val CARB_DELAY_MIN = 10
  /** acted(k) = part of 1 U that has acted k steps after delivery; 0 for k < 0, from ICfg.iobCalcForTreatment */
  class InsulinKernel(iCfg: ICfg) { val steps: Int; fun acted(k: Int): Double }
  /** absorbed(k) = part of the carbs absorbed k steps after the entry, straight line over hours after the delay */
  class CarbKernel(hours: Int) { fun absorbed(k: Int): Double }
  class Effects(grid: HistoryGrid, liveIndex: Int? = null) {
      /** Insulin that acts between i and i+h. With liveIndex, all doses after liveIndex are ignored (used for the network inputs, in training and live alike). */
      fun insulinActing(i: Int, h: Int): Double
      fun carbsAbsorbed(i: Int, h: Int, hours: Int): Double
      fun iob(i: Int): Double            // insulin on board at i, doses up to i
      fun cob(i: Int, hours: Int = 3): Double
      fun lastCarbsStepsAgo(i: Int): Int?; fun lastCarbsGrams(i: Int): Double
      fun lastBolusStepsAgo(i: Int, minUnits: Double = 0.5): Int?
  }
  ```
- `insulinActing(i, h) = sum over curves c, over steps j <= i + h of insulin[c][j] * (acted_c(i + h - j) - acted_c(i - j))`.
  `carbsAbsorbed` the same with `absorbed`. Kernels are built once per curve and cached.
- In a live grid (Task 7 `readLive`) the only insulin after now is the planned rest of the running temp basal, so the
  live curve uses `Effects(liveGrid)` and gets it; the network inputs use `Effects(liveGrid, liveIndex)` and do not.

- [ ] **Step 1: Failing tests:**
  - `one unit acts fully over its duration`: `insulinActing(0, kernel.steps)` for 1 U at step 0 -> 1.0 within 0.01; `iob(0) == 1.0` within 0.01; `iob(kernel.steps) < 0.01`.
  - `insulin given inside the window counts, not before its time`: 1 U at step 10 -> `insulinActing(0, 5) == 0.0`, `insulinActing(0, 24) > 0.0`.
  - `live index ignores later doses`: same dose, `Effects(grid, liveIndex = 5).insulinActing(5, 24) == 0.0`.
  - `carbs absorb in a straight line after the delay`: 60 g at step 0, 2 h kernel -> `carbsAbsorbed(0, 2) == 0.0`, `carbsAbsorbed(0, 26) == 60.0` within 1e-6, `cob(0, 2) == 60.0`.
  - `acted matches iobCalcForTreatment`: for a Fiasp-like ICfg, `1 - acted(12)` equals `iobCalcForTreatment(1 U, +60 min).iobContrib` within 1e-9.
- [ ] **Step 2:** APS-TEST, expect FAIL.
- [ ] **Step 3:** Implement `Effects.kt`.
- [ ] **Step 4:** APS-TEST, expect PASS.
- [ ] **Step 5:** Commit "Personal prediction: insulin and carb effects".

### Task 5: Linear model and ridge fit (pure)

**Files:**
- Create: `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/learned/model/LinearModel.kt`
- Create: `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/learned/model/RidgeFit.kt`
- Test: `plugins/aps/src/commonTest/kotlin/app/aaps/plugins/aps/learned/model/RidgeFitTest.kt`, `LinearModelTest.kt`

**Interfaces:**
- Consumes: `HistoryGrid`, `Effects`.
- Produces:
  ```kotlin
  object LinearFeatures {
      val NAMES = listOf("ins","ins_s1","ins_c1","ins_s2","ins_c2",
                         "carb_1h","carb_2h","carb_3h","carb_4h","carb_6h",
                         "drift","drift_s1","drift_c1","drift_s2","drift_c2",
                         "trend_short_15","trend_mid_15","trend_mid_45","trend_long_45")   // 19
      val PRIOR: DoubleArray            // ins = 1.0, carb_* = 0.2, all others 0.0
      val NON_NEGATIVE: Set<Int>        // the carb_* indexes
      fun fill(grid: HistoryGrid, effects: Effects, i: Int, h: Int, out: DoubleArray)
  }
  class RidgeAccumulator(n: Int) { fun add(x: DoubleArray, y: Double, weight: Double); val count: Long }
  object RidgeFit { fun solve(acc: RidgeAccumulator, prior: DoubleArray, nonNegative: Set<Int>, lambda: Double = 1e-3): DoubleArray }
  data class LinearModel(val theta: List<Double>) {
      fun delta(grid: HistoryGrid, effects: Effects, i: Int, h: Int): Double   // predicted BG(i+h) - BG(i), mg/dl
      fun isfMultiplier(dayAngle: Double): Double                              // clamped 0.3..3.0
      fun carbFactor(): Double                                                 // sum of carb_* weights
  }
  ```
- Feature values (`d` = `dayAngle[i]`, `s1 = sin d`, `c1 = cos d`, `s2 = sin 2d`, `c2 = cos 2d`):
  `ins* = -isf[i] * insulinActing(i,h) * {1,s1,c1,s2,c2}`;
  `carb_D = isf[i] / ic[i] * carbsAbsorbed(i,h,D)`;
  `drift* = h * 5 / 60.0 * {1,s1,c1,s2,c2}` (mg/dl per hour);
  trend: `dS = bg[i]-bg[i-1]`, `dM = (bg[i]-bg[i-3])/3`, `dL = (bg[i]-bg[i-6])/6`, `phi(t) = t * (1 - exp(-h / t))` with `t` in steps:
  `trend_short_15 = dS*phi(3)`, `trend_mid_15 = dM*phi(3)`, `trend_mid_45 = dM*phi(9)`, `trend_long_45 = dL*phi(9)`.
- `solve`: `(XtWX + lambda * diag(XtWX) + 1e-6 I) theta = XtWy + lambda * diag(XtWX) * prior`, Cholesky. Then any
  non-negative index below 0 is fixed to 0 and the system is solved again without it, at most 5 rounds.
- `delta` uses `isfMultiplier` (clamped) for the insulin part, so the curve matches what is shown.

- [ ] **Step 1: Failing tests:**
  - `ridge finds known parameters`: 5000 random rows of 3 features, `y = 2 x0 - 1 x1 + 0.5 x2` + small noise -> theta within 0.02.
  - `ridge stays at the prior without data`: 3 rows, prior `[1,0,0]`, lambda 1e3 -> theta within 0.05 of prior.
  - `negative weight of a non-negative feature is set to zero`.
  - `linear model with prior predicts the profile effect`: grid with 1 U at step 0, ISF 50, no carbs, flat BG; `LinearModel(PRIOR).delta(grid, effects, 0, 24)` equals `-50 * insulinActing(0,24)` within 1e-6.
  - `isf multiplier is clamped`: theta ins = 5.0 -> `isfMultiplier(0.0) == 3.0`.
- [ ] **Step 2:** APS-TEST, expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** APS-TEST, expect PASS.
- [ ] **Step 5:** Commit "Personal prediction: linear model with ridge fit".

### Task 6: Residual network, trainer and forecaster (pure)

**Files:**
- Create: `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/learned/model/ResidualNetwork.kt`
- Create: `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/learned/model/LearnedModel.kt`
- Create: `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/learned/train/Samples.kt`
- Create: `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/learned/train/Trainer.kt`
- Create: `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/learned/Forecaster.kt`
- Create in `core/interfaces/src/commonMain/kotlin/app/aaps/core/interfaces/aps/LearnedPrediction.kt`: the interface and `LearnedForecast` (below)
- Test: `.../learned/model/ResidualNetworkTest.kt`, `.../learned/train/TrainerTest.kt`, `.../learned/ForecasterTest.kt`

**Interfaces:**
- Consumes: Tasks 3-5.
- Produces:
  ```kotlin
  // core/interfaces
  data class LearnedPoint(val timestamp: Long, val mgdl: Double)
  data class LearnedForecast(
      val timestamp: Long, val bgMgdl: Double,
      val points: List<LearnedPoint>,          // 24, from +5 to +120 min
      val typicalErrorMgdl: List<Double>,      // 24, mean absolute error on the test days per point
      val eventualMgdl: Double, val isfMgdl: Double, val ic: Double, val targetMgdl: Double,
      val ready: Boolean, val modelTrainedAt: Long
  ) { fun at(minutes: Int): Double? }
  interface LearnedPrediction {
      val enabled: StateFlow<Boolean>
      val forecast: StateFlow<LearnedForecast?>
      suspend fun forecastNow(): LearnedForecast?
  }
  // plugins/aps
  class ResidualNetwork(inputs: Int = 16, hidden: Int = 32, outputs: Int = 4, seed: Int = 1) {
      fun predict(x: FloatArray): FloatArray            // scaled outputs
      fun trainEpoch(x: Array<FloatArray>, y: Array<FloatArray>, w: FloatArray, batch: Int = 256, lr: Float = 1e-3f)
      fun loss(x: Array<FloatArray>, y: Array<FloatArray>, w: FloatArray): Double
      fun toState(): NetworkState; companion object { fun fromState(s: NetworkState): ResidualNetwork }
  }
  @Serializable data class NetworkState(...)   // weights, biases, Adam not saved
  @Serializable data class Metrics(val mae30: Double, val mae60: Double, val mae120: Double)
  @Serializable data class LearnedModel(
      val version: Int = 1, val trainedAt: Long, val windowDays: Int, val samples: Int,
      val theta: List<Double>, val network: NetworkState,
      val inputMean: List<Float>, val inputStd: List<Float>, val outputScale: Float = 50f,
      val model: Metrics, val autoIsf: Metrics?, val naive: Metrics, val autoIsfCoverage: Double,
      val typicalErrorMgdl: List<Double>, val ready: Boolean, val clampHits: Int
  ) { fun toJson(): String; companion object { fun fromJson(json: String): LearnedModel? } }
  data class StoredRun(val timestamp: Long, val mainCurve: List<Double>)   // AutoISF 5-min points, mg/dl
  object Samples {
      const val TEST_DAYS = 14; const val MIN_SAMPLES = 2000
      fun valid(grid: HistoryGrid, i: Int): Boolean
      fun weight(ageMs: Long): Double              // 0.5 ^ (ageDays / 90)
      fun networkInputs(grid: HistoryGrid, effects: Effects, linear: LinearModel, i: Int, out: FloatArray)
  }
  class Trainer(private val log: (String) -> Unit, private val progress: (String) -> Unit) {
      /** Null when there is not enough data. Checks cancellation between epochs and chunks. */
      suspend fun train(grid: HistoryGrid, now: Long, windowDays: Int, autoIsfRuns: List<StoredRun>): LearnedModel?
  }
  object Forecaster {
      /** Null when BG at liveIndex or the profile is missing. */
      fun forecast(model: LearnedModel, grid: HistoryGrid, liveIndex: Int): LearnedForecast?
  }
  ```
- `Samples.valid`: BG at `i, i-1, i-3, i-6`; at most one missing point in `i+1..i+24` (filled linearly, never the last);
  profile at `i`; not excluded; `i >= 120` steps of lead.
- Network inputs, in this order: `bg, dS, dM, dL, dS-dM, sin d, cos d, iob, insulinActing(i,1), cob, min since last
  carbs (cap 360), grams of last carbs, min since last bolus >= 0.5 U (cap 360), tbr ratio (net insulin of step i * 12 /
  basal), linear delta at 12 and at 24 steps with the live kind (Effects with liveIndex = i)`. Standardized with the
  train-set mean and std (std 0 -> 1).
- Network targets: `(actual(i+h) - bg[i] - linear.delta(i,h)) / outputScale` for `h = 6, 12, 18, 24` with the normal
  (not live) Effects. Output at use: times `outputScale`, clamped to +/-60 mg/dl (count `clampHits`); between knots straight
  lines, 0 at `h = 0`.
- Trainer steps (log one line each, text from the spec 5.10): count samples (< `MIN_SAMPLES` -> log "not enough data",
  return null); split last `TEST_DAYS` as test; ridge on train and on all (two accumulators, one pass); network on
  train with early stop on the test loss (patience 5, max 60 epochs); metrics on test for model, naive (delta 0) and
  AutoISF (`StoredRun` nearest within 5 min, `mainCurve[h-1]`); coverage = part of test samples with a run; network
  again on all samples for the best epoch count (at least 5); ready = `model.mae60 < (if (coverage >= 0.5) autoIsf.mae60 else naive.mae60)`.
- Forecaster: points `bg[i] + linear.delta(live) + network residual`, clamped 39..400; `eventual = point(24) -
  isfNow * iob(i+24) + isf/ic * carbFactor * remaining carbs at i+24 (3 h kernel weighted by carb_* weights)`;
  `isfNow = isf[i] * isfMultiplier(dayAngle[i])`.

- [ ] **Step 1: Failing tests:**
  - `network gradient check`: numeric vs analytic gradient of the loss on 3 samples, relative error < 1e-3.
  - `network training lowers the loss`: `y = sin(x0)` on 500 samples, 30 epochs -> loss at the end < 0.3 * loss at the start.
  - `trainer finds a known body` (main test): build a synthetic `HistoryInput` of 60 days, 1-min BG simulated with true ISF multiplier 0.8 at night and 1.2 by day, carb factor 1.1 with 2 h absorption, drift +6 mg/dl/h from 04:00 to 07:00 local, a meal and bolus 3 times a day, SMBs from a simple rule. `train(...)` gives a model with `isfMultiplier(night)` in 0.7..0.9, `isfMultiplier(day)` in 1.1..1.3, `carbFactor()` in 1.0..1.2, `model.mae60 < naive.mae60`.
  - `not enough data` (Review Focus 1): 5 days -> `train` returns null and the log has a line that contains "not enough data".
  - `live features equal training features`: for a step `i` well inside the grid, `networkInputs` from the full grid with `Effects(grid, liveIndex = i)` for the live part equals what `Forecaster` builds from a grid cut at `i` (`grid` from `from = time(i) - 12 h` to `time(i) + 2 h`, BG after `i` removed).
  - `json round trip`: `LearnedModel.fromJson(model.toJson()) == model`; `fromJson("{")` is null.
  - `no forecast without BG now` (Review Focus 4): BG at `liveIndex` is NaN -> `forecast(...) == null`.
  - `forecast stays inside 39 to 400`: a model with a huge drift theta -> all points in 39..400.
- [ ] **Step 2:** APS-TEST, expect FAIL.
- [ ] **Step 3:** Implement (the synthetic generator lives in `commonTest/.../learned/SyntheticHistory.kt`).
- [ ] **Step 4:** APS-TEST, expect PASS. Note the time of the main test in the commit message.
- [ ] **Step 5:** Commit "Personal prediction: network, trainer and forecaster".

### Task 7: History reader (database)

**Files:**
- Create: `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/learned/HistoryReader.kt`
- Test: `plugins/aps/src/androidHostTest/kotlin/app/aaps/plugins/aps/learned/HistoryReaderTest.kt` (Mockito)

**Interfaces:**
- Consumes: `PersistenceLayer`, `ActivePlugin`, `DateUtil`; Task 3 types.
- Produces:
  ```kotlin
  @SingleIn(AppScope::class) @Inject
  class HistoryReader(persistenceLayer: PersistenceLayer, activePlugin: ActivePlugin, profileFunction: ProfileFunction, processedTbrEbData: ProcessedTbrEbData, dateUtil: DateUtil) {
      /** Reads [from, to) in 7-day chunks. */
      suspend fun read(from: Long, to: Long): HistoryInput
      /** The last 12 h before now and the next 2 h, with the running temp basal until its planned end as a planned segment; carbs after now are left out. */
      suspend fun readLive(now: Long): HistoryInput
      /** Stored loop runs of [from, to), one day at a time, main curve: COB when COB > 0, else UAM, else IOB. */
      suspend fun storedRuns(from: Long, to: Long): List<StoredRun>
  }
  ```
- Sources: `getBgReadingsDataFromTimeToTime`; `getBolusesFromTimeToTime` (skip `isBasalInsulin`, skip invalid);
  `getTemporaryBasalsStartingFromTimeToTime` (start 1 day earlier, keep those that overlap); `getExtendedBolusesStartingFromTimeToTime`;
  `getCarbsFromTimeToTimeExpanded`; `getEffectiveProfileSwitchesFromTimeToTime` (plus the last one before `from`) -> a
  `ProfileTimeline` that finds the active EPS by binary search and reads ISF/IC/basal/target with `ProfileSealed.EPS`
  at that time of day; `getTherapyEventDataFromToTime` (types `SENSOR_CHANGE`, `SENSOR_STARTED`);
  `getValidCalibrationEntriesSince`; `getApsResults` + `algorithmResult?.predBGs`.

- [ ] **Step 1: Failing tests** with mocked `PersistenceLayer`:
  - `profile timeline uses the switch active at the time`: EPS A at day 0, EPS B at day 1 -> `valuesAt(day0 + 1 h)` from A, `valuesAt(day1 + 1 h)` from B, `valuesAt(before A)` null.
  - `live read adds the planned rest of the running temp basal`: TBR started 10 min ago for 30 min -> the segment ends at `now + 20 min`.
  - `stored run main curve prefers COB when carbs are on board`.
- [ ] **Step 2:** Run `:plugins:aps:testAndroidHostTest --tests "app.aaps.plugins.aps.learned.HistoryReaderTest"`, expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same run, expect PASS.
- [ ] **Step 5:** Commit "Personal prediction: history reader".

### Task 8: The plugin

**Files:**
- Create: `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/learned/LearnedPredictionPlugin.kt`
- Create: `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/learned/TrainingSchedule.kt`
- Create: `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/learned/keys/LearnedKeys.kt`
- Modify: `app/src/test/kotlin/app/aaps/di/metro/ContributedPluginsTest.kt` (add key `260` after `252`)
- Modify: `app/src/testFull/resources/prefs-schema.txt` (rewritten by `PreferenceKeySnapshotTest`, check the diff)
- Strings `plugins/aps/src/androidMain/res/values/strings.xml` (+ uk): `learned_prediction` "Personal prediction" / "Персональний прогноз";
  `learned_prediction_description` "Experimental. Learns from your history and predicts BG for the next 2 hours. Does not change insulin delivery." /
  "Експериментально. Вчиться на вашій історії й прогнозує глюкозу на 2 години вперед. Не змінює подачу інсуліну.";
  `learned_window_days` "Days of history to learn from" / "Скільки днів історії брати для навчання"
- Test: `plugins/aps/src/commonTest/kotlin/app/aaps/plugins/aps/learned/TrainingScheduleTest.kt`,
  `plugins/aps/src/androidHostTest/kotlin/app/aaps/plugins/aps/learned/LearnedPredictionPluginTest.kt`

**Interfaces:**
- Consumes: Tasks 1, 2, 6, 7.
- Produces:
  ```kotlin
  enum class LearnedIntKey : IntPreferenceKey { TrainingWindowDays("learned_prediction_window_days", 180, 30, 730, title = ApsStrings.learned_window_days) }
  enum class LearnedStringKey : StringNonPreferenceKey { ModelJson("learned_prediction_model", "", exportable = false) }
  object TrainingSchedule {
      const val RETRAIN_MS = 24 * 3_600_000L; const val RETRY_MS = 3_600_000L; const val TIME_LIMIT_MS = 10 * 60_000L
      fun shouldStart(now: Long, model: LearnedModel?, lastAttempt: Long, running: Boolean): Boolean
  }
  enum class LearnedState { NO_MODEL, TRAINING, READY, NOT_READY }
  data class LearnedStatus(val state: LearnedState, val progress: String?, val model: LearnedModel?)
  @ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
  @ContributesBinding(AppScope::class, binding = binding<LearnedPrediction>())
  @IntKey(260) @SingleIn(AppScope::class) @Inject
  class LearnedPredictionPlugin(...) : PluginBaseWithPreferences(...), LearnedPrediction {
      val status: StateFlow<LearnedStatus>; val log: SyncLogBuffer   // LTag.APS, 100 lines
      fun trainNow(); fun stopTraining(); fun resetModel()
  }
  ```
- `PluginDescription().mainType(PluginType.EXPERIMENTAL).icon(Icons.Default.Science).pluginName(...).description(...)
  .composeContent { (it as LearnedPredictionPlugin).let { p -> LearnedPredictionScreen(p) } }` (screen from Task 9; in
  this task a placeholder `Text` is fine). Off by default, `ownPreferences = LearnedIntKey.entries + LearnedStringKey.entries`,
  preference screen with `TrainingWindowDays`.
- `onStart`: `enabled = true`; load the model from `ModelJson`; collect `rxBus.toFlow(EventBucketedDataCreated::class)`
  debounced 5 s in `appScope` -> compute `forecast` (`HistoryReader.readLive` + `Forecaster`), then if
  `TrainingSchedule.shouldStart(...)` start training. `onStop`: cancel jobs, `enabled = false`, `forecast = null`.
- Training job: `appScope.launch(Dispatchers.Default)` + `withTimeout(TIME_LIMIT_MS)`; reads `read(now - windowDays, now)` and
  `storedRuns(now - 14 d, now)`; on success saves JSON, logs, recomputes the forecast; on cancel, timeout or error logs
  the reason and keeps the old model. `trainNow` ignores the 24 h rule but not a running job.
- A prediction line goes to the log at most once per 15 minutes.

- [ ] **Step 1: Failing tests:**
  - `TrainingScheduleTest`: no model and no attempt -> true; no model, attempt 30 min ago -> false; model 23 h old -> false; model 25 h old and attempt 2 h ago -> true; running -> false.
  - `LearnedPredictionPluginTest` `stop keeps the old model` (Review Focus 5): saved model JSON, `trainNow()` with a `HistoryReader` mock that suspends, then `stopTraining()` -> `preferences.put(ModelJson, ...)` never called, `status.value.model` is still the old one.
- [ ] **Step 2:** Run APS-TEST and the plugin test, expect FAIL.
- [ ] **Step 3:** Implement; add key 260 to `ContributedPluginsTest`.
- [ ] **Step 4:** Run APS-TEST, the plugin test, `:app:testFullDebugUnitTest --tests "*PreferenceKeySnapshotTest" --tests "*ContributedPluginsTest" --tests "*ViewModelOwnershipTest"`; commit the rewritten `prefs-schema.txt` after reading its diff (only the two new keys).
- [ ] **Step 5:** Commit "Personal prediction: the plugin, training schedule and log".

### Task 9: Plugin screen

**Files:**
- Create: `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/learned/compose/LearnedPredictionScreen.kt`
- Strings (+ uk), all `learned_*`: states (no model, training, ready, not ready "Not better than AutoISF yet"), "Last
  training: %1$s", table headers ("Model", "AutoISF", "BG stays", "30 min", "60 min", "120 min"), "What it learned",
  "ISF %1$s: %2$s (profile %3$s)" for night/morning/day/evening (angles at 03:00, 09:00, 15:00, 21:00 local), "Carbs:
  x%1$s, mostly %2$s h", "Drift %1$s: %2$s per hour", buttons "Train now", "Stop", "Reset model".

**Interfaces:**
- Consumes: `LearnedPredictionPlugin.status`, `.log.entries`, `trainNow/stopTraining/resetModel`; `SyncLogList` (Task 1).
- Produces: `@Composable fun LearnedPredictionScreen(plugin: LearnedPredictionPlugin)`.

- [ ] **Step 1:** Implement the screen: status card, error table (rows model/AutoISF/BG stays, columns 30/60/120, values
  with units), learned card, button row (`Stop` only while training), `SyncLogList` filling the rest. Wire it in
  `composeContent`.
- [ ] **Step 2:** Snapshot check: a temporary test that renders the screen with a fake status and writes a PNG to the
  scratchpad; look at it, then delete the test file.
- [ ] **Step 3:** Run `:plugins:aps:compileAndroidMain` (or APS-TEST). Expected: BUILD SUCCESSFUL.
- [ ] **Step 4:** Commit "Personal prediction: plugin screen".

### Task 10: Graph layer

**Files:**
- Modify: `core/interfaces/.../overview/graph/GraphConfiguration.kt` (`SeriesType.LEARNED_PREDICTION`, in `DEFAULT_BG_OVERLAYS`)
- Modify: `ui/.../overview/graphs/GraphConfigRepositoryImpl.kt` (new key `KEY_MODEL_LAYER = "modelLayer"`: written as
  `LEARNED_PREDICTION in bgOverlays`; on read, absent -> on; `LEARNED_PREDICTION` left out of the old `bgOverlays` key)
- Modify: `ui/.../overview/graphs/GraphsSection.kt` (`BG_OVERLAY_SERIES` gets it, the menu shows it only when
  `learnedAvailable`; `CONFIGURABLE_SERIES` excludes it; short name "Model" / full "Personal prediction (experimental)")
- Modify: `ui/.../overview/graphs/SecondaryGraphCompose.kt` (`when` branch, unused like `BG_PARABOLA`)
- Modify: `ui/.../overview/graphs/GraphViewModel.kt` (inject `LearnedPrediction`; `learnedForecastFlow` = its forecast,
  or always null when `fullWindow`; `learnedAvailable` = its `enabled`, false when `fullWindow`; a forecast older than 15 minutes is dropped)
- Modify: `ui/.../overview/graphs/BgGraphCompose.kt` (new layer after layer 5: three lines - centre 2 dp solid, upper
  and lower 1 dp dashed at alpha 0.5 - from `points +/- typicalErrorMgdl`, converted to graph units; dummies when off;
  the points count for the axis like the loop predictions)
- Modify: `ui/.../overview/graphs/GraphCardHeader.kt` (legend "Model" when shown, "Model: not better than AutoISF yet" when `!ready`)
- Modify: `ui/.../overview/graphs/GraphInspect.kt` (a "Model" row when the finger is on a time with a model point)
- Modify: `core/ui/.../compose/GeneralColors.kt` (`learnedPrediction`: light `0xFF7E57C2`, dark `0xFFB39DDB`)
- Strings in `core/ui` (+ uk): `learned_shortname` "Model" / "Модель"; `learned_not_ready_legend` "Model: not better than AutoISF yet" / "Модель: ще не краща за AutoISF"; `graph_series_learned` "Personal prediction (experimental)" / "Персональний прогноз (експериментально)"
- Test: `ui/src/androidHostTest/.../graphs/GraphConfigRepositoryImplTest.kt`, `GraphViewModelTest.kt` (constructor)

- [ ] **Step 1: Failing tests** in `GraphConfigRepositoryImplTest`:
  - `model layer is on for a config saved before it`: JSON with `bgLayers` and no `modelLayer` -> `LEARNED_PREDICTION in bgOverlays`.
  - `model layer switched off stays off`: round trip of a config without it -> not in `bgOverlays`.
  - `old key does not get the model layer`: `toJson` -> the `bgOverlays` array has no `LEARNED_PREDICTION`.
- [ ] **Step 2:** Run `:ui:testAndroidHostTest --tests "*GraphConfigRepositoryImplTest"`, expect FAIL.
- [ ] **Step 3:** Implement all files above; fix `GraphViewModelTest` and any other constructor call.
- [ ] **Step 4:** Run `:ui:testAndroidHostTest`. Expected: PASS. Snapshot of the BG graph with a fake forecast (temporary test, PNG to scratchpad, then delete).
- [ ] **Step 5:** Commit "Personal prediction: graph layer".

### Task 11: Model advice (pure)

**Files:**
- Create: `ui/src/commonMain/kotlin/app/aaps/ui/compose/insulinDialog/ModelAdvice.kt`
- Test: `ui/src/commonTest/kotlin/app/aaps/ui/compose/insulinDialog/ModelAdviceTest.kt`

**Interfaces:**
- Consumes: `LearnedForecast` (Task 6).
- Produces:
  ```kotlin
  enum class ModelAdviceKind { NO_DATA, LOW_NOW, NOT_READY, LOW_PREDICTED, MISSING, TOO_MUCH, NONE }
  @Immutable data class ModelAdvice(val kind: ModelAdviceKind, val units: Double = 0.0, val carbs: Int = 0,
      val minPredictedMgdl: Double? = null, val forecast: LearnedForecast? = null, val loopMayGive: Boolean = false)
  const val MODEL_FORECAST_MAX_AGE_MS = 15 * 60_000L
  fun modelAdviceOf(forecast: LearnedForecast?, bgMgdl: Double?, lowMgdl: Double, bolusStep: Double, closedLoopSmb: Boolean, now: Long): ModelAdvice
  ```
- Order: BG < low -> `LOW_NOW`; forecast null or older than 15 min -> `NO_DATA`; `!ready` -> `NOT_READY`; any point <
  low -> `LOW_PREDICTED` with the lowest point; `need = (eventual - target) / isf` floored towards 0 to the step:
  `need >= step` -> `MISSING` (`loopMayGive = closedLoopSmb`), `need <= -step` -> `TOO_MUCH` with `carbs = round(|need| * ic)`,
  else `NONE`.

- [ ] **Step 1: Failing tests:** one per kind; `floored towards zero` (need 0.67, step 0.05 -> 0.65; need -0.67 -> -0.65,
  carbs `round(0.65 * 10) = 7` with ic 10); `low now wins over everything`; `old forecast is no data` (16 min).
- [ ] **Step 2:** Run `:ui:testAndroidHostTest --tests "*ModelAdviceTest"`, expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same run, expect PASS.
- [ ] **Step 5:** Commit "Personal prediction: model advice rules".

### Task 12: Model advice in the Insulin dialog

**Files:**
- Modify: `ui/.../overview/chips/BolusRecommendationSource.kt` (inject `LearnedPrediction`; `suspend fun modelAdvice(): ModelAdvice?` -
  null when `!enabled.value`, else `modelAdviceOf(forecastNow(), ...)` with the same BG, low mark, step and
  `loopGivesSmb` as `current()`; wrapped in `runCatching`, failure -> `ModelAdvice(NO_DATA)`)
- Create: `ui/.../insulinDialog/ModelAdviceCard.kt` (`internal fun ModelAdviceCard(advice: ModelAdvice, modifier)`;
  same look as `BolusRecommendationCard`, title "Model advice (experimental)", no button)
- Modify: `ui/.../insulinDialog/InsulinDialogUiState.kt` (`modelAdvice: ModelAdvice? = null`),
  `InsulinDialogViewModel.kt` (load it in `init` next to `recommendation`), `InsulinDialogScreen.kt` (card under the loop card)
- Strings in `ui` (+ uk), `model_adv_*`: title; "Do not bolus: BG is low."; "No current prediction of the model.";
  "The model is not better than AutoISF yet, so it gives no dose."; "Do not bolus: the model predicts %1$s."; "About
  %1$s are missing."; "The loop may give part of this itself as SMB."; "About %1$s too much insulin. About %2$s would
  cover it."; "No bolus needed."; detail lines "In 30 min: %1$s", "In 60 min: %1$s", "In 120 min: %1$s", "Eventual: %1$s",
  "Personal ISF: %1$s", "Typical error at 60 min: %1$s", "Model trained: %1$s ago".
- Test: `ui/src/androidHostTest/.../insulinDialog/InsulinDialogViewModelTest.kt`, `ChipsViewModelTest`, `OverviewViewModelFixture` (mock `modelAdvice()` -> null)

- [ ] **Step 1: Failing test** in `InsulinDialogViewModelTest`: `model advice is loaded` - source mock returns
  `ModelAdvice(MISSING, units = 0.5)` -> `uiState.value.modelAdvice?.kind == MISSING`; returns null -> `modelAdvice == null`.
- [ ] **Step 2:** Run `:ui:testAndroidHostTest --tests "*InsulinDialogViewModelTest"`, expect FAIL.
- [ ] **Step 3:** Implement; fix the other mocks.
- [ ] **Step 4:** Run `:ui:testAndroidHostTest`, expect PASS. Snapshot of the dialog with both cards (temporary test, then delete).
- [ ] **Step 5:** Commit "Personal prediction: model advice in the Insulin dialog".

### Task 13: Full check, README, push

- [ ] **Step 1:** CI suite: `./gradlew --console=plain --no-daemon --continue -Dorg.gradle.workers.max=3 -PfirebaseDisable testFullDebugUnitTest testDebugUnitTest jvmTest testAndroidHostTest :desktop:shell:test > /tmp/claude-0/ci.log 2>&1`. Expected: BUILD SUCCESSFUL (Maven 429 -> run again).
- [ ] **Step 2:** iOS compile: `:plugins:aps:compileKotlinIosArm64 :ui:compileKotlinIosArm64 :core:interfaces:compileKotlinIosArm64 :core:ui:compileKotlinIosArm64 :plugins:sync:compileKotlinIosArm64 :plugins:configuration:compileKotlinIosArm64`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3:** README: a section "Personal prediction (experimental)" - what it learns, where to switch it on, the log,
  the graph layer, the advice block, the ready gate, that it never changes delivery.
- [ ] **Step 4:** Commit "README: personal prediction" and push both branches.
