# decision-queue — design

## D1 The queue is its own script, not a selector on `list.sh`

**Chosen.** `tools/issues/decisions.sh`, ~90 lines, one tracker read.

*Alternatives.* (a) A `--awaiting-decision` selector in `tools/issues/list.sh`: rejected because `list.sh` is a
label query that returns issues; the queue needs the *comment* of each item to answer "what is asked", and folding
a second read into the selection tool would make every `list.sh` call carry a cost no other caller wants. (b) A
skill-only recipe ("read the label, then read the comments"): rejected because the recipe is what was missing — a
person following prose is the state #194 measured, and a recipe cannot be tested offline. (c) An `openspec`-only
statement of the lane: rejected, the same reason.

**Risk.** A second reader of the decision label could drift from `run.sh`'s cap query. Mitigation: both read the
same role (`roles.decisionAgent`) and the case asserts the queue's selection against the profile's value, not a
literal.

## D2 The question is the item's own last statement, not a field in the body

**Chosen.** The first non-empty line of the item's **last comment**.

*Alternatives.* (a) A structured marker in the body (`<!-- decision: … -->`): rejected — a body edit rewrites it,
`tools/issues/create.sh` fingerprints the body, and the escalation is a later event than the filing, so the marker
would have to be appended by a writer that does not own bodies. (b) A field in the run's state: rejected — that is
the machine-local record the whole lane exists to escape (#192). (c) The last comment's whole body: rejected as
output; the options belong under the question, and a queue line has to stay one line.

**Risk.** A person's own later comment becomes "the question". That is deliberate — the more recent statement is
the current one — and it is why the queue prints the tracker's text rather than a summary it could be wrong about.

## D3 The answer verb takes the transition as a flag, never a label expression

**Chosen.** `--continue` (default), `--device`, `--retire`.

*Alternatives.* (a) `--label <name>` / `--state <name>` passthrough: rejected — it would let a caller write any
state, including the reserved authorisation label, through a verb that is meant to be the *narrow* path; the gate
in `tools/issues/lib.sh` refuses reserved labels, but a verb whose arguments are the vocabulary is a verb that can
express the wrong transition. (b) A single `--close` flag plus a separate `update.sh` call by the person:
rejected — two commands are the folklore again, and the second one is where the decision label stays on the item
and keeps counting against the cap.

**Risk.** A fourth outcome will need a fourth flag. Accepted: the outcomes are the states the profile declares,
and a new one is a profile decision that should be visible in a diff.

## D4 The answer is recorded before the transition

**Chosen.** `update.sh --comment "Decision: …"` first, then the transition.

*Alternatives.* (a) Transition first, comment after: rejected — a failed comment would leave the item in a state
with no reason, which is exactly #192. (b) One write that carries both: impossible through the tooling's writers,
and `gh issue edit` cannot comment in the same call as a label change.

**Risk.** A failed transition leaves the decision recorded and the item still awaiting one. That is the safe
direction: the item can be answered again, and the first attempt is visible.

## D5 The handover rides the worker's existing read-back block

**Chosen.** `LOOP-DECISION:` in `tools/loop/prompts/worker.md`, read by the same `report_field` the other fields
use, and prefixed `**The decision:**` in the escalation comment — the shape the blocked path already produced from
the reviewer's report.

*Alternatives.* (a) A separate "options file" in the item's evidence directory: rejected — the run's state is
machine-local, and an escalation whose options live there cannot be answered a day later (measured: #109's
reasoning survived only in that directory). (b) Requiring the reviewer to state it for skips as well: rejected —
a skip never reaches a review round.

**Risk.** A worker that omits the field (an old prompt, a model that ignores the instruction). Mitigation: the
escalation still carries the root cause and the evidence, and the queue names the missing question explicitly, so
the gap is visible rather than silent.

## Threading and lifecycle

None: these are single-process CLI scripts, run to completion. No dispatcher, no background work, no Android
component. The only shared state is the tracker itself, and every write goes through the two writers that own it.

## Verification

- Offline, against the stub tracker (`tools/issues/fixtures/gh`): seven cases for the queue and the answer verb,
  two loop cases for the handover. Red first: `decisions.sh` and `answer.sh` did not exist, so every case failed
  at the missing script.
- `tools/check-portability.sh`: the two new engines name no project value (the profile supplies `decisionAgent`,
  `openStatus`, `deviceStatus`, `holdStatus`; the fixtures carry the literal labels, which is their job).
- On the real tracker, the drain of the twelve open decisions was the field test: ten unparked after their
  citations were repaired, one retired as refuted, one moved to the device lane — each with the reason recorded on
  the item by the answer path this change writes (`evidence/drain.txt`).
