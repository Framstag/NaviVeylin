# Tasks — a gate's record names the tree it measured, and the round reads it

Every task names its own verification, and every verification below was run in the change root on the base
`ba96953f6d43df8ac5926c23697a75294cc10af2`. The three cases came first and were red on that base, with the engine as
the base left it: `gate#aThirdFullRunInOneSessionIsRefused` (extended with the record's own tree assertions) fails at
`'LOOP-GATE-TREE: head=' not found`, `run#aReviewDiffNamesTheTreeItWasWrittenFrom` (new) at `the diff under review was
handed no tree identity`, and `gate#theGateAndTheRoundNameOneTree` (new) at `the gate names the tree it measured` —
`evidence/red-on-HEAD-aThirdFullRunInOneSessionIsRefused.log` (2026-11-08T07:19:09Z),
`…-aReviewDiffNamesTheTreeItWasWrittenFrom.log` (07:19:09Z) and `…-theGateAndTheRoundNameOneTree.log` (07:19:09Z),
each `loop-selftest: 1 of 1 cases failed`. No compiled file is touched, so this change owes no build: the shell suites
are the whole gate for it.

- [x] 1. Write the red cases first. `gate#aThirdFullRunInOneSessionIsRefused` gains the record's own tree assertions —
  every record line carries the commit and a digest, the console names the same identity, the refusal names each
  earlier run's tree, and a tree corrected between two full runs records a different digest while the earlier records
  keep theirs. `run#aReviewDiffNamesTheTreeItWasWrittenFrom` is new: the diff a round is handed carries
  `<diff>.identity`, and the reviewer's task names it and the gate line it is compared against.
  `gate#theGateAndTheRoundNameOneTree` is new: the gate's `diff=` and the `.identity`'s are one digest on a tree
  nobody corrected, a corrected path moves it, and both moments take one derivation.
  - verified: `bash tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`
    (`evidence/declaration-check.log`), the plan's first line for a delta that writes a case; then all three cases
    fail on the base — the three `evidence/red-on-HEAD-*.log` above.

- [x] 2. `tools/loop/lib.sh`: `loop_tree_identity <tree> <base> [<pathspec>]` → `head=<sha> diff=<digest>`, the digest
  over the paths the base names plus every untracked path, each with `git hash-object` of its working-tree content, a
  deleted path recorded as `deleted`, less the pathspec it is given; and `loop_change_identity <tree> <base>`, the
  same derivation with the pathspec read from the profile, so both moments take one path set and neither can hold one
  of its own. No project path and no project value: the engine names nothing of its own (D16).
  - verified: `bash -n tools/loop/lib.sh tools/loop/gate.sh tools/loop/run.sh tools/loop-cases.sh
    tools/loop-mutations.sh` → no output, exit 0.

- [x] 3. `tools/loop/gate.sh`: derive the identity once the base is resolved, print `LOOP-GATE-TREE: <identity>` before
  the first step (only when a step will run — `--plan` measures nothing and prints only the delta's commands), write it
  as the record line's third field, and name each earlier run's tree beside its command in the refusal.
  - verified: `bash tools/loop-selftest.sh --case 'gate#aThirdFullRunInOneSessionIsRefused'` → `PASS`
    (`evidence/green-aThirdFullRunInOneSessionIsRefused.log`).

- [x] 4. `tools/loop/run.sh`: `write_review_diff` writes the same identity beside the diff it writes
  (`<diff>.identity`), through `loop_change_identity`, derived from the run's own base whatever range the diff is taken
  over, so a correction round's narrower diff still names the change. `tools/loop/prompts/reviewer.md` item 3 names
  both recorded values and grades a mismatch `BLOCKING:`.
  - verified: `bash tools/loop-selftest.sh --case 'run#aReviewDiffNamesTheTreeItWasWrittenFrom'` → `PASS`
    (`evidence/green-aReviewDiffNamesTheTreeItWasWrittenFrom.log`);
    `--case 'gate#theGateAndTheRoundNameOneTree'` → `PASS`
    (`evidence/green-theGateAndTheRoundNameOneTree.log`).

- [x] 5. `guidelines/Backlog.recipes.md`:567 "The gate and its suite" states the rule, the observation that would
  falsify it and the measurement behind it, in the paragraph that already owns the record; the section's own rule is
  not restated a second time anywhere.
  - verified: `./tools/look.sh 'LOOP-GATE-TREE'` → `guidelines/Backlog.recipes.md` among the files that hold it;
    `bash tools/check-doc-routes.sh` → `all 329 section references resolve, all 7 skill references resolve`
    (`evidence/doc-routes.log`); `bash tools/check-portability.sh` → `OK — 56 engines and 7 templates name no project
    value (118 value(s) read from the project)` (`evidence/portability.log`).

- [x] 6. `tools/loop-mutations.sh`: four records for the new invariants — `262.1` drops the identity from the record,
  `262.2` drops the write beside the diff, `262.3` makes the digest independent of the change, `262.4` drops the path
  set the one derivation leaves the generated trees out of.
  - verified: `bash tools/loop-mutations.sh --check` → `all 291 cases passed — every selected record applies to the
    file it names` (`evidence/mutation-check.log`); `--only 262.1`, `…262.2`, `…262.3`, `…262.4` each →
    `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0` (`evidence/revert-check-262.*.log`), each red on the
    case it names and for its own reason.

- [x] 7. The suites the delta owes, scoped by what it touched — including the two prompt cases this change's round-1
  review found missing, both of which declare the `tools/loop/prompts/reviewer.md` this diff edits.
  - verified: `bash tools/loop-selftest.sh --group gate` → `all 30 cases passed (…) — 21.51 s`
    (`evidence/group-gate.log`); `--group run` → `all 136 cases passed (…) — 166.37 s` (`evidence/group-run.log`);
    `--group prompt` → `all 17 cases passed (…) — 22.74 s` (`evidence/group-prompt.log`); `--group driver` → `all 23
    cases passed (…) — 21.50 s` (`evidence/group-driver.log`); `--group mutation` → `all 2 cases passed (…) — 2.83 s`
    (`evidence/group-mutation.log`); the two cases the round-1 review named individually →
    `PASS prompt#reviewerIsBoundedToReading` (`evidence/green-prompt-reviewerIsBoundedToReading.log`) and
    `PASS prompt#iterationProtocolHandsTheReviewerTheSameSkeleton`
    (`evidence/green-prompt-iterationProtocolHandsTheReviewerTheSameSkeleton.log`); and the whole selection the delta
    owes runs green in one pass — `bash tools/loop-selftest.sh --from-git ba96953f6d43df8ac5926c23697a75294cc10af2` →
    `all 373 cases passed (JDK 27, offline, no token, no sandbox) — 396.75 s`
    (`evidence/selection-from-git.log`). The case count moves by two: `bash tools/loop-selftest.sh --list | wc -l` →
    `373` (371 before, `evidence/case-count.log`).

- [x] 8. The change is valid and its delta replaces nothing it does not restate.
  - verified: `openspec validate fix-a-gate-record-names-the-tree-it-measured --strict` → `Change
    'fix-a-gate-record-names-the-tree-it-measured' is valid` (`evidence/openspec-validate.log`);
    `bash tools/check-openspec-hygiene.sh` → `OpenSpec artifacts are consistent` (`evidence/openspec-hygiene.log`).

- [x] 9. The round-1 review's two blocking findings are answered in this pass, not deferred.
  - `evidence/review-round-1-requested.md` item 1/4 (three of the six scenarios had no execution) → the two prompt
    cases are run and named in task 7; the seventh scenario names the new `gate#theGateAndTheRoundNameOneTree`.
  - the same review's item 8 (the two derivations never meet, so `diff=` differs on a tree nobody corrected) → one
    derivation both callers take (task 2), the path set it takes from the profile (task 2), the case that compares the
    two moments (task 1) and mutation `262.4` that turns it red (task 6); the paragraph at `guidelines/Backlog.recipes.md`:567 says which paths the digest
    covers and what would falsify the rule (task 5).
  - verified: `bash tools/loop-selftest.sh --case 'gate#theGateAndTheRoundNameOneTree'` → `PASS`
    (`evidence/green-theGateAndTheRoundNameOneTree.log`), `bash tools/loop-mutations.sh --only 262.4` →
    `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0` (`evidence/revert-check-262.4.log`).
