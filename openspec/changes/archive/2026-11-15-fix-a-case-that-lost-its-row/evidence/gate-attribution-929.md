# Gate red attribution — item #929 (phase F)

The gate was run once, by the gate child, before this phase, and is never re-run here. Its verdict line, verbatim
from this directory's `evidence/gate.log:1760` (mirrored at `items/929/evidence/gate.log`):

    LOOP-GATE: tests=1115 failures=1 errors=0 (tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach 17s, tools/loop/tests/loop-selftest.sh --from-git d794277ba7506e287ea94f22223e76926f20bf18 1278s, bash tools/loop/tests/loop-mutations.sh --check 10s, bash tools/gen-feature-list.sh --check-classification 10s, tools/check-doc-values.sh 0s, tools/tests/check-doc-values-selftest.sh 0s, tools/check-doc-routes.sh 1s, tools/tests/check-doc-routes-selftest.sh 8s, tools/mutate.sh --mutations tools/tests/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 2s, tools/check-openspec-hygiene.sh 1s, tools/tests/check-openspec-hygiene-selftest.sh 1s, tools/check-portability.sh 2s, tools/tests/check-portability-selftest.sh 42s, tools/tests/mutate-selftest.sh 1s, tools/mutate.sh --mutations tools/tests/portability-mutations.json --list 0s, tools/issues/tests/issue-tooling-selftest.sh 121s, tools/mutate.sh --mutations tools/issues/tests/status-mutations.json --list 0s, tools/mutate.sh --mutations tools/issues/tests/create-mutations.json --list 0s, tools/mutate.sh --mutations tools/issues/tests/close-mutations.json --list 0s, tools/mutate.sh --mutations tools/issues/tests/duplicates-mutations.json --list 0s, tools/mutate.sh --mutations tools/issues/tests/decision-question-mutations.json --list 1s) — failing: tools/loop/tests/loop-selftest.sh --from-git d794277ba7506e287ea94f22223e76926f20bf18

One failing step, `failures=1`, `errors=0`. `gate.log` carries exactly one `FAIL` line in the whole run — line 640 —
and the tally of that step, `gate.log:1240`: `loop-selftest: 1 of 462 cases failed — 1276.35 s`. The change's own
cases are green in the same gate: `PASS driver#aCaseWithNoSelectionRowIsRefused` (`gate.log:1131`), and
`tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach 17s` is not in the `failing:`
list. The affected cases and the change's red case are what this change answers for, and they stand.

## What this change touches

The reviewer-identity-checked pre-gate diff (`items/929/review-pre-gate.diff`, `head=f749cf75eabdec5e45e121416618d8374da0448d`,
`diff=f07478f2410b666e`) names two files, +112/−0, nothing else:

    tools/loop/tests/loop-cases.sh
    tools/loop/tests/loop-selftest.sh

`loop-cases.sh`'s three hunks are `@@ -427` (the new case's `LOOP_SHELL_CASES` row), `@@ -1175` (its dispatch arm)
and `@@ -16926` (its 41-line body) — one added case, `driver#aCaseWithNoSelectionRowIsRefused`.
`loop-selftest.sh`'s one hunk is `@@ -454` (the registration check after `shell_cases=$(loop_shell_cases)`).
No deletion anywhere; the gate child's completion added only the archived delta and `evidence/` under `openspec/`.

## Red 1 — `failures=1`: `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` (the `--from-git` step)

Failing output read from `evidence/gate.log:636-640` and `:1240`, verbatim:

    run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock|   the run recorded the item its signal interrupted: expected 'blocked', got ''
    --- run#aRoundTheRunEndedRecordsItsBound[14] (exit 1)
      the run recorded the item its signal interrupted: expected 'blocked', got ''
    FAIL run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock
    loop-selftest: 1 of 462 cases failed — 1276.35 s

**Red before this work.**

