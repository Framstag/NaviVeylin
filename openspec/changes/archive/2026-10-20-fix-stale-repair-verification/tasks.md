# Tasks — fix-stale-repair-verification

## 1. The red cases

- [x] 1.1 Add `resolver#basenameRepairLeavesALongerPathAlone` to `tools/resolve-stale-cases.sh` (registry,
  dispatch, body, and the `absent_in` helper it re-classifies with): a tree holding `tools/alpha/alpha.sh`, a body
  citing the doubled `tools/alpha/tools/alpha/alpha.sh` and the bare `alpha.sh`; it asserts the written body
  carries the collapsed citation, carries no `tools/alpha/tools/alpha`, and leaves no `absent` citation.
  Verification: on the base commit `648d2bc` it fails with `and the doubled citation is collapsed, not
  re-doubled by the basename repair: 'the writer is \`tools/alpha/alpha.sh\`' not found in: …`, exit 1
  (`evidence/red.txt`, 2026-10-20T10:15:00Z). Parent: `stale-citation-resolution`, "A citation is repaired when
  the repair is mechanical".
- [x] 1.2 Add `resolver#repairWithoutAChangeIsNotReported` — one citation at `\`/alpha.sh\``, so the basename
  repair may not touch it and the body is unchanged; asserts `.repaired == 0`, no recorded write carrying
  `citation repaired`, and a park whose comment names `the rewrite changed nothing`. Verification: on the base
  commit it fails with `no repair is counted when the body did not change: expected '0', got '1'`, exit 1
  (`evidence/red.txt`).
- [x] 1.3 Add `resolver#repairIsVerifiedByReclassification` — `old/Place.kt` renamed to `new/Place.kt` and then
  deleted, so the rename map names a target the tree does not hold; asserts `.repaired == 0`, no `citation
  repaired` write, and a park whose comment names `the target is not a tracked path`. Verification: on the base
  commit it fails with `a target the history names but the tree does not hold is not a repair: expected '0', got
  '1'`, exit 1 (`evidence/red.txt`).

## 2. The fix

- [x] 2.1 In `tools/issues/rewrite.awk`, admit `-v standalone=1`: refuse a match whose left character is `/`, so
  a basename repair cannot rewrite the tail of a longer path. Default stays 0, so the absent loop's boundary
  rule is unchanged. Verification: `case 1.1` passes; `classifier#fragmentIsNotACitation` and
  `resolver#markersSurviveTheRepair` stay green (they assert the existing boundary rules).
- [x] 2.2 In `tools/issues/resolve-stale.sh`, pass `-v standalone=1` on the suffix loop's rewrite (`:208`) and
  record a pair (`tok<TAB>canon`) in each loop instead of composing the bullet there. Verification:
  `bash -n tools/issues/resolve-stale.sh`; case 1.1 passes.
- [x] 2.3 Add the verification block between the absent loop and the `due` guard: keep a pair only when the body
  differs from the input, `issues_citation_class "$new"` is not `absent`, and the old token is not an `absent`
  citation of `issues_citation_matrix_of "$new_body"`; compose `repairs` from the kept pairs and add a failing
  pair to `unresolved` with what failed. Verification: cases 1.2 and 1.3 pass; the park comment carries the
  reason and never the phrase `citation repaired`.
- [x] 2.4 Add mutations `5.12` (the `standalone` condition), `5.13` (the changed-body guard) and `5.14` (the
  re-classification guard) to `tools/resolve-stale-mutations.json`, naming case 1.1, case 1.2 and case 1.3
  respectively, and update mutation `5.5`'s search string to the changed suffix-loop line. Verification:
  `bash tools/mutate.sh --mutations tools/resolve-stale-mutations.json --list` lists all 14;
  `--only 5.12`, `--only 5.13` and `--only 5.14` each report `RED(exit 1)` then `GREEN(exit 0)`
  (`evidence/revert-checks.md`); the whole set reports `14 mutation(s) run, 13 went red and restored green, 1
  problem(s)`, the one being 5.7 below (`evidence/mutations-full.txt`).

## 3. Verification

- [x] 3.1 The full resolver self-test, offline: 25 of 25 (`evidence/green.txt`, 2026-10-20T10:16:00Z).
- [x] 3.2 No compiled file is touched, so this task replaces the Gradle build task with the structural check and
  says so instead of running a build "for completeness" (`guidelines/Build.md` §2, the scope block): the gate is
  `bash tools/resolve-stale-selftest.sh`, the command the CI step "Check stale citation resolver" runs. No
  tracker write, no dependency, no native code.
- [x] 3.3 The neighbouring suites, run once: `tools/issue-tooling-selftest.sh` 91 of 91, exit 0
  (`evidence/issue-tooling-selftest.txt`) and `tools/check-portability.sh` OK, exit 0 (`evidence/portability.txt`) —
  the second because `tools/issues/**` is one of the engines it scans. `tools/loop-selftest.sh` reports `1 of 114
  cases failed` on `worktree#keepsByDefaultAndRemovesOnRequest`, the documented artefact of this tree's own path
  (`worktrees/loop`), which the archived `2026-10-20-fix-stale-repair-budget` change records as pre-existing
  (`evidence/loop-selftest.txt`). This change touches no loop file.
- [x] 3.4 Mutation `5.7` of the same set reports `MUTATION-DID-NOT-APPLY` (its search, `--status on-hold`,
  matches no line since the status became a profile value) — pre-existing, named as an exclusion rather than
  repaired inside a fix for another defect, as the archived `2026-10-20-fix-stale-repair-budget` change records.

## 4. Archive

- [x] 4.1 `openspec validate fix-stale-repair-verification --strict`, then archive the change and confirm the
  delta merged into `openspec/specs/stale-citation-resolution/spec.md` (the requirement's three new scenarios and
  the `Case` lines). Verification: `openspec list` shows no open change, and the merged spec carries
  `A rewrite that changes nothing is not a repair`.
