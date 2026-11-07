# Spec Delta — agent-issue-loop

## MODIFIED Requirements

### Requirement: An item the run's own bound cut is recorded `cut`, not `blocked`

An item a bound the run enforces ended SHALL be recorded `cut`: the run's deadline or a role's turn bound
ending a review round, and the role's turn bound, the context bound, the child clock, a vanished writer or the run's deadline ending the item's own child.
It SHALL carry `bound` as its stop reason when the bound ended the item's own child, while a round the run ended
carries none, as it did before — so the mechanism's spend is totalled beside the result — SHALL write no
decision label and no comment, SHALL NOT enter the two-blocked streak `state.sh next` stops on, and SHALL be named on
a line of its own in the report and counted there as a cut rather than a block — the line and the tally read the
item's `result` alone, so no reader needs the mechanism's vocabulary to tell work from a refusal. The work an ended
child left SHALL stay where the child left it, SHALL be named in the item's own record, and SHALL NOT be committed.
The round a review bound ended SHALL be written on the branch as a cut with its announcement withdrawn. A state a
tree wrote before `cut` carried a bound keeps that `blocked` + `bound` pair out of the streak, while a pre-mechanism
pair with no stop reason still stops the run, and an answer the *transport* cut twice is not this case: no bound
ended it, so it stays a block with its reason.

#### Scenario: A round the turn bound ended leaves no decision behind

- **WHEN** an item's tail writes `LOOP-VERDICT: closed`, its gate answered and its change is archived, and the run
  then ends review round 1 at the reviewer's role's turn bound
- **THEN** the item is recorded `cut` with its round marked cut, no `agent/needs-decision` label is written for it
  and the report's decision list does not hold it, the branch carries `review-round-1-cut.md` and no
  `review-round-1-requested.md`, and `branch-state` reads `adoptable` — work the next run finishes, not a question
  only its owner can answer
- **Case**: `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`

#### Scenario: A child the turn bound ended is recorded cut, not blocked

- **WHEN** an item's own child reaches its role's recorded turn bound and the run ends it there
- **THEN** the item is recorded `cut` with `bound` as its stop reason and the count in its note, the report counts it
  as a cut and not as a block and names it on the run's own bound line, no `agent/needs-decision` label and no
  retry-or-abandon comment is written for it, and nothing is closed
- **Case**: `run#childPastItsTurnBoundIsCut`

#### Scenario: A child the item's own wall clock ended is recorded cut, not blocked

- **WHEN** the wall-clock cap ends an item's worker that has left changes in the change root
- **THEN** the item is recorded `cut` with `bound` as its stop reason, the run commits none of the work — the change
  root's `HEAD` is still the base commit — the tree still carries what the child left, the item's note names the
  patch it was written to and says the attempt is the next run's to resume, and no decision label is written for it
- **Case**: `run#childOutlastingTheWallClockIsCut`, `run#treeOfAChildTheCapEndedIsNotCommitted`

#### Scenario: A child the run's own deadline ended is recorded cut

- **WHEN** the run's deadline passes while an item's child runs and the run ends it there
- **THEN** the item is recorded `cut` with `bound` as its stop reason and the `LOOP-TIMEOUT` transcript it left, no
  decision label is written for it, and the run records the deadline as its own stop reason
- **Case**: `run#childIsEndedAtTheRunsDeadline`

#### Scenario: Two consecutive cuts do not stop the run

- **WHEN** two items in a row are recorded `cut` and the queue still holds a candidate
- **THEN** `state.sh next` hands out that candidate, a block that follows a cut is the first block of its own pair,
  and two blocks in a row still stop the run
- **Case**: `state#nextHonoursEveryStopCondition`

#### Scenario: A block at a bound holds no pair

- **WHEN** two items in a row carry a bound — recorded `blocked` with `bound` as their stop reason by a state a tree
  wrote before `cut`, or `cut` with `bound` by this run — and the queue still holds a candidate
