# Gate red attribution — item #930 (phase F)

The gate was run once, by the gate child, before this phase, and is never re-run here. Its verdict line, verbatim
from this directory's `evidence/gate.log:1761` (mirrored at `items/930/gate.log:1761` and
`items/930/gate-tally/gate.log:1761`):

    LOOP-GATE: tests=1116 failures=1 errors=0 (loop-selftest --case, loop-selftest --from-git, loop-mutations --check, feature-list/doc-values/doc-routes/todo-references/openspec-hygiene/portability checks + selftests, issue-tooling-selftest + 5 mutation lists — failing: tools/loop/tests/loop-selftest.sh --from-git d794277ba7506e287ea94f22223e76926f20bf18, case run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock)

One failing step, `failures=1`, `errors=0`. `gate.log` carries exactly one `FAIL` line in the whole run — line 637 —
and the failing step's own tally, `gate.log:1241`: `loop-selftest: 1 of 463 cases failed — 1283.59 s`, carried by
this directory's `evidence/gate-from-git.xml`
(`tests="463" failures="1" errors="0"`). The change's own cases are green in the same gate —
`gate.log:1133-1134`:

    PASS driver#aLocalIsNotAShareOfAnotherFilesName
    PASS driver#aQuotedProgramIsNotAShellRead

and the change's other steps are not in the `failing:` list: `tools/loop/tests/loop-selftest.sh --case
driver#everyCaseDeclaresTheFilesItCanReach 16s`, `bash tools/loop/tests/loop-mutations.sh --check 10s`.
The affected cases and the change's red case are what this change answers for, and they stand.

## What this change touches

`git diff --name-only d794277ba7506e287ea94f22223e76926f20bf18 5f92d336` (base → gate head) names three files, and
nothing else:

    tools/loop/report.sh
    tools/loop/shared.tsv
    tools/loop/tests/loop-cases.sh

`loop-cases.sh`'s seven hunks sit at base lines `432`, `1180`, `16938`, `16963`, `17007`, `17025`, `17366` — the two
new cases' list and dispatch entries and the `layout_shared_awk` derivation. Nothing else in the file moves.

## Red 1 — `failures=1`: `run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock` (the `--from-git` step)

Failing output read from `evidence/gate.log:633-637` and `:1241`, verbatim:

    run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock|   the run recorded the item its signal interrupted: expected 'blocked', got ''
    --- run#reviewerEndedAtTheRunsDeadlineIsNotADecision[14] (exit 1)
      the run recorded the item its signal interrupted: expected 'blocked', got ''
    FAIL run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock
    loop-selftest: 1 of 463 cases failed — 1283.59 s

**Red before this work.**

- The case is **staged red on purpose** and says so in the tree: the comment above the case,
  `tools/loop/tests/loop-cases.sh:15668-15670` — "The measured red of issue #860, staged on purpose: the signal the
  case sends reaches no process, so the run goes on …". This change's hunks (above) touch neither that comment, the
  case body at `:15671`, nor its `# files:` declaration at `:15672`.
- **Another change owns it, openly**: the change root carries `openspec/changes/fix-a-hup-case-ends-on-its-own-clock/`
  (unarchived — `proposal.md:31` names this case as *the red case, on the base `0848c35d`*, and its
  `evidence/red-on-HEAD.xml` is its red record). A red of an open delta of another change is that change's, not this
  one's.
- **Measured red on 2026-11-13T22:52:39Z**, three days before this item's work (its red test is timestamped
  2026-11-16T00:01:25Z): copied verbatim into this directory as `prior-red-aHup-2026-11-13.log` and
  `prior-red-aHup-2026-11-13.xml` — `<testsuite name="… --case run#aHupThe* (HEAD 0848c35d)" tests="1"
  failures="1">`. The same record was carried by items #883, #916 and #934, whose gates waived this same case with it
  (`…/archive/2026-11-15-fix-the-file-set-leg-runs-the-cheapest-file/evidence/gate-attribution-934.md`).
