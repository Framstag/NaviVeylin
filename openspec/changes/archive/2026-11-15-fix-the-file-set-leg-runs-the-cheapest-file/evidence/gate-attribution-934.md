# Gate red attribution — item #934 (phase F)

The gate was run once, by the gate child, before this phase, and is never re-run here. Its verdict line, verbatim
from this directory's `evidence/gate.log:1804` (mirrored at `items/934/gate-934.log:1804`):

    LOOP-GATE: tests=1150 failures=1 errors=0 (tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach 17s, tools/loop/tests/loop-selftest.sh --from-git 8f84a8b8cef90ba18fa70caaab89df2f0f763b48 1298s, bash tools/loop/tests/loop-mutations.sh --check 11s, tools/loop/tests/loop-mutations-selftest.sh 91s, … ) — failing: tools/loop/tests/loop-selftest.sh --from-git 8f84a8b8cef90ba18fa70caaab89df2f0f763b48

One failing step, `failures=1`, `errors=0`. `gate.log` carries exactly one `FAIL` line in the whole run — line 642 —
and the tally of that step, `gate.log:1244`: `loop-selftest: 1 of 461 cases failed — 1297.36 s`. The change's own
steps are green in the same gate: `tools/loop/tests/loop-mutations-selftest.sh 91s` (the leg this change fixes, was
~26 min and burning its `timeout 900`), `bash tools/loop/tests/loop-mutations.sh --check 11s` (all 403 records) and
`tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach 17s` — none of them in the
`failing:` list. The affected cases and the change's red case are what this change answers for, and they stand.

## What this change touches

`git diff --name-only 8f84a8b8cef90ba18fa70caaab89df2f0f763b48 5cb0e4b6bce41b954fa1d498e1bac1af3b12ced6`
(base → gate head) names three files, and nothing else:

    tools/loop/tests/loop-cases.sh
    tools/loop/tests/loop-mutations-selftest.sh
    tools/loop/tests/loop-mutations.sh

`loop-cases.sh`'s three hunks are `@@ -208` (the new case's list entry), `@@ -959` (its dispatch entry) and
`@@ -15913` (its 27-line body) — one added case, `mutation#theFileSetLegScopesByTheCheapestFile`. Nothing else in
the file moves.

## Red 1 — `failures=1`: `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` (the `--from-git` step)

Failing output read from `evidence/gate.log:638-642` and `:1244`, verbatim:

    run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock|   the run recorded the item its signal interrupted: expected 'blocked', got ''
    --- run#reviewerEndedAtTheRunsDeadlineIsNotADecision[14] (exit 1)
      the run recorded the item its signal interrupted: expected 'blocked', got ''
    FAIL run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock
    loop-selftest: 1 of 461 cases failed — 1297.36 s

**Red before this work.**

- The case is **staged red on purpose** and says so in the tree: the comment above the case,
  `tools/loop/tests/loop-cases.sh:15664-15666` — "The measured red of issue #860, staged on purpose: the signal the
  case sends reaches no process, so the run goes on …". This change's hunks (above) do not touch that comment, the
  case body at `:15667`, or any helper it calls.
- **Measured red on 2026-11-13T22:52:39Z**, two days before this item's work (its red test is timestamped
  2026-11-15T21:01:35Z): the hup change's own red record, copied verbatim into this directory as
  `prior-red-aHup-2026-11-13.log` and `prior-red-aHup-2026-11-13.xml` — `<testsuite name="… --case
  run#aHupThe* (HEAD 0848c35d)" tests="1" failures="1">` with
  `2026-11-13T22:52:39Z FAIL run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock`. The same record was carried by
  items #883 and #916, whose gates waived this same case with it.
- **The verdict is a pure function of bytes this change never touches.** The case declares its files at
  `tools/loop/tests/loop-cases.sh:15668`: `tools/loop/tests/fixtures/scenarios/ tools/loop/run.sh
  tools/pi-child-events.sh tools/tooling.json`. None is in the diff above, i.e. all are byte-identical to base
  `8f84a8b8`; the case body and its assertion are untouched too. The assertion reads what `tools/loop/run.sh`
  records for a signal-interrupted item — code this diff cannot reach — so the case fails on the base's bytes as
  well. The intersection of the case's declared files with the change's diff is empty; the only overlapping file,
  `loop-cases.sh`, is edited at three hunks that add one new `mutation#` case and touch neither
  `case_run_aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` nor a shared helper — corroborated by
  `driver#everyCaseDeclaresTheFilesItCanReach` passing in this same gate.

**Stays red after this work** — this gate ran after the archive and printed the output above (`gate.log:642`), the
only `FAIL` in 1150 tests.

**Why this change cannot answer for it.** The change answers for the self-test's file-set leg: its red case
`mutation#theFileSetLegScopesByTheCheapestFile`, the `mutation#*`/`gate#theMapCoversEveryPipelineStep`/
`run#theStopWaitIsDecidedByTheStateTheRunWrites` cases whose records name `loop-cases.sh`, the self-test's own 36
cases and the two records `934.1`/`934.2` — all green in this gate. A HUP/clock case whose declared files the diff
does not intersect is off its paths, and it was red before the first line of this change was written.

## Waiver

    LOOP-GATE-WAIVED: the --from-git step's one failing case run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock is staged red on purpose (issue #860, tools/loop/tests/loop-cases.sh:15664), off this change's paths — its declared files (loop-cases.sh:15668) intersect the base→head diff in nothing, red before this work (prior-red-aHup-2026-11-13.log, 2026-11-13T22:52:39Z) and still red after it (evidence/gate.log:642)
    LOOP-GATE-WAIVED-SUITE: tools/loop/tests/loop-selftest.sh --from-git 8f84a8b8cef90ba18fa70caaab89df2f0f763b48

## Copies

Every result cited here is copied into this `evidence/` directory and into the run's evidence directory
`/home/tim/projects/NaviVeylin/.pi/issue-loop/items/934`:

- `gate.log` (the gate's own full log; the item dir carried it first as `gate-934.log`)
- `prior-red-aHup-2026-11-13.log`
- `prior-red-aHup-2026-11-13.xml`

No path outside this change's `evidence/` directory was written after the gate; no tree edit, no commit, no second
gate run, no `tools/loop/evidence-check.sh` run.
