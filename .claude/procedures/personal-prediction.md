# Personal prediction (experimental plugin)

How to work on the plugin that learns BG from the phone's own history. What it does for the user
is in the top level `README.md`, section *Personal prediction (experimental)*. Why it is built this
way is in `docs/superpowers/specs/2026-11-21-learned-prediction-design.md` (v1) and
`docs/superpowers/specs/2026-11-21-personal-prediction-v2-design.md` (v2). Read the v2 design before
a bigger change.

## Hard rules

- **It never changes insulin delivery.** The loop, SMB, temp basals and limits do not read it, and
  the loop never waits for it. Keep it that way; a change that would cross this line needs the
  owner's explicit OK.
- A dose advice never asks for more than the profile would: the ISF used is never below the
  profile ISF, the amount is rounded towards 0 to the pump step, and the guards (BG low now, no
  fresh forecast, not ready, a low on either curve in 2 h, BG falling) come first.
- The expected (GRU) curve gives no dose. Only the "no more insulin" curve does.
- A curve is *ready* only after a fair comparison with AutoISF on the same moments of the kept-back
  test days, and it must not see fewer lows coming than AutoISF there (from 20 lows on). A dose
  needs the live check too. Do not loosen these to make a curve look ready.
- The test days only measure. Early stopping uses the validation days before them; 2 hours at each
  edge are left out; the measured GRU never starts from a model that saw the validation or test
  days. A change that lets the test days choose anything (an epoch, a setting) makes the ready flag
  too kind.
- In closed loop data insulin follows rising BG, so a free model learns that insulin raises BG.
  The insulin effect keeps a known curve and a fixed sign. Do not let a network learn it freely.
- Everything is plain Kotlin in `commonMain`. The owner chose no Rust and no ML library.
- Training runs in the background with a time limit and can be stopped; the old model stays until
  a new one is ready.

## Where the code is

- Plugin: `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/learned/`
  - `LearnedPredictionPlugin` - start/stop, live forecast on each new reading, training.
  - `HistoryReader`, `grid/` - history to a 5-minute grid; `Phases` for 1-minute sensors.
  - `model/` - `Effects` (insulin and carb curves), `LinearModel` + `RidgeFit`, `ResidualNetwork`,
    `GruNetwork`, `LearnedModel` (the saved model, with a version).
  - `train/` - `Trainer` (linear part), `ExpectedCurveTrainer` (GRU), `ErrorBands`,
    `UnannouncedMeals`, `MealInProgress`, `SequenceFeatures`, `Samples`, `Scaling`,
    `CgmOnlyBaseline` (the "BG only" yardstick, idea from JugglucoNG, GPL-3.0, no code copied),
    `DayResampling` (95 % ranges over the test days).
  - `Forecaster`, `LiveScore`, `ForecastArchive`, `AutoIsfRunArchive`, `ForecastAlerts`,
    `TrainingSchedule`, `LearnedReport`, `KeptRecords`.
  - `LearnedFiles` - model and records are files of the plugin (`model.json`, `autoisf_runs.txt`,
    `forecasts.txt`), not settings. Each platform has its own implementation (`AndroidLearnedFiles`,
    `IosLearnedFiles`, `DesktopLearnedFiles` in `desktop/shell`).
  - `keys/LearnedKeys` - settings (training window, low/high warnings).
  - `compose/LearnedPredictionScreen` - the plugin screen.
- The interface other modules see: `core/interfaces/.../aps/LearnedPrediction.kt`
  (`LearnedForecast`, `LearnedPoint`, `LearnedParts`).
- Users in `ui/`: BG graph layers (`GraphViewModel`, `BgGraphCompose`, `GraphCardHeader`,
  `GraphInspect`), the Insulin dialog block `insulinDialog/ModelAdviceCard`,
  `overview/chips/BolusRecommendationSource`. Test fake: `ui/.../testing/FakeLearnedPrediction`.
- Config Builder category *Experimental*: `plugins/configuration`.

## Tests

- Fast, plugin only (from the plans):
  `./gradlew --console=plain --no-daemon -Dorg.gradle.workers.max=3 :plugins:aps:testAndroidHostTest --tests "app.aaps.plugins.aps.learned.*"`
- `commonTest` holds most tests (`SyntheticHistory` builds test data, `MemoryLearnedFiles` is the
  file fake). Run `:plugins:aps:jvmTest` too if a change touches `commonTest`.
- A change to the plugin's start/stop or background jobs: also
  `:app:testFullDebugUnitTest --tests "*PluginLifetimeWorkScanTest"` (it lists the plugin's jobs by
  name).
- A change to `LearnedPrediction` or the graph: the `ui` tests (`GraphViewModelTest`,
  `OverviewViewModelFixture`).
- Before push: the full CI command from `CLAUDE.md`.
- A test that trains a model is slow. Note its time in the commit when it grows.

## When the model changes

- Change the version in `LearnedModel` when the saved format changes. A model of an older version
  is not used; the plugin learns again. Say so in the README.
- Keep the README section and the plugin screen texts in step with the change. New English strings
  get a Ukrainian translation too (fork rule, see `.claude/fork/README.md`).
- To judge a change on real data, ask the owner for *Copy report* from the plugin screen. Do not
  guess from synthetic tests alone.
