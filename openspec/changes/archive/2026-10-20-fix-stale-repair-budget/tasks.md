# Tasks — fix-stale-repair-budget

## 1. The red case

- [x] 1.1 Add `resolver#ambiguityDoesNotConsumeTheBound` to `tools/resolve-stale-cases.sh` (registry, dispatch
  and body): an ambiguous item (`a/Dup.kt`, `b/Dup.kt`) first in tracker order, a repairable one
  (`app/src/Foo.kt`) second, run with `--apply --max-items 1`. It asserts `repaired=1`, `due=1`, `waiting=0`,
  `ambiguous=1` and that the written body is the writable item's. Verification: on the base commit `9e7e969` it
  fails with `the one slot the bound allowed went to the item a run can write: expected '1', got '0'`, exit 1
  (`evidence/red.txt`, 2026-10-20T09:17:07Z). Parent: `stale-citation-resolution`, "The resolver runs on its
  own, bounded and idempotent".

## 2. The fix

- [x] 2.1 In `tools/issues/resolve-stale.sh`, delete the unconditional `due=$((due + 1))` after the
  parked/skip filters and charge the bound at one guard above the two write branches:
  `if [ -n "$repairs" ] || [ -n "$unresolved" ]; then due=$((due + 1)); else skipped=$((skipped + 1)); fi`.
  Update the two comments that call a visit "due" (the matrix comment and the default beside `max_items=20`).
  Verification: `bash -n tools/issues/resolve-stale.sh`; the red case passes.
- [x] 2.2 Add mutation `5.11` to `tools/resolve-stale-mutations.json` — the guard's line replaced by the
  unconditional `due=$((due + 1))`, naming `resolver#ambiguityDoesNotConsumeTheBound`. Verification:
  `bash tools/mutate.sh --mutations tools/resolve-stale-mutations.json --only 5.11` reports
  `RED(exit 1)` then `GREEN(exit 0)` after an exact restore (`evidence/revert-checks.md`).

## 3. Verification

- [x] 3.1 The full resolver self-test, offline: 22 of 22 (`evidence/green.txt`, 2026-10-20).
- [x] 3.2 No compiled file is touched, so this task replaces the Gradle build task with the structural check
  and says so instead of running a build "for completeness" (`guidelines/Build.md` §2, the scope block): the
  gate is `bash tools/resolve-stale-selftest.sh`, the command the CI step "Check stale citation resolver" runs.
  No tracker write, no dependency, no native code.
- [x] 3.3 Exclusions, named: mutation `5.7` of the same set reports `MUTATION-DID-NOT-APPLY` because its search
  (`--status on-hold`) matches no line of the base commit either — it predates this change, records a
  falsification of behaviour that still exists (the status is now read from the profile), and is left for its
  own item rather than repaired inside a fix for another defect.
- [x] 3.4 The neighbouring suite, `tools/loop-selftest.sh`: 113 of 114, this tree's documented worktree-path
  artefact (`worktree#keepsByDefaultAndRemovesOnRequest`, whose fixture repository carries `worktrees/loop` and
  whose case greps for that string) — it is unrelated to this change, which touches no loop file
  (`evidence/loop-selftest.txt`). The suites that could observe the counters through the resolver are covered
  by 3.1: `tools/loop/prescreen.sh` and `tools/sensors/metrics/stale-citations.sh` are exercised inside the
  resolver suite (`prescreen#refusalIsVisibleInTheTracker`, `pipeline#residueIsADeclaredRow`), and
  `tools/declared-cases.sh fix-stale-repair-budget` selects nothing, so the module suite has nothing to run.
