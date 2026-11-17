# Tasks

The change's line numbers are the base's (`4c4e6819`) where they are a claim about the defect, and the tree's as
this change leaves it where they are a claim about the fix; the red run's tally is `evidence/red-on-HEAD.xml`.

The gate is not this child's to run (the run reviews the diff first and hands this item's one gate to the child
after it), so the last two tasks are that child's.

## 1. The red case

- [x] 1.1 `gate#aTestFailureIsAFailureNotAnError` in `tools/loop/tests/loop-cases.sh`: a gate whose one step prints
      `> Task :app:testMobileDebugUnitTest FAILED`, two `com.example.SpecTest > … FAILED` lines with the
      `AssertionError` under each, and exits 1 — no summary anywhere. Registered in `LOOP_SHELL_CASES` and in the
      case dispatch. Verify: the declaration case first, as the cheapest check such a delta owes —
      `bash tools/loop/tests/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`
      (`evidence/declaration-case.log`).
- [x] 1.2 Red against the engine at the base: `LOOP_ENGINE` handed a copy of `tools/loop` whose `gate.sh` is
      `git show 4c4e6819:tools/loop/gate.sh`, one
      `bash tools/loop/tests/loop-selftest.sh --case 'gate#aTestFailureIsAFailureNotAnError'` run at
      2026-11-14T19:12:18Z → `FAIL … 'LOOP-GATE: tests=0 failures=2 errors=0' not found` (expected, not retained) — the
      gate's line reading
      `tests=0 failures=0 errors=1`; `tests=1 failures=1 errors=0` (`evidence/red-on-HEAD.log`,
      `evidence/red-on-HEAD.xml`).

## 2. The engine — `tools/loop/gate.sh`

- [x] 2.1 `finish_step` (`gate.sh:599`–`611`): where a non-zero step printed no summary, its own named failing
      cases are its failures — a `<class> > <case> FAILED` line is one (the task's header begins with `>`, so it is
      not read as a case) — guarded by `rc=0` and by `st_overran`. `tests=` stays the count the suite reported. The
      docstring above the function states the reading. Verify: the red case passes — `evidence/red-case-green.log`
      carries `PASS gate#aTestFailureIsAFailureNotAnError`, timestamped 2026-11-14T19:12:10Z in the item's
      `events.jsonl` record `call_jfub8at4` — and that run's captured output held its asserted
      `LOOP-GATE: tests=0 failures=2 errors=0`, not retained in any file (the case's `assert_contains` on `$out`,
      `tools/loop/tests/loop-cases.sh:5945`); the PASS is what remains of it.
- [x] 2.2 The group the fix reaches: `bash tools/loop/tests/loop-selftest.sh --group gate` → `all 38 cases passed`
      (`evidence/gate-group.log`), the extended rule's neighbours
      `gate#aFailingSuiteWithNoSummaryInventsNoTest` (the configuration step stays an error),
      `gate#aFailingSuiteIsReadFromTheSameStream` and `gate#aStepIsBoundedAndTheOverrunNamesIt` among them.
- [x] 2.3 The record that keeps the case: `916.1` in `tools/loop/tests/loop-mutations.sh`, removing the new reading.
      Verify: `bash tools/loop/tests/loop-mutations.sh --only 916.1` → `RED 916.1 … restore … green again`,
      `not-noticed: 0` (2026-11-14T19:13:36Z; `evidence/mutation-916.1.log`).

## 3. The documents

- [x] 3.1 `guidelines/Backlog.recipes.md` section 7c ("The gate and its suite"), after #268's paragraph: the shape that is a failure, the two states
      that keep the error, the case and record `916.1`. Verify: `tools/check-doc-values.sh` and
      `tools/check-doc-routes.sh` — the structural checks of the file this delta edits, run in place of a build it
      owes none of — both exit 0 (`evidence/doc-checks.log`).
- [x] 3.2 The capability delta in this change: the requirement "A suite that reported no summary invents no
      number" MODIFIED, one scenario added, and the two it carried restated — the first one's WHEN narrowed to a
      step that named no failing case, which is the state #268's rule now covers. Verify:
      `tools/check-openspec-hygiene.sh` (`evidence/doc-checks.log`).

## 4. What the gate child writes

- [x] 4.1 Write this change's `evidence/` from the item's evidence directory
      (`/home/tim/projects/NaviVeylin/.pi/issue-loop/items/916`) — `red-on-HEAD.log`, `red-on-HEAD.xml`,
      `red-case-green.log`, `gate-group.log`, `declaration-case.log`, `mutation-916.1.log`, `doc-checks.log`, plus
      the gate tally transcribed from the red run's lines (`red-on-HEAD-gate-tally.xml`) — and run the mechanical
      pointer check over it: `bash tools/loop/evidence-check.sh <change dir> --root .` → `evidence-check: PASS`
      (`evidence/evidence-check.log`).
- [x] 4.2 Archive the change under `openspec/changes/archive/2026-11-14-fix-a-failing-test-is-a-failure/` and run
      `./tools/loop/gate.sh --base 4c4e681967c30eedc0cdd874a900562ab7f8b32b` over the completed tree, recording its
      line.
