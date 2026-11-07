## This round is read before the gate

The run has not run the project's gate on this change and does not expect one yet. It is deferred to a child that
starts after you: that child applies your findings in one pass, and then runs the item's one gate. So:

- **Item 3 is not yours.** The gate's evidence does not exist yet. Record item 3 as `PASS` with the line
  `the gate has not run — this round precedes it`, and grade nothing else about it. Do not run the gate and do not run
  a suite or a case to fill the gap: a gate you ran is the item's second, and its result would be quoted as if the
  change had earned it.
- **A check the gate will run is not yours to refuse.** Every check the measuring command's block names — the
  `LOOP-MEASURE-CHECK` lines the change child quotes into the evidence you are given — is a step the gate child runs:
  grade it `PASS` on that line, beside the case the change names for it, and never `UNPROVEN` for want of a log the
  change child did not produce — that refusal is what sends the child back to run the selection the gate owns. What you
  may refuse is a claim the gate cannot settle: a scenario no case in the diff exercises, a number no command in the
  record produced, a task marked done whose case is absent.
- **A `BLOCKING:` finding here is a correction, not a refusal.** This round does not block the item and is not a round
  of the review budget: it hands one list to the child that runs the gate, and the round after the gate decides the
  change. So name each thing to change exactly — the line, the claim, the pointer — in the shape one pass can answer.
- **The change is not archived.** Its directory is still under the change root, and its `evidence/` may hold what the
  change child copied there rather than what the archive will carry. Read it where it is; a file only the archive would
  hold is a finding about the change, not a missing artefact, and the archive is not yours to judge here.
- `LOOP-CORRECTIONS-ONLY` and `LOOP-DECISION` are not read from this round. `LOOP-BLOCKING:` and `VERDICT:` are, and a
  `VERDICT: FAIL` with a count above zero is what hands your list to the gate child.

Items 5 (whether the mutation that proves the change targets the production invariant) and 6 (whether every quoted
number is the output of a command the record carries) are graded as they always are: the change child ran its mutation
before this round, and the numbers it quotes are in its records.
