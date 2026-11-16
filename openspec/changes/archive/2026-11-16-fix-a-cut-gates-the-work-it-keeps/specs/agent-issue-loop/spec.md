# agent-issue-loop delta

## ADDED Requirements

### Requirement: A cut gates the work it keeps, before the item ends

When the run records an item `cut` and the kept patch of the work its child left carries a file, the run SHALL run the
profile's gate command (`LOOP_GATE_CMD`) in the change root, on the tree exactly as the ended child left it, before the
item's record is written, and SHALL record the result beside the cut as `cutGate: {result, log}` — `passed`, `failed`
or `bound` for the status the gate answered, with its log at `<item dir>/cut-gate.log`. The note the item's `resume
#<item>` action carries SHALL say which result it was, so the caller's action names a measured tree instead of a patch
nobody has run anything against.

The item SHALL still end `cut`, SHALL keep the bound as its `stopReason`, and SHALL record no decision label and no
closure: a gate result is not a verdict, so the ceiling that keeps an item finite is untouched and incomplete work is
never manufactured into `blocked`. The gate SHALL NOT start once the run's own deadline has passed, SHALL NOT start
over a kept patch that carries no file, and SHALL NOT start where the change root does not carry the gate the profile
names; an item with none of those carries `cutGate: null` and no result is claimed for it. The gate's own clock SHALL be
`LOOP_GATE_STEP_MAX_SECONDS` or the time the run has left before its deadline, whichever is shorter.

#### Scenario: The gate runs on the tree the cut left, and its result is recorded beside the cut

- **WHEN** a run's own bound ends an item's child, the kept patch carries the files the child wrote, the run's deadline
  has not passed and the change root carries the gate the profile names
- **THEN** that gate is run in the change root on the tree the cut left — the files the ended children wrote are there
  when it runs — the item's record carries `cutGate.result: passed` and a `cutGate.log` inside the item's own directory,
  its note says the gate ran on the work it kept and what it answered, and the item is still recorded `cut` with the
  bound as its mechanism, on the one continuation the ceiling allows and with no third child started
- **Case**: `run#aCutRunsItsGateOnTheKeptWork`

#### Scenario: A cut that kept no file records no gate result

- **WHEN** the run's own bound ends an item's child before it wrote anything, so the kept patch carries the preamble
  and no hunk
- **THEN** no gate is run on that tree, the item's record carries `cutGate: null` rather than a result, and the item is
  still recorded `cut` with the note that says why it was not continued
- **Case**: `run#aCutWhosePatchDoesNotApplyIsLeftCut`
