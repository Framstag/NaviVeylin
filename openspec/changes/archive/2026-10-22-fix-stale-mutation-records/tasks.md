# Tasks — fix-stale-mutation-records

Verification follows the changed files (`guidelines/Build.md` §2): every path this change touches is shell tooling
or documentation, so the gate is `./tools/loop/gate.sh` and it owes, by its own profile,
`tools/loop-selftest.sh`, `bash tools/loop-mutations.sh --check`, `tools/loop-mutations-selftest.sh`,
`tools/check-doc-routes.sh` and `bash tools/gen-feature-list.sh --check-classification`. No Gradle build is owed: no
compiled file changes.

## 1. The red case, before the fix

- [x] 1.1 Write the three cases in `tools/loop-cases.sh` (`mutation#everyDeclaredRecordApplies`,
  `mutation#aStaleRecordIsNamed`, `gate#anEnginePathOwesTheRecordCheck`), registered in `LOOP_SHELL_CASES` and in
  `loop_run_shell_case`. **Verify**: `bash tools/loop-selftest.sh --case <each>` prints `FAIL` on HEAD —
  `.pi/issue-loop/items/264/red-on-head.log`.
- [x] 1.2 Record the defect as the tree carries it: `bash tools/loop-mutations.sh --only '9.9 12.2'` reports
  `MUTATION-DID-NOT-APPLY` for both, exit 1 — `.pi/issue-loop/items/264/mutations-baseline.log`.

## 2. The check

- [x] 2.1 Add `--check` to `tools/loop-mutations.sh`: `MUTATION-PATTERN-STALE <label> <file>` per record with an
  expression that changes nothing, exit 1 if any, exit 0 otherwise, no case run and nothing in the tree mutated.
  **Verify**: with the two records still stale, `bash tools/loop-mutations.sh --check` names exactly `9.9` and
  `12.2`, `2 of 84 checked records no longer apply`, exit 1 —
  `.pi/issue-loop/items/264/check-names-the-stale-records.log`.
- [x] 2.2 Skip the two prerequisites that only running a case needs when `--check` is set, and add `--check` to the
  header's usage, exit and bound paragraphs. **Verify**: `bash -n tools/loop-mutations.sh`; the check passes against
  `--engine tools/loop` from any working directory.

## 3. The two records

- [x] 3.1 Rewrite `9.9` and `12.2` to anchor on the head of `run.sh`'s child-start line. **Verify**:
  `bash tools/loop-mutations.sh --only '9.9 12.2'` → `RED 9.9`, `restore 9.9 … green again`, `RED 12.2`,
  `restore 12.2 … green again`, `not-noticed: 0`, exit 0 —
  `.pi/issue-loop/items/264/mutations-9.9-12.2.log`.
- [x] 3.2 Confirm the check now passes over the whole declared set. **Verify**:
  `bash tools/loop-mutations.sh --check` → `all 87 cases passed`, exit 0.

## 4. The gate owes it

- [x] 4.1 Add the two `;`-pairs to `LOOP_GATE_SUITES` in `tools/loop/loop.conf`, with the paragraph that says why
  the check is cheap enough for the gate and the whole set is not. **Verify**:
  `bash tools/loop/gate.sh --plan --base 9f10749` names `bash tools/loop-mutations.sh --check`, and
  `bash tools/loop-selftest.sh --case 'gate#anEnginePathOwesTheRecordCheck'` passes.
- [x] 4.2 Falsify each new case with its own record (`41.1`–`41.3`). **Verify**:
  `bash tools/loop-mutations.sh --only '41.1 41.2 41.3'` → three `RED`, three `green again`, `not-noticed: 0` —
  `.pi/issue-loop/items/264/mutations-41.log`.

## 5. Documentation

- [x] 5.1 `.pi/skills/revert-check/SKILL.md`: `--check` in the usage block, and the paragraph that says a record is a
  quote that goes stale, with the gate that owes the check. **Verify**: `tools/check-doc-routes.sh` (the gate).
- [x] 5.2 This change's delta on `agent-issue-loop` names one case per scenario. **Verify**:
  `openspec validate fix-stale-mutation-records --strict`.

## 6. Close

- [x] 6.1 `./tools/loop/gate.sh` once, its result quoted, its tallies copied into `evidence/`.
- [x] 6.2 `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-stale-mutation-records --root .`
  reports no dangling pointer, and the change is archived into `openspec/changes/archive/`.