- **THEN** `state.sh next` hands out that candidate, because what the pair is read for is a refusal's prediction
  about the next item — the same gate, the same provider, the same tree — and a bound predicts nothing about an
  item that starts a fresh worker on a fresh tree; a refusal after a bound cuts is the first block of its own pair,
  and a state written before the mechanism existed, whose two `blocked` items carry no stop reason, still stops the
  run as it always did
- **Case**: `state#aBlockAtTheRunsBoundIsNoRefusal`

### Requirement: A block records the mechanism that stopped it

An item recorded `blocked` SHALL carry the mechanism that stopped it, from a closed vocabulary — `refused` when a
review round refused the change, `unmergeable` when the base would not take the branch, `bound` when the run ended a
child at a bound it enforces — and a block none caused SHALL carry none. `bound` is also the one mechanism an item
recorded `cut` carries, because the same bound is the phase the next run resumes; every other value SHALL be a usage
error before anything is written, so a mechanism's spend is totalled without the note's prose.

#### Scenario: The refusing round is named

- **WHEN** the last review round at the cap refuses the change and the item is recorded blocked
- **THEN** the item's record carries `refused` as its stop reason, beside the note that names the round
- **Case**: `run#reviewRoundsCapAtTwo`

#### Scenario: The base's refusal is named

- **WHEN** the run cannot merge the item's branch onto the base and the item is recorded blocked
- **THEN** the item's record carries `unmergeable`, beside the note that names the branch
- **Case**: `run#doesNotCloseOnAMergeTheBaseCannotTake`

#### Scenario: The tree's refusal of an adoption is named on the exclusion

- **WHEN** the run records an adoption's refusal on an item the pre-screen excluded, and is asked to record one
  for an issue that is not one of the run's exclusions
- **THEN** the exclusion carries `unmergeable` beside the refusal's own text while its verdict, its reason and the
  branch stay as the pre-screen read them, and the second request exits with the refusal naming that the issue is
  not one exclusion of this run and leaves the state unwritten — so the mechanism's second home is the exclusion
  and not a free field
- **Case**: `state#anAdoptionRefusalIsRecordedBesideTheExclusion`

#### Scenario: A block the run's own bound caused is named as one

- **WHEN** a state a tree wrote before this change records an item `blocked` with `bound` as its stop reason
- **THEN** the item keeps `bound` beside its `blocked` result, and `state.sh next` keeps the pair out of the streak
  it stops on, because the mechanism says the run's own bound ended the phase — the same fact this run records
  with `cut`
- **Case**: `state#aBlockAtTheRunsBoundIsNoRefusal`

#### Scenario: A bound the run ended a phase with is named beside the result

- **WHEN** a child of the item reaches its role's turn bound, outlives the child clock or is ended by the run's
  deadline, and the run ends it
- **THEN** the item is recorded `cut` with `bound` as its stop reason, its note says the attempt is left for the next
  run rather than asked about, and no decision label is written for it — the item is work the next run resumes, so it
  spends no decision slot and stays eligible
- **Case**: `run#childPastItsTurnBoundIsCut`

#### Scenario: A mechanism outside the vocabulary is refused

- **WHEN** a stop reason is given that is neither `refused` nor `unmergeable` nor `bound`, or `refused` or
  `unmergeable` is given for a result that is not a block, or `bound` is given for a result that is neither a block
  nor a cut
- **THEN** the record is refused as a usage error and the state is not written, so the field cannot turn into a
  second prose channel
- **Case**: `state#aBlockedItemNamesTheMechanismThatStoppedIt`

#### Scenario: The report tables the mechanism beside the spend

- **WHEN** the run's report prints its per-item table
- **THEN** each row carries the mechanism its block stopped on, or `-` when it carries none, in a column of its
  own, so a mechanism's spend is totalled from the table
- **Case**: `report#wallTimeAndReasonInTheTable`

### Requirement: A child the run ended itself leaves no commit

