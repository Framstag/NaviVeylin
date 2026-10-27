# Proposal

## Why

A worker's `stale` skip is gate 1 of its eligibility check — "the claim is verified in the tree today" — and its
meaning is that the item's premise no longer holds. The run records that verdict as a bare hold:

- `tools/loop/run.sh:1849` escalates only `needs-decision`, `needs-diagnosis` and `falsified`; every other skip,
  `stale` included, takes the hold branch at `tools/loop/run.sh:1869`;
- `tools/loop/labels.sh:88` maps `stale` to `status/on-hold` with a comment and no decision label;
- the pre-screen then meets an item on hold with no decision label at `tools/loop/prescreen.sh:375` and reports it
  as `held` — "the item is on hold with no recorded decision … neither work nor a decision this run may take".

So the one verdict that says nothing is left to do puts the item where nothing can reach it: not work, not a
question, and open for ever. Measured on #147 (2026-10-27T17:11:06Z label timeline, `.pi/issue-loop/items/147/`):
the run wrote `status/on-hold` beside its comment, and the item was closed by hand through
`tools/issues/close.sh --not-planned`.

The `falsified` skip already escapes that park (`openspec/specs/agent-issue-loop/spec.md`, "A skip that falsifies
the premise is closed as mis-diagnosed"), but a falsification closes only with a measurement **and** a review of
the unchanged tree; `stale` carries neither — the worker prompt stops at the verification
(`tools/loop/prompts/worker.md:98`) and the run never starts a review for a skip. Closing on it would close an
item on a claim nobody measured, which is the state that requirement refuses.

Owning capability: `agent-issue-loop` — "A state the run writes is readable from the item", "A hold is not a
decision", and "A skip that falsifies the premise is closed as mis-diagnosed". Owning guideline section:
`guidelines/Backlog.md` §5 "Who may write what", the rules that keep the decision queue answerable — which
this change adds one of.

Red test, on HEAD, before the fix:

```
FAIL run#staleSkipAsksItsQuestion
  a stale skip leaves a decision, not a bare hold nobody can answer: '--add-label agent/needs-decision' not found in: gh issue edit 21 --add-label agent/in-progress
```

`XML tests="1" failures="1"` (`evidence/stale-skip-red.xml`, 2026-10-27T19:36:18Z), and the same case against the
single mutated guard (`evidence/revert-check.txt`): `XML tests="1" failures="1"` red, then green again on the
restore.

## What Changes

- A `stale` skip escalates: the run writes the decision label and the hold together, with one comment carrying
  the verdict's reason and the question — retire the item as stale, or re-diagnose it. `tools/loop/run.sh`'s skip
  path admits the verdict to the escalation branch it already has.
- A stale skip whose report carries no `LOOP-DECISION` gets the run's own stated fork, exactly as a block with no
  reviewer decision does (`run.sh:1899`): the escalation must hold something a person can answer.
- `tools/loop/labels.sh` drops its `stale` verdict: the run no longer writes it, and a hold that is reachable from
  the label writer must stay a hold that states no question. The three hold verdicts (`unspecifiable`, `too-wide`,
  `not-spec-shaped`, `needs-target`) are unchanged.
- `tools/loop/prompts/worker.md` tells the worker that a stale skip is a question and that its `LOOP-DECISION` is
  required with it.

Not in scope: closing a stale item, the falsification closure, the device verdict, the decision cap and the
pre-screen's held reporting — all unchanged.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `agent-issue-loop`: a new requirement, "A stale skip asks its own question", and the existing hold vocabulary is
  narrowed to the verdicts that do state no question.

## Impact

Tooling only: `tools/loop/run.sh`, `tools/loop/labels.sh`, `tools/loop/prompts/worker.md`, their cases
(`tools/loop-cases.sh`) and one mutation record (`tools/loop-mutations.sh`). No application source, no resource,
no native code, no compiled file — so the change owes no build.

Surface, before the plan was fixed — `grep -rn 'stale' tools/loop-cases.sh tools/loop/run.sh tools/loop/labels.sh
tools/loop/prompts/worker.md tools/loop-mutations.sh` (filtered of the unrelated stale-base, stale-citation and
stale-tree rows): **2 cases added, 2 rewritten, 1 case re-asserted, 2 engine files, 1 prompt, 1 mutation record,
0 fixtures realigned**. The rewritten cases are `labels#verdictToLabelMatrix` (its `stale` row) and
`labels#holdCarriesItsReason` (it named `stale` as its hold; it now names `unspecifiable`, a hold that stays one).
