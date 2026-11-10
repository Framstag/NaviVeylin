# Spec Delta — agent-issue-loop

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
not track, less the generated trees the review's diff leaves out (the profile's `LOOP_REVIEW_EXCLUDE_PATHSPEC`) — and
over the change's own files as the working tree holds them, tracked or not, under the one name the change has before
it is archived: a path under the profile's `LOOP_CHANGE_SOURCE_DIR` and its `archive/` SHALL read as
`<LOOP_CHANGE_SOURCE_DIR>/<name>/…` with the date prefix the profile records as `LOOP_CHANGE_ARCHIVE_PREFIX` removed,
and the same exclude SHALL leave the change's generated trees out of both halves. Each path SHALL be hashed by its
working-tree content, so the same content reads as the same tree whether the change is committed or not, so a tree
written into a change directory after the gate is not the tree the gate measured, and so the evidence a gate writes
after naming its tree cannot read as a correction of it. The gate SHALL name the same identity on its own output before
its first step, and a refusal SHALL name the tree each earlier run measured beside the command it ran. A `--plan` call
measures nothing and SHALL name no tree.

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

#### Scenario: A change's own files name one tree, archived or not

- **WHEN** a change's own files are in the tree under the unarchived name when a gate names the tree it measured, and
  that change is moved under the archive's date-prefixed directory name before the identity beside a round's diff is
  derived
- **THEN** the two digests are equal, so the archive's move of a change nobody corrected is not a tree the gate never
  saw, whether the move is committed or still uncommitted
- **AND** a non-evidence file of that change rewritten after the gate gives a different digest, so a change directory
  repaired after the gate is still a tree the reviewer's third item can see
- **AND** a file written into the change's `evidence/` after the gate gives the same digest
- **Case**: `gate#aMovedChangeNamesTheSameTree`