The run SHALL commit an item's tree only after the child that produced it has finished by its own exit. A child the
run ended — the wall-clock cap's status, or the `LOOP-TIMEOUT` line its transcript carries — SHALL leave the branch as
it stood: the item is recorded `cut` with that transcript, and the tree is kept exactly as the child left it, because
nothing is discarded unrecorded: `--reset-worktree` discards it, and so does this run before it starts its next item,
the patch having been written first. It holds for the rework worker a refusing review round starts as well. The tree
that child left SHALL be readable without the tree itself: before the run moves to its next item, the changes it left
— the tracked diff and each untracked file — SHALL be written to `<item dir>/leftover-from-killed-run.patch`, and the
transcript the ended child leaves SHALL carry a `LOOP-LEFTOVERS:` line naming that file. The item's own state record
SHALL name that file as well, so the report can point a caller at the work an ended child left (issue #674), and SHALL
carry no decision label, because the clock that ended it is the run's budget rather than a finding.

#### Scenario: A worker the cap ends leaves the branch at the base

- **WHEN** the wall-clock cap ends an item's worker and that worker has left changes in the change root
- **THEN** the run commits none of them — the change root's `HEAD` is still the base commit — the item is recorded
  `cut` with its `LOOP-TIMEOUT` transcript, the tree still carries what the child left (kept, not discarded), and the
  run's own line says the commit was skipped
- **Case**: `run#treeOfAChildTheCapEndedIsNotCommitted`

#### Scenario: The cap ends the rework worker of a refusing review round

- **WHEN** a review round refuses the change and the wall-clock cap ends the rework worker that round starts, after
  that worker has written into the change root
- **THEN** the run commits none of it — the branch carries only what the item's own worker had already earned — the
  item is recorded `cut` with the round's `LOOP-TIMEOUT` transcript, the tree still carries what that worker left, and
  the note names the tree it did not commit
- **Case**: `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`

#### Scenario: The changes a cap-ended child left are kept as a patch

- **WHEN** the wall-clock cap ends an item's worker that has left modifications and an untracked file in the tree
- **THEN** `<item dir>/leftover-from-killed-run.patch` exists, carries the worker's modifications (staged and
  unstaged, against `HEAD`) and that untracked file's content, the child's transcript carries a `LOOP-LEFTOVERS:`
  line naming it, and the item's record names it beside the cut
- **Case**: `run#treeOfACapEndedChildIsKeptAsAPatch`

#### Scenario: The item the run's own deadline cut is not a decision

- **WHEN** the run's deadline passes while an item's child is running, and the run ends that child
- **THEN** the item is recorded `cut` with `bound` as its stop reason and the `LOOP-TIMEOUT`/`LOOP-DEADLINE` lines of
  its transcript and its tree uncommitted, no decision label is written for it, and the report's decision list does
  not hold it — the item is left for the next run
- **Case**: `run#childIsEndedAtTheRunsDeadline`

#### Scenario: A reviewer the run's deadline cut is not a refusal

The deadline is consulted while a child runs, and a reviewer is a child: its transcript carried the same
`LOOP-DEADLINE` line as the worker's, and no part of the run read it — so a round the clock ended was read as one
the reviewer never answered, which bought a rework worker for a change nobody refused (the cut-short retry could not
fire: `run_child` writes `LOOP-TIMEOUT` beside `LOOP-DEADLINE`, and the retry condition read that line), and the
item was escalated with the other blocks.

- **WHEN** the run's deadline passes while the reviewer of a review round is running, and the run ends that child
- **THEN** the round is recorded as cut rather than refused — the transcript's `LOOP-DEADLINE` line is read, no
  retry and no rework worker is started for it, and the item is recorded `cut` with no decision label and no
  entry in the report's decision list, exactly as the item whose worker the same clock ended
- **Case**: `run#reviewerEndedAtTheRunsDeadlineIsNotADecision`

### Requirement: One command starts a budgeted run, with no dialogue

A run SHALL be started by one command whose budget is supplied as arguments and recorded, and SHALL
require no interactive answer, so that the same run can be repeated and audited.

#### Scenario: The budget is arguments, not a prompt

