# The decision answer says what the owner gets, whether it solves it, and what it risks

## Why

`.pi/skills/answer-decision` is the only reader between an item parked on `agent/needs-decision` and an answered
one, and its shape was last pinned on 2026-11-13 by the change `pin-the-decision-answer-shape`. Four defects were
reported on 2026-11-16 by the person who answers those items — a software architect who also reads them as a product
owner. The report is the evidence for all four (a report, not a measurement: nobody recorded the conversations it
speaks of), and each maps to a place in the skill at `HEAD` that a reader can check.

**1. The owner cannot see what an option gives.** An option at `HEAD` is two things — a mechanism in one sentence and
an `Example:` — plus three table cells. Nothing states the deliverable, so the reader has to infer "what do I get"
from a mechanism. `grep -c 'What you get' .pi/skills/answer-decision/SKILL.md` → 0, and `Does it solve` → 0.

**2. The owner cannot see whether the question is answered.** No option says whether it *closes* the observation the
item states, and a partial option reads exactly like a complete one. `4b` requires an example and `4c` a pro/contra;
neither requires the word "this still leaves X". Measured: the shipped #788 worked example offers A, B, C, D with no
residue for B, C or D beyond a table cell.

**3. The risk is one column.** `4c`'s short-term table carries `what breaks if it is wrong` as a cell, and the
strategic table has no risk column at all. Nothing states how bad, who it hits, whether it can be undone, or whether
anything would catch it — and "not deciding" is not priced anywhere, although the queue spends a decision slot and
the symptom continues while an item waits (`guidelines/Backlog.md` §5).

**4. The answer arrives in pieces.** `§2` lists the queue and then asks which item; `§3` permits unbounded reads with
no statement that the sheet is the next thing the owner sees, so a turn may end on "I have read the item, now let me
check the branch". The skill also never says what the `ask_user` choice should carry, so the choice itself was a bare
option letter — the owner had to scroll back to judge it.

The two lanes are the framing defect behind 1–3: `4a` says two of the six contents lines carry no code, then hands the
options themselves to the architect — mechanism, `file:line`, jq. There is nowhere in the skill that names a lane, no
rule against a code token in the product line, and no template, so every answer improvised its own structure.

## What Changes

- `.pi/skills/answer-decision/SKILL.md`: the option becomes five labelled lines — **What you get**, **Does it solve
  it** (full / partial / no + residue), **How you'll know**, **Risk** (level + the clause that sets it + four fields),
  and **For the architect** (mechanism, files, gate minutes, rounds to land, the worked example). A new §4 names the
  two lanes and refuses a code token in the owner's. A new §7 states the criteria before ranking, as one table. A new
  §8 puts the risks side by side with a **do nothing** row. §2 states that the listing answers "which are waiting",
  that one waiting item needs no question, and what the one question carries. §3 becomes one batched read pass whose
  next message is the sheet. §9's recommendation carries its own risk and the first sign of failure.
- `.pi/skills/answer-decision/references/answer-shape.md` (new, 4.4 kB): the skeleton to fill and seven checks before
  sending. A shape stated only in prose drifts; a skeleton with the block order in it does not.
- `guidelines/Backlog.md` §5 states the same shape in its own words — the two lanes, the option's three questions, the
  priced "do nothing", one pass and one question — and names the cases that hold it.
- `openspec/specs/decision-queue` gains two requirements (this change's deltas) so the shape has a contract and not
  only a case.
- `tools/issues/tests/issue-tooling-cases.sh` gains four cases; the case count moves 173 → 177 (`--list`).

## Impact

Files: `.pi/skills/answer-decision/SKILL.md`, `.pi/skills/answer-decision/references/answer-shape.md`,
`guidelines/Backlog.md` (§5), `tools/issues/tests/issue-tooling-cases.sh`, `openspec/specs/decision-queue/spec.md`.
No compiled file, no module, no Gradle configuration, no manifest, no resource. Additive: no shipped behaviour, no
tooling verb and no label changes, so the rollback is reverting the four files — a `git revert` with no migration.
Breaking: nothing; the four cases and the `answer.sh`/`decisions.sh` contract they exercise are untouched.

## Scope

Tooling and documentation only — the decision reader, its skeleton, its guideline paragraph and its cases. Not the
phone app, not the car app, not the loop's own escalation path (the run still writes the fork it writes; only its
reader changed). The item's own option list is still re-priced by the reader, which is the existing rule.

## What this does not do

- It does not change `tools/issues/answer.sh`, `decisions.sh`, any label, any exit code or any tracker write.
- It does not make the reader smarter: an option it cannot exemplify is still not offered, and a figure it did not
  measure is still marked "unmeasured".
- It does not close the issue this was reported from, because there is none: the report came in conversation on
  2026-11-16 and this change is its record.
- It does not touch the loop's worker prompt. If the *run's* escalation comment should also state an outcome and a
  risk (the run writes the fork's raw material), that is the capstone item this change names in `design.md` D4 and
  leaves open.
