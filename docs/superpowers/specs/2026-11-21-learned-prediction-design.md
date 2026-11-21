# Personal prediction (experimental) - design

Status: draft for review, 2026-11-21. Fork `4.0.0+aisf3.2.1-rc`.

## 1. Intent

The user wants a model that learns from their own history how their body reacts to insulin, to food, and how
glucose rises "on its own". From that history, the current BG and the current treatments, the model draws its
own prediction for the next 2 hours (with the points at 30, 60 and 120 minutes as the main numbers) and says how
much insulin is missing, or how much is too much.

This is the first step. The long-term wish of the user is that a model like this replaces the closed loop one
day. This step does **not** do that: it only predicts and advises. The design keeps the model explainable and
measured against AutoISF, so the later decision can be made from numbers.

The user has a little more than 2 years of history on the phone.

## 2. Goals, non-goals, anti-goals

Goals:

- A new plugin in a new Config Builder category **Experimental**, clearly marked as experimental.
- The plugin trains a model from the local database and keeps it up to date.
- While it trains, it writes a log the user can read, like the NSClient log.
- In real time it predicts BG for every 5 minutes up to 120 minutes ahead.
- The prediction can be shown on the BG graph, switched on and off like the other graph layers.
- When the plugin is on, the Insulin dialog shows a second advice block: the advice of the model, next to the
  advice of the loop from the previous task.
- The plugin shows how good the model is: its error at 30, 60 and 120 minutes, next to the error of AutoISF
  and of "BG stays the same", measured on days the model did not learn from.

Non-goals (for this step):

- The model does not change insulin delivery in any way. The loop, SMB, TBR and all limits stay as they are.
- No data from Nightscout. The local database is the only source.
- No steps, heart rate or other sensors as model inputs.
- No learning per meal type or per food.

Anti-goals (a failure even if everything "works"):

- The loop must never wait for the model. Training must never block or slow a loop run.
- The model must not show a confident curve or a dose when it is worse than AutoISF on the test days.
- Training must not make the phone hot or drain the battery: it runs in the background, at most once a day
  plus when the user asks, and has a time limit.
- No data leaves the phone.

## 3. Constraints

- Kotlin Multiplatform: all code in `commonMain`, pure Kotlin maths. No TensorFlow or other ML library (they do
  not run in shared code for iOS). The model is small, so plain Kotlin is enough.
- No new module dependencies. Code goes into existing modules; shared types go into `core/interfaces`.
- Metro DI rules: every class injected into a ViewModel needs an owner (`ViewModelOwnershipTest`).
- Strings in English `values`, Ukrainian `values-uk-rUA` for fork strings (approved for this fork).
- Simple English in code, explicit imports, theme values in Compose.
- The CI suite (`testFullDebugUnitTest testDebugUnitTest jvmTest testAndroidHostTest :desktop:shell:test`) and
  the iOS compile must stay green.

## 4. Approaches

Three ways were considered:

1. **Correction on top of the AutoISF prediction.** Learn how wrong the stored AutoISF predictions were and add
   that error to the AutoISF curve. Simplest and fastest, but it can never be better than AutoISF in kind, and
   its dose advice would still use the ISF of the loop.
2. **Own model: known insulin action + personal parameters + a small network for the rest.** The insulin effect
   uses the known curve of each bolus; the model learns the personal ISF by time of day, how fast carbs act, and
   the drift by time of day. A small neural network learns what is left. It learns from raw history (BG,
   boluses, carbs, TBR), so it really learns the body of the user, and because the insulin part has a fixed
   shape it can answer "what does 1 U more do", which the dose advice needs.
3. **Pure neural network** on the last 2 hours of BG and treatments. Most flexible, but in closed loop data the
   loop gives insulin exactly when BG rises, so such a network learns "SMB makes BG rise". It cannot give a
   reliable dose advice, needs months of data and can give strange values in rare cases such as lows.

**Picked by the user: approach 2.**

## 5. Design

### 5.1 Where the code lives

- Plugin and model: `plugins/aps`, package `app.aaps.plugins.aps.learned`. It already has access to the
  persistence layer, profiles and the insulin curves.
