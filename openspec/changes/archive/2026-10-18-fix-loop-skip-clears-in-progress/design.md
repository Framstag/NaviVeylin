# fix-loop-skip-clears-in-progress — design

## D1 — The mark comes off on the run's exit paths, not in the verdict matrix

`labels.sh` owns the label vocabulary and its matrix asserts exactly what each verdict writes: a verdict adds
its own label and its status, and only `pick`/`unpick`/`close` touch `agent/in-progress`
(`labels#verdictToLabelMatrix`). So the missing removal is the *run's* to make, at the one place it knows an
item has left its hands — the `case "$outcome"` at the end of an item — and not a change to the matrix, which
would make every verdict carry a label edit the matrix case would then have to bless.

That leaves the three exit paths symmetric, which is what `labels.sh`'s header already promised:

| outcome | the mark |
|---|---|
| closed | removed by `close` |
| blocked | removed by the blocked path's `unpick` |
| skipped | removed by the skip path's `unpick` (this change) |

## D2 — Verification: the loop's own shell gates

One case (`run#aSkipClearsTheInProgressMark`) asserts the gh record of a skipped item carries
`--remove-label agent/in-progress` and the verdict's status; one mutation (`12.8`) removes the call and the
case must go red. The gate is the loop suite, the mutation set and the portability suite — no compiled file
changes.

The defect was found by reading the tracker after a real run, so the fix carries the tracker repair as its
own step (`tools/issues/update.sh 109 --remove-label agent/in-progress`): the code stops producing the state,
and the one item that has it is repaired once.
