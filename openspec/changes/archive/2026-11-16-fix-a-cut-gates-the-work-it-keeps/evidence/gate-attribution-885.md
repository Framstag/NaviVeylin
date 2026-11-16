# Gate red attribution — item #885 (phase F)

The gate was run once, by the gate child, before this phase, and is never re-run here. Its verdict line, verbatim
from this directory's `evidence/gate.log:1808` (mirrored at `items/885/evidence-gate.log:1808`, same cksum
`1476660539 151217`):

    LOOP-GATE: tests=1155 failures=1 errors=0 (tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach 17s, tools/loop/tests/loop-selftest.sh --from-git 2807ba611a1430188d8880552d30c68ff2458cdb 1301s, … ) — failing: tools/loop/tests/loop-selftest.sh --from-git 2807ba611a1430188d8880552d30c68ff2458cdb

One failing step, `failures=1`, `errors=0`. `gate.log` carries exactly one `FAIL` line in the whole run — line 642 —
and the failing step's own tally, `gate.log:1248`: `loop-selftest: 1 of 465 cases failed — 1299.27 s` (step line
`gate.log:1249`). This change's own cases are green in the same gate:

    gate.log:1204  PASS run#aCutWhosePatchDoesNotApplyIsLeftCut
    gate.log:1205  PASS run#aCutRunsItsGateOnTheKeptWork

and its other steps are in no `failing:` list: `--case driver#everyCaseDeclaresTheFilesItCanReach` (`gate.log:9-10`,
`:1148`), `bash tools/loop/tests/loop-mutations.sh --check 12s`, `loop-mutations-selftest.sh 92s` and the rest of the
tally. The affected cases and the change's red case are what this change answers for, and they stand.

## What this change touches

`git diff --name-only 2807ba611a1430188d8880552d30c68ff2458cdb 69e6e2fc1b761e662b9dec73ca98d7632d40032d`
(base → `LOOP-GATE-TREE` head, `gate.log:5`) names five files, and nothing else:

    guidelines/Backlog.recipes.md
    tools/loop/run.sh
    tools/loop/state.sh
    tools/loop/tests/loop-cases.sh
    tools/loop/tests/loop-mutations.sh

The hunks, `git diff --unified=0` over the same range:

- `tools/loop/run.sh` — `@@ -668,0 +669,5` (the `cut_gate_result`/`cut_gate_log` variables), `@@ -743,0 +749,58`
  (`gate_the_kept_work`), `@@ -4077,0 +4141,10` (the `cut)` arm's call, note and `--cut-gate*` flags), `@@ -4082,0
  +4156` (one line in the same arm).
- `tools/loop/state.sh` — five hunks inside `cmd_done` (`+689`, `+716-720`, `+962-963`, `+996-997`, `+1009`): the
  `--cut-gate*` flags and the `cutGate` field only.
- `tools/loop/tests/loop-cases.sh` — `@@ -488` (the new case's list entry), `@@ -1234` (its dispatch entry),
  `@@ -20302` (one assertion in `run#aCutWhosePatchDoesNotApplyIsLeftCut`) and `@@ -20307` (the new case's 100-line
  body at `:20312`). Nothing else in the file moves.
- `tools/loop/tests/loop-mutations.sh` — record `885.1`.

## Red 1 — `failures=1`: `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` (the `--from-git` step)

Failing output read from `evidence/gate.log:638-642` and `:1248`, verbatim:

    run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock|   the run recorded the item its signal interrupted: expected 'blocked', got ''
    --- run#reviewerEndedAtTheRunsDeadlineIsNotADecision[14] (exit 1)
      the run recorded the item its signal interrupted: expected 'blocked', got ''
    FAIL run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock
    loop-selftest: 1 of 465 cases failed — 1299.27 s

**Red before this work.**

- The case is **staged red on purpose** and says so in the tree: the comment above the case,
  `tools/loop/tests/loop-cases.sh:15717-15719` — "The measured red of issue #860, staged on purpose: the signal the
  case sends reaches no process, so the run goes on …". This change touches neither that comment, the case body at
  `:15720-15774`, nor its `# files:` declaration at `:15721`.
