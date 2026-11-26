# Design — the red gate travels with the change it blocked

## One writer, one reader, one place

The branch already has the durability the review rounds have (issue #262): a verdict is a file under the archived
change's `evidence/`, written by the run and committed by `end-item`. `end-item` is also the one place a write after
the gate is allowed, and it already carries the gate's record there for a gate that passed (issue #979,
`carry_gate_record`). So this change adds no new writer and no new place: it stops one guard from refusing.

1. **The writer** (`carry_gate_record`, `tools/loop/worktree.sh`): a red tally is copied as
   `evidence/gate-red.log`, carrying the tally's `LOOP-GATE:`/`LOOP-GATE-STEP:` lines only. It is copied whether or
   not the change already carries a green record of its own — a blocked attempt's evidence holds exactly the logs its
   focused runs made, and those are the record that made it adoptable. A green tally keeps today's behaviour byte for
   byte, including the "the change answers for its own gate" guard.
2. **The reader** (`carried_gate_red`, beside `carried_verification`): the evidence records whose text states a
   `LOOP-GATE:` line whose `gate_tally_state` is `red`, printed as one comma-separated list, or nothing.
3. **The state** (`branch_state_of`): `gate-red`, answered once the branch's one archived change is found and
   **before** `archived-not-green`. A red gate is the reason the verification is incomplete — the blocked run
   stopped with the cases it reached logged and the rest unlogged — so naming the gate is the more specific answer.
   `cmd_branch_state` prints the change and that record for it, and the doc comment lists the state.
4. **The refusal** (`cmd_adopt_item`): before the verification refusal, so the sentence names what happened rather than
   "no green verification record".

`prescreen.sh` and `leftovers.sh` are read, not edited: the pre-screen's arm treats every attempt state but
`unfinished` and `rework-cut` as the pre-screen's decision (so `gate-red` is the owner's), and the adoption of a
finished attempt asks `adopt-item`, whose refusal is the record.

## The alternatives the evidence eliminates

- **Carry the whole `gate.log` and the step XMLs of a red run.** Rejected: `verification_record_green` reads a `.log`
  with a `PASS` line and no `FAIL` line as a passing measurement, and a blocked run's gate log carries the verdicts of
  the steps that did pass. A `gate-red.log` that carries only the gate's tally lines cannot ever read as green.
- **Write the verdict from `gate.sh`.** Rejected: the gate's own directory is build output the tree ignores and the
  next item's start clears (issue #979's measurement), so a write there is not the durability this needs.
- **Refuse every branch whose evidence is one case's log** (the sibling item, #1026). Read and left to its own change:
  it does not look at the verdict, and a blocked run may copy several logs, so it does not answer this item.
- **Re-run the gate on the branch.** Rejected: nothing on the branch says which tree the gate measured, and a second
  gate per item is the budget the loop explicitly refuses.

## Blast radius

`tools/loop/worktree.sh` (the writer, the reader, the state, the refusal), `tools/loop/layout.tsv` (the new symbol),
`tools/loop/tests/loop-cases.sh` (one new case, one assertion rewritten), `tools/loop/tests/loop-mutations.sh` (one
mutation for the new arm), `guidelines/Backlog.recipes.md` section 7d (one sentence). No reader of the merge is added:
the state is refused where the merge is taken.

## Rollback

`git revert` of the commit returns every file to its previous text. Evidence already written as `gate-red.log` then
reads as nothing, which is today's behaviour: the branch falls back to `archived-not-green` or `adoptable` exactly as
it did before. No migration, no data repair.
