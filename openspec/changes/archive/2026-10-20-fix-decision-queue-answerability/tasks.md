# Tasks

## 1. The red cases first

- [x] 1.1 `tools/loop-cases.sh`: `run#blockedItemAsksItsOwnQuestion` — the fixture worker claims `PASS` and
      commits nothing, so the run blocks the item and escalates it; the recorded escalation comment must carry
      a `The decision:` field naming `retry` and `abandon`. Red on HEAD (the body holds only the reason).
      Covers `agent-issue-loop`, "A block asks its own question", scenario 1.
- [x] 1.2 `tools/loop-cases.sh`: `labels#closeRemovesTheDecisionLabel` — an item carrying
      `agent/needs-decision` is closed through `labels.sh close`; the stub `gh` log must show
      `--remove-label agent/needs-decision` beside `--add-label status/fixed-by`. Red on HEAD.
      Covers the modified "Closure follows a passing review and carries evidence", scenario
      "A closure takes the decision label off the item".

## 2. The fixes

- [x] 2.1 `tools/loop/run.sh`: the blocked path's `else` branch states the question when the reviewer wrote
      none. Verification: task 1.1 green.
- [x] 2.2 `tools/issues/close.sh`: the closure reads the item's labels and passes `--remove-label
      agent/needs-decision` to the existing `update.sh --status` write only when the item carries it.
      Verification: task 1.2 green, and `tools/issue-tooling-selftest.sh` unchanged at 91/91.

## 3. Spec delta

- [x] 3.1 `openspec/changes/fix-decision-queue-answerability/specs/agent-issue-loop/spec.md`: the MODIFIED
      requirement keeps its scenarios and gains the closure's label write; the ADDED requirement holds the
      block's question with its case. Verification: `openspec validate fix-decision-queue-answerability
      --strict`.

## 4. Structural checks — the scope block (no compiled file is touched)

- [x] 4.1 `bash -n` on `tools/loop/run.sh`, `tools/issues/close.sh`, `tools/loop-cases.sh`,
      `tools/loop-mutations.sh`; `bash tools/loop-selftest.sh` 120/120;
      `bash tools/issue-tooling-selftest.sh` 91/91; `bash tools/check-portability.sh --quiet` unchanged.
      Exclusion, named: no Gradle gate and no `./gradlew` suite — no Kotlin, Java, C++, manifest or resource
      file is touched (`guidelines/Build.md` §2, the scope block).
- [x] 4.2 Revert-check: `tools/loop-mutations.sh --only "29.1 29.2"` — both red-when-mutated, both green after
      the restore, no restore failure; and `bash tools/mutate.sh --mutations tools/close-mutations.json` — all
      five of the issue tooling's closure mutations red-when-mutated and green after the restore (A1.1's
      `search` was this change's casualty and is repaired in the same commit).
- [x] 4.4 The queue's own filter: `tools/issues/decisions.sh` requested no `state` in its `--json` fields and
      filtered on `.state` anyway, so a closed item stayed in the queue by construction — the mechanism behind
      "#81 was closed and still listed". Verification: `decisions#listsTheQueueAndItsQuestions` asserts that a
      closed item carrying the label is not in the queue, and removing the filter makes it fail
      (`expected '2', got '3'`). The field half is code-read only: the stub `gh` answers the whole fixture
      regardless of the `--json` field list, so no case can see the difference.
- [x] 4.3 `guidelines/Backlog.md` §5: one clause — a closure leaves no decision label behind, and the block's
      question is stated when the reviewer wrote none. Verification: `check-doc-routes.sh`.

## 5. Closing the item

- [ ] 5.1 Archive this change (independent review of the two hunks and the two cases, then
      `openspec archive fix-decision-queue-answerability`), and close #225 with the archived change's name as
      its evidence.
