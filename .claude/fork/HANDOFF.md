# Handoff notes

Working state for the next chat. It rots fast: check it against `git log` before you plan from it,
and update or delete lines that are no longer true. Last checked at commit `c238b8891`
("Personal prediction: the whole screen scrolls", 2026-11-22). These notes were written back from
the repo on 2026-11-27, because the notes of the earlier session were never committed.

## Where things stand

- `4.0.0+aisf3.2.1-rc` and `claude/festive-rubin-f2bil0` were at the same commit `c238b8891`.
- **Release candidate, not tested on a phone** (README status line). Nothing in it is confirmed to
  work at runtime until the owner says so.
- Done on the branch, in this order (see `git log --author=Claude --since=2026-11-13`):
  1. 2026-11-13/14: merge of autoISF 3.2.1 into AAPS 4.0.0, then autoISF features on top (1-minute
     readings, BG parabola, exercise button, algorithm status in AAPSClient, ready automations,
     simple mode off, objectives done), protection fix (bolus PIN on its own), client control fixes.
  2. 2026-11-19/20: overview for a phone (profile card, BG card, basal/bolus/loop cards, graph card
     with IOB/COB/basal inside the BG graph, mini map, statistics), bottom menu, "who gave it" in the
     treatment history, insulin trade names, all sensitivity plugins again, CI with 4 workers.
  3. 2026-11-21: bolus advice from the loop run in the Insulin dialog; personal prediction v1
     (linear model + residual network), then the safety fix `1dc8f54dc` (careful dose, fair
     comparison with AutoISF).
  4. 2026-11-21/22: personal prediction v2 (GRU expected curve, every minute, live check, alerts,
     meal in progress, report, files instead of settings, desktop build, scrolling screen).
- Designs and plans: `docs/superpowers/specs/2026-11-21-*.md`, `docs/superpowers/plans/2026-11-21-*.md`.
  The checkboxes in the plans were not ticked; the commits show which tasks landed.

## Open work

- **Port from iAPS and JugglucoNG** (2026-11-27, branch `claude/project-thread-4heq8j`): keto
  protection, B30, auto CR (`AutoIsfExtras`, called from `OpenAPSAutoISFPlugin.applyB30AndKetoProtect`),
  loop settings in scenes (`SceneAction.LoopSettings`, read live by the AutoISF plugin, end
  conditions in `SceneExpiryRunner`), `LowConfirmation` in the prediction plugin, the meal
  calculator (`ui/food/`, `core/objects/food/`, `MealCard` in the carbs dialog), FPU as eCarbs of
  the same batch entry, AI meal photo. Unit tests only, not tested on a phone. Not done yet: speech
  of BG, a floating BG window and Health Connect export (proposed to the owner as a next step).
  Barcode is typed, there is no camera scanner yet.

- **Overview in landscape and on a tablet** (2026-11-27): the same cards as in portrait
  (`OverviewMainCards`) and a Pump button in the status card. Merged without a local build (no
  Android SDK in that cloud chat); the owner builds it with AAPS CI. Not tested on a phone.

- **Phone test of the rc build.** Open loop first, compared with the owner's autoISF 3.2.x build on
  AAPS 3.4 (README "Updating from autoISF 3.2.x").
- **Personal prediction on the real phone.** v1 on the phone learned an ISF of about 30 % of the
  profile (v2 design, section 3). v2 was made to fix that, but there is no record yet of what v2
  learned on the phone. Ask the owner for a *Copy report* from the plugin screen before changing
  the model.
- **Keeping up with ga-zelle** `400-dev/v400-dev` (README "Keeping up with autoISF"): check for new
  commits from time to time.
- Nothing about unmerged pull requests is known from the repo. Check open PRs on GitHub.

## Rules from the owner that the repo shows

- The owner asked for two prediction curves, a GRU and Kotlin only (no Rust, no ML library); see
  the v2 design, sections 1, 4 and 5. Do not re-open these choices without a measured reason.
- The prediction plugin never changes insulin delivery, and a dose advice never asks for more than
  the profile would (v2 design, anti-goals).
- Ask before commit and push (also in `CLAUDE.md`).
