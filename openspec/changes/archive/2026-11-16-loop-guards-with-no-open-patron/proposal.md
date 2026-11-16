# Proposal — a guard census, before anything is retired

## Why

The engine's guards are its history. Measured 2026-11-14 on `tools/loop/run.sh`: **210 guard/refusal sites**,
204 mentions of `issue #`, and **128 distinct issue numbers** cited. Each of those numbers is a run that failed
once and a rule that was written so it cannot fail that way again — which is how the file reached 3793 lines from
410 in four weeks.

Nothing counts them. No case knows which guard was added for which item, nothing asks the tracker whether that
item is still open, and nothing says which guards a case exercises. So the engine carries rules whose cause may
have been fixed elsewhere and whose case may have gone; the reader cannot tell a load-bearing rule from a
memory. That is why the answer to "can we remove this?" is always "run the suite and see" — a device-cost answer
for a question about a comment.

This change builds the census first and retires second, and it is written to stop rather than to guess: **if the
census finds fewer than 10 % of the guards unheld, this change ends and is archived as not-needed**, with the
number as its whole deliverable. A retirement campaign on a healthy engine is damage.

## Outcome (2026-11-16)

`tools/loop/census.sh` read this tree: **94 guards**, **4 closed patrons**, **72 guards whose refusal no case
carries**, **90 guards citing no issue**, and **2 guards in the retirement pair** (closed patron *and* no case).
2 of 94 is **2.1 %**, below the 10 % stop rule. This change therefore stops with the census as its deliverable;
`the-unheld-guards-are-retired` has nothing to start from this number. The command and its output are
`evidence/census-2026-11-16.log`.

## What Changes

- **`tools/loop/census.sh`** — one tool, no write to any state, no run phase. It reads the engine's guard sites
  and the issue numbers their comments cite, asks the tracker once for those issues' states, and prints:
  - guards whose cited item is **closed** (the patron is gone);
  - guards whose refusal text appears in **no case** body (nothing exercises them);
  - guards citing **no issue at all** (a rule whose reason was never written down).
  One line per number and a total, plus `--json` for a caller that wants the list.
- **The census refuses nothing and stops no run.** It is a person's and a routine's tool, it needs a tracker
  credential a child must not have, and a census that blocked a run would be a second reason to stop working.
- **The retirement rule is the successor's work.** This change produces the number and the record of it; the
guards it unholds are retired by `the-unheld-guards-are-retired`, in one task per owner file, each with its reason
recorded — and only if this change's number clears the 10 % threshold.
- **Scope**: `tools/loop/**` only. No app code, no dependency, no build change, no new configuration key — the
  threshold lives in this change's own text, not in the engine.
- **Not this change**: the retirements themselves, and the record of a retired guard.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. `agent-issue-loop` gains three requirements, all new: the patron anchor, the census's three numbers, and the
census's own refusal-not-to-refuse. The retirement record and the retirement rule are the successor change's.
No shipped requirement is replaced — this change removes no guard and adds no rule to any script.

## Impact

Code:

- `tools/loop/census.sh` (new) — the reader, the tracker query, the three counts, `--json`.
- `tools/loop/tests/loop-cases.sh` — the `census#` cases.
- `tools/loop/tests/fixtures/` — a stub-`gh` fixture answering a closed and an open item, so the cases run
  offline.

Guidelines and documents:

- `guidelines/Backlog.md` section 7 (the loop's procedure) — the census, and what a person does with its number.
- `guidelines/BuildEvidence.md` §4f — the census's number is evidence about the engine, recorded with a date and a
  command, so a later reader can re-take it.

Rollback: revert the commit. A retired guard's rule comes back with the revert; nothing persisted depends on the
record, which is a committed table and not a runtime input.

Costs to prove by measurement rather than assume:

- **the census itself**: the three numbers on the committed tree, recorded with the date and the command; the
  change's stop rule is read from them.

surface: 3 requirements and 8 scenarios added to 1 delta file, 0 requirement bodies rewritten, 1 new census tool,
0 guards retired (the successor's work) — counted with
`grep -hc '^#### Scenario:' openspec/changes/loop-guards-with-no-open-patron/specs/agent-issue-loop/spec.md` and
`grep -h '^### Requirement:' openspec/changes/loop-guards-with-no-open-patron/specs/agent-issue-loop/spec.md`
