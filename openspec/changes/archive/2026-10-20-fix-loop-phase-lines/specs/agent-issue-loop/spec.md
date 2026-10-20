# Spec Delta — agent-issue-loop

## ADDED Requirements

### Requirement: The run names its phases and the live view into it

A run SHALL print one line wherever it crosses a boundary it can observe itself, and its header SHALL name the
step that runs next and the view a person can use meanwhile. A phase line is terminal output only: it is never
read back as a field, so a run that printed them and one that did not record the same state and print the same
report.

#### Scenario: The header names the next step and the live view

- **WHEN** a run starts and has resolved its state path
- **THEN** the console carries a line naming the pre-screen as the step that runs next and saying that it does
  not return at once, the state directory, and the command `bash tools/loop/state.sh show` that shows what the
  run is doing — so an operator does not have to remember either the path or the command to tell a working run
  from a stopped one
- **Case**: `run#headerNamesTheLiveView`

#### Scenario: The pre-screen is announced and reported before it returns

- **WHEN** the run calls the pre-screen, whose whole answer used to be captured by a command substitution and
  replayed after it returned
- **THEN** one line is printed before the call and one after it carrying the exit status
- **AND** the pre-screen's own output reaches the console while the call runs, because it is streamed rather
  than replayed, and the same bytes are kept in the state directory for a path that reads them afterwards
- **AND** the status the run decides on is still the pre-screen's own, not the stream's
- **Case**: `run#prescreenAnnouncesItselfBeforeItReturns`

#### Scenario: Every boundary outside a child prints one line in one format

- **WHEN** the run crosses a boundary that is not a child process — the pre-screen, the worktree, the picked
  item, the gate's evidence, the item's commit
- **THEN** it prints one line per crossing in the format `loop: <phase> · <elapsed> · <what>`, where the
  elapsed time is counted from the run's own start, and the item's line names the report file a person can read
  while the item is worked
- **AND** none of these lines is written to the state file, parsed back into a field, or carried into the
  report's text, so a heartbeated run and a quiet one are the same run
- **Case**: `run#everyPhaseBoundaryIsPrinted`
