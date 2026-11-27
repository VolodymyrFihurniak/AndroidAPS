# AAPS 4.0.0 with autoISF 3.2.1 (release candidate)
* For documentation about AAPS without autoISF, check the wiki: https://wiki.aaps.app
* Everyone who’s been looping with AAPS needs to fill out the form after 3 days of looping
  https://docs.google.com/forms/d/14KcMjlINPMJHVt28MDRupa4sz4DDIooI4SrW0P3HSN8/viewform?c=0&w=1

> **Status: release candidate, not tested on a phone yet.** This branch moves autoISF 3.2.1 from
> AAPS 3.4.2.6 to AAPS 4.0.0. AAPS 4.0 is a big rewrite (Kotlin Multiplatform, Compose UI, Metro
> instead of Dagger), so most of the autoISF code was ported by hand, not just merged.
> Run it in open loop first and compare the results with your current autoISF 3.2.x build.

## What is autoISF?
AutoISF adds more power to the algorithm used in AAPS by adjusting the insulin sensitivity
based on different scenarios (e.g. high BG, accelerating/decelerating BG, BG plateau).
autoISF has many different settings to fine-tune these adjustments.
However, it is important to start with well-tested basal rate and settings for insulin sensitivity
and carb ratios.

## Where to find documentation about autoISF
* Please visit ga-zelle’s repository [GitHub - ga-zelle/autoISF](https://github.com/ga-zelle/autoISF/tree/A3.4.2.6_ai3.2.1).
* This repo contains a [ReadMe document](https://github.com/ga-zelle/autoISF/blob/A3.4.2.6_ai3.2.1/README.md) with a description of the latest changes to AutoISF
* The [**Quick Guide (bzw. Kurzanleitung)**](https://github.com/ga-zelle/autoISF/blob/A3.4.2.6_ai3.2.1/autoISF3.2.0_Quick_Guide.pdf) provides an overview of autoISF and its features

## What this branch contains
* [AAPS 4.0.0](https://github.com/nightscout/AndroidAPS/releases/tag/4.0.0) from the official
  [Nightscout AAPS](https://github.com/nightscout/AndroidAPS) repo, with its new design and features.
* autoISF 3.2.1 from [3.4.2.6+aisf3.2.1](https://github.com/T-o-b-i-a-s/AndroidAPS/tree/3.4.2.6+aisf3.2.1),
  merged into it. The git history of both lines is kept (a merge commit), so later updates of
  AAPS and of autoISF can be merged again.

### autoISF features in this build
* AutoISF algorithm 3.2.1: acce_ISF, bg_ISF, pp_ISF, dura_ISF, iobTH, SMB delivery ratio,
  "even/odd target" SMB control. The plugin can be selected without dev mode.
* Activity monitor: the steps and the movement of the phone change the sensitivity
  (settings: *OpenAPS AutoISF → Activity modifies sensitivity*). It also works with OpenAPS SMB.
  The app asks for the "Physical activity" permission when the activity monitor is switched on.
* Exercise mode (high temp target raises sensitivity) and the half basal exercise target
  (now in the units of the user) for AutoISF and SMB.
* Libre special settings: calibration offset/slope and smoothing of 1 minute values from xDrip+,
  a 20 minute stop of SMB after a new calibration, and a shorter database cleanup.
* 1 minute data (Libre 2/3 from xDrip+): the loop runs for every reading and starts its glucose
  status at the newest reading, as aisf did on AAPS 3.4. The BG value and the trend arrow on the
  main screen, the widget, the notification, the watch and the bolus wizard also change with every
  reading. AAPS 4.0 alone does all of this only every 5 minutes. The 5 minute grid of AAPS 4.0
  stays for autosens and the graph. Nothing to switch on:
  it works as soon as xDrip+ sends a value every minute ("Broadcast locally" in its Inter-app
  settings). How often SMBs are given is still limited by *SMB interval*.
* The AutoISF key values of every loop run are stored in the database (table `autoIsfValues`) and
  can be shown in the overview: add the series *AutoISF factors* (acce, bg, pp, dura, final) or
  *IOB threshold* to a graph below the BG graph. The sensitivity dialog shows the factors of the
  last run.
* BG parabola on the BG graph (option *BG parabola (autoISF)* in the BG graph settings, on by
  default): the fitted part is a solid line, the next 20 minutes are dashed and shown only together
  with the predictions.
* Exercise mode button in the overview, next to the temp basal chip: it shows and switches
  *High temp target raises sensitivity*. It is shown while AutoISF or SMB is the active APS.
  While the mode is on, the widget shows the same icon next to the temp basal icon.
* AAPSClient shows the algorithm of the master (*ALG: AutoISF*, *DynISF*, *SMB*, ...) next to the
  OpenAPS status. Tap the card to see the debug lines of the last loop run of the master (the
  "Script debug" of aisf).
* Automation: actions *Enable/Disable glucose ISF weights*, *Set bgAccel weight*, *Set iobTH*,
  *Set automation state*; triggers *iobTH percent*, *BgAccel weight*, *Check automation state*.
* Automation States plugin: named states like `Sleeping` with their possible values. AutoISF
  reads `Sleeping` to stop the inactivity detection.
* Logs are rolled every hour into `Documents/aapsLogs` and kept for 30 days.

### Ready made automations
They are added once to the automation list. The AutoISF examples are **switched off**: their
values are only a starting point. Check and change them, then switch on the ones you want.

| Automation | When | What |
|---|---|---|
| AutoISF example: night, iobTH 80% | every day at 22:00 | iobTH 80% |
| AutoISF example: day, iobTH 100% | every day at 06:00 | iobTH 100% |
| AutoISF example: sport, high temp target | 1500 steps or more in 30 minutes | temp target 140 mg/dL for 60 min (with exercise mode on, this raises the sensitivity) |
| AutoISF example: meal, bgAccel weight up | BG ≥ 120 mg/dL, delta ≥ +6 mg/dL, bgAccel weight < 0.2 | bgAccel weight 0.2 |
| AutoISF example: after meal, bgAccel weight back | delta ≤ 0, bgAccel weight > 0.1 | bgAccel weight 0.1 (set your own normal value) |
| Daily settings export (**on**) | every day at 03:00 | export of the settings |

For the daily export, *Unattended settings export* is on by default (Maintenance settings). It needs
the master password: do one export by hand (Maintenance → Export settings), then the password is
kept for the automatic export. When it must be entered again, the app shows a notification.

For a sleeping state as in aisf: create the state `Sleeping` with the values `True` and `False` in the
Automation States plugin, and add *Set automation state* to the night and day automations. Once the
state exists, AutoISF uses it instead of the overnight hours to stop the inactivity detection.

### Unlocked in this build
* **Simple mode is off.** Simple mode hides the AutoISF settings and uses their default values
  (weights 0, AutoISF off), so AutoISF cannot work in it. It is off by default, and AutoISF switches
  it off every time it starts. The switch stays in the general settings for the other algorithms.
* **All objectives are done.** At every start, each objective that is not done yet is marked as
  started and accomplished, so closed loop, SMB, autosens, automation and the insulin concentration
  are open at once. Objectives that were done before keep their dates. This build is meant for users
  who looped with autoISF before: make sure you know how to run the loop safely.
* The debug controls of the objectives screen (*Enable fake time and progress*, *Reset*) are shown.
* Engineering mode is always on, so the AutoISF automations and Autotune are available.
* **All sensitivity plugins can be chosen**, as in AAPS 3.x: *Sensitivity Oref1*, *Sensitivity AAPS*
  and *Sensitivity WeightedAverage*. AAPS 4.0 shows the last two only with AMA. With SMB or AutoISF
  they work too, but UAM is off unless Oref1 is chosen.
* **Insulins by trade name**, as in JugglucoNG: the insulin editor offers *NovoRapid*, *Humalog*,
  *Apidra* (75 min) and *Fiasp* (55 min) beside the AAPS templates (*Lyumjev* is one already). Each
  takes the peak of the AAPS template it belongs to and the trade name as its name; peak and DIA can
  still be changed. The clinical curves of JugglucoNG are not used: they put the peak much later
  (Fiasp about 91 min, Lyumjev about 120 min), and the loop would then give more insulin.

### Basal card on the main screen
* On a phone the top of the overview is a grid of tiles. Each tile has an icon, a title and a small
  arrow, and a tap opens what the tile shows:
  - *BG* (wide): the value in the colour of its range, its unit, the trend, the age of the reading and
    the changes over 5, 15 and 40 minutes; it opens the BG check. Beside it *Target*: the temp target
    with its time left and the colour of its reason, or the profile target the loop corrects toward
    when no temp target runs; it opens the temp targets. The exercise button sits on this tile, because the
    exercise mode works through a high temp target. The BG tile has no title line.
  - *Bolus* (the advice of the loop, see below), *Carbs* and *IOB* (opens the IOB details).
  - *Basal* (the temp basal; opens the temp basal dialog; its status line opens the basal details), *Loop*
    (opens the running modes; shows when the loop last ran, in the warning colour after 15 minutes,
    the running mode and the autosens ratio, which opens the sensitivity details) and *Sensor* (opens
    a sheet with the status: sensor, insulin, cannula, battery).
  - *Profile*: the name, the time left of a temporary profile with a bar, yellow when the profile is
    changed; it opens the profile management. *Pump*: the pump name, the reservoir, the age of the
    cannula and of the insulin; the pump battery is shown only when it is critical (the AAPS warning
    level is 51 %, so a half full battery was always shown). It opens the
    screen of the active pump, or the status sheet where the Manage sheet has no pump (AAPSClient).
  The titles of the tiles are small and grey, the values large. The texts stay on one line and get
  smaller with a big system font. The graph card has no title row on a phone either: the ranges and
  the history button are in one row, so the graph gets the height.
  The *Bolus* tile shows the advice of the loop, and a
  tap opens the Insulin dialog, where the same advice stands at the top with its numbers (BG in 1 h,
  eventual BG, insulin the loop needs, ISF, age of the run). The advice comes from the last algorithm
  run, not from the bolus wizard (which counts a negative IOB as missing insulin and so could advise
  a bolus at 3.9 mmol/l). In this order: BG below the low mark, carbs asked by the loop, or any
  prediction curve (IOB, COB, UAM, zero-temp) below the low mark in the next 2 hours means *no
  bolus*, with the reason; no need means *not needed*; in closed loop with SMB the loop gives the
  insulin itself, so no dose is offered; in open loop the dose is the need minus the SMB of that run,
  capped by the max bolus and rounded down to the pump step, and a button puts it into the amount
  field. Nothing is given until the user confirms with OK. A run older than 15 minutes gives no
  advice. The
  graphs sit in one card *Glucose and insulin*: a button to the history, the ranges 3 h, 6 h, 12 h
  and 24 h (their labels stay on one line and get smaller with a big system font on a narrow screen,
  as on Samsung phones), one row of layer switches, the BG graph and a strip under it. Each switch
  (*Predictions*, *Parabola*, *GRU*, *Linear*, *IOB*, *COB*, *Basal*, *Activity*) has the colour and
  the line of its layer, so the row is the legend too, and a tap shows or hides the layer (in simple
  mode the switches only show what is drawn). The predictions of the loop are dashed lines in the
  usual AAPS colours (IOB blue, COB orange, UAM yellow, zero-temp cyan) instead of dots, so the COB
  prediction is no longer mixed up with high readings. The strip under BG holds IOB (U, left axis),
  COB (g, right axis, in its colour), the SMBs and boluses, the insulin activity and, at the bottom,
  the basal band: it grows up from the bottom edge (given basal filled, profile basal dashed). Before
  this, IOB and COB were areas on the BG axis with no scale of their own and the basal hung from the
  top of the BG graph. The graph settings (pencil on the BG graph) can still switch each layer, and
  *Glucose as a line* draws the readings as a line instead of dots (above the high mark in the high
  colour; a low reading keeps its red dot). Without events (notes, exercise, a running mode that is
  not closed loop) the belt above BG is a thin strip instead of an empty 48 dp box. The names of the
  graphs below BG are on the left, so they no longer cover the labels of the right axis, and the
  AutoISF graph names its five lines (acce, bg, pp, dura, final) in their colours. The low and the
  high mark are dashed lines with their value on the right. The target is a thicker line with
  *target* and its value on the left. A config saved before gets IOB, COB and
  the basal switched on, and a graph with COB alone below BG is removed. A hold on the graphs shows the values of that time in a card, as in xdripswift: the time
  (with *forecast* after now), the glucose large with its unit, then IOB, COB and basal with their
  names. The card stands at the top on the side away from the finger. Under the IOB strip a mini map
  shows the whole loaded range with the shown part framed; a tap moves the graph there, a drag moves
  it with the finger. The BG graph grows to fill the screen, with at least 200 dp. Under the graphs: the statistics of today (time in the
  overview's range, CV and the average, each with a bar; tap opens the statistics).
* **Landscape and tablets** show the same cards: on the left the cards (a tablet has a large clock
  above them), on the right the graph card with the statistics of today. The BG graph there fills the
  height of the screen too. The card has no title there: the ranges and the history button are in
  one row, so the graph gets the height. Before, a turned phone showed the old screen with the BG circle and the
  chips. The status (sensor, insulin, cannula, battery) opens from the *Sensor* tile.
* The bottom menu: *Log* (the treatment history), *Statistics*, *History*, a *+* in the middle as a
  filled button (the treatments sheet), *Scenes* and *Manage*, and the buttons that need the user (pump setup, BG
  source, objectives, loop request, permissions). There is no *Home*: the menu belongs to the home
  screen, so Home did nothing. It holds at most 5 buttons: when more are needed, History, then
  Statistics, then Log give way (they are in the side menu too).
* **Who gave it**, in the treatment history: each bolus and each temp basal gets a small line
  *Algorithm* (an SMB or a TBR of the loop), *Automation*, *Client: <device name>*, or *You* with the
  screen it came from (*You · Bolus wizard*, *You · Wear*, ...). It is read from the user entry log,
  no database change. From now on the master writes the device name of a paired client into the
  user entry of each action it does for that client; older client actions, and records entered in
  Nightscout, show *Client or Nightscout*. A bolus read from pump history has no line.
* The strings this build adds (basal card, graph card, statistics, menu, AutoISF settings and
  automations) have a Ukrainian translation. Upstream strings still come from Crowdin.
* **Basal card** next to IOB and COB, in their style: the basal given now in U/h, and under it the
  percent of the active profile and the state: *By profile*, *TBR · 12 min left*, *PS 110%* while a
  profile switch is active, or *Asked 0.90 U/h* while the loop asked for a temp basal the pump has
  not set yet (open loop, or a failed command). The percent is worked out against the basal of the
  profile at this time, with the percent of a profile switch already in it, so a TBR and a profile
  switch are never mixed up; "—" when the profile basal is 0 or not known.
* The card does not show an old rate as the basal now. With no pump data for 15 minutes it shows
  "—" and *No recent pump data*. A pump that suspended itself shows 0 U/h and *Suspended by pump*,
  a disconnected pump 0 U/h and *Pump disconnected*. On AAPSClient the data of the master's pump is
  used.
* Tap the card for the details: profile, profile basal now, basal given, percent, profile switch,
  temp basal and the time left, the age of the pump data, the basal of the profile over the day,
  and a button to the profiles.
* The icon-only temp basal chip is gone: the card shows the same icons.
* IOB/BAS graph: the basal band is at the bottom and grows up, IOB starts above it. Where more basal
  is given than the profile, the band is orange; where less, blue.
  While the pump itself is suspended the given basal is drawn as 0. Holding a graph shows the
  profile basal of the same minute next to the given basal. The basal band can be switched off in
  the settings of the IOB graph (*Basal (profile and given)*); a graph setup saved before keeps it on.
* Only the display changed: the basal the loop, the pump and the IOB calculation use is the same.

### Personal prediction (experimental)
A test feature that learns from the history on the phone how BG reacts to insulin, carbs and the
time of day, and predicts BG for the next 2 hours as two curves. **It never changes insulin
delivery**: the loop, SMB, temp basals and all limits work as before, and the loop never waits for it.
* Switch it on in *Config Builder → Experimental → Personal prediction*. Its one setting is how many
  days of history it learns from (180 by default, 30 to 730); older days count less (half every 90
  days). The model is saved on the phone and is not part of the settings export.
* **Two curves.**
  * *Expected*: where BG really goes, the loop included, as the loop usually acted in the history. A
    small recurrent network (GRU, 32 units) reads the last 3 hours step by step (BG, insulin, IOB,
    carbs, COB, temp basal, time of day) and what BG did at the same time of day on the last 7 days.
  * *No more insulin*: what happens if nothing more is given, like the IOB curve of the loop. A
    linear part learns the personal ISF by time of day (kept within 50 % to 200 % of the profile ISF),
    how strongly and how fast carbs act (absorption of 1 to 6 hours), what BG does on its own by time
    of day, and the BG trend; it is pulled towards the profile, so with little data it stays the
    profile. The insulin acts along the known curve of each dose. A small network (16 inputs, 32
    units) learns what is left over at 30, 60, 90 and 120 minutes, limited to ±60 mg/dl.
  * All in plain Kotlin, on the phone.
* **How it learns.** The history becomes a grid of 5-minute steps (BG, every bolus and SMB with its
  own insulin curve, temp basals and extended boluses as insulin above or below the profile basal,
  carbs, the profile active at that time). Priming boluses are not counted. Meals that were eaten but
  not entered are found from BG alone (a rise faster than insulin and entered carbs explain) and left
  out of the linear part, so a full closed loop without carb entries does not make insulin look weak.
  A meal that goes on now (BG rose faster than insulin and entered carbs explain in the last 30
  minutes) goes on in the curve and fades as fast as it already slows down, as oref's UAM does; the
  GRU gets that rise as an input too. What BG does on its own is kept within 1 mmol/l per hour.
  With a 1-minute sensor
  the grid is built 5 times, shifted by 0 to 4 minutes (for the last 180 days at most), so the
  expected curve sees 5 times more moments; it keeps at most 100 000 of them.
* **How it is measured.** The last 14 days are kept back as test days, and the 7 days before them
  as validation days. The networks learn from the days before those and stop when the validation
  days stop getting better; the test days only measure. A moment whose next 2 hours reach into the
  next part is left out, so no part sees the BG of the next one. On the test days both curves,
  AutoISF (its stored loop runs), a yardstick that reads only BG and "BG stays the same" are
  compared as they would be live, at 30, 60 and 120 minutes, on the same moments. The yardstick
  ("BG only": one ridge fit per point on the BG change over 5 to 60 minutes, BG against its 24-hour
  mean, BG and the time of day, the idea of the forecast of
  [JugglucoNG](https://github.com/ctqvva/JugglucoNG)) shows whether a curve earns its size. For each
  curve the plugin also counts the lows below 70 mg/dl at 30 or 60 minutes that it saw coming and
  its false warnings, and gives the 60-minute error minus that of AutoISF with a 95 % range over
  the test days (whole days drawn again 1000 times); a range that holds 0 is not sure. A curve is
  *ready* when it beats AutoISF at 60 minutes there, and those moments cover at least half of the
  test days, and, once there are at least 20 lows on those moments, it sees at least as many of
  them coming as AutoISF; without enough stored AutoISF runs neither curve is ready. Then both
  learn again on all days. The database may keep the loop runs for fewer days, so
  the plugin keeps its own copy of the AutoISF predictions of the last 15 days (at 30, 60 and 120
  minutes, one every 5 minutes); the log shows for each test day which part of it had a run.
* **Live check.** The plugin also keeps its own forecasts of the last 8 days and checks them every
  hour against the real BG, next to AutoISF and "BG stays the same" on the same moments. A model may
  give a dose only when it is better than AutoISF at 60 minutes in this live check, on at least 3
  days of moments with an AutoISF prediction. The screen shows the table of the last 7 days.
* **When it learns.** When it is switched on without a model, then once a day while the phone is
  charging (after two days also without a charger), or on *Train now*. The final GRU starts from
  the one of the last model when it has the same size, so one more day of data needs few epochs.
  The GRU that is measured does so only when the last model never saw the validation and test days;
  else its test error would look better than it is.
  A training stops after 20 minutes at most (each of the two passes of the expected curve after 6
  minutes), and *Stop* ends it; the old model stays until a new one is ready. It needs at least about
  4 weeks of history (3 of them are kept back to validate and test). A model saved by an older
  version is not used; it learns again.
* **Every minute.** Each new reading in the database gives a new forecast that starts at that
  reading, not at the last whole 5 minutes.
* **Its screen** shows one ready line per curve, the error table (both curves, AutoISF and "BG stays
  the same"), how many 1-minute phases were used, how long each part of the training took, what it
  learned (ISF and BG on its own at 3:00, 9:00, 15:00 and 21:00, the carb factor and absorption), the
  buttons *Train now*, *Stop* and *Reset model*, and a log of each training (with insulin per day as
  the model sees it, to compare with the TDD) and of both curves every 15 minutes, as on the NSClient
  screen.
* **Graph.** The BG graph gets two layers (in the layer menu only while the plugin is on, on by
  default): the GRU curve as a solid purple line with a dashed band of its typical error, and the
  linear curve as a dashed purple line. The layer switches of the graph card name them *GRU* and
  *Linear*, and the value card shows both values on the future part. A forecast older than 15
  minutes is not drawn.
* **Insulin dialog.** Under the advice of the loop a second block *Model advice (experimental)*:
  the same guards first (BG low now, no fresh forecast, not ready, a low in the next 2 hours on either
  curve, BG falling), then the units missing or too much (with the grams that would cover them), from
  the eventual BG of the "no more insulin" curve, the target and the ISF (never below the profile
  ISF), rounded towards 0 to the pump step; in closed loop with SMB it notes that the loop may give
  part of it. The expected curve gives no dose: it already holds what the loop gives next. Under it
  BG in 30, 60 and 120 minutes on both curves, the eventual BG, the personal ISF, the typical error
  and when the model learned. There is no button: a dose is typed by hand.
* **Warnings.** In the settings of the plugin two switches, off by default: *Warn of a low* (the
  forecast goes below the low mark in the next 30 minutes) and *Warn of a high* (above the high mark
  in the next 60 minutes). They use the GRU curve, and do not wait for the live check: a warning is
  not a dose. The same warning comes again after 30 minutes at the soonest.
* **Copy report** on the plugin screen puts a text report of the model, both error tables, the live
  check and the log on the clipboard, to send when something looks wrong.
* **Its data** (the model, the copy of the AutoISF predictions and its own forecasts) lives in files
  of the plugin, not in the settings, so a settings export stays small. A model kept in the settings
  by an older build moves to its file on the first start.
* It works on AAPSClient too, from the client's own database.

### Ported from iAPS and JugglucoNG (not tested on a phone)
Ideas and code from [iAPS](https://github.com/Artificial-Pancreas/iAPS) (MIT) and
[JugglucoNG](https://github.com/ctqvva/JugglucoNG) (GPL-3.0). Everything is **off by default**.
* **Keto protection** (OpenAPS AutoISF > Keto protection). When the loop asks for a zero temp, a
  small safety basal (5-50 % of the profile basal, or a fixed rate up to 2 U/h) runs instead, so
  there is never many hours without insulin. Only when BG is at or above the target, never with an
  SMB in the same run. "Only with negative IOB" (on by default) waits until IOB is below minus one
  hour of basal.
* **B30** (OpenAPS AutoISF > B30). After a meal bolus of at least *minimum bolus* with a low temp
  target, the loop runs a higher temp basal (basal × factor, at most max basal) for 30 min. It never
  lowers what the loop wants and never runs when the loop holds back insulin. While BG is still low
  and flat it also stops SMBs.
* **Auto CR** (OpenAPS AutoISF settings). The loop changes the carb ratio by the same factor as
  AutoISF changes the ISF. The bolus wizard keeps the profile CR.
* **Loop settings in a scene** (new wizard step "Loop settings"). A scene can change ISF, CR and
  basal (50-200 %), max IOB, the SMB and UAM minutes, iobTH and the AutoISF weights, like an iAPS
  override. It can also end on its own: when carbs are entered, or when BG goes above or below a
  limit. Only the AutoISF loop reads these settings.
* **Low warning needs a confirmation** (personal prediction). A low forecast far away must be seen
  in two readings, and a fall back to the level before a short rise without insulin is not a low.
  A low soon or strong insulin still warns at once. From JugglucoNG.
* **Meal calculator in the carbs dialog.** "Add food" searches My foods and 232 common foods
  (USDA values, names in English, Ukrainian and Russian) on the phone, and on request
  [Open Food Facts](https://world.openfoodfacts.org) by name or by a typed barcode. Each food with
  its weight adds carbs, fat and protein. Typing carbs by hand clears the list.
* **Fat and protein as extended carbs** (carbs dialog settings). With the setting on, the fat and
  protein of the meal become extended carbs of the same entry, after a delay (Warsaw method, factor
  0.5 by default). From iAPS.
* **Carbs from a meal photo** (carbs dialog settings). With your own API key for OpenAI, Anthropic
  Claude or Google Gemini, the Photo button sends a photo of the plate (made smaller, 1024 px) and
  adds the foods it sees. It is only a guess: check every amount. Only on Android.
* **Insulin curves from the studies:** [docs/insulin-curve-evidence.md](docs/insulin-curve-evidence.md),
  a short reading aid from JugglucoNG.

### Fixes compared to AAPS 4.0
* **Bolus PIN works on its own, as in AAPS 3.4.** AAPS 4.0 ignored every protection while no master
  password was set, and the bolus protection while the settings had none, without telling. Now each
  level (application, bolus, settings) is protected on its own. Opening settings that have no
  protection does not let the next bolus pass without its PIN.
* **Commands from AAPSClient no longer expire because the phone clocks differ.** A command is valid
  for 8 seconds, and the master checked that time with its own clock: a master clock ahead by 8 s or
  more made every command "expired". The client now learns the clock of the master from its signed
  answers and gives the time in that clock. The first command after a start can still expire when
  the clocks are far apart; the next one works. A master that receives a command late (screen off,
  battery saving) still rejects it, on purpose. The NSClient log shows how late an expired command
  was (master) and how far the clocks are apart (client).
* **The NSClient log shows why a client command got no answer.** AAPS 4.0 wrote it only to the AAPS
  log file. Now the master writes to the NSClient log how many seconds each command took to come,
  why it ignored a command (wrong signature, old command number, phone not paired, clock more than
  5 minutes off), and when Nightscout refused its answer. Before, a refused answer was logged as
  written, and the client only showed "No reply from the master in time". The client logs the
  command number that got no answer, so a late answer can be found next to it.

Everything of aisf 3.2.1 is ported. The aisf widget showed the AutoISF factor as "ai: 85%"; the
AAPS 4.0 widget shows the profile ISF and the ISF in use (from → to), which for AutoISF is the
result of AutoISF.

### Changes compared to autoISF 3.2.1 on AAPS 3.4
* The UKF smoothing plugin of AAPS 4.0 is used. It comes from the same source as the one in aisf.
* The database version is 37 (AAPS 4.0 = 36, plus the autoISF table). A database of an
  aisf 3.2.x build (version 32) is upgraded: the migration adds what AAPS 4.0 added in its own
  version 32, so no data is lost.
* Automations, automation states and settings of aisf 3.2.x are read with the same names.
* The steps count trigger uses the newest steps of the last 5 minutes, from the watch or from the
  phone. This is the newer fix of ga-zelle for AAPS 4.0, not the 1 minute window of aisf 3.2.1.

## Keeping up with autoISF
ga-zelle, the author of autoISF, works on autoISF for AAPS 4.0 in the branch
[`400-dev/v400-dev`](https://github.com/ga-zelle/AndroidAPS/tree/400-dev/v400-dev). Its newest fix
(steps count trigger) is already in this branch. To see whether there is something new:

```bash
git remote add gazelle https://github.com/ga-zelle/AndroidAPS.git
git fetch gazelle 400-dev/v400-dev
git log --oneline --author=ga-zelle HEAD..gazelle/400-dev/v400-dev
```

Take his commits with `git cherry-pick <commit>`. When he releases autoISF for AAPS 4.0, merge his
release branch into this one.

## Updating from autoISF 3.2.x (AAPS 3.4)
1. Export your settings in the old build (Maintenance → Export settings).
2. Read the [AAPS 4.0 release notes](https://github.com/nightscout/AndroidAPS/releases/tag/4.0.0)
   first. AAPS 4.0 itself has many changes (new UI, insulin management, ...).
3. Install this build over the old one (same signing key). Do **not** uninstall the old app.
4. Check the AutoISF settings and the activity monitor settings, then start in open loop.

# Building
## How to build this branch within the Browser (Github Actions)
1. Sign in to Github, or [create a new Github Account](https://github.com/signup) if you do not
   have one yet
2. Create a fork of this repo in your own account by pressing the "Fork" button.
3. If you have never used the browser build before, follow the [preparation steps](https://wiki.aaps.app/en/latest/SettingUpAaps/BrowserBuild.html#preparation-steps)
   to create secrets within your forked repository. This is a one time step.
4. Start the browser build using the Github Action
   [aaps-ci](https://wiki.aaps.app/en/latest/SettingUpAaps/BrowserBuild.html#aaps-ci-github-actions-to-build-the-aaps-apk).
   When the description asks you to select the branch, select this branch `4.0.0+aisf3.2.1-rc`.
   The action builds the branch you select, there is no version to choose.
5. After the build is completed, you can download the APK from your Google Drive that was used when
   setting up the Github repository secrets in step 3

## How to build this branch in Android Studio
1. Get the latest version of Android Studio (AAPS 4.0 needs JDK 21 and Android SDK 37)
2. Use "Get from VCS" with the url of this repository, without a branch name.
3. Switch to the branch `4.0.0+aisf3.2.1-rc` (Remote branches → `origin/4.0.0+aisf3.2.1-rc` → Checkout).
4. Build the APK with Build -> Generate signed Bundle / APK
5. In case of any error messages during the build, try a "Clean build" first
   (Build -> Clean) and then start the APK build again.

General remark:
If you have not used AAPS 4.0 before, please first build and run the regular AAPS 4.0.0 from
https://github.com/nightscout/AndroidAPS and check that it works fine. Only then upgrade to the
version including autoISF.
