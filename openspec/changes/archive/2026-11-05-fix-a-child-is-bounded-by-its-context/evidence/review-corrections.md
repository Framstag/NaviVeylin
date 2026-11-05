# The pre-gate round's finding, and what this session did with it — 2026-11-05

The round before the gate ran on this diff and answered `VERDICT: FAIL` with `LOOP-BLOCKING: 1`
(`evidence/pre-gate-review.md`, round 1, 13 381 B). This file is the record of the corrections. The run that
took the round was ended by the turn bound while it was applying them (the item's `status/on-hold` comment on
#760), so the corrections were applied by the session that finished the item, on the same branch.

## The blocking finding — applied

> `tools/loop/run.sh:2270` narrows the reviewer's cut reason to `"… at one of the item's spend bounds (its turns
> or its context) …"`, and `run.sh:2598` writes exactly that into the item's note, while `tools/loop-cases.sh:5131`
> asserts `"turn bound"` — the substring is absent from the new text, so
> `run#aReviewerEndedAtItsTurnBoundIsNotARefusal` goes red on the applied tree.

Both strings now read `one of the item's spend bounds (its turn bound or its context bound)`, which is the
reviewer's first correction and changes no claim: the turn bound is still named, and the context bound is named
beside it.

```
$ ./tools/loop-selftest.sh --case 'run#aReviewerEndedAtItsTurnBoundIsNotARefusal'
PASS run#aReviewerEndedAtItsTurnBoundIsNotARefusal
```

Recorded in `evidence/green-review-case.log`; the four bound cases were re-run with it on the same tree
(`evidence/green-cases.log` carries the pair this change owns, `evidence/green-review-case.log` this one).

## The notes — applied

- **The delta was ADDED-only where a MODIFIED block was owed.** Two shipped scenarios enumerated the turn bound as
  the only spend bound — `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault` and
  `run#aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun`, the second describing exactly the code path this
  change edits. Both requirements are now carried as `## MODIFIED Requirements` blocks with every scenario they
  already had, verbatim, and the two WHEN clauses read "one of its spend bounds". The blocks were extracted from
  `openspec/specs/agent-issue-loop/spec.md` mechanically — the shipped text is repeated, not retyped — so no
  scenario name can drift.
- **The ADDED requirement's body was over the validator's 500 characters** (1 200+ chars of history before the
  contract). It now states the contract in 498 characters and leaves the examples to the two scenarios.
  `openspec validate fix-a-child-is-bounded-by-its-context --strict` answers `is valid`.
- **`guidelines/Backlog.md` §7b named two bounds** where the recipe now records three; it names the context bound.
  The mirror sentence in `Backlog.recipes.md` (§7b, the #757 paragraph) called `LOOP_ITEM_TURNS_MAX` "the **stop**";
  it now names both stops.

## The notes — declined, with the reason

- **`tests=2 failures=2` (proposal, "the roll-up the report cites").** `evidence/red-summary.txt` states that no
  single command printed the pair and derives it from the two runs, each of which printed `1 of 1 cases failed`.
  That is what the proposal means by it; the roll-up is the derivation, and the derivation is in the evidence.
- **Neither green log carries a clock time.** The green side is dated (`# the applied tree, 2026-11-05`) and names
  the case; the red side carries both timestamps. A green run's clock time adds nothing a re-run cannot produce.

## The overreach — removed

Claims no command in the record produced are gone from every file this change touches:

- "at the window every provider this project runs sits at" — removed from `loop.conf`, `run.sh`, the recipe and
  `design.md`; the derivation is the archive's own percentiles, which are measured.
- "the archive's event streams run to megabytes per item" (`design.md`) — replaced by the reason that needs no
  size: the stream grows with the run and the wait looks once a second.
- "the same turn count costs 40 k or 240 k" (`loop.conf`, `run.sh`, the recipe) and "227 items at 78 k tokens a turn,
  97 % of the spend charged as cache read, tokens per item p50 10.4 M …" (proposal) — the item body's numbers, quoted
  as measurement. The same argument is now made with this change's own command:
  `evidence/context-distribution.txt` (n=300, p50 107 669, p75 143 124, p90 174 984, p95 191 815, max 244 764, and
  the ten items above 200 000).
- "10 of the 300 recorded items (3.3 %)" stays, because `evidence/context-distribution.txt` lists the ten and
  prints the count.
