# Personal prediction v2 (experimental) - design

Status: draft for review, 2026-11-21. Builds on `2026-11-21-learned-prediction-design.md` (v1) and on the safety
fix `1dc8f54dcd`.

## 1. Intent

The user wants the model to predict more accurately and to be computed every minute. The user asked for two curves:

- **Expected**: where BG will really go, including what the loop will do (its future SMBs and temp basals). This is
  what forecasting research measures, and what the user wants to see on the graph.
- **No more insulin**: what happens if nothing more is given. This is what a dose advice needs (v1 has it).

The long-term wish stays the same: a model like this may replace the closed loop one day, decided from measured
numbers.

## 2. What the research says (November 2026)

- With a lot of personal data, a light LSTM/GRU beats pretrained time-series foundation models (Chronos-2, TimesFM)
  by 4-21 % (GlucoFM-Bench, arXiv 2606.06881). The user has about a year of data on the phone.
- Patch transformers (PatchTST, Crossformer) gain from a long history, best with one week (Karagoz 2025). A cheap way
  to give a small model this: inputs that describe the same time of day over the last 7 days.
- In closed loop data insulin is given because BG rises, so a free model learns that insulin raises BG. A known
  insulin curve and a fixed sign of its effect protect against that (Rubin-Falcone 2024). v1 already does this for the
  "no more insulin" curve.
- On a phone a small network runs in well under a millisecond (GluKDnet 2026), so a forecast every minute costs
  nothing.

## 3. What the real phone showed (v1)

- The linear part learned ISF 30 % of the profile (insulin looked weak), so the "no more insulin" curve stayed high
  while BG fell fast. The safety fix keeps the dose safe; the curve itself is still wrong.
- 23 945 samples from 360 days: the reasons are now in the log (safety fix), v2 does not depend on them.
- The trend now (BG falling 0.7 mmol/l in 5 minutes) was nearly ignored by the curve.

## 4. Goals, non-goals, anti-goals

Goals:

- Two curves, each measured against AutoISF on the same moments, each with its own ready flag.
- The expected curve from a small GRU over the last 3 hours, trained on the phone.
- A better "no more insulin" curve: the insulin effect learned only where it is not hidden by meals and the loop.
- A forecast every minute with a 1-minute sensor: the curve starts at the newest reading, not at a 5-minute step.
- Everything in Kotlin (`commonMain`), as v1. Training in the background within a time limit.

Non-goals:

- No Rust, no ML library (the user: training speed is enough; on-device speed is not the problem, see 2).
- No change to insulin delivery. The loop is not touched.
- No pretrained or cloud model; nothing leaves the phone.

Anti-goals:

- A dose advice that asks for more insulin than the profile would (kept from the safety fix).
- A curve that says "ready" without a fair comparison with AutoISF (kept from the safety fix).
- Training that blocks or slows the loop, or runs for more than the time limit.

## 5. Approaches (decided)

For the expected curve three models were weighed: a GRU over the last 3 hours (best accuracy in recent benchmarks,
slowest to train), a small causal CNN (close in accuracy, faster), and a bigger feature MLP (least work, least gain).
**The user picked the GRU.** For speed the user picked Kotlin and not Rust.

## 6. Design

### 6.1 Per-minute grid

- `HistoryGridBuilder` gets an anchored mode: the steps end exactly at a given time (the newest reading), instead of
  starting at a multiple of 5 minutes. The live grid is built anchored at the newest reading, every minute.
- Training sees all 5 phases: with a 1-minute sensor (median gap between readings 2 minutes or less) the history is
  built 5 times, shifted by 0, 1, 2, 3 and 4 minutes. With a 5-minute sensor only phase 0 is used. The samples of all
  phases are pooled; when there are more than 100 000, each is kept with the same chance so that about 100 000 stay
  (seeded, repeatable). The recency weight still applies in the loss.
- Memory: a sample is only (phase, step). Its sequence and targets are read from the grids when its batch is built,
  never stored, so 100 000 samples of 36 steps do not need hundreds of MB. Five grids of a year are about 40 MB.

### 6.2 Expected curve: GRU

