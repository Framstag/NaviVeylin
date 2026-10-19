# decision-queue

## Why

The loop's escalations went into a lane nobody had built the other half of. It marks an item as awaiting a
decision, records the work already done and takes the next candidate — and then a person is expected to answer,
with no command that lists what awaits an answer and none that records one. Measured 2026-10-20: twelve items
carried `agent/needs-decision` against a cap of three, so `tools/loop/run.sh` refused to start before its
pre-screen, and the means of clearing the pile were folklore — that the labels are the mode, that the answer is a
comment, which state to set back, and that `agent/eligible` can only be withdrawn in the tracker's UI (#194).

`guidelines/Backlog.md` records the lane as change 7, `decision-queue`, in the state **named, not written**, with
the reason it was deferred: "it depends on a working loop, and the prepared-options format needs the loop's
experience to design". Both premises now hold — the loop runs, and two real escalations (#109 refuted, #110
undiagnosed) show what an escalation can and cannot state.

## What Changes

- **A queue view**, `tools/issues/decisions.sh`: every item carrying the profile's decision label, with the
  question the item itself carries last. Read-only; an empty queue exits 0.
- **An answer verb**, `tools/issues/answer.sh <issue> --decision <text> [--continue|--device|--retire]
  [--evidence <text>]`: the decision is recorded as a comment **before** any state changes, then the transition
  the answer implies is performed through `tools/issues/update.sh` and `tools/issues/close.sh`, so the
  reserved-label refusal, the closed vocabulary and "evidence before closure" apply unchanged. An item that
  awaits no decision is refused.
- **The mid-item handover**: the worker prompt's read-back block gains `LOOP-DECISION` (the question, the options
  in order, the recommendation), and a skip that escalates carries it into the escalation comment beside the root
  cause and the evidence — so an item is answerable from itself.
- **Two profile roles** the two new engines read rather than repeat: `openStatus` (the state an answered item
  returns to) and `heldVerdict` (from the prerequisite fix). `check-portability` keeps forbidding project values
  in the engines.

## Spec

New capability `openspec/specs/decision-queue/spec.md`: five requirements, eleven scenarios, each naming its case.
The prerequisite contract lives in `openspec/specs/agent-issue-loop/spec.md` ("A state the run writes is readable
from the item", "A hold is not a decision") and landed with its own two changes before this one.

## Impact

| file | change |
|---|---|
| `tools/issues/decisions.sh` | new — the queue |
| `tools/issues/answer.sh` | new — the answer verb |
| `tools/tooling.json` | `roles.openStatus`, and `roles.heldVerdict` from the prerequisite fix |
| `tools/loop/prompts/worker.md` | the read-back block gains `LOOP-DECISION` |
| `tools/loop/run.sh` | the skip escalation carries that field |
| `tools/issue-tooling-cases.sh`, `tools/issue-tooling-selftest.sh` (list) | seven cases, offline against the stub tracker |
| `tools/loop-cases.sh` | `prompt#workerNamesTheDecisionField`, `run#skipEscalationCarriesTheDecision` |
| `tools/issues/fixtures/scenarios/decision-queue/` | the fixture: awaiting with a question, awaiting with none, held without the label |
| `guidelines/Backlog.md` | change 7's row: what landed, and where the answer path is documented |
| `.pi/skills/update-issue/SKILL.md` | the answer verb, next to the other tracker writes |

No Android module, no manifest, no Gradle change: this is tracker tooling. Additive; the rollback is a revert of
this commit — the labels written by an answer are ordinary states, and no item's history depends on the scripts.

## Cases and evidence

`decisions#listsTheQueueAndItsQuestions`, `decisions#emptyQueueIsNotAnError`, `answer#requiresTheDecisionText`,
`answer#refusesWhatIsNotADecision`, `answer#continueReturnsTheItemToTheQueue`, `answer#deviceMovesToTheDeviceLane`,
`answer#retireClosesAsRetired`, `prompt#workerNamesTheDecisionField`, `run#skipEscalationCarriesTheDecision`.

- `evidence/gate.txt` — `issue-tooling-selftest` 90/90, `loop-selftest` 80/80, `check-portability` OK
- `evidence/drain.txt` — the twelve-item drain that this lane made answerable, with what each item awaited
