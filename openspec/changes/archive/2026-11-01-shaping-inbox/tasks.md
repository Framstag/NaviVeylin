# Tasks — the shaping inbox

## 1. The vocabulary

- [x] 1.1 `tools/tooling.json`: `agent` gains `needs-shaping`; a new `roles.shapingVerdicts` names
      `needs-diagnosis`, `unspecifiable`, `too-wide`, `not-spec-shaped`, and the roles header states that an empty
      list means no verdict writes the label.
      Verification: `jq -r '.roles.shapingVerdicts | length' tools/tooling.json` is 4 and
      `tools/issues/check-metadata.sh` passes.
- [x] 1.2 `tools/issues/labels.sh --dry-run` reports the one label to create and creates nothing; `--apply` creates
      it with the namespace's description.
      Verification: the dry run names exactly `agent/needs-shaping`.
- [x] 1.3 `tools/loop/lib.sh`: the role reader accepts the new key beside the existing ones, and a profile that omits
      it reads as the empty list rather than aborting a run.
      Verification: `tools/loop-selftest.sh` passes with the shipped profile, and a fixture profile without the key
      starts a run.

## 2. The run's write

- [x] 2.1 `tools/loop/labels.sh`: on a skip whose verdict the roles list names, the same call that writes the hold
      and its accounting line also adds the shaping label; the function reports the label it wrote.
      Verification: case `run#aShapingVerdictWritesTheLabel` asserts the item's label set after the call and that
      exactly one write was issued.
- [x] 2.2 A verdict outside the list writes no label, and a `stale` skip is untouched: it still escalates a decision,
      as its own requirement states.
      Verification: cases `run#aVerdictOutsideTheListWritesNoLabel` and the existing stale-skip case still passes.
- [x] 2.3 `too-wide`'s comment states that the item wants splitting, in the same sentence the verdict is accounted
      for.
      Verification: the comment's first line names `too-wide` and the word `split`, asserted by 2.1's case.
- [x] 2.4 The decision ceiling still counts only the decision label.
      Verification: case `run#theShapingInboxDoesNotCountAgainstTheCeiling` starts a run with more shaping items
      than `--max-decisions`.

## 3. The queue

- [x] 3.1 `tools/issues/lib.sh`: a shaping-statement extraction beside `ISSUES_QUESTION_JQ`, reading the verdict word
      and the clause the run wrote — one home, so the two queues cannot disagree about one comment.
      Verification: `grep -c 'ISSUES_SHAPING_JQ' tools/issues/lib.sh` is ≥ 1 and both queues source it.
- [x] 3.2 `tools/issues/shaping.sh` — read-only, `--limit`, `--json`, and `LOOP-SHAPING-COUNT n`; it names an item
      carrying the label with no statement as carrying none, and exits 0 on an empty queue.
      Verification: cases `shaping#theQueuePrintsTheVerdictAndClause`, `shaping#anItemWithNoStatementIsNamed`,
      `shaping#anEmptyQueueExitsZero`, `shaping#anEmptyListIsAnEmptyQueue`.
- [x] 3.3 `decisions.sh` does not list a shaping item, and `answer.sh` refuses one, naming the queue that holds it.
      Verification: cases `shaping#theDecisionQueueIgnoresTheShapingLabel`,
      `shaping#answeringAShapingItemIsRefused`.

## 4. Leaving the inbox

- [x] 4.1 `tools/issues/update.sh` and `tools/issues/close.sh`: the shaping label is removed in the same write that
      records the outcome, on every path that already removes the decision label.
      Verification: cases `shaping#aReshapedItemLeavesTheQueue`, `shaping#aClosedItemLeavesNoLabel`.
- [x] 4.2 An undeclared label is refused by the gate every writer passes, with no label created.
      Verification: case `shaping#anUndeclaredLabelIsRefused`, run against a profile without the value.

## 5. The documents

- [x] 5.1 `guidelines/Backlog.md` §5 "Who may write what": one paragraph — the shaping label, its writers, and that
      it is neither a hold nor a decision, referencing the capability.
      Verification: `grep -n 'needs-shaping' guidelines/Backlog.md` finds §5.
- [x] 5.2 `guidelines/Backlog.md` §6 "How an item becomes work": the road diagram gains the shaping exit, one line.
      Verification: §6 names the queue verb and does not restate the requirement text.
- [x] 5.3 `AGENTS.md`: the backlog routing row's skill table gains nothing new, but `Backlog.md` §5's mention of the
      queues is consistent with `decisions.sh`'s — check, do not edit unless it diverges.
      Verification: `tools/check-doc-routes.sh` passes.

## 6. Verification and archive

- [x] 6.1 Both suites pass and their case counts are recorded with the commands that printed them.
      Verification: `evidence/tooling-cases.txt` and `evidence/loop-cases.txt` carry the before/after counts.
- [x] 6.2 `openspec validate shaping-inbox --strict` exits 0;
      `bash tools/loop/evidence-check.sh openspec/changes/shaping-inbox --root .` resolves every pointer.
      Verification: both exit 0, outputs in `evidence/`.
- [x] 6.3 Run `tools/issues/shaping.sh` against the tracker once the label exists and record what it reports in
      `evidence/shaping-on-tracker.txt`, with the command — the 39 bare holds are the first population and the
      number is the change's own measurement of the gap it closes.
      Verification: the file carries the command and the count.
- [x] 6.4 `tools/feature-list/specs.json` gains the classification entry for the new capability `shaping-inbox`, in
      the area `build-tooling`, because an unclassified capability stops the feature-list run
      (`guidelines/FeatureList.md` §4).
      Verification: `bash tools/gen-feature-list.sh --check-classification` reports no unclassified entry for
      `shaping-inbox`.
- [x] 6.5 Archive; confirm `openspec/specs/shaping-inbox/spec.md` exists with a `## Purpose` this change wrote.
      Verification: `openspec show shaping-inbox --type spec --json | jq -r .purpose` is not the `TBD` sentence.