- Input sequence: the last 36 steps (3 hours) before now, each step with: BG, BG change, net insulin given in the
  step, insulin activity, IOB, carbs eaten in the step, carb absorption, COB, the temp basal ratio, sin and cos of
  the time of day (11 values, scaled with the training mean and std).
- Context inputs, once per sample: the mean BG and the mean BG change over the next 2 hours at the same time of day
  over the last 7 days (2 values per knot: 30, 60, 90, 120 minutes), the profile ISF and IC, and the linear "no more
  insulin" prediction at 30, 60, 90 and 120 minutes. Days without BG at that time are skipped; with none of the 7 the
  mean is BG now and the change 0, and one more input says how many days were found (0-7).
- Network: one GRU layer with 32 units over the sequence, the last state joined with the context inputs, a dense layer
  of 32 tanh units, an output of 24 values (BG change from now at +5 ... +120 minutes, divided by 50).
- Target: the real BG change, so it learns what the loop usually does next. Loss: mean squared error over the 24
  points, the same recency weights as v1.
- Training: Adam, mini-batch, the gradient of a batch split over the CPU cores, early stop on the test days (patience
  4), at most 25 epochs. Time limit for the whole training raised from 10 to 20 minutes; the log gives the time of each
  part.
- Plain Kotlin GRU with backpropagation through time, a gradient check test, as the v1 network.

### 6.3 "No more insulin" curve

- The v1 linear model and network stay, with one change: the insulin weights (`ins*`) are fitted only on quiet
  samples, where insulin is not hidden by meals or by the loop: no carbs from 3 hours before to the end of the window,
  and BG not rising at the start (change over 15 minutes not above 0). The other weights are then fitted on all
  samples with the insulin weights fixed. The ridge pull of the insulin weights towards the profile is ten times
  stronger, and the ISF multiplier is kept in 0.5-2.0 (was 0.3-3.0).
- It is measured as in v1 (live, nothing given after the moment), against AutoISF on the same moments.

### 6.4 Measuring and ready

- Both curves are measured on the test days against the real BG, and on the moments with a stored AutoISF run against
  AutoISF's main curve. Each curve has its own ready flag: better than AutoISF at 60 minutes on the same moments,
  which cover at least half of the test days (the rule of the safety fix).
- `LearnedModel` version 3 holds both parts and both sets of errors.

### 6.5 What the user sees

- **Graph**: two layers instead of one: *Model: expected* (solid line with the band of its typical error) and *Model:
  no more insulin* (dashed line). Each can be switched on and off; both on by default once the plugin is on.
- **Plugin screen**: the error table gets a column set per curve (expected, no more insulin, AutoISF, BG stays), the
  time each training part took, and the share of 1-minute phases used.
- **Insulin dialog**: the model block shows both curves at 30, 60 and 120 minutes. The dose advice still uses the
  "no more insulin" curve with the rules of the safety fix. A low on either curve in the next 2 hours means "do not
  bolus".

### 6.6 Tests

- Anchored grid: steps end at the anchor; phases 0-4 give shifted grids; 5-minute sensors use phase 0 only.
- GRU: gradient check through time; training lowers the loss on a toy sequence task.
- Synthetic body with a loop that gives SMBs on rises: the expected curve beats "BG stays" and the linear part; the
  quiet-sample fit finds the true ISF within 15 % where v1 did not (the test reproduces the 30 % problem first).
- Ready flags per curve; model version 3 JSON round trip and rejection of version 2.
- Advice: a low on the expected curve blocks a bolus.

## 7. Calls made in this design (please check)

- GRU with 32 units, 3 hours of history in 5-minute steps, 24 outputs; at most 25 epochs; batch gradient over the
  CPU cores.
- The 7-day same-time-of-day inputs as the cheap way to a long history.
- At most 100 000 training samples after pooling the phases.
- Insulin weights fitted on quiet samples only, with a 10 times stronger pull to the profile and limits 0.5-2.0.
- Time limit 20 minutes.
- Two graph layers, both on by default.
- The dose advice stays on the "no more insulin" curve; the expected curve only adds a low guard.
