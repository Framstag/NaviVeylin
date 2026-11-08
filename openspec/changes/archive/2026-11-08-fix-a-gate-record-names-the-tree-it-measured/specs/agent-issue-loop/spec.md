## MODIFIED Requirements

### Requirement: A child pays for the full gate at most the recorded number of times

The gate SHALL leave one record of each **full run it starts**, beside the child's own event stream
(`LOOP_CHILD_EVENTS`), where it bounds the runs and the stream names a session, and SHALL count only the records that
carry the id of the session it is running in — the id the stream's last `session` line names, so the gate is charged
for this child's runs and not for a previous child's. A config that records no `LOOP_GATE_RUNS_MAX`, or records `0`,
SHALL refuse nothing; a run started with no stream, or with a stream that names no session, SHALL be refused nothing
either. A `--plan` call SHALL NOT be counted and SHALL NOT be refused: it runs no step, and it is the answer a refused
run is given.

A full run past the recorded number SHALL be refused rather than run: it SHALL exit `3`, name each earlier run with the
command it ran, name the recorded bound, print the commands the delta owes — the same list `--plan` prints — instead of
running them, and leave no record of its own, so the child is handed the scoped command and never a third full round.

That record SHALL name the tree the run measured: the commit it was taken on and a digest of the change itself. The
digest SHALL be taken over the paths the round's diff carries — the paths the run's base names and every path git does
not track, less the generated trees the review's diff leaves out (the profile's `LOOP_REVIEW_EXCLUDE_PATHSPEC`) — with
the hash of each path's working-tree content, so the same content reads as the same tree whether the change is
committed or not, and so the evidence a gate writes *after* naming its tree cannot read as a correction of it. The gate
SHALL name the same identity on its own output before its first step, and a refusal SHALL name the tree each earlier
run measured beside the command it ran. A `--plan` call measures nothing and SHALL name no tree.

#### Scenario: The third full run in one child is refused with the scoped command

- **WHEN** a child whose session the stream names has already started the number of full runs the profile records, and
  it starts one more
- **THEN** that run exits `3` without running a step, its output names the runs already made and the recorded bound,
  and prints the command the delta owes as the scoped one
- **AND** the runs before the limit measure the step the delta owes, a `--plan` call is neither refused nor recorded,
  and a run recorded under another session id is not charged to this child
- **Case**: `gate#aThirdFullRunInOneSessionIsRefused`

#### Scenario: A full run's record names the tree it measured, and a corrected tree is a different one

- **WHEN** a full run of the gate starts in a child whose session the stream names, and the tree it measured is
  corrected afterwards
- **THEN** the record left for that run carries the commit and the digest of the change it measured, and the run's
  output names the same identity before its first step
- **AND** a run made after the correction records a different digest while the records already made keep the tree
  they measured, so a gate over a tree corrected afterwards is a record a reader can tell apart
- **AND** a refusal names the tree each earlier run measured beside the command it ran
- **Case**: `gate#aThirdFullRunInOneSessionIsRefused`

#### Scenario: The gate and the round name one tree, over one path set

- **WHEN** a gate has named the tree it measured, and the identity beside the diff is derived afterwards, with the
  gate's own evidence written into the change directory between the two moments
- **THEN** the two digests are equal, so the ordinary flow is the tree the gate saw and not a corrected one
- **AND** a path outside the generated trees corrected after the gate gives a different digest
- **AND** both moments take one derivation, so neither can hold a path set of its own
- **Case**: `gate#theGateAndTheRoundNameOneTree`

### Requirement: A review's task states what the round may run, and quotes the gate instead of running it

The task a review round is given SHALL state, before the items it judges: the inputs the round reads, the acts
it SHALL NOT perform — running the gate and running a test suite among them — the wall-clock bound it runs
under, and the shape of its answer. A green gate result SHALL be quoted from the evidence the change carries,
never reproduced. A diff review SHALL be stated to be a reading task. It SHALL state that a quoted number is
the output of a command in the change's record.

It SHALL name both recorded identities item 3 compares: where the identity of the tree the diff under review was
written from is recorded beside that diff, and the line on which the gate's own record names the tree it measured.
A gate whose digest is not the diff's SHALL be stated to have gated a tree the round is not reading.

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

#### Scenario: The round compares the tree the gate measured with the tree it is reading

- **WHEN** the run hands a round the diff of a change whose evidence carries a gate result
- **THEN** an identity file beside that diff names the commit the diff was written on and the digest of the change,
  and the task names that file and the gate's `LOOP-GATE-TREE:` line, and a gate whose digest is not the diff's is
  a tree the gate never saw
- **Case**: `run#aReviewDiffNamesTheTreeItWasWrittenFrom`
