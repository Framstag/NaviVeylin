# Spec Delta — agent-issue-loop

## REMOVED Requirements

### Requirement: The gate ends the child that ran it, and a fresh child writes the archive

**Reason**: the order it states is the item's own defect (issue #837). A phase that stands **after** the gate wrote the
change directory — its `design.md`/`tasks.md` when the pointer check reported them, and the delta `openspec archive`
merges into the capability spec — so the digest the gate recorded and the digest beside the round's diff differed on
exactly the writes the round was then asked to judge, and the item could only land on a hand repair (#721 round 2,
#90). Its two measured halves are kept by the successor requirement below, "The change is completed before the gate,
and a fresh child records the gate's result": the phase split and the item's spend summed over its children's streams.

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

## ADDED Requirements

### Requirement: The change is completed before the gate, and a fresh child records the gate's result

The change child SHALL end at its diff; a fresh child SHALL apply the reviewer's findings and complete the change —
cited results, the pointer check, the archive — before it runs the gate, and the child after it SHALL write only the
change's `evidence/`. A gate the item's verdict is `blocked` for, or — with no verdict — a red tally or one that says
it was not run, SHALL leave the change unarchived, and the run SHALL restore it before it commits. An item's spend
SHALL be its children's sum.

#### Scenario: The change is completed before the gate measures the tree

- **WHEN** a change child's session has ended at its diff and the reviewer's findings, if any, have been applied
- **THEN** the cited test results are copied, the mechanical pointer check is run and what it reports is fixed, the
  change is archived with the delta it merges, and only then does the gate run — so the tree the gate names is the tree
  the round after it reads, and the phase that follows the gate writes no path of the change outside its `evidence/`
- **Case**: `gate#theGateCompletesTheChangeBeforeTheGate`

#### Scenario: A red gate restores the change to its unarchived name

- **WHEN** the gate's tally carries a nonzero `failures=` or `errors=`, or its line says the gate was not run, and the
  change is under the archive's date-prefixed directory
- **THEN** the run moves it back under the unarchived name and prints the move before it commits, when the item's
  verdict is `blocked` — or when no child reported a verdict and the tally is all there is — a name the tree holds no
  archive of is refused rather than reported as restored, and a verdict that is not `blocked` moves nothing
- **AND** a red tally a closing child waived with `LOOP-GATE-WAIVED` closes the item and the archive stands, because
  the waiver is the judgement the archive was taken under and the verdict is read before the move is decided
- **Case**: `gate#aRedGateRestoresTheChangeToItsUnarchivedName`, `run#aCloseOverARedGateIsWaivedOrBlocked`

#### Scenario: The gate line ends the child that ran the gate

- **WHEN** an item's first child reports a `LOOP-GATE` line and was not ended by the run's own clocks
- **THEN** that child's session ends there, a second fresh child is started to record the gate's result, and the run
  reads the item's verdict, change, gate line and decision from it — while a first child that reports `skipped`,
  `blocked`, `falsified` or no line at all starts no second child, because there is no result to record
- **Case**: `run#childUsageIsRecordedPerItem`, `run#falsifiedSkipClosesWithItsMeasurement`

#### Scenario: The tail starts at the baseline, and the item pays for both children

- **WHEN** an item's two children each wrote an event stream
- **THEN** the item's recorded tokens, tools and cost are the sum of the two streams, and the largest context it
  records is the largest single session's — the second child's first turn is charged the project's baseline, not the
  context the first child had reached
- **Case**: `run#childUsageIsRecordedPerItem`

#### Scenario: The tail is handed the gate, the tasks and the gate's own output

- **WHEN** the second child is started
- **THEN** its prompt names the gate line the child before the gate reported, the change and the `tasks.md` it planned,
  the root cause and red test the change child established, and a bounded extract of the gate it ran — that child's own
  tool output, where the gate printed, and its closing answer after it — so it re-derives none of them and reads no
  path twice
- **Case**: `run#childUsageIsRecordedPerItem`

#### Scenario: A gate that did not pass is the tail's verdict, and the item is blocked with it

- **WHEN** the tail child finds that the gate did not pass on the change's own paths
- **THEN** the tail reports `blocked` with the gate line and the failing output it was handed, the run restores the
  change to its unarchived name from that verdict, and the item is recorded `blocked` with that verdict and the gate's
  own numbers in its reason — never as "no archived change appears in the run's diff", which is the opposite of what
  the tail reported and drops what the next attempt needs
- **Case**: `run#aRedGateIsBlockedByTheTail`

#### Scenario: The rework of a refusing round is split the same way

- **WHEN** a review round refuses a change and the repair worker the round buys reports its gate line
- **THEN** that worker ends there too and a fresh child carries its result, because a repair round that paid the tail
  in-session would cost what the item did
- **Case**: `run#aBlockingRefusalStillTakesTheRoundTwoPath`

#### Scenario: A phase's checklist is the prompt of the child that is handed the phase

- **WHEN** the phase state is printed for a phase a second process performs
- **THEN** the checklist it prints is that process's own prompt section, and the call names the file it read it
  from, rather than a section of a prompt whose child has already ended
- **Case**: `phase#oneCallPrintsThePhaseState`
