## MODIFIED Requirements

### Requirement: A review's task states what the round may run, and quotes the gate instead of running it

The task a review round is given SHALL state, before the items it judges: the inputs the round reads, the acts
it SHALL NOT perform — running the gate and running a test suite among them — the wall-clock bound it runs
under, and the shape of its answer. A green gate result SHALL be quoted from the evidence the change carries,
never reproduced. A diff review SHALL be stated to be a reading task. It SHALL state that a quoted number is
the output of a command in the change's record.

#### Scenario: The round's task names the gate and the suite it may not run

- **WHEN** the run renders the reviewer's task for an item
- **THEN** the text carries the inputs the round reads, the read-only list with the gate, the test suite and
  the network named in it, the wall-clock bound the run itself enforces on the child, the rule that a green
  gate is quoted from the evidence, and that the review is a reading task — and item 3 of the eight asks
  whether the evidence shows a real execution rather than inviting one
- **Case**: `prompt#reviewerIsBoundedToReading`

#### Scenario: A reviewer a session starts itself is handed the same task text

- **WHEN** a session that changes this project spawns its own reviewer, outside the loop
- **THEN** the project's iteration protocol names the loop's reviewer task as the text to hand over, and repeats
  the prohibitions that text carries — so the same round is bounded whether the run or a session started it
- **Case**: `prompt#iterationProtocolHandsTheReviewerTheSameSkeleton`

#### Scenario: The round's task states the rule a quoted number follows

- **WHEN** the run renders the reviewer's task for an item
- **THEN** the task states that a quoted number is the output of a command in the change's record, and that a
  number no command produced is a correction graded `NOTE:` rather than a blocking refusal unless the change's
  own contract depends on it
- **Case**: `prompt#reviewerIsBoundedToReading`

### Requirement: The worker is handed one command that answers what the change owes

A run SHALL hand the worker of an item one reading command, named by the project's configuration, that prints
the change's own numbers as one block: the paths the change touches, the checks its pathspec owes with the
command that runs each, the tallies the tree already carries — up to a per-directory limit, and, when the
block stops at that limit, the number it withheld — the project's declared case count with the command that
printed it, the gate's own timing summed from the tallies it reads, and the change's spec scenarios counted
against the cases that name them. The block SHALL say which change it measured, and SHALL NOT choose between
two open changes: it SHALL name them and measure neither. The command SHALL run none of those checks — the gate
is run once per item — and SHALL NOT hold a project value of its own: the checks come from the same call the gate
makes, so the profile stays the one place the mapping lives. Every line of the block SHALL carry one prefix, so
that the block's end is visible rather than promised.

The task SHALL also state that a fact already gathered is cited rather than gathered again: a file read once is
read from the transcript, and a measurement already taken is quoted, not re-taken. It SHALL state that a quoted
number is the output of a command in the record, cited from the block when the block carries it.

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

#### Scenario: Two open changes are named, not guessed between

- **WHEN** two changes are open under `openspec/changes/` and the worker runs the measuring command without
  naming one
- **THEN** the block names both changes and carries no scenario or case count for either, and a run that
  passes `--change <name>` prints the counts of that change alone
- **Case**: `measure#refusesToGuessWhichChangeItMeasures`

#### Scenario: A capped tally list says what it withheld

- **WHEN** the gate's tally directory holds more tally files than the block prints
- **THEN** the block prints its limit's worth of tallies and one further line carrying the number it withheld
  against the number it found
- **Case**: `measure#saysHowManyTalliesItWithheld`

#### Scenario: The block carries the suite's case count and the gate's timing

- **WHEN** the profile names a command that lists the project's test cases and the gate's tally directory holds
  tallies with their own `time`
- **THEN** the block carries the case count with the command that printed it and the gate's timing summed from
  the tallies, and a profile that names no listing command says so rather than printing no line at all
- **Case**: `measure#printsTheSuiteCountAndTheGateTiming`

#### Scenario: The task names the command and the rule that stops a re-read

- **WHEN** a run renders the worker's task for an item
- **THEN** the task names the measuring command the configuration records, the prefix the block's lines carry,
  that the block runs no check, and that a file read once is cited rather than read again
- **Case**: `prompt#workerIsHandedTheMeasureBlock`

#### Scenario: The task states the rule a quoted number follows

- **WHEN** a run renders the worker's task for an item
- **THEN** the task states that a number the change quotes is the output of a command in its record, that a
  number the block carries is cited from it, and that a number with neither is not a claim
- **Case**: `prompt#workerIsHandedTheMeasureBlock`
