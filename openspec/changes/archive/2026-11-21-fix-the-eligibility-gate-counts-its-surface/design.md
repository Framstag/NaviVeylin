## The root cause

`tools/loop/prompts/worker.md:221` states row 5 as `it fits one capability delta and one change (bounded files)` and
names no measure. Every other row of that gate names what is verified, so a verdict is a reading of the tree; row 5
names a shape of sentence ("bounded files", a parenthetical) and leaves the count to the child. Two things follow, and
the run started 2026-11-17T10:58Z paid for both: the refusal is free to take, so it is taken without a count (six
refusals, 8.29 Mtok together, 140 s mean), and the admission is free to make, so it is made without one (four items
onto one 4 379-line engine file, 78.7 Mtok, 65.4 % of the run, all four `blocked` on exactly the width row 5 exists to
refuse).

## The single fix

Row 5 reads its count from the census the worker has already taken in Phase A step 5 — `tools/look.sh '<the rule>'`,
whose output is one line, `LOOK: <n> matches in <m> files of <where>` — and compares it with a bound the tree already
records for the child that will work the item: `LOOP_CHILD_TURNS_MAX_WORKER` (`tools/loop/loop.conf`, 200). One site is
ten turns (the read it owes, the edit, its own case, the gate's focused run and the correction a red case asks for),
so the change fits while `<n>` is at most a tenth of those turns. The row is refused `too-wide` with
`surface: <n> sites over <m> files against <t> turns` and the split the child proposes.

The count is free because Phase A step 5 already asks for it: the prompt requires the proposal to carry
`surface: …` with the command that produced it, so row 5 reads a number the child wrote rather than taking a second
reading of the tree — the defect `prompt#workerNamesTheChangeSurfaceBeforeThePlan` exists for.

## Alternatives the evidence eliminated

- **A new profile key for the surface bound** (`LOOP_ITEM_SURFACE_MAX_SITES`). Rejected: nothing in the engine would
  read it — the bound is applied by the child, not by a script — and a key no command reads is a number a later
  reader cannot check. The turns bound is already recorded for exactly this population, and it is the number the
  child's own session is ended by.
- **Pricing the surface in tokens against `LOOP_ITEM_CONTEXT_WARN`.** The unit is right and the bound is recorded
  (143 124, the checkpoint the 2026-11-17 items crossed), but a child cannot take the number: sizing a file needs
  `wc` or a byte count, and both are refused by the policy every child is started with (`worker.md`, "What your own
  shell and tools refuse"). A measure a child cannot take is the judgement this row already had.
- **Comparing the census with a landed change's own `LOOP-MEASURE-PATH` count.** Rejected: that number is the delta's
  paths, not the surface it had to read, and it admits a 24-path change as proof that a 24-path change is bounded —
  the comparison is with a stranger, not with a bound.
- **Making the pre-screen refuse the width instead.** Rejected: the pre-screen reads the item text and the tracker,
  and the surface exists only after Phase A's census; a script that guessed it would guess from the title.

## Blast radius

One prompt row, one guideline paragraph, one case and one mutation record. No script reads the row, no engine arm
consumes it, and the verdict list, the labels and the report are untouched: `too-wide` is already in the profile's
`shapingVerdicts` and already carries `agent/needs-shaping status/on-hold` (`labels#verdictToLabelMatrix`,
`run#aSkipPrintsTheReasonTheChildGave`). A child that skips `too-wide` today writes the same verdict and the same
record; what changes is that the sentence before the verdict is a count.

## Rollback

Revert the four hunks: the row returns to `it fits one capability delta and one change (bounded files)`, the case and
its mutation record go with it. Nothing was archived, moved or renamed, and no state or record format changed, so a
revert costs one landing and leaves every earlier item's record readable.

## What this change does not do

The ten-turns-per-site figure is a **policy**, not a measurement, and it is stated as one: it is derived from the
archive's own calls-per-turn rate (1.05 recorded, 1.11 measured over 227 items) and from what a site costs a child
that has to keep its own case green. What would tune it is the record this change makes every refusal carry —
`surface: <n> sites over <m> files against <t> turns` — read across the refusals and the admissions of later runs,
which no run has yet written. Until such a reading exists, the bound is a tenth of the role's turns rather than a
figure of its own, and a later reader who finds it untuned owes that reading before defending or moving it.
