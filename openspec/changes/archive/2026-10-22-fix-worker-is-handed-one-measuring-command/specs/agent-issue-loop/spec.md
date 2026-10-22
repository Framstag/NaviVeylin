# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: The worker is handed one command that answers what the change owes

A run SHALL hand the worker of an item one reading command, named by the project's configuration, that prints
the change's own numbers as one block: the paths the change touches, the checks its pathspec owes with the
command that runs each, the tallies the tree already carries, and the change's spec scenarios counted against
the cases that name them. The command SHALL run none of those checks — the gate is run once per item — and
SHALL NOT hold a project value of its own: the checks come from the same call the gate makes, so the profile
stays the one place the mapping lives. Every line of the block SHALL carry one prefix, so that the block's end
is visible rather than promised.

The task SHALL also state that a fact already gathered is cited rather than gathered again: a file read once
is read from the transcript, and a measurement already taken is quoted, not re-taken.

#### Scenario: The block names every check the pathspec owes

- **WHEN** a change touches two paths that the profile's suite map gates differently and the worker runs the
  measuring command once
- **THEN** one block is printed, every line of it carries the `LOOP-MEASURE` prefix, the block names both
  pathspec paths and both owed checks with the command that runs each, and no check has been executed
- **Case**: `measure#namesTheOwedChecksInOneBlock`

#### Scenario: The block carries the numbers a review asks for

- **WHEN** a change carries a tally in the gate's directory, a tally in its own `evidence/` directory, a delta
  whose three scenarios name two cases, and a `surface:` line in its proposal
- **THEN** the block carries each tally with its own `tests=`/`failures=`, the scenario and case counts with the
  number that name no case, and the proposal's `surface:` record quoted
- **Case**: `measure#printsTheNumbersTheReviewerAsksFor`

#### Scenario: The task names the command and the rule that stops a re-read

- **WHEN** a run renders the worker's task for an item
- **THEN** the task names the measuring command the configuration records, the prefix the block's lines carry,
  that the block runs no check, and that a file read once is cited rather than read again
- **Case**: `prompt#workerIsHandedTheMeasureBlock`