- **Measured red on 2026-11-13T22:52:39Z**, two and a half days before this item's own red test (its stamp is
  `2026-11-16T02:38:18Z`, `evidence/red-on-HEAD.xml`): the same case failing on the base's bytes, `stop '', item ''`
  — the very reading this gate read — copied verbatim into this directory as `prior-red-aHup-2026-11-13.log` and
  `prior-red-aHup-2026-11-13.xml` (`<testsuite name="bash tools/loop/tests/loop-selftest.sh --case run#aHupThe*
  (HEAD 0848c35d)" tests="1" failures="1">` with `2026-11-13T22:52:39Z FAIL …`). Items #930 and #934 waived this
  same case with this same record (`…/archive/2026-11-16-fix-a-local-is-not-a-shared-name/evidence/gate-attribution-930.md`,
  `…/2026-11-15-fix-the-file-set-leg-runs-the-cheapest-file/evidence/gate-attribution-934.md`), and #934's own gate
  printed the identical message on 2026-11-15 (`…/2026-11-15-fix-the-file-set-leg-runs-the-cheapest-file/evidence/gate.log:638`).
- **The verdict is a pure function of bytes this change never touches.** The case declares its files at
  `loop-cases.sh:15721`: `tools/loop/tests/fixtures/scenarios/ tools/loop/run.sh tools/pi-child-events.sh
  tools/tooling.json`. The one of them this diff edits is `tools/loop/run.sh`, and its four hunks (above) sit at
  `669-673`, `749-806`, `4141-4150`, `4156` — while the assertion reads what the run's own signal path records, the
  `--result blocked` write at `run.sh:827-829`, which no hunk reaches; the `cut)` arm the change gates is a different
  arm of the item loop, and the case never ends its item `cut`. In `state.sh` the change adds the `cutGate` field to
  `cmd_done` and moves nothing else. The case body and its assertion are untouched, corroborated by
  `driver#everyCaseDeclaresTheFilesItCanReach` passing in this same gate (`gate.log:1148`). The base's own bytes
  produced this reading on 2026-11-13 (above), so the failure is the base's, not the delta's.

**Stays red after this work** — this gate ran after the archive and printed the output above (`gate.log:642`), the
only `FAIL` in 1155 tests.

**Why this change cannot answer for it.** The change answers for the cut gate: its red case
`run#aCutRunsItsGateOnTheKeptWork` (red on the base, `evidence/red-on-HEAD.xml`), `run#aCutWhosePatchDoesNotApplyIsLeftCut`,
the `885.1` mutation record and the affected selection of 243 cases (`evidence/affected-cases-green.log`) — all green
in this gate. A HUP/clock case staged red on purpose by issue #860, whose body and read path the diff does not reach,
is off its paths, and it was red before the first line of this change was written.

## Waiver

    LOOP-GATE-WAIVED: the --from-git step's one failing case run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock is staged red on purpose (issue #860, tools/loop/tests/loop-cases.sh:15717-15719), off this change's paths — the base→head hunks reach neither the case body (:15720-15774) nor the signal-interrupted record it reads (tools/loop/run.sh:827-829), red before this work (prior-red-aHup-2026-11-13.log, 2026-11-13T22:52:39Z) and still red after it (evidence/gate.log:642)
    LOOP-GATE-WAIVED-SUITE: tools/loop/tests/loop-selftest.sh --from-git 2807ba611a1430188d8880552d30c68ff2458cdb

## What this phase verified (phase F, item 3)

- The change is archived as `openspec/changes/archive/2026-11-16-fix-a-cut-gates-the-work-it-keeps/` (its
  `proposal.md`, `design.md`, `tasks.md`, `specs/`, `evidence/` all present), and the gate's log was written from
  that tree (`evidence/gate.log`, 1808 lines, `LOOP-GATE-TREE head=69e6e2fc…` inside it).
- `tasks.md` is the file the work package names, and the red test the first process named —
  `run#aCutRunsItsGateOnTheKeptWork fails on HEAD — XML tests=1 failures=1 (2026-11-16T02:38:18Z)` — is the case the
  gate's own tallies carry: `evidence/red-on-HEAD.xml` holds one `<testsuite tests="1" failures="1">` with that
  stamp, and the gate passed the case on this tree (`gate.log:1205`).

## Copies

Every result cited here is copied into this `evidence/` directory and into the run's evidence directory
`/home/tim/projects/NaviVeylin/.pi/issue-loop/items/885`:

- `gate.log` (the gate's own full log; the item dir carries the same bytes as `evidence-gate.log`, cksum
  `1476660539 151217`)
- `red-on-HEAD.xml` (the item dir carries it as `evidence-red-on-HEAD.xml`)
- `prior-red-aHup-2026-11-13.log`
- `prior-red-aHup-2026-11-13.xml`
- `gate-attribution-885.md` (this file)

No path outside this change's `evidence/` directory was written after the gate; no tree edit, no commit, no second
gate run, no `tools/loop/evidence-check.sh` run.
