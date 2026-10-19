# decision-queue — tasks

- [x] 1.1 `tools/issues/decisions.sh`: the queue, read-only, with `--json`, the profile's decision role and the
      question taken from the item's last comment (`decision-queue`, "The queue names what awaits a decision").
- [x] 1.2 `decisions#listsTheQueueAndItsQuestions` — red on HEAD (no script), green: the queue prints only the
      items carrying the label, in issue order, with the recorded question, and names an item whose escalation
      left none.
- [x] 1.3 `decisions#emptyQueueIsNotAnError` — an empty queue exits 0.
- [x] 2.1 `tools/issues/answer.sh`: the answer verb, with the comment written before any state change, the three
      transitions, and the refusal of an item that awaits no decision.
- [x] 2.2 `answer#requiresTheDecisionText`, `answer#refusesWhatIsNotADecision` — both write nothing.
- [x] 2.3 `answer#continueReturnsTheItemToTheQueue` — the comment precedes the label edit, the decision label is
      removed and the hold replaced by the queue's state.
- [x] 2.4 `answer#deviceMovesToTheDeviceLane` — the profile's device state, decision label cleared.
- [x] 2.5 `answer#retireClosesAsRetired` — evidence required first, then `close.sh --not-planned`; the item
      carries the settled state a person's decision records and no longer counts against the cap.
- [x] 3.1 `tools/loop/prompts/worker.md`: `LOOP-DECISION` in the read-back block, with what it must contain.
- [x] 3.2 `tools/loop/run.sh`: the skip escalation carries the field beside the root cause and the evidence.
- [x] 3.3 `prompt#workerNamesTheDecisionField`, `run#skipEscalationCarriesTheDecision`.
- [x] 4.1 `tools/tooling.json`: `roles.openStatus`; the engine reads it instead of spelling the value
      (`tools/check-portability.sh` OK, 42 engines, 98 values).
- [x] 4.2 The `decision-queue` fixture scenario: awaiting with a question, awaiting with none, held without the
      label.
- [x] 5.1 `guidelines/Backlog.md` change 7's row states what landed and where the answer path lives.
- [x] 5.2 `.pi/skills/update-issue/SKILL.md` names the answer verb next to the other tracker writes.
- [x] 6.1 Gates: `issue-tooling-selftest` 90/90, `loop-selftest` 80/80, `check-portability` OK; evidence in
      `evidence/gate.txt`.
- [x] 6.2 Field test: the twelve-item drain, each answer recorded on its item (`evidence/drain.txt`).
- [x] 6.3 `openspec archive decision-queue` — the change archiving is the last task, so it is marked done by the archive run.
