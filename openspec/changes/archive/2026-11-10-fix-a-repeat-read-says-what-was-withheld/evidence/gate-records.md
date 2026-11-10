# The gate record this change carries

One gate measured the tree this change now stands on, and this is that gate: the round-2 gate, run once over the tree
after this round's corrections.

- **The command it ran**: `./tools/loop/gate.sh --base d5d2e668e5acb4cffb47a832b83843f0d7e91be1`, once, in
  `/home/tim/projects/NaviVeylin/.pi/worktrees/loop` — the base is the run's own base (`tasks.md` 5.1), so the tree the
  gate measured is the tree the run's next diff is taken over.
- **The tree it measured**: `LOOP-GATE-TREE: head=695a5dacdc1ac210c55c50778e51543a94a5854d diff=bf5a238382a20cac`
  (`evidence/gate-result.txt:6`, `evidence/gate-round-2.log:4`). The digest is the run's own derivation
  (`tools/loop/lib.sh`, `loop_change_identity`), the one the run writes beside the diff it hands the next review round,
  so the two can be compared by a reader instead of guessed at.
- **Its tally**: `LOOP-GATE: tests=991 failures=0 errors=1` (`evidence/gate-result.txt:9`). The one error is
  `bash tools/gen-feature-list.sh --check-classification`, red on four unclassified delta spec ids that two **other**
  open changes owe; `evidence/gate-attribution.md` carries the attribution and the clause the close is waived with.
- **Its tallies as files**: `evidence/gate-tallies-round-2/` — 23 XMLs, one per suite the gate ran.

## What the earlier gates measured, and what no document of this change cites as its gate

The first round's gate ran over another tree — `head=b89f16dcb2b8ce1976c8bbe5e0d0f78af745d7d9 diff=a879203cc03fe474`,
over the merge-base with `origin/main` — and this round's corrections (the guideline clauses, the change's own
documents and the fixture path in the case) are edits that gate did not measure. Its log and its result are kept as
history in the item's own directory (`items/831/gate-round-1.log`, `items/831/gate-result.txt`) and are named here as
history: no document of this change cites them as the gate that measured this tree.