- The case is **staged red on purpose** and says so in the tree: the comment above the case,
  `tools/loop/tests/loop-cases.sh:15666-15668` — "The measured red of issue #860, staged on purpose: the signal the
  case sends reaches no process, so the run goes on …". This change's hunks (above) do not touch that comment, the
  case body at `:15669`, or any helper it calls.
- **Measured red on 2026-11-13T22:52:39Z**, two days before this item's work (its red test is timestamped
  2026-11-15T22:37:47Z): the hup change's own red record, copied verbatim into this directory as
  `prior-red-aHup-2026-11-13.log` and `prior-red-aHup-2026-11-13.xml` — `<testsuite name="… --case
  run#aHupThe* (HEAD 0848c35d)" tests="1" failures="1">` with
  `2026-11-13T22:52:39Z FAIL run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock`. The same record was carried by
  items #883, #916 and #934, whose gates waived this same case with it
  (`openspec/changes/archive/2026-11-15-fix-the-file-set-leg-runs-the-cheapest-file/evidence/gate-attribution-934.md`).
- **The verdict is a pure function of bytes this change never touches.** The case declares its files at
  `tools/loop/tests/loop-cases.sh:15670`: `tools/loop/tests/fixtures/scenarios/ tools/loop/run.sh
  tools/pi-child-events.sh tools/tooling.json`. None is in the diff above. The case body and its assertion are
  untouched too. The assertion reads what `tools/loop/run.sh` records for a signal-interrupted item — code this
  diff cannot reach — so the case fails on the base's bytes as well. The intersection of the case's declared files
  with the change's diff is empty; the only overlapping file, `loop-cases.sh`, is edited at three hunks that add one
  new `driver#` case and touch neither `case_run_aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` nor a shared
  helper — corroborated by `driver#everyCaseDeclaresTheFilesItCanReach` passing in this same gate.

**Stays red after this work** — this gate ran after the archive and printed the output above (`gate.log:640`), the
only `FAIL` in 1115 tests.

**Why this change cannot answer for it.** The change answers for the registration check: its red case
`driver#aCaseWithNoSelectionRowIsRefused` (green in this gate, `gate.log:1131`), the driver group's 32 cases, the
declaration check and `--list` over 462 rows — all green in this gate. A HUP/clock case whose declared files the
diff does not intersect is off its paths, and it was red before the first line of this change was written.

## Waiver

    LOOP-GATE-WAIVED: the --from-git step's one failing case run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock is staged red on purpose (issue #860, tools/loop/tests/loop-cases.sh:15666-15668), off this change's paths — its declared files (loop-cases.sh:15670) intersect the base→head diff in nothing, red before this work (prior-red-aHup-2026-11-13.log, 2026-11-13T22:52:39Z) and still red after it (evidence/gate.log:640)
    LOOP-GATE-WAIVED-SUITE: tools/loop/tests/loop-selftest.sh --from-git d794277ba7506e287ea94f22223e76926f20bf18

## Copies

Every result cited here is copied into this `evidence/` directory and into the run's evidence directory
`/home/tim/projects/NaviVeylin/.pi/issue-loop/items/929`:

- `gate.log` — this directory's, mirrored at `items/929/evidence/gate.log`
- `tools_loop_tests_loop-selftest.sh_--from-git_d794277ba7506e287ea94f22223e76926f2.xml` — this directory's, mirrored
  at `items/929/evidence/…` and `items/929/tools_loop_tests_loop-selftest.sh_--from-git_d794277ba7506e287ea94f22223e76926f2.xml`
- `red-on-base.xml` (the change's red test's carrier) — mirrored at `items/929/evidence/red-on-base.xml`
- `prior-red-aHup-2026-11-13.log` — flat at `items/929/prior-red-aHup-2026-11-13.log`
- `prior-red-aHup-2026-11-13.xml` — flat at `items/929/prior-red-aHup-2026-11-13.xml`
- this attribution file itself, flat at `items/929/gate-attribution-929.md`

No path outside this change's `evidence/` directory was written after the gate; no tree edit, no commit, no second
gate run, no `tools/loop/evidence-check.sh` run.
