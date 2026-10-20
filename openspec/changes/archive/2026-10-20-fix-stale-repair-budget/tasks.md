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

## 4. Round-2 corrections (the review refused the change on one clause)

- [x] 4.1 The requirement's bound clause states a bound the default mode can satisfy: "The bound SHALL be spent
  only on the items the run acts on — a repair or a park, and in a dry run the items it would act on —". In
  both copies, the delta and the merged `openspec/specs/stale-citation-resolution/spec.md`. Verification:
  `grep -n 'acts on' openspec/specs/stale-citation-resolution/spec.md
  openspec/changes/archive/2026-10-20-fix-stale-repair-budget/specs/stale-citation-resolution/spec.md`
  finds the clause in each, and the word `writes` no longer carries the bound anywhere in either file.
- [x] 4.2 The count in the evidence prose is the one the quoted run line supports: `due=25` is `repaired=4` +
  `parked=5` + 16, so **16 of the 25** visited items produced no write, and `ambiguous=22` counts tokens —
  corrected in `proposal.md`, `design.md` and the production comment at `tools/issues/resolve-stale.sh`.
  Verification: `grep -rn '22 of 25\|22 of the 25'
  openspec/changes/archive/2026-10-20-fix-stale-repair-budget/proposal.md
  openspec/changes/archive/2026-10-20-fix-stale-repair-budget/design.md tools/issues/resolve-stale.sh` — the
  three files the claim names — finds nothing. The directory-wide grep this task first stated was false: it
  matched this task line itself and the old `evidence/diff.txt`, and is narrowed here (5.2).
- [x] 4.3 Two line citations corrected against base `9e7e969`: the walk's `while` is `:168` (not `:171`), in
  `proposal.md` and `design.md`, and the park write is `:248` (not `:249`), in both. Verification:
  `git show 9e7e969:tools/issues/resolve-stale.sh | sed -n '168p;248p'` prints the `while` and the
  `--status "$hold_status"` call.
- [x] 4.4 The "header comment of the `due` rule updated" attribution moved from `tools/resolve-stale-cases.sh`
  to `tools/issues/resolve-stale.sh`, where the comment lives (`proposal.md`, "What Changes"). Verification:
  `git diff 9e7e969..HEAD -- tools/resolve-stale-cases.sh` carries the registry, the dispatch and the case
  body, and no comment about the bound.
- [x] 4.5 The two unrelated blank-line deletions in the merged spec are restored, so the `fix-*` delta rewrites
  no text it does not own. Verification: `git diff 9e7e969..HEAD --
  openspec/specs/stale-citation-resolution/spec.md` shows the requirement and the five scenarios' `Case` lines
  only.
- [x] 4.6 The mutated run's failure message is quoted in `evidence/revert-checks.md`, not only its exit status:
  `the one slot the bound allowed went to the item a run can write: expected '1', got '0'`, with the hand-applied
  `5.11`, the byte-identical restore and the green re-run beside it. Verification: `evidence/revert-checks.md`,
  the block that begins `$ bash tools/resolve-stale-selftest.sh --case`.
- [x] 4.7 The structural gate re-run after these edits — the comments of `tools/issues/resolve-stale.sh`
  changed, no compiled file did: `bash tools/resolve-stale-selftest.sh`, 22 of 22, exit 0
  (`evidence/green-round2.txt`, 2026-10-20T09:50:34Z). Verification: that file; the case the change adds is
  among the 22 `PASS` lines.

## 5. Round-3 corrections (the review refused round 2's evidence, not its fix)

- [x] 5.1 `evidence/diff.txt` regenerated from the base commit to this tree, the archived change directory
  included — it had been round 1's diff, quoting the clause round 2 refused and two blob hashes that no longer
  exist. The file itself is the one exclusion, a file cannot quote its own content, and its header says so.
  Verification: on the diff's merged-spec section — `sed -n
  '/^diff --git a\/openspec\/specs\/stale-citation-resolution\/spec.md/,/^diff --git a\/tools/p' evidence/diff.txt` —
  `grep -c 'the items the run writes'` prints `0` and `grep -c 'the items the run acts on'` prints `1`, so the
  quoted clause is this tree's; `grep -n 'index f3e0cb2'` shows `..3e49a34`, not round 1's `..3fa5fe3`; and
  `grep -c '^diff --git'` prints `14`, the 13 tracked files `git diff --name-only 9e7e969 -- .
  ':(exclude)…/evidence/diff.txt'` lists plus the untracked `evidence/green-round3.txt` the second section adds.
- [x] 5.2 Task 4.2's verification narrowed to the three files its claim names (4.2, above), so the command it
  states is true on the corrected tree. Verification: that command, run verbatim, finds nothing.
- [x] 5.3 The second unrelated blank-line deletion in the merged spec is restored (the one before
  `## Requirements`), so 4.5's claim now holds literally. Verification: `git diff 9e7e969..HEAD --
  openspec/specs/stale-citation-resolution/spec.md` carries no whitespace-only hunk — one hunk, the
  requirement and the `Case` lines.
- [x] 5.4 The production comments no longer say the bound is on what a run "writes", which excluded the default
  dry run the corrected clause covers: they say `would write` (`tools/issues/resolve-stale.sh:40` and the
  comment above the guard). Verification: `grep -n 'would write' tools/issues/resolve-stale.sh` finds both, and
  `grep -n 'a run \*\*writes\*\*' tools/issues/resolve-stale.sh` finds none.
- [x] 5.5 The round-2 block of `evidence/revert-checks.md` carries its own timestamp, so it is not read under
  round 1's date. Verification: the block's `(recorded …)` line.
- [x] 5.6 `design.md` (c) states the hysteresis the tooling has — the metric row is `threshold 5,
  consecutive 2` and `tools/sensors/run.sh:371-377` records the first above-bound run and files on the second —
  instead of "on its first scheduled run". Verification: `design.md`, the sentence citing
  `tools/sensors/sensors.json`.
- [x] 5.7 The structural gate re-run after 5.3 and 5.4 — `tools/issues/resolve-stale.sh` changed again, no
  compiled file did: `bash tools/resolve-stale-selftest.sh`, 22 of 22, exit 0 (`evidence/green-round3.txt`,
  2026-10-20T09:54:33Z).
  Verification: that file, and `bash -n tools/issues/resolve-stale.sh`.
