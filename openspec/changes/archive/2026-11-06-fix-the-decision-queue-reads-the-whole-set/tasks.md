# Tasks

Reference: `proposal.md` for scope, the red run and the surface count; `design.md` for why and for what the evidence
eliminated; `specs/decision-queue/spec.md` for the contract.

## 1. The red cases, before any engine change

Requirement: "The queue reads the whole labelled set, and refuses a set it could not cover", scenarios "An item
older than any fixed page is still listed" and "A read that could not cover the set is refused".

- [x] 1.1 `tools/issues/fixtures/gh`: the stub gains the fidelity the two cases need and only that — `gh issue list`
      answers `--label` and `--state` filtering and, when the fixture holds more items than `--limit`, slices
      newest-created first (the real CLI's order); `gh api graphql` answers `totalCount` from the fixture's own
      items and refuses a query it does not describe; `GH_STUB_COUNT_LAG` makes that count lag the list, the race
      the refusal is for. A fixture smaller than its window is still served whole and in its own order.
      Verify: `bash -n tools/issues/fixtures/gh`, then the three answers on the `decision-queue` fixture —
      `gh issue list --state all --limit 3` → `74 73 72` (newest first, one cut),
      `gh issue list --state all --label agent/needs-decision --limit 3` → `71 73 74`, and
      `gh api graphql … --jq .data.repository.issues.totalCount` → `3`.
- [x] 1.2 `tools/issue-tooling-cases.sh`: the helper `decision_tracker` (a tracker of N items, the named ones
      labelled, generated rather than checked in) and the two cases, with their list and dispatch entries.
      Verify: `tools/issue-tooling-selftest.sh --list | wc -l` → `160` (158 before this change).
- [x] 1.3 They fail on the base engine — `tools/issues/decisions.sh` at `2e59e48a`, the fix kept aside
      (2026-11-06T19:42:44Z–19:42:46Z, `evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`, tests=2 failures=2):
      `decisions#theOldestQueuedItemIsListed` lists only `#405` — `'#1  **The decision:** …' not found in: #405 … 1
      item(s) await a decision` for a tracker of 405 — and `decisions#aReadThatCannotCoverTheSetIsRefused` prints
      `2 item(s) await a decision` and exits 0 where the set the tracker counted was 1.

## 2. The engine

- [x] 2.1 `tools/issues/decisions.sh`: the count first (`gh api graphql` `totalCount` for the label), the list scoped
      by that label and asked for one item more than the count, the refusal (`decision-set-uncovered`, exit 3) when
      the read comes back full, the set line beside the queue's count, and `--limit` gone from the usage, the header
      and the help range. Verify: `decisions#theOldestQueuedItemIsListed` and
      `decisions#aReadThatCannotCoverTheSetIsRefused` PASS (2026-11-06T19:44:49Z–19:44:55Z,
      `evidence/green-on-change.log`), and `tools/issues/decisions.sh --help` prints its header and not
      `set -uo pipefail`.
- [x] 2.2 The cases that own the queue and its neighbour are unchanged: `decisions#listsTheQueueAndItsQuestions`,
      `decisions#theQuestionIsNotTheVerdictWord`, `decisions#emptyQueueIsNotAnError` and
      `shaping#theDecisionQueueIgnoresTheShapingLabel` PASS in the same log.
- [x] 2.3 `guidelines/Backlog.md` §5: the block that keeps the decision queue answerable goes from five rules to six,
      the sixth being the read the queue owes the label. Verify: `tools/look.sh 'Six rules keep the decision queue'
      --path guidelines/Backlog.md` → 1 match, and `tools/check-doc-routes.sh` still resolves every reference.

## 3. Verification

- [x] 3.1 The whole self-test, not only the queue, on the final tree: `tools/issue-tooling-selftest.sh` → `all 160
      cases passed (JDK 27, offline, no token, HEAD 2e59e48a, tools cf3c0506, 2026-11-06T19:46:18Z–19:47:46Z)`
      (`evidence/issue-tooling-selftest.log`; the same run before the guideline and the change's own artifacts were
      written, `tools 3cd9bd6c`, passed at 19:44:19Z).
- [x] 3.2 The checks a `tools/issues/**` + `guidelines/**` + `openspec/**` delta owes, and the change's own
      readiness — `check-portability.sh` → `OK — 56 engines and 7 templates name no project value`,
      `check-doc-routes.sh` → `all 329 section references resolve, all 5 skill references resolve`, its self-test
      → `all 34 cases passed`, `check-todo-references.sh` → `66 citation(s) resolve`, `check-openspec-hygiene.sh`
      → `OpenSpec artifacts are consistent`, its self-test → `all 7 cases passed`, `gen-feature-list.sh
      --check-classification`, `issues/check-metadata.sh` → `0 violation(s)`, `issues/metadata-mutations.sh --list`,
      `mutate.sh --mutations tools/decision-question-mutations.json --list` (2026-11-06T19:45:16Z–19:45:32Z and
      19:47:46Z once the proposal existed, `evidence/structural-checks.log`), `check-change-readiness.sh` →
      `LOOP-CHANGE-READY: ok` and `openspec validate … --strict` → `is valid`.
- [x] 3.3 No compiled file is touched, so this change owes the structural checks above rather than a build: the
      suite it owes is the issue tooling's self-test, and no Gradle task is run "for completeness".
- [x] 3.4 The gate is not this session's: it is deferred to a fresh child after a reviewer has read the diff, so no
      `./tools/loop/gate.sh` was run here.
