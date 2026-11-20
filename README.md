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
* On a phone the top of the overview follows the user's concept. On top a card with what the user
  sets: the profile, the temp target and the exercise button. Under it the main card with the BG value
  in the colour of its range, its unit, the trend in words, the age of the reading, the changes over
  5, 15 and 40 minutes, and IOB and COB on the right. Then the cards *Basal*, *Sensitivity* and
  *Loop* (running mode and when the loop last ran, in the warning colour after 15 minutes). The
  graphs sit in one card *Glucose and insulin*: a button to the history, the ranges 3 h, 6 h, 12 h
  and 24 h, a legend of what is shown, and the BG graph. On a phone the BG graph holds IOB and COB
  as areas at the bottom (each up to a quarter of the height, scaled to its own highest value in
  the window) and the basal as a band that hangs from the top (given basal filled, profile basal
  dashed), as in the user's xdripswift. There is no separate IOB or COB graph any more; the graph
  settings (pencil on the BG graph) switch each of them on or off, and *Glucose as a line* draws the
  readings as a line instead of dots (above the high mark in the high colour; a low reading keeps
  its red dot). The low and the high mark are dashed lines with their value on the right. A config
  saved before gets IOB, COB and the basal in the BG graph, and a graph with COB alone below it is
  removed. A hold on the graphs shows the values of that time in a card, as in xdripswift: the time
  (with *forecast* after now), the glucose large with its unit, then IOB, COB and basal with their
  names. The card stands at the top on the side away from the finger. Under the BG graph a strip
  shows the whole loaded range with the shown part framed; a tap moves the graph there, a drag moves
  it with the finger. The BG graph grows to fill the screen, with at least 200 dp. Under the graphs: the statistics of today (time in the
  overview's range, CV and the average, each with a bar; tap opens the statistics). Tablets and
  landscape keep the BG circle with the chips beside it.
* The bottom menu: *Home*, *Treatments*, *Statistics*, *History*, *Scenes* and *Manage*, and the
  buttons that need the user (pump setup, BG source, objectives, loop request, permissions). It holds
  at most 6 buttons: when more are needed, History, then Statistics, then Home give way (they are in
  the side menu too).
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
* IOB/BAS graph: where more basal is given than the profile, the band is orange; where less, blue.
  While the pump itself is suspended the given basal is drawn as 0. Holding a graph shows the
  profile basal of the same minute next to the given basal. The basal band can be switched off in
  the settings of the IOB graph (*Basal (profile and given)*); a graph setup saved before keeps it on.
* Only the display changed: the basal the loop, the pump and the IOB calculation use is the same.

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
