# Tasks — fix-stale-title-token-settled

## 1. The red cases

- [x] 1.1 Add `resolver#titleSuffixBesideItsCanonicalPathIsSettled` to `tools/resolve-stale-cases.sh` (registry,
  dispatch, body): a tree holding `tools/alpha/alpha.sh`, a title naming `` `alpha.sh` `` and a body citing
  `tools/alpha/alpha.sh` in full; it asserts `.parked == 0`, `.due == 0`, `.writes == 0`, no `status/on-hold`
  write and no comment. Verification: on the base commit `3211d4d` it fails with `an item whose body already
  holds the canonical path is not parked: expected '0', got '1'`, exit 1 (`evidence/red.txt`,
  2026-10-20T20:40:27Z). Parent: `stale-citation-resolution`, "The resolver runs on its own, bounded and
  idempotent".
- [x] 1.2 Add `resolver#titleAbsentCitationIsParkedWhereItStands` — a title citing `Ghost.kt:3`, a body citing
  `Foo.kt:12`; asserts `.parked == 1`, the comment names `the citation is in the title`, and no comment claims
  `the rewrite changed nothing`. Verification: on the base commit it fails with `and the reason names where it
  is: 'the citation is in the title' not found in: …`, exit 1 (`evidence/red.txt`).

## 2. The fix

- [x] 2.1 In `tools/issues/resolve-stale.sh`, read the body's own matrix once per visited item and add
  `body_cites <token>` beside the other helpers. Verification: `bash -n tools/issues/resolve-stale.sh`.
- [x] 2.2 Skip a `suffix` token the body does not carry (`body_cites "$tok" || continue`, after the
  `[ "$canon" != "$tok" ]` guard) and short-circuit the `absent` loop for the same tokens with the
  `the citation is in the title, which this tool does not rewrite` bullet, ahead of the successor walk.
  Verification: case 1.1 and case 1.2 pass; `resolver#repairWithoutAChangeIsNotReported` and
  `resolver#basenameRepairLeavesALongerPathAlone` stay green (their tokens stand in the body).
- [x] 2.3 Extend the file header's idempotence paragraph with the title/body asymmetry, so the next reader sees
  why a title token is settled. Verification: the paragraph names `#226` and the body-only write.
- [x] 2.4 Add mutation `5.15` to `tools/resolve-stale-mutations.json` — the `body_cites` predicate made to
  return true — naming case 1.1. Verification: `bash tools/mutate.sh --mutations
  tools/resolve-stale-mutations.json --list` lists all 15; `--only 5.15` reports `RED(exit 1)` then
  `GREEN(exit 0)` (`evidence/revert-checks.md`).
- [x] 2.5 Add one clause to `guidelines/Backlog.md` §7: a citation standing only in an item's title is never
  rewritten — a title whose tracked path the body already carries leaves the item settled, and a title the tree
  does not hold parks it — because the resolver writes bodies. Verification: the paragraph still states the
  repair ladder and the parking rule; `tools/check-doc-routes.sh` OK.

## 3. Verification

- [x] 3.1 The full resolver self-test, offline: 27 of 27 (`evidence/green.txt`).
- [x] 3.2 No compiled file is touched, so this task replaces the Gradle build task with the structural check and
  says so instead of running a build "for completeness" (`guidelines/Build.md` §2, the scope block): the gate is
  `bash tools/resolve-stale-selftest.sh`, the command the CI step "Check stale citation resolver" runs. No
  tracker write, no dependency, no native code.
- [x] 3.3 The neighbouring suites, run once: `tools/issue-tooling-selftest.sh` (the shared `lib.sh` is imported
  by this script, not changed) and `tools/check-portability.sh` — the second because `tools/issues/**` is one of
  the engines it scans — plus `tools/loop-selftest.sh`, whose pre-screen is the other caller of the classifier.
  Any failure is named as an exclusion or as this change's.
- [x] 3.4 The real-tracker reproduction, dry run only, both before and after: `bash
  tools/issues/resolve-stale.sh --json --issues <the three items>` reports `due=3 parked=3` on HEAD
  (`evidence/head-dryrun-real-items.txt`) and `due=0 parked=0` after the fix
  (`evidence/green-dryrun-real-items.txt`). No `--apply`, so the tracker is untouched.

## 4. Archive

- [x] 4.1 `openspec validate fix-stale-title-token-settled --strict`, then archive the change and confirm the
  delta merged into `openspec/specs/stale-citation-resolution/spec.md` (the two new scenarios and the
  requirement's title/body clause). Verification: `openspec list` shows no open `fix-stale-title-token-settled`,
  and the merged spec carries `A citation the body does not carry is settled`.

## 5. Round-2 corrections (review, wording only)

- [x] 5.1 Narrow the requirement's title/body paragraph in both the merged spec and the archived delta: a
  body-absent token is never *rewritten*; a resolving one leaves the item settled and costing no slot, a
  non-resolving one is parked and costs the slot a park costs (`tools/issues/resolve-stale.sh:307`, the `due`
  guard). Verification: `openspec validate stale-citation-resolution --type spec` → valid
  (`evidence/round2-openspec-validate.txt`); the merged and delta texts are identical.
- [x] 5.2 Narrow `guidelines/Backlog.md` §7's clause to the two outcomes the tree holds — a title whose tracked
  path the body already carries leaves the item settled, one the tree does not hold parks it. Verification:
  `tools/check-doc-routes.sh` → 89 of 89 (`evidence/round2-doc-routes.txt`).
- [x] 5.3 Correct the change's own prose: the proposal and design no longer date the repairing run (no artifact
  of this tree post-dates that date), and the design no longer claims a title-only `absent` token reaches a
  no-op rewrite — the second red case shows the ladder's absent verdict and a park reason that names nothing
  about the title.
- [x] 5.4 Re-run the gate after the prose edits: `bash tools/resolve-stale-selftest.sh` → 27 of 27, exit 0
  (`evidence/round2-resolver-selftest.txt`). No compiled file is touched, so no Gradle gate applies.
- [x] 5.5 Complete the evidence: `evidence/diff.txt` regenerated over the whole production diff (5 file
  headers, the merged-spec hunk included) and the dry runs' input `real-items-201-181-182.json` archived beside
  its transcripts, so the reproduction can be re-run from the archive.