- An interface `LearnedPrediction` in `core/interfaces` (package `app.aaps.core.interfaces.aps`) with the state
  the UI needs: whether the plugin is on, the current forecast, the model status and the advice input. The `ui`
  module (graph, Insulin dialog) reads only this interface.
- The log helpers `SyncLogBuffer` and `SyncLogList` move from `plugins/sync` to core modules (buffer next to
  `SyncLogEntry` in `core/interfaces`, list composable in `core/ui`), so the new plugin uses the same log
  without a new module dependency. NSClient keeps working the same way.

### 5.2 Config Builder

- New `PluginType.EXPERIMENTAL` (multi-select, does not sync to clients). Added to the category list after
  `GENERAL`, with a title "Experimental" and a short warning line in the category: "Test features. They do not
  change insulin delivery." Every exhaustive `when` over `PluginType` gets the new value.
- The plugin: name "Personal prediction", description "Experimental. Learns from your history and predicts BG
  for the next 2 hours. Does not change insulin delivery." Off by default.

### 5.3 Data for training

Source: the local database. Window: the last `N` days, preference, default **180**, range 30-730. Older days get
a lower weight (half the weight every 90 days), because the body, the insulin and the settings change over time.

The history is read in 7-day chunks (BG is stored every minute, 2 years would not fit in memory as objects) and
turned into a grid of 5-minute points:

- **BG**: the reading closest to each grid point within 3 minutes, else a gap. Readings below 39 or above 400
  mg/dl count as gaps.
- **Insulin**: every bolus (including SMB) with its own `ICfg`, plus the temp basals and extended boluses as net
  insulin over the profile basal at that time (cut into 5-minute pieces, as AAPS does for basal IOB). The
  profile at each time comes from the effective profile switch history.
- **Carbs**: every carb entry, extended carbs spread over their duration.
- **Profile values** at each grid point: ISF, IC, target. The effective profile switch list is read once and
  looked up by time, not one database query per point.
- **Sensor starts and calibrations** from the therapy events and calibration entries, for the sample filter.

A grid point `t` becomes a training sample when BG exists at `t`, at `t-5`, `t-15` and `t-30`, and at all 24
points from `t+5` to `t+120` (gaps of one point are filled linearly, longer gaps drop the sample). Samples during
the first 2 hours of a sensor and right after a calibration are dropped.

The same feature code is used for training and for the live prediction, so both see the data the same way.

### 5.4 The model

All BG values in mg/dl. For a sample at time `t` and a horizon `h` (5, 10, ... 120 minutes) the model predicts
`BG(t+h) - BG(t)` as the sum of four parts.

**Insulin part.** `IE(t,h)` = insulin that acts between `t` and `t+h`, the sum over all insulin pieces of
`IOB(t) - IOB(t+h)` from `ICfg.iobCalcForTreatment`. In training this includes insulin given inside the window
(the history knows it). In the live prediction only insulin already given counts, plus the running temp basal
until its planned end. So the live curve means "what happens if nothing more is given", the same meaning as the
IOB curve of the loop.

    insulin part = - ISF_profile(t) * m(t) * IE(t,h)
    m(t) = a0 + a1 sin(d) + a2 cos(d) + a3 sin(2d) + a4 cos(2d),   d = time of day as an angle

`m(t)` is the personal ISF multiplier by time of day.

**Carb part.** For each absorption time `D` in {1, 2, 3, 4, 6} hours, `CA_D(t,h)` = grams absorbed between `t`
and `t+h` if all carbs were absorbed in a straight line over `D` hours after a 10-minute delay.

    carb part = CSF_profile(t) * sum over D of b_D * CA_D(t,h),   CSF_profile = ISF_profile / IC_profile,  b_D >= 0

The sum of `b_D` is the personal carb factor, and where the weight sits says how fast carbs act.

**Drift part.** What happens with no insulin and no carbs: basal that does not fit, the dawn rise, and so on.

    drift part = h * (c0 + c1 sin(d) + c2 cos(d) + c3 sin(2d) + c4 cos(2d))

**Trend part.** The current movement goes on for a while: a few terms of the BG delta (5, 15, 30 minutes) times
a fixed curve over `h` that grows and then flattens. The builder picks the exact curves; every term gets a name
in the log.

