# Gate red attribution — item #886 (phase F)

The gate was run once, by the gate child, before this phase, and is never re-run here. Its verdict line, verbatim
from this directory's `evidence/gate.log:1803` (mirrored at `items/886/gate-886.log:1803`):

    LOOP-GATE: tests=1156 failures=1 errors=0 (tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach 16s, tools/loop/tests/loop-selftest.sh --from-git 8493f017c5d6aeb2a3e731188e8a80fd6580958e 1297s, bash tools/loop/tests/loop-mutations.sh --check 12s, tools/loop/tests/loop-mutations-selftest.sh 91s, bash tools/gen-feature-list.sh --check-classification 12s, tools/check-doc-values.sh 0s, tools/tests/check-doc-values-selftest.sh 0s, tools/check-doc-routes.sh 1s, tools/tests/check-doc-routes-selftest.sh 8s, tools/mutate.sh --mutations tools/tests/check-doc-routes-mutations.json --list 1s, tools/check-todo-references.sh 2s, tools/check-openspec-hygiene.sh 1s, tools/tests/check-openspec-hygiene-selftest.sh 1s, tools/check-portability.sh 2s, tools/tests/check-portability-selftest.sh 32s, tools/tests/mutate-selftest.sh 1s, tools/mutate.sh --mutations tools/tests/portability-mutations.json --list 0s, tools/issues/tests/issue-tooling-selftest.sh 112s, tools/mutate.sh --mutations tools/issues/tests/status-mutations.json --list 0s, tools/mutate.sh --mutations tools/issues/tests/create-mutations.json --list 0s, tools/mutate.sh --mutations tools/issues/tests/close-mutations.json --list 0s, tools/mutate.sh --mutations tools/issues/tests/duplicates-mutations.json --list 0s, tools/mutate.sh --mutations tools/issues/tests/decision-question-mutations.json --list 0s) — failing: tools/loop/tests/loop-selftest.sh --from-git 8493f017c5d6aeb2a3e731188e8a80fd6580958e

One failing step, `failures=1`, `errors=0`. That step's own tally, `gate.log:1243`:
`loop-selftest: 1 of 465 cases failed — 1295.18 s`, carried by this directory's
`evidence/gate-from-git-8493f017.xml` (`tests="465" failures="1" errors="0"`). The gate carries exactly one `FAIL`
line — `gate.log:637`:

    run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock|   the run recorded the item its signal interrupted: expected 'blocked', got ''
    PASS run#aHupEndedRunRecordsItsStopAndUnpicksItsItem
    --- run#reviewerEndedAtTheRunsDeadlineIsNotADecision[14] (exit 1)
      the run recorded the item its signal interrupted: expected 'blocked', got ''
    FAIL run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock
    loop-selftest: 1 of 465 cases failed — 1295.18 s

The change's own cases are green in the same gate — `gate.log:1168`:
`PASS repeat#aVerificationReadIsBoundedNotHandedOverWhole` (its red on HEAD is this change's red test,
`evidence/red-on-head-repeat-886.log`: `the read after the edit is not the whole file again: 'LAST-LINE' should not
appear in: FIRST-LINE`, tally `loop-selftest: 1 of 1 cases failed`) — and `gate.log:4` / `:1146`:
`PASS driver#everyCaseDeclaresTheFilesItCanReach`. The change's other steps are not in the `failing:` list.
The affected cases and the change's red case are what this change answers for, and they stand.

## What this change touches

The pre-gate review diff (`items/886/review-pre-gate.diff`) names four files, and nothing else:

    guidelines/Backlog.recipes.md
    tools/loop/tests/loop-cases.sh
    tools/loop/tests/loop-mutations.sh
    tools/pi-trim-result.mjs

`loop-cases.sh`'s five hunks sit at base lines `461`, `1213`, `19002`, `19047`, `19182` — the renamed repeat case's
list and dispatch entries, its body, the two prose realignments and the mutation record. Nothing else in the file
moves.

## Red 1 — `failures=1`: `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` (the `--from-git` step)

Failing output read from `evidence/gate.log:633-637` and `:1243`, quoted above.

**Red before this work.**

- The case is **stated red on purpose** and says so in the tree: the comment above the case,
  `tools/loop/tests/loop-cases.sh:15717-15719` — "The measured red of issue #860, staged on purpose: the signal the
  case sends reaches no process, so the run goes on …". This change's hunks (above) touch neither that comment, the
  case body at `:15720`, its `# files:` declaration at `:15721`, its list entry at `:251` nor its dispatch at `:1007`.
- **Another change owns it**: `openspec/changes/archive/2026-11-14-fix-a-hup-case-ends-on-its-own-clock/` carries this
  case's red record (`evidence/gate-console-retained.log`); the case it staged red is still red in this gate, so the
  case is that change's delta and not this one's.