- **WHEN** a run is started
- **THEN** the item cap, the time budget, whether a review is taken and whether closure is allowed are
  arguments whose effective values are recorded and printed in the run's header

#### Scenario: The run stops on its own authority

- **WHEN** the item cap is reached, the time budget is spent, the queue is empty, two items in a row were
  blocked, the remaining time cannot pay for another item, or the host has less available memory than the
  recorded floor
- **THEN** the run stops without needing a further decision

#### Scenario: The floor the run refuses by is what its own items cost

A constant floor could not tell a 100-minute item (#126, 2026-10-19) from a 12-minute one, so it admitted the
first with 45 minutes left and refused 28 minutes of cheap work after it (issue #206). One constant for every
item made a run of 10-15 min skips stop up to ~50 min before its deadline (issue #674), so the floor follows the
cheapest item the run has measured.

- **WHEN** the remaining time is compared against the floor, and the run has finished at least one item whose
  wall time it recorded (`done --wall SECONDS`)
- **THEN** the floor is derived from the **cheapest** recorded wall time — its minutes times the 1.5 spread the
  measured costs show, rounded up to the whole minute — and is not raised to `LOOP_ITEM_FLOOR_MINUTES`, so a
  cheap tail is admitted and a single expensive item among cheap ones does not refuse it; the refusal names the
  floor, the cheapest wall time, how many finished items it came from and the configured minimum, e.g. `only 28
  min left, below the 90 min floor for the next item (cheapest 60 min over 3 finished items, configured minimum
  65)`
- **Case**: `state#floorIsDerivedFromTheFinishedItemsWallTimes`

#### Scenario: A run with no measurement keeps the configured minimum

- **WHEN** no finished item of the run records a wall time — the run has just started, or every item so far
  was recorded without `--wall`
- **THEN** the floor is the configured `LOOP_ITEM_FLOOR_MINUTES`, and the refusal names that value together
  with the reason no measurement was used, rather than implying a measurement it does not have
- **Case**: `state#floorWithNoHistoryIsTheConfiguredMinimum`

#### Scenario: A child that outlasts the configured wall-clock is ended and recorded

The budget is consulted between items, so a child process is the one place a spent budget could not be
noticed: the run waited for it with no limit, and a gate that never returns could spend the whole run inside
one wait. The bound is not one constant for every item: what a child is given follows what its own
item's gate costs, which a profile records as a number, and an item was ended 8 s after its green
gate had been written for want of that number (issue #663).

- **WHEN** an agent process runs longer than the child wall-clock the run gives it — the larger of the recorded
  floor (`LOOP_CHILD_MAX_SECONDS`, default 2700 s = 45 min) and what one item's gate owes
  (`LOOP_ITEM_GATE_SECONDS`, which a project without a gate leaves out)
- **THEN** the run ends that process and every process it started, appends one machine-readable line to the
  report it was writing (`LOOP-TIMEOUT: the child exceeded <n> s …`), and returns a status that is not the
  child's own, so the item is recorded `cut` with `bound` as its stop reason and that transcript, and the run
  goes on to its next stop condition instead of spending its clock in one wait
- **Case**: `run#childOutlastingTheWallClockIsCut`

#### Scenario: A child that never started is the run's fault, not the item's

A status alone cannot tell a child whose argv the environment refused from a child that worked the
item and failed: on 2026-10-20 both children of that run died in under 4 s with the same 35-byte complaint,
and the run recorded two items `blocked`, escalated both with `agent/needs-decision` and `status/on-hold`, and
stopped on "two consecutive items ended blocked" 3 minutes into an 8 h budget — two questions nobody had asked, and two
of the run's three decision slots (issue #212). The status is not the test, because a provider refuses a call
without touching the child's argv: that shape ends with a zero status and an answer of nothing (issue #742).

- **WHEN** an agent process ends with a non-zero status, wrote no verdict line, was not ended by the run's own
  wall-clock, and left the item's tree at the run's base commit
- **THEN** the run records that once as its own stop reason, naming the child's own last line and the size of the
  answer it left, and stops without escalating an item: no item record is written for it, no decision label and
  no hold reach it, and it is left as its owner set it — `status/open` and no `agent/needs-decision` — because no
  part of it was read
- **Case**: `run#childThatNeverStartedIsTheRunsFault`

#### Scenario: A child whose answer arrived empty is the run's fault too

A provider that refuses every request leaves a child that exits 0 with nothing to say, and the run read that as
success: measured 2026-11-01T23:30:01Z (issue #742), two items of one run each left a 0-byte report and had every
`turn_end` event on `stopReason: error`, and each was recorded `blocked` on "reported success but the tree is
still at the base commit" and escalated — two decision slots no reviewer and no worker asked for, and the run
stopped on two consecutive blocked items 121 s into a 360-minute budget.

- **WHEN** an agent process ends with a zero status, wrote no verdict line, was not ended by the run's own
  wall-clock or its turn bound, and left the item's tree at the run's base commit
- **THEN** the run records that once as its own stop reason, naming the status, the size of the answer it left and
  the child's own last line, and stops without escalating an item: no item record is written for it, no decision
  label and no hold reach it, and it is left as its owner set it — `status/open` and no `agent/needs-decision` —
  because no part of it was read
- **Case**: `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault`

#### Scenario: A run is recorded

- **WHEN** a run ends
- **THEN** a report exists naming each item, its verdict, its wall time, its spend, its review rounds and what
  it closed, so the run justifies itself with numbers — every item's row carries the wall time the run recorded
  for it, the tools its children called with their count, the tokens they used with their cost and the largest
  context they reached, and, when the item's result is not a closure, the reason the item recorded, and the
  columns the report already had keep their name and their order
- **Case**: `report#wallTimeAndReasonInTheTable`

#### Scenario: An item's spend is read from the child's own stream

The agent reports its tool calls, its tokens and its cost in its event stream, and the loop used to start it in
a mode that discards the stream, so the one measurement of spend that exists was never read (issue #211).

- **WHEN** a child's process produced an event stream and the item is recorded with it
- **THEN** the item's metrics hold the count of each tool its children called, the tokens they used summed over
  their responses (each response counted once, at its last usage record, not once per streamed update), what
  those tokens cost, and the largest context any of them reached — the biggest input-plus-cache-read of a
  response, or a compaction's `tokensBefore` when the context was compacted — and the row the report prints
  shows them in the `tools`, `tokens` and `context` columns
- **Case**: `run#childUsageIsRecordedPerItem`

#### Scenario: Nothing reported is printed as zero

A provider that streams no usage leaves zeros behind, and a zero in a column of numbers reads as a measurement
that was made.

- **WHEN** an item's stream holds no usage record, holds only zeroed ones, or no stream was written at all —
  a project whose agent command prints plain text is the third case
- **THEN** the report prints `-` in the `tools`, `tokens` and `context` columns rather than `0`, the item is
  worked and recorded exactly as before, and nothing about the run depends on the stream existing
- **Case**: `report#zeroUsagePrintsDashNotZero`, `run#plainTextChildStillReports`

#### Scenario: The report names the actions the caller owes

The report is read instead of the tracker, so a list of ids is not an answer: a decision without its question,
a branch without its owner, a gate without its log and a kept worktree without its path send the caller back
to `gh` to reconstruct what the run already knew (issue #208). A branch is named here by what it **carries**:
the loop lands an item's work as a commit of its own, so a branch whose patches the base already holds has
nothing to land (issue #228).

- **WHEN** a run ends holding a decision it escalated, a branch that carries a patch the run's base does not
  have, a kept worktree, an item whose gate never completed, a citation the pre-screen refused, or the work an
  ended child left as a patch
- **THEN** the report prints one named action for each: the decision with the question the run wrote beside
  it — or, where the state does not hold that text, where the question is (`gh issue view <n>`) rather than an
  invented one — the branch with the patches it carries and the item that owns it, the kept worktree with its
  path, the item's transcript for the gate that never completed, the repair command for the refused
  citation, and the recovery line naming the patch an ended child's work was kept in (issue #674)
- **Case**: `report#actionsNameDecisionAndBranch`

#### Scenario: A branch whose patches are in the base is not an action

- **WHEN** a run ends and one of the item branches it sees carries no patch the base does not have — every one
  of its commits was landed, or re-landed, as a commit of the loop's own
- **THEN** the action list does not name that branch, and its `none` line is printed when nothing else is owed,
  so a leftover cannot be read as work a caller has to land
- **Case**: `report#landedBranchIsNoAction`

#### Scenario: An action list is empty only when the state says so

- **WHEN** no decision, no branch carrying an unlanded patch, no kept worktree, no incomplete gate, no refused
  citation and no leftover patch is recorded — or when the state names any one of them
- **THEN** the report prints the action list heading with `none` and what that covers in the first case, and
  never prints `none` in the second, so an empty list cannot hide a branch that carries work
- **Case**: `report#emptyRunSaysNone`

#### Scenario: The host a run works on is read and named

The budget had no term for the machine: on 2026-10-19 the same 5 680 tests took 3m47s on a quiet host and 30
minutes inside one Gradle task at 194 MB free and 18 GB of swap, so the run's `wallSeconds` measured the
desktop session (issue #209).

- **WHEN** a run is started, and the host publishes the memory it has available in the file the configured
  key names
- **THEN** the run's header prints the available memory in MiB beside the budget and beside the floor it is
  compared against, so a run's numbers can be read against the host it ran on
- **Case**: `run#hostAboveTheFloorRunsTheItem`

#### Scenario: A host below the floor is reported instead of spent

- **WHEN** the available memory read before an item's first agent process is started is below the recorded
  floor (`LOOP_MIN_FREE_MIB`, default 1024 MiB)
- **THEN** no agent process is started and no branch is created for the item, the item is recorded `blocked`
  with the measurement and the floor it was compared against, and the run stops with that shortfall as its
  stop reason — a run on a starved host cannot become productive by taking the next item either
- **Case**: `run#hostBelowTheFloorBlocksTheItemAndStops`

#### Scenario: A host that publishes no reading is not refused

A check that cannot be made is not a refusal: the engine has to run in a container without procfs, or on a
host that keeps the number somewhere else.

- **WHEN** the file the configured key names does not exist, or carries no `MemAvailable` value
- **THEN** the check passes, the item's child is started as it always was, and the header says the reading is
  unavailable instead of inventing a number
- **Case**: `run#hostWithoutTheReadingPasses`

#### Scenario: The bound a child is given is what one item's own gate costs

Measured 2026-10-26 (issue #663) on a `tools/loop/**` item: gate round 1 (red) 957 s, one focused falsification
suite 233 s, gate round 2 (green) 972 s and 542 s of reading, edit, evidence, archive and report — 2 704 s
against the 2 700 s the constant allowed, so the run ended the child 8 s after its green `gate-summary.xml` was
written; two more items ended the same way the same day with their work on disk.

- **WHEN** a child is started and the profile records what one item's gate owes (`LOOP_ITEM_GATE_SECONDS`)
- **THEN** the run gives the child the larger of that number and `LOOP_CHILD_MAX_SECONDS`, the header names the
  bound it gave, and a `LOOP-TIMEOUT` line names the same number — and a profile that records no gate cost keeps
  the floor
- **Case**: `run#theChildCapIsNeverBelowWhatOneItemsGateOwes`

### Requirement: The bound on a child is priced in what it spends, not in how many turns it takes

The run SHALL bound a child in what a turn costs, not in how many turns it takes: it SHALL read the largest context the child's stream reported (`input + cacheRead`, or a compaction's `tokensBefore`, whichever is greater) in the wait that enforces the clock and the turn count, SHALL print one checkpoint line at `LOOP_ITEM_CONTEXT_WARN`, and SHALL end it and everything it started at `LOOP_ITEM_CONTEXT_MAX`. Both keys SHALL be recorded values named in the run's header; `0` or no key is no bound. An item this bound ends SHALL be recorded `cut` with `bound` as its stop reason, exactly as every other bound the run enforces ends one (issue #790): the phase is the next run's to resume, not a finding about the item.

#### Scenario: A child whose context crosses the recorded bound is ended and named

- **WHEN** a child's stream reports a growing context — 120 000, 155 000, then 210 000 tokens — while
  `LOOP_ITEM_CONTEXT_MAX` records 200 000 and `LOOP_ITEM_CONTEXT_WARN` records 143 124, and the wall clock still has
  45 min to run
- **THEN** the run prints the checkpoint once, naming the recorded 143 124, ends the child's process group, writes
  `LOOP-TIMEOUT: the child reached the recorded context bound of 200000 tokens (LOOP_ITEM_CONTEXT_MAX) at 210000
  tokens — the run ended it and everything it started` into the child's transcript with the wall clock's own exit
  status, records the item `cut` with `bound` as its stop reason and that line as its note, and closes nothing
- **Case**: `run#childPastItsContextBoundIsCut`

#### Scenario: A recorded zero keeps the context bound off

- **WHEN** the profile records `LOOP_ITEM_CONTEXT_MAX=0` and `LOOP_ITEM_CONTEXT_WARN=0` and a child's stream reports a
  210 000-token context before it outlives a two-second wall clock
- **THEN** the header names the bound as off (`item-context-max=0`), no checkpoint line is printed, the transcript
  carries only the wall clock's `LOOP-TIMEOUT`, and the item is recorded `cut` with `bound` as its stop reason
  exactly as a child the recorded wall clock ended
- **Case**: `run#theRecordedZeroKeepsTheContextBoundOff`

### Requirement: A wait ends when the writer of the record it waits for is gone

The wait for a child's completion record SHALL read, beside the clocks and the spend bounds it already enforces, whether the subshell that writes that record is still running, and SHALL end the wait when it is not: the record is written by the run's own subshell, so its pid is the only reading that tells a child still working from a child whose writer was killed before it wrote one. That reading SHALL be taken only while the record is absent, SHALL be used only to end the wait, and SHALL NOT make the wait depend on the pid in any other way — a pid the shell has already reaped answers nothing, and a `wait` on a pid the shell no longer tracks returns no status (issue #227). A wait it ends SHALL be recorded as a bound the run enforces: one machine-readable line naming the vanished writer (`LOOP-TIMEOUT: the child's writer (pid <n>) vanished before it wrote its completion record …`), everything the child left ended as the other bounds end it, and the item `cut` with `bound` as its stop reason, no decision label and no decision slot (issue #790). A child that is merely slow SHALL be waited for exactly as before, and a pid that answers `kill -0` SHALL NOT end a wait.

#### Scenario: A child whose writer vanished ends the wait

Measured 2026-11-06: the run started at 06:27:14Z was reparented to `systemd --user` when the session that owned it
died and sat in this poll with a single `sleep 0.05` 17 minutes into a 600-minute budget, its lock still held and item
#793 still `agent/in-progress`; the child cap would have ended the wait at 45 minutes (issue #795).

- **WHEN** the subshell writing a child's completion record is killed before it writes the record, with the child's
  wall clock an hour away and the run's deadline not reached
- **THEN** the run ends the wait within seconds, appends `LOOP-TIMEOUT: the child's writer (pid <n>) vanished before it
  wrote its completion record` to the child's transcript, and records the item `cut` with `bound` as its stop
  reason, its note naming the vanished writer, no `agent/needs-decision` label and no decision slot
- **Case**: `run#aVanishedWriterEndsTheWait`

#### Scenario: A child that is merely slow is still waited for

- **WHEN** a child is running and has written nothing — a worker reading its package looks like this — and the pid its
  record is written by answers `kill -0`
- **THEN** the run keeps waiting, writes no `LOOP-TIMEOUT` line for it, and ends the wait on the record the child writes
  when it finishes
- **Case**: `run#aSlowChildIsNotMistakenForAVanishedWriter`
