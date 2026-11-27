# This fork: AAPS 4.0 with autoISF

Read this first in a new chat. The rest of `CLAUDE.md` is the upstream file of AAPS (written for
the Windows and macOS machines of the AAPS maintainer). This file says what is different here.

## What the repo is

- `VolodymyrFihurniak/AndroidAPS`: a fork of [nightscout/AndroidAPS](https://github.com/nightscout/AndroidAPS)
  with autoISF of [T-o-b-i-a-s](https://github.com/T-o-b-i-a-s/AndroidAPS) and
  [ga-zelle](https://github.com/ga-zelle/AndroidAPS) merged in.
- The owner writes in Ukrainian. Code, comments, commit messages and English strings stay in the
  simple English that `CLAUDE.md` asks for. Strings this build adds get a Ukrainian translation in
  `values-uk-rUA` (see the README); upstream strings still come from Crowdin.
- The user facing description of every change is in the top level `README.md`. Keep it up to date
  in the same commit as the change.

## Branches

| Branch | What it is |
|---|---|
| `4.0.0+aisf3.2.1-rc` | Main work branch. AAPS 4.0.0 + autoISF 3.2.1, release candidate. |
| `claude/festive-rubin-f2bil0` | Branch of an earlier Claude session; was at the same commit as the rc branch. |
| `3.4.2.6+aisf3.2.1`, `3.4.2.1+aisf3.2.0`, `3.4.0.0+aisf3.2.0` | Older autoISF builds on AAPS 3.4. Do not change them. |
| `one-shot/build-aaps-logfix` | One-off build branch. |

Check `git ls-remote --heads origin` before trusting this table.

## How the branch was made

- Merge commit `Merge autoISF 3.2.1 (3.4.2.6+aisf3.2.1) into AAPS 4.0.0` (2026-11-13). The history of
  both lines is kept, so later AAPS and autoISF releases can be merged again.
- AAPS 4.0 is Kotlin Multiplatform, Compose and Metro (not Dagger), so most autoISF code was ported
  by hand. See the README sections *Changes compared to autoISF 3.2.1* and *Keeping up with autoISF*
  (how to fetch and cherry-pick ga-zelle's `400-dev/v400-dev`).
- The database version is 37 (AAPS 4.0 = 36, plus the `autoIsfValues` table).

## Where the fork's code is

- Code this fork adds or changes in upstream files has a comment starting with **`autoISF build`**.
  `git grep -n "autoISF build"` finds it. Keep that marker on new changes: it is what makes the next
  merge of upstream possible.
- Biggest areas, by files changed since the merge: `ui/` (overview on a phone, graph card, basal and
  bolus cards, Insulin dialog), `plugins/aps/` (autoISF and the personal prediction plugin),
  `core/interfaces`, `plugins/sync` (client control), `plugins/automation`, `plugins/constraints`
  (objectives unlocked).
- Personal prediction (experimental plugin): see `.claude/procedures/personal-prediction.md`.
- Designs and plans of bigger features: `docs/superpowers/specs/` and `docs/superpowers/plans/`.

## Building and testing

- The owner builds the APK with GitHub Actions (`aaps-ci.yml`, `branch-ci.yml`), not on a local
  machine. There is no Windows or macOS checkout in a cloud chat: the Windows command rules at the
  top of `CLAUDE.md` do not apply there, use `./gradlew`.
- Run the CI test command from `CLAUDE.md` ("Verify Before You Push") before claiming a change is
  safe. For the prediction plugin alone the faster command is in its procedure.
- Nothing is tested on a phone until the owner says so. Do not write "works" in the README or a
  commit for something only the tests saw.

## Safety

This app doses insulin. A change that can alter delivery (loop, SMB, temp basal, limits,
protection) needs the owner's explicit OK and a test that would fail without it. The personal
prediction plugin must never change delivery.

Current state and open work: `.claude/fork/HANDOFF.md`.