- **Measured red on 2026-11-13T22:52:39Z**, before this item's work (its red test is timestamped
  2026-11-16T03:55:58Z): copied verbatim into this directory as `prior-red-aHup-2026-11-13.log` and
  `prior-red-aHup-2026-11-13.xml` — `<testsuite name="… --case run#aHupThe* (HEAD 0848c35d)" tests="1" failures="1">`.
  The same record was carried and waived by items #878, #883, #916, #930 and #934
  (`…/archive/2026-11-16-fix-a-local-is-not-a-shared-name/evidence/gate-attribution-930.md`).
- **The run's own pre-flight names it base-red**: `.pi/issue-loop/run.json`'s `run.baseRed` carries
  `"tools/loop/tests/loop-selftest.sh --from-git 8f84a8b8cef90ba18fa70caaab89df2f0f763b48":
  ["run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock"]`, copied here as `base-red-record.json` — recorded at run
  start, before any delta of this run was known.
- **The verdict is a pure function of bytes this change never touches.** The case declares its files at
  `tools/loop/tests/loop-cases.sh:15721`: `tools/loop/tests/fixtures/scenarios/ tools/loop/run.sh
  tools/pi-child-events.sh tools/tooling.json`. None is in the diff above; the case body and its assertion are
  untouched too. The assertion reads what `tools/loop/run.sh` records for a signal-interrupted item — code this diff
  cannot reach. The intersection of the case's declared files with the base→head diff is empty; the only overlapping
  file, `loop-cases.sh`, is edited at five hunks nowhere near the case, corroborated by
  `driver#everyCaseDeclaresTheFilesItCanReach` passing in this same gate (`gate.log:4`, `:1146`).

**Stays red after this work** — this gate ran after the archive and printed the output above (`gate.log:637`), the
only `FAIL` in 1156 tests.

**Why this change cannot answer for it.** The change answers for the bounded verification read: `VERIFICATION_READ_BOUND`,
`verificationResult`, the `repeatResult` sha-difference branch, the repeat cases and mutation record 886.1 —
`repeat#*` green at `gate.log:1168`, mutations `--check` green in the same gate. A HUP/clock case whose declared
files the diff does not intersect, staged red on purpose and owned by another change's delta, is off its paths, and
it was red before the first line of this change was written.

## Waiver

    LOOP-GATE-WAIVED: the --from-git step's one failing case run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock is staged red on purpose (issue #860, tools/loop/tests/loop-cases.sh:15717-15719), owned by the change openspec/changes/archive/2026-11-14-fix-a-hup-case-ends-on-its-own-clock, off this change's paths — its declared files (loop-cases.sh:15721) intersect the base→head diff in nothing, red before this work (prior-red-aHup-2026-11-13.log, 2026-11-13T22:52:39Z; run.baseRed in base-red-record.json) and still red after it (evidence/gate.log:637)
    LOOP-GATE-WAIVED-SUITE: tools/loop/tests/loop-selftest.sh --from-git 8493f017c5d6aeb2a3e731188e8a80fd6580958e

## What this phase verified (phase F, item 3)

- The change is archived as `openspec/changes/archive/2026-11-16-fix-a-verification-read-is-bounded/` (its
  `proposal.md`, `design.md`, `tasks.md`, `specs/`, `evidence/` all present) — confirmed before the gate printed:
  the gate child's archive line and this directory's existence.
- `tasks.md` is the file the work package names, all tasks checked, and its red case is the one the gate's tallies
  carry green: `repeat#aVerificationReadIsBoundedNotHandedOverWhole` (`gate.log:1168`); the red-on-HEAD record it
  cites is `evidence/red-on-head-repeat-886.log`, tally `loop-selftest: 1 of 1 cases failed`, XML not retained
  (recorded in `tasks.md` §2.2).

## Copies

Every result cited here is copied into this `evidence/` directory and into the run's evidence directory
`/home/tim/projects/NaviVeylin/.pi/issue-loop/items/886`:

- `gate.log` (the gate's own full log; the item dir carries it as `gate-886.log`)
- `gate-from-git-8493f017.xml` (the failing step's tally; the item dir carries it as `gate-from-git-886.xml`)
- `red-on-head-repeat-886.log`, `green-repeat-886.log`, `surface-886.txt`, `mutation-886.1.log`,
  `mutations-check-886.log` (the change's own results, both halves)
- `prior-red-aHup-2026-11-13.log`
- `prior-red-aHup-2026-11-13.xml`
- `base-red-record.json`

No path outside this change's `evidence/` directory (and the item directory) was written after the gate; no tree
edit, no commit, no second gate run, no `tools/loop/evidence-check.sh` run.