The four parts above are linear in their parameters. They are fitted together by ridge regression over all
samples and all 24 horizons (streaming: only the small `X^T X` matrix and `X^T y` vector are kept). The ridge
pulls `a0` to 1, the other `a*`, `c*` to 0 and the `b_D` to an even split that sums to 1, so with little data the
model stays close to the profile. `b_D < 0` is not allowed (projected fit). `m(t)` is clamped to 0.3-3 when used,
and the log warns when the fit wanted more.

**Network part.** A small neural network learns what the linear parts leave over. Inputs (about 16, scaled):
BG, the three deltas, BG acceleration, time of day (sin and cos), IOB, insulin activity now, COB, minutes since
the last carbs, grams of the last carbs, minutes since the last bolus, the running temp basal ratio, and the
linear prediction at 60 and 120 minutes. One hidden layer of 32 units. Outputs: the residual at 30, 60, 90 and
120 minutes; the points in between come from straight lines, with 0 at `h = 0`. The output is clamped to
+/-60 mg/dl (the log counts how often the clamp was hit). Trained with Adam in mini-batches, early stop on the
test days, at most 60 epochs.

The network inputs use only what is known at `t`. "The linear prediction at 60 and 120 minutes" as an input is
the live kind (no insulin after `t`), also in training. The network target is the actual change minus the
linear parts with the real insulin, so it learns what would happen without that later insulin: in closed loop
the later SMBs hide rises, and the target puts those rises back.

The network trains on the 4 knot horizons only. Per sample that is about 16 inputs and 4 targets as `Float`,
which keeps 730 days under 20 MB.

The final curve is the sum of the four linear parts and the network part, clamped to 39-400 mg/dl.

### 5.5 Training and measuring

- The last 14 days of the window are the **test days**. The model does not learn from them. After the test, the
  model is fitted again on all days, test days included, and that model is the one used.
- On the test days the log and the plugin screen show the mean absolute error at 30, 60 and 120 minutes of:
  "BG stays the same", AutoISF (the stored loop runs: the run nearest to the sample time within 5 minutes, its
  main curve COB/UAM/IOB at that horizon, the same rule as the loop advice) and the model. Stored runs are read
  one day at a time.
- **Ready gate**: the model is "ready" when its error at 60 minutes on the test days is lower than that of
  AutoISF (or than "BG stays the same" if fewer than half of the test samples have a stored run). When it is not ready, the curve is
  still drawn (it is a test mode), with a label "not better than AutoISF yet", and the Insulin dialog shows no
  dose from the model, only the reason.
- When it trains: when the plugin is switched on and there is no model; then once every 24 hours, in the
  background, after a loop run; and when the user taps "Train now". A training run has a time limit of 10
  minutes and can be cancelled ("Stop"). While a training runs, the old model keeps predicting.
- The model (all parameters, the network weights, the scaling and the test results) is saved as JSON, so it
  survives a restart. "Reset model" deletes it.

### 5.6 Live prediction

- On the master after every loop run (`EventAPSCalculationFinished`), on a client after every new BG, the plugin
  builds the features for "now" and computes the curve. Also when a new model is ready.
- A forecast older than 15 minutes is not shown.
- The forecast holds: the 24 points, the values at 30, 60 and 120 minutes, the "eventual" value, the personal
  ISF now, and the typical error per horizon from the test days.
- `eventual = BG(t+120) - ISF_profile * m(t) * IOB(t+120) + CSF_profile * sum(b_D) * carbs not absorbed at
  t+120`, with the absorption of the fitted `b_D` mix. It is what the curve would end at once all insulin and
  carbs on board have acted.

### 5.7 Graph

- New graph layer `SeriesType.LEARNED_PREDICTION`. It appears in the layer menu only when the plugin is on. It is
  on by default (added through the same migration as the earlier new layers).
- Drawn as a line in its own colour from the theme, with a light band of +/- the typical error at each horizon,
  so the user sees how sure the model is. It sits on the same time axis as the loop predictions.
- The value card of the graph (inspect) shows the model value when the finger is on the future part.
- The legend gets the entry "Model".

