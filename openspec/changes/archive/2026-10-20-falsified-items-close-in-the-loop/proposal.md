# Proposal

## Why

The loop's Phase A sometimes ends with the opposite of a fix: the item's named cause does not hold. The
evidence is a measurement, and it is produced inside the run — then thrown away as a decision.

Measured 2026-10-20 (run `run-20261020T144829Z`, budget 8 items / 600 min). In the first 15 minutes the
run processed four items and closed none:

| item | outcome | what the worker measured |
|---|---|---|
| #89 (§142, "viewport restore costs 6.2 s") | skipped / `needs-decision` | the seconds are the one-time Robolectric bootstrap of a test JVM fork (`2.831 s`), not the blamed `acquire` → `isNavigating` await (`12–17 ms`); a nav-free class pays the same seconds; probe reverted, `tests=4 failures=0` |
| #99 | skipped / `needs-decision` | the deterministic generation tool has no caller — not `./gradlew release`, not CI (only `--check-classification` runs) |
| #137 | skipped / `needs-diagnosis` | the mechanism is not isolated |
| #91 | blocked | the worker claimed success and committed nothing (the run caught it — correct) |

Each of the first three cost a decision slot and a person's attention, although the run had already
answered the item: the premise is false. The tooling makes this mandatory — `run.sh`'s skip path escalates
every non-"needs device" skip, and `close.sh` is never called for a skipped item — because the rule was
written for the skips that *are* questions: too big for one fix, spec-shaped, needs a device, needs
diagnosis. "The item is mis-diagnosed, here is the number" is not one of them.

The consequence compounds with the backlog the loop now works: it is fed by `§`-imported items whose text
describes states from weeks ago, so falsification is not an exception but a routine outcome — three of the
four items in that quarter hour. A run that must ask about each one spends the owner's decision budget on
items it has finished thinking about, and the cap that counts them (`LOOP_MAX_DECISIONS_DEFAULT`, raised to
10 the same day) hides the trend instead of removing it.

Not in scope: answering genuine decisions (the four skip classes above keep escalating unchanged), any
change to how a *blocked* item is treated, and the tracker hygiene that closes provably-done work (its own
change, `sensors-mechanical-hygiene`).

## What Changes

- A skip whose finding is **falsification** is a new verdict, and it carries the measurement: the worker
  states what the item blames, what it measured instead, and how the measurement was taken (case name,
  command, or probe), and the diff is empty.
- When closure is allowed for the run (`--may-close yes`), such an item is **closed as mis-diagnosed** with
  that measurement as the evidence comment — the same two artifacts a normal closure posts (reason, then
  evidence), on an item whose reason is "the premise does not hold".
- The reviewer of the item must have seen the measurement before the closure, whenever the run takes a review
  at all: a falsification is a claim about the tree like any other, so it is reviewed like a change, in the same
  round budget. (`--review off` waives review for every closure, a fix's included; a run started that way
  closes a falsification with `review round 0`.)
- With `--may-close no` the measurement is still posted as the item's comment and the item is left exactly as
  it arrived — no settled state, no decision label, and it is queued again next run; the run never closes what
  its caller did not allow.
- A falsified item is recorded in the run's state with its own result value (not `skipped`), so the report
  can count "closed as mis-diagnosed" apart from "closed by fix" and from "skipped".

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `agent-issue-loop`: "Closure follows a passing review and carries evidence" gains a second closure path
  (a reviewed falsification), and one requirement is added — **a skip that falsifies the premise is closed
  as mis-diagnosed**: the run SHALL close it with the measurement as its evidence when closure is allowed,
  and SHALL escalate it unchanged when it is not. The four existing question-shaped skips and the blocked
  path keep their contracts word for word.

Previous specifications changed: `agent-issue-loop` only. Referenced but not modified: `backlog-source-of-truth`
(the item remains the only record), `backlog-sensors` (its checks measure conditions, not judgements).

## Impact

Scope: **tooling only** — no application source, no manifest, no resource, no native code. Affected files:

- `tools/loop/run.sh` — the skip path: a falsified verdict is routed to `loop_labels`/`close.sh` with the
  measurement, and recorded with its own result; the blocked path and the four question-shaped skips are
  untouched.
- `tools/loop/prompts/worker.md` — the machine-readable field the worker already answers gains the
  falsification form: what is blamed, what was measured, how it was measured.
- `tools/loop/prompts/reviewer.md` — the reviewer is told that a falsification is reviewed like a change
  (does the measurement actually cover the blamed mechanism?).
- `tools/loop/state.sh` — `done --result` accepts `falsified`, and the progress line counts it; without this the
  run's own bookkeeping refuses the outcome it writes.
- `tools/loop/report.sh` — one column value / one counter for "closed as mis-diagnosed", so the run's yield
  does not read as if nothing happened.
- `tools/loop-cases.sh` — the cases that hold the verdict: the worker stub claims a falsification with a
  measurement → the item is closed with evidence; the same claim with `--may-close no` → the comment is posted
  and nothing is closed; the claim without a measurement → an escalation; and a falsification whose tree moved
  during the review → blocked.
- `tools/loop-mutations.sh` — two mutations: route the falsified verdict back to the skip, and remove the second
  read of the tree; each case must go red.
- `tools/loop/loop.conf` — no new key: the closure permission is the existing `--may-close`, whose default is
  `yes` (`LOOP_MAY_CLOSE_DEFAULT`), so an omitted flag **closes** a reviewed falsification.
- `guidelines/Backlog.md` §5 — one rule added: a reviewed falsification closes the item as mis-diagnosed, only
  on an unchanged tree, under the same `--may-close` a fix closure rides.
- `openspec/specs/agent-issue-loop/spec.md` — the delta (one modified requirement, one added).

Additive or breaking: **additive** — the verdict fires only when the worker answers the new fields, and the
four question-shaped skips and the blocked path behave as before. Two things a run with `--may-close yes` will
see differently, because they are the change: a measured falsification is closed instead of escalated, and a
falsification whose tree moved during its review is blocked. With `--may-close no` the item gets the measurement
as a comment, records `result falsified` instead of `skipped`, and carries no settled state and no decision
label. A falsification closure records the **retired** state (`--not-planned`), which
`tools/issues/reopen.sh` refuses to reverse by design (`closure-not-reopenable`) — a wrong falsification is
reopened by hand in the tracker, the same way a person's "not planned" is. Rollback path: drop the verdict from
`run.sh` and the prompts; items closed under it stay retired, and no state or tracker schema changes.

Verification: the loop's own gate (`tools/loop-selftest.sh`, offline, no token, no model) plus the five cases
and the two mutations; no Gradle gate is owed because the delta compiles nothing
(`guidelines/Build.md` §2, the scope block). Device verification: none — no application surface.

Open question carried into `design.md`: whether the closure may happen inside the same item (chosen) or
only in a second, cheaper pass that re-reads the worker's measurement; and whether a falsification must be
reviewed at all when the diff is empty (it must: the measurement is the claim).

## Why the alternative "let `cleanup-todo` sweep it later" is not enough

`cleanup-todo` is a skill, not a scheduled step, and nothing calls it — the same session that noticed this
had to be told to run it by hand. Relying on it means the evidence the run already holds waits for a person
to remember a pass, and the decision pile grows in the meantime. The mechanical half of that skill's job is
addressed by `sensors-mechanical-hygiene`; the judgement half is addressed here, by the process that holds
the evidence.