- **The run's own pre-flight names it base-red**: `.pi/issue-loop/run.json`'s `run.baseRed` carries
  `"tools/loop/tests/loop-selftest.sh --from-git …": ["run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock"]`,
  copied here as `base-red-record.json` (the key holds the run-start base `8f84a8b8`, recorded before any delta of
  this run was known — the record names the case, and the case is the unit the subtraction is about).
- **The verdict is a pure function of bytes this change never touches.** The case declares its files at
  `tools/loop/tests/loop-cases.sh:15672`: `tools/loop/tests/fixtures/scenarios/ tools/loop/run.sh
  tools/pi-child-events.sh tools/tooling.json`. None is in the diff above, i.e. all are byte-identical to base
  `d794277b`; the case body and its assertion are untouched too. The assertion reads what `tools/loop/run.sh`
  records for a signal-interrupted item — code this diff cannot reach. The intersection of the case's declared files
  with the base→head diff is empty; the only overlapping file, `loop-cases.sh`, is edited at seven hunks that add two
  `driver#` cases and the derivation, and touch neither `case_run_aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock`
  nor a helper it calls — corroborated by `driver#everyCaseDeclaresTheFilesItCanReach` passing in this same gate.

**Stays red after this work** — this gate ran after the archive and printed the output above (`gate.log:637`), the
only `FAIL` in 1116 tests.

**Why this change cannot answer for it.** The change answers for the shared-name derivation: its two red cases
`driver#aLocalIsNotAShareOfAnotherFilesName` and `driver#aQuotedProgramIsNotAShellRead` (green at
`gate.log:1133-1134`), `driver#theSharedNamesAreDeclared`, the `shared.tsv` rows and the `revert-checks` records —
all green in this gate. A HUP/clock case whose declared files the diff does not intersect, staged red on purpose and
owned by an open delta of another change, is off its paths, and it was red before the first line of this change was
written.

## Waiver

    LOOP-GATE-WAIVED: the --from-git step's one failing case run#aHupTheRunNeverTookIsAnsweredAtTheCasesOwnClock is staged red on purpose (issue #860, tools/loop/tests/loop-cases.sh:15668-15670), owned by the open change openspec/changes/fix-a-hup-case-ends-on-its-own-clock, off this change's paths — its declared files (loop-cases.sh:15672) intersect the base→head diff in nothing, red before this work (prior-red-aHup-2026-11-13.log, 2026-11-13T22:52:39Z; run.baseRed in base-red-record.json) and still red after it (evidence/gate.log:637)
    LOOP-GATE-WAIVED-SUITE: tools/loop/tests/loop-selftest.sh --from-git d794277ba7506e287ea94f22223e76926f20bf18

## What this phase verified (phase F, item 3)

- The change is archived as `openspec/changes/archive/2026-11-16-fix-a-local-is-not-a-shared-name/` (its
  `proposal.md`, `design.md`, `tasks.md`, `specs/`, `evidence/` all present), and the gate's log was written from
  that tree (`evidence/gate.log`, 1761 lines, `LOOP-GATE-TREE` line inside it).
- `tasks.md` is the file the work package names, and its two red cases are the ones the gate's tallies carry:
  `evidence/red-on-HEAD.xml` holds two `<testsuite>` elements, `tests="1" failures="1"` each — `tests=2
  failures=2`, 2026-11-16T00:01:25Z — and the gate passed both cases (`gate.log:1133-1134`).

## Copies

Every result cited here is copied into this `evidence/` directory and into the run's evidence directory
`/home/tim/projects/NaviVeylin/.pi/issue-loop/items/930`:

- `gate.log` (the gate's own full log; the item dir carried it first as `gate.log` and `gate-tally/gate.log`)
- `gate-from-git.xml` (the failing step's tally; the item dir carries it as
  `gate-tally/tools_loop_tests_loop-selftest.sh_--from-git_d794277ba7506e287ea94f22223e76926f2.xml`)
- `prior-red-aHup-2026-11-13.log`
- `prior-red-aHup-2026-11-13.xml`
- `base-red-record.json`

No path outside this change's `evidence/` directory was written after the gate; no tree edit, no commit, no second
gate run, no `tools/loop/evidence-check.sh` run.