### 5.8 Dose advice in the Insulin dialog

A second block under the loop advice: "Model advice (experimental)". It uses the same order of guards as the loop
advice, with the model's own curve:

1. BG below the low mark now: do not bolus.
2. Model not ready: say why, no numbers about a dose.
3. Any point of the model curve below the low mark in the next 2 hours: do not bolus, show the lowest value.
4. `need = (eventual - target) / ISF_personal_now`, rounded down to the pump step (towards 0). `target` is the
   target the loop uses now: the temp target when one runs, else the profile target.
   - `need >= step`: "About X U are missing."
   - `need <= -step`: "About X U too much insulin, about Y g of carbs would cover it" (`Y = X * IC`).
   - Else: "No bolus needed."
5. In closed loop with SMB, a line under a "missing" advice: "The loop may give part of this itself as SMB."
6. Under it: the values at 30, 60 and 120 minutes, the eventual value, the personal ISF, the typical error at
   60 minutes, and the age of the model.

There is **no** "Use" button in this block. The model is a test; the user can type a dose by hand.

### 5.9 Plugin screen

- Status line: off / no model / training (with progress) / ready / not ready, and when it last trained.
- The error table from 5.5 (three rows, three horizons).
- What it learned, in the user's units: personal ISF by time of day next to the profile ISF (4 values: night,
  morning, day, evening), the carb factor and the absorption time, the drift by time of day.
- Buttons: "Train now", "Stop", "Reset model".
- The log list, newest first.

### 5.10 Log

Short lines, one per step, for example:

- `Training started: 180 days, 51 840 samples, 812 dropped (gaps)`
- `Data: 2 418 boluses, 41 230 SMB, 1 102 carb entries`
- `Linear fit: ISF x0.92 night, x1.10 morning; carbs x0.95, mostly 2-3 h; drift +4 mg/dl/h at 5-7`
- `Network: epoch 12, test error 60 min 21.4 mg/dl`
- `Test days: 60 min error model 19.8, AutoISF 24.1, BG stays 31.0 -> ready`
- `Prediction 12:05: 30 min 142, 60 min 151, 120 min 138`
- warnings: clamps hit, fit outside limits, training stopped by the time limit

Values in the log follow the user's units.

### 5.11 Preferences

- Training window in days (default 180, 30-730).

Nothing else in this step.

### 5.12 Clients

The plugin can be switched on on an AAPSClient too. It learns from the client's database (synced from
Nightscout) and shows the curve and the advice the same way. It does not need the master.

### 5.13 Tests

`commonTest` in `plugins/aps` (and `ui` where UI state is pure):

- Ridge fit with a prior, on synthetic data: recovers known parameters.
- The network: forward pass, a gradient check of the backward pass, training lowers the loss.
- Feature builder on a synthetic history made with a known ISF, carb factor and drift: the fitted model finds
  them back within a small error, and the live features equal the training features for the same moment.
- Sample filter: gaps, sensor start, calibration.
- Ready gate and the dose advice rules (every branch).
- Model JSON round trip.

## 6. Decided by the user

- Approach 2.
- New Config Builder menu, marked experimental.
- A training log like the NSClient log.
- The prediction is an optional graph layer.
- The model gives its own dose advice in the Insulin dialog, as in the previous task.
- Horizons 30, 60, 120 minutes.

## 7. Calls made in this design (please check)

- Training window 180 days by default, older days weigh less, although 2 years are on the phone.
- The last 14 days are kept back to measure the model, then the model is fitted again on everything.
- Ready gate: the model must beat AutoISF at 60 minutes, or it gives no dose.
- No "Use" button in the model advice block.
- The graph layer is on by default once the plugin is on, and shows a band of the typical error.
- Retraining once a day in the background, 10-minute limit.
- Works on clients too.
- The network learns only the 4 knot horizons; the points in between are straight lines.
- In closed loop with SMB the model advice gets the note that the loop may give part of it.
- Fewer than half of the test samples with a stored loop run: the gate compares with "BG stays the same".

## 8. Left to the builder

- The exact trend terms and the exact network input scaling.
- Where the model JSON is stored (file or a string preference).
- Colours (from the theme) and the exact layout of the plugin screen.
