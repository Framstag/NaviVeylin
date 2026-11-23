# Design — the cut arm's one size finding

## The decision

The item's own worker ended at the worker role's turn bound is the item's size, not a phase left mid-flight. The arm
discriminates on the key the child's own `LOOP-TIMEOUT` line carried — already parsed for the state record at
`run.sh:2158`, so no second reading of the child's output and no new parser — and writes the verdict the run's context
ceiling already writes for the same finding, `too-wide`, rather than inventing a sixth result or a new verdict word.

The write stays the two writes the skipped arm already makes (`run.sh:2646` then the verdict), `unpick` first: the
in-progress mark comes off on every exit path, and `labels.sh`'s shaping write does not remove it. The size finding's
sentence takes the place of the "left for the next run" one rather than standing beside it, because the two are the
issue's two readings of one cut and a note carrying both would be the state #1039 replaces. A failure of either
write degrades the way the arm already degrades — `label_failures` names what could not be written in the item's note
— so the item's own note never claims a hold the tracker does not carry.

## What is deliberately not changed

- **The other bounds.** The child clock, the run's deadline, a review round's turn bound, the gate child's turn bound,
  the tail's and the rework worker's all leave a tree the next run resumes at the size it was; each keeps the plain
  `unpick` and the existing sentence. `LOOP_CHILD_TURNS_MAX_WORKER_REWORK` is a different key, so a refusing round's
  rework is untouched.
- **The result vocabulary.** The result stays `cut` with `bound` as its mechanism; only the label write and the note
  discriminate. `next`'s two-blocked streak, the report's `cut short by the run's own bound:` line and the
  pre-screen's exclusion of a cut child's branch are all keyed on the result, so they read the same.
- **The report.** `report.sh:454` prints the item's own note, so the reason reaches the report without a second
  print site.
- **No new case, no new mutation.** Child B of #1033 carries the standalone case and the mutation; this child extends
  the two cases that already own this arm.

## Open decisions

none — the verdict word (`too-wide`, the profile's `roles.shapingVerdicts`), the write path (`labels.sh`), the key
(`LOOP_CHILD_TURNS_MAX_WORKER`) and the one-write-two-step shape are all already in the tree; the only open question
was whether a state write and a tracker write may disagree, and the arm's existing `label_failures` answer is kept.
