# Design — fix-loop-child-that-never-started

## D1 — How a child that never started is told apart from one that failed

The root cause is that `run_child` hands its caller the child's status and nothing else, so the outcome chain
has one number to classify from. Three ways to get the distinction were available; the evidence eliminates all
three, and only one is left.

**Alternative A — label the fault from the child's own message.** The incident's answer was `Error: Unknown
option: --mode json`, so the engine could recognise a refusal of that shape. **Refused:** it reads another
program's prose, and the prose was the *adapter's* defect, fixed since — the pattern would be a rule about a
message that no longer exists. It is also the leak `run#engineNamesNoProjectFact` and
`tools/check-portability.sh` exist to refuse: a mode flag is a fact about *this* project's agent, and the engine
may name none (design D16). Another project's refusal reads differently, and one that refuses in a language
other than English would be classified as a failed item again.

**Alternative B — have the adapter emit a "could not start" line.** The adapter is the one process that knows
how the child was started. **Impossible:** the adapter knows the child's exit status and its output, which is
exactly what `run.sh` knows — it cannot tell a refused argv from a crash three minutes into the work. A project
whose configured command is plain text has no adapter at all (`run#plainTextChildStillReports`), so the fact
would be missing exactly where the engine claims to need no second code path.

**Alternative C — leave the item `blocked` and only soften the stop condition.** The run stops on "two
consecutive items ended blocked" (`state.sh:238`), so counting an environment fault as half of that pair could
be relaxed instead. **Rejected:** it treats the symptom. The item is still recorded as blocked and still
escalated — `agent/needs-decision` + `status/on-hold` + a comment — so the tracker still asks a person to
decide two items nobody read, and each still spends one of the run's three decision slots
(`LOOP_MAX_DECISIONS_DEFAULT`). The next run then refuses to start on the cap the first one filled.

**Chosen: the item's own evidence, read by the run itself.** `run.sh` already reads the tree, the archive and
the child's verdict before it decides an outcome; the same evidence answers this question. A worker that

- ended with a non-zero status,
- wrote no `LOOP-VERDICT` line, and
- left the item's tree at the run's base commit (`worktree.sh end-item` has just committed whatever it found),

never got far enough to read the item. That is one mechanism, decided from facts the run reads instead of from
a claim, and it is false for both children that must stay blocked:

| child | what distinguishes it | why it stays a blocked item |
|---|---|---|
| ended by the run's own wall-clock | its transcript carries `LOOP-TIMEOUT` (and `rc` is `LOOP_CHILD_TIMEOUT`) | it *did* work, and its transcript is the evidence (issue #203) |
| worked, committed, then failed | `head_now != base` | the branch carries work a later pass escalates as a previous attempt (issue #176) |

## D2 — What the run does with it, and why the item is left untouched

The fault is the run's, so the record is the run's: the in-progress mark comes off (`loop_labels unpick`, the
same call every exit path makes), **no item record is written**, nothing is escalated, and the run stops through
`stop_run`, which prints and records `.run.stop.reason` — one record, naming the child's own last line
(`last_line`). The run then ends through its ordinary end path, so the report is printed and the lock is
released; the run's exit status stays `LOOP_OK`, because a child the environment refused is not a failed run —
the same reading the host-memory path takes (`run.sh:584-591`, base-commit numbering).

The item is deliberately left with **no** record:

- nothing about it was read, so no reason about it can be true;
- `loop_state done --result blocked` is what the "two consecutive items ended blocked" condition counts, so
  writing one would spend the run's stop condition on a fault that is not the item's;
- an item record also removes the item from the queue (`state.sh:412`), which would retire an item the run
  never worked for the rest of the run.

Leaving the queue entry and the item's labels alone means the next run picks the same item up unchanged.

**Risk: a child that crashes late is stopped as a fault.** Accepted, and bounded by the base-commit condition:
a late crash has committed something, so it keeps the blocked path. A child that crashes late *before* it
commits anything is indistinguishable from one that never started — by construction, since the evidence is all
that exists — and it loses only the escalation, which the next pass re-derives from the branch or produces
again from a fresh attempt. The opposite error is the one measured on 2026-10-20: an escalation for an item
nobody read, which no later pass can retract.

## D3 — Why the reviewer child keeps its current reading

A reviewer that dies without a `VERDICT:` line still ends its round as a failed review (blocked, escalated),
and this change does not touch that path. It is a different question with a different answer: the item **was**
read — its work is in the tree, its change is archived — so a block on it names a true state ("the review did
not pass"), and the escalation carries the work as evidence. Making the reviewer's death a run fault would
discard a finished item's review record, which is the opposite of what this change protects. If a reviewer
that never started is ever measured, it is its own item with its own red case.

## Blast radius

- `tools/loop/run.sh` — one new helper (`last_line`), one branch inserted after `head_now` is read and before
  the outcome chain. No other statement changes; every later path reads the same variables.
- `tools/loop/fixtures/pi` — one optional fixture file printed before the report; nothing existing changes.
- `tools/loop-cases.sh`, `tools/loop-mutations.sh` — one case and one mutation, in both registries.
- `guidelines/Backlog.md` §7 and the capability spec — the rule and its measurement.

The engine names no project value; `tools/check-portability.sh` is unchanged and still reports the same count.

## Rollback

`git revert` of the one commit. The fault branch is self-contained: removing it restores the previous outcome
chain byte for byte, and `last_line` then has no caller. Nothing recorded in `run.json`, no label and no branch
depends on the new branch having run, because it writes no item record and no label beyond removing
`agent/in-progress` — which every exit path already removes.
