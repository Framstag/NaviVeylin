## MODIFIED Requirements

### Requirement: The bound on a child is priced in what it spends, not in how many turns it takes

The run SHALL bound a child in what a turn costs, not in how many turns it takes: it SHALL read the largest context the child's **own** lines of its stream reported (`input + cacheRead`, or a compaction's `tokensBefore`, whichever is greater) — the lines standing before it started are the baseline, because every child of an item appends to one stream — in the wait that enforces the clock and the turn count, SHALL print one checkpoint line at `LOOP_ITEM_CONTEXT_WARN`, and SHALL end it and everything it started at the bound the child's **role** records, `LOOP_ITEM_CONTEXT_MAX_<ROLE>`, falling back to `LOOP_ITEM_CONTEXT_MAX` where a role records none. Both keys SHALL be recorded values named in the run's header; `0` or no key is no bound. An item this bound ends SHALL be recorded `cut` with `bound` as its stop reason, exactly as every other bound the run enforces ends one (issue #790) — and SHALL first be continued by the run that cut it: that run SHALL apply the work it kept onto the branch's `HEAD` and start exactly **one** continuation child from it, whose answer decides the item. A second cut of the same item, and a kept patch the applier does not take, SHALL leave the item `cut`, with the non-apply recorded and no continuation started.

#### Scenario: A child whose context crosses the recorded bound is ended and named

- **WHEN** a child's stream reports a growing context — 120 000, 155 000, then 210 000 tokens — while
  `LOOP_ITEM_CONTEXT_MAX` records 200 000 and `LOOP_ITEM_CONTEXT_WARN` records 143 124, and the wall clock still has
  45 min to run
- **THEN** the run prints the checkpoint once, naming the recorded 143 124, ends the child's process group, writes
  `LOOP-TIMEOUT: the child reached the recorded context bound of 200000 tokens (LOOP_ITEM_CONTEXT_MAX) at 210000
  tokens — the run ended it and everything it started` into the child's transcript with the wall clock's own exit
  status, records the item `cut` with `bound` as its stop reason and that line as its note, and closes nothing — after
  it has applied the work the cut child left and started one continuation child, which this case's fixture cuts at the
  same bound, so the second cut is the item's `cut` and no third child starts
- **Case**: `run#childPastItsContextBoundIsCut`

#### Scenario: A worker the context bound ended is continued by the run that cut it

- **WHEN** a worker is ended at the recorded context bound having left its change in the tree, and the run's own
  deadline has not passed
- **THEN** the run puts the tree back on the branch's `HEAD`, applies the kept patch onto it, records the resume as
  `applied`, renders and starts exactly one continuation child on the work package plus the patch, records its step as
  `worker-continuation`, and the item's outcome is that child's answer with the continuation sentence in its note
- **Case**: `run#aContextCutWorkerIsContinuedByItsOwnRun`

#### Scenario: A cut whose kept work does not apply stays cut

- **WHEN** the work a cut child left is a patch the applier does not take — a child the bound ended before it wrote
  anything leaves a patch with no hunk in it
- **THEN** the run starts no continuation, records the item `cut` with `bound` as its stop reason, records the
  applier's `no-apply` answer with the item, and says on its own output that the work does not apply onto the branch's
  `HEAD`
- **Case**: `run#aCutWhosePatchDoesNotApplyIsLeftCut`

#### Scenario: A reading phase is bounded by its own key

Measured 2026-11-10 over the day's three runs: 19 items attempted, 9 recorded `cut`, 8 of them at a largest context of
199 178–211 475 against the recorded 200 000, while the six that closed read 114 522–192 886 — the stop sat inside the
population that finishes, and every one of the eight belonged to a role whose work is reading the item.

- **WHEN** the item's key records 160 000, the reviewer's own key records 250 000, a worker's stream reports 150 000 and
  a review round's stream reports 200 000, and a second run's review round reports 260 000
- **THEN** the header names every role's figure (`item-context-bounds=…`), the round that read 200 000 answers and the
  item keeps its outcome, and the round that read 260 000 is ended with a transcript naming
  `LOOP_ITEM_CONTEXT_MAX_REVIEWER` and recorded `cut`
- **Case**: `run#eachRoleIsBoundedByItsOwnContext`

#### Scenario: A recorded zero keeps the context bound off

- **WHEN** the profile records `LOOP_ITEM_CONTEXT_MAX=0` and `LOOP_ITEM_CONTEXT_WARN=0` and a child's stream reports a
  210 000-token context before it outlives a two-second wall clock
- **THEN** the header names the bound as off (`item-context-max=0`), no checkpoint line is printed, the transcript
  carries only the wall clock's `LOOP-TIMEOUT` and names no context bound, and the item is recorded `cut`
- **Case**: `run#theRecordedZeroKeepsTheContextBoundOff`
