# agent-issue-loop Specification

## Purpose
A backlog item in the tracker is closed by an unattended, budgeted run: one fresh agent process works one
item from a prepared package, a separate reviewer checks it, the run's own script owns the budget and the
version control, and nothing closes until evidence exists. A defect is fixed; a feature is planned
propose-first and then applied.

## Requirements

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
  wall-clock or one of its spend bounds, and left the item's tree at the run's base commit
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

### Requirement: The run acts only on authorised, eligible items

The run SHALL refuse to start when the identity it runs as is not authorised, SHALL select only items that
are eligible for automated work, and SHALL never write the authorisation label itself. A candidate the
pre-screen refuses for a reason the item cannot escape by itself SHALL leave that refusal on the item in the
tracker, so the item's state says why it is not worked after the run's own state is gone.

#### Scenario: An unauthorised identity stops the run before any work

- **WHEN** the identity the run acts as is not on the allowlist
- **THEN** it stops before creating anything, and no branch, label or state is written

#### Scenario: An item without the opt-in is not worked

- **WHEN** the eligible set is selected
- **THEN** an item whose owner has not applied the opt-in is never worked, however defect-shaped it is

#### Scenario: The run cannot authorise itself

- **WHEN** the run writes a label
- **THEN** the authorisation label is not among the labels it can write, and no code path of the run
  writes it

#### Scenario: A gated item is recorded, not silently dropped

- **WHEN** a candidate is rejected
- **THEN** its verdict and the one clause of evidence for it are recorded, so the rejection is part of the
  run's output

#### Scenario: A refusal the item cannot escape is written to the tracker

- **WHEN** the pre-screen refuses a candidate as `stale`, `class-feature` or `settled`
- **THEN** the item carries the verdict's refusal label (`agent/excluded-stale`, `agent/excluded-feature`,
  `agent/excluded-settled`) after the run, so the owner finds it with one query instead of reading a report
- **AND** the authorisation label and the status are left as the owner set them — the opt-in is neither
  withdrawn nor re-written
- **AND** a dry run writes no such label

#### Scenario: A refusal that no longer holds is withdrawn

- **WHEN** a later pre-screen reaches the same item and its verdict is no longer a refusal
- **THEN** the refusal label is removed, so a repaired citation or a re-classified item is worked rather than
  frozen by a label no run re-reads

### Requirement: The queue repairs the tooling the run depends on first

The pre-screen SHALL rank a defect in a category the profile names as leading ahead of every other item, SHALL
mix the leading categories' other classes in one after every N other items (N the profile's
`firstCategoryEvery`), and SHALL let the profile's class order and the issue number decide each stream. The
category order SHALL be a rank, never a gate: it SHALL NOT change a verdict, a label or a count.

#### Scenario: Harness items lead the queue, bugs before improvements inside a group

A defect in the tooling that runs, measures, gates or reports the loop is paid again by every item after it and
can make the run's own report false, so it is worked before a defect in the shipping app. A harness improvement
is not a defect: it is mixed in at the profile's interval rather than leading.

- **WHEN** the pre-screen ranks a backlog whose eligible items are a harness bug, a harness improvement, an app
  bug and an app improvement
- **THEN** the harness bug leads, the harness improvement is mixed in after every `firstCategoryEvery` other
  item, and the app bug and app improvement follow in the profile's class order — so a leading-category
  improvement neither leads the queue nor disappears from it
- **Case**: `prescreen#harnessItemsLeadTheQueue`, `prescreen#oneHarnessImprovementPerFourAppItems`

#### Scenario: The leading categories are the profile's, and an empty list is no priority

- **WHEN** a project's profile names its own leading categories, or names none
- **THEN** the order uses exactly the declared values — a renamed set is ordered on, not merely scanned — and an
  empty list leaves the class order alone, so a project that wants no category priority says so in its profile
  rather than in the engine
- **AND** a profile that omits any role the pre-screen reads, including `firstCategoryClasses` and
  `firstCategoryEvery`, stops the pre-screen with a reason naming it, so a half-adapted profile cannot rank
  silently
- **Case**: `prescreen#aRenamedLeadingCategoryLeads`, `prescreen#priorityCategoriesAreReadFromTheProfile`,
  `prescreen#anUndeclaredRoleStopsTheScreen`, `prescreen#anUndeclaredLeadingIntervalStopsTheScreen`

#### Scenario: A priority category is a rank, not a gate

- **WHEN** an item in a leading category is on hold, or its author is not authorised
- **THEN** it is excluded exactly as any other item would be, and no verdict, label or count changes because of
  its category
- **Case**: `prescreen#priorityDoesNotBypassTheGate`

#### Scenario: The class order still decides inside a group

- **WHEN** two candidates share a leading category, or share a category that does not lead
- **THEN** the profile's class order decides first and the issue number second, so the category key never
  reverses a bug against an improvement of the same group
- **Case**: `prescreen#harnessItemsLeadTheQueue`, `prescreen#ranksBugsFirstAndKeepsFeatures`

### Requirement: Version control belongs to the run's script, not to the agent

The run SHALL create and manage the working tree and the branch of each item itself, and no phase of an
item SHALL be able to invoke version control on its own behalf. A previous attempt's branch SHALL be decided by
what it *carries*, read by **patch identity** and not by ancestry — the run lands work as `fix: … (#N)`, so such
a branch is not an ancestor of the base: a branch whose attempt **finished** — one archived change of its own,
added by a commit that names its issue — SHALL be merged and its item closed by the run; when that branch's own
evidence is not admissible for adoption it SHALL be refused and escalated as a decision; and when its evidence
admits it but the project tree, the base or the replay refuses the merge, it SHALL be refused and recorded as the
item's mechanism `unmergeable` beside the pre-screen's exclusion, with no decision label and no item record, so
the item stays queued work the next run retries; a branch that carries commits and no archived change of its own
SHALL NOT be a decision: its commits SHALL be kept under a name the run gives them and the item SHALL be worked.

#### Scenario: One working tree per run

- **WHEN** a run starts
- **THEN** it prepares one working tree for the whole run and seeds it with what the project's build and
  test need, so the cold cost is paid once
- **Case**: `worktree#seedsAllFourInputs`

#### Scenario: One branch per item, from the run's base

- **WHEN** an item starts
- **THEN** it is worked on its own branch from the base commit the run recorded
- **Case**: `worktree#oneBranchPerItemOffTheBase`

#### Scenario: A commit carries the run's own identity

- **WHEN** the run commits an item's work on a machine with no `user.name` or `user.email` configured
- **THEN** both commits carry the identity the run already acts as — the login its own gate preflight
  resolved, as that login's noreply address — so the run never depends on an ambient git identity and never
  invents one, and a login that cannot be resolved stops the run with a named reason
- **Case**: `run#commitsWithoutAnAmbientIdentity`

#### Scenario: A branch that carries no work of its own is reused

- **WHEN** an item starts and a branch of that item's name already exists, and every commit on it is
  already in the run's base history, so the base is an ancestor of it
- **THEN** the item is worked on that branch, started from the run's base, so an item a previous run left
  blocked can be taken up again
- **Case**: `worktree#reusesABranchThatCarriesNoWorkOfItsOwn`

#### Scenario: A branch whose work already landed is reused

- **WHEN** an item starts and a branch of that item's name already exists, and the base carries every patch
  on it although the branch is not an ancestor of the base — the loop's own landing shape, where the work
  arrived as a new commit
- **THEN** the item is worked on that branch, started from the run's base, because a leftover is not a
  previous attempt: nothing on the branch is work the base would lose
- **Case**: `worktree#reusesABranchWhoseWorkAlreadyLanded`

#### Scenario: A branch that carries a previous attempt is refused, named and recorded

- **WHEN** an item's branch carries a commit the run's base does not have — whether the run learns it when the
  item starts, or the pre-screen asks git for it before any item is picked, on the item's branch-name prefix
  because the slug is not known before the title is read — and the branch adds an archived change of its own
  that is not admissible for adoption (no green verification record, or the reviewer's refusal of that change)
- **THEN** the item is refused with the branch and the commits it carries named, and it is recorded as
  awaiting a decision before a budget slot is spent on it — in the run's own exclusion list and on the item,
  with the branch as the evidence — because a finished attempt's change is never thrown away and a refusal is
  never silent
- **Case**: `prescreen#aFinishedAttemptBranchIsADecision`, `worktree#refusesABranchCarryingAPreviousAttempt`,
  `run#leftoverBranchIsRecordedNotDropped`

#### Scenario: A landed leftover is not a decision

- **WHEN** the pre-screen meets a branch of the item's name whose every patch the run's base already carries
- **THEN** the item is left eligible: no verdict, no exclusion and no decision slot is spent on a branch that
  holds no unlanded work
- **Case**: `prescreen#landedBranchIsNotADecision`

#### Scenario: A finished attempt's branch is merged and its item closed

- **WHEN** an item's branch carries a commit the run's base does not have, and that branch adds exactly one
  archived change **of its own** — the commit that added it names `(#<issue>)` — whose verification record is
  green — learned either when the item starts or when the pre-screen records the branch as an exclusion
- **THEN** the branch's commits are merged onto the base, the base the run recorded advances to the merge, the
  item is closed with the branch, its commits and the evidence it was checked for named in the run's own record
  and report, and no budget slot is spent on a fresh attempt; when the tree the merge would land in, the base or
  the replay refuses it, nothing is merged and nothing is closed, the refusal is recorded as the item's mechanism
  `unmergeable` beside its exclusion with the refusal's own text and no decision label written for it, and the
  branch stays the evidence — so a finished attempt is not treated as a blocked one, an attempt whose merge the
  tree refuses is work the next run retries rather than a question only its owner can answer, and a change
  another item's commit added does not make this branch adoptable
- **Case**: `run#mergesAFinishedAttemptAndClosesTheItem`

#### Scenario: A change another item's commit added does not make the branch adoptable

- **WHEN** the run is asked to adopt a branch whose only archived change was added by a commit that names
  another issue
- **THEN** nothing is merged and the refusal names the missing input — a single archived change of this branch's
  own — rather than a change the branch never wrote
- **Case**: `worktree#doesNotAdoptAChangeCreditedToAnotherIssue`

#### Scenario: A branch without a green verification is not merged

- **WHEN** the run learns of a previous attempt's branch that carries a commit the base does not have but whose
  archived change has no green verification record
- **THEN** the branch is not touched and the refusal names what it looked for, so a blocked attempt's branch is
  neither merged nor closed and its decision stays the owner's
- **Case**: `run#doesNotMergeAnAttemptWithoutAGreenVerification`

#### Scenario: A stale base stops the next item

- **WHEN** the main branch has advanced past the run's base commit
- **THEN** the next item is refused and both commits are named, rather than building on a tree nobody
  reviewed
- **Case**: `worktree#refusesWhenMainAdvanced`

#### Scenario: Nothing is pushed or opened by default

- **WHEN** a run does not ask to publish
- **THEN** no push and no change request happens, whatever the items did
- **Case**: `worktree#noPushWithoutTheFlag`

### Requirement: Each item is worked by a fresh process, reviewed by a different one

An item SHALL be worked by an agent process that starts fresh, given a prepared package describing the
item as data, and SHALL then be reviewed by a second, fresh process that is not the one that wrote the
change.

#### Scenario: The item arrives as a prepared package

- **WHEN** an agent process is started for an item
- **THEN** it receives a prepared package — the item's claim and evidence, the specification passage and
  the acceptance condition — and the item's own text is passed only inside a delimited data field
- **Case**: `prompt#inlineInjectionCannotReachTheInstructionPosition`

#### Scenario: A process runs where the project's own policy applies

- **WHEN** an agent process is started for an item
- **THEN** it is started in the project and told by path which tree it changes, so the project's own
  policy governs the process, and the package it is handed, the evidence directory it writes and the tree
  it changes all lie inside the boundary the process may reach
- **Case**: `run#childStartsInTheProjectRoot`

#### Scenario: A process that writes outside its tree is caught

- **WHEN** an agent process changes the project's own tree while an item is in flight
- **THEN** the run records one line naming what changed — the differing status entries and any process whose
  working directory is inside the project and outside the worktree — and none of that write is committed into
  the item's change, because the worktree is the only tree a phase may write
- **Case**: `run#childWriteOutsideTheWorktreeIsNoted`, `run#dirtyBaseRefusesTheMergeAndNamesThePath`

#### Scenario: A difference the run cannot attribute does not decide the item

- **WHEN** a difference is recorded and the run was not started with `--strict-outside-write yes`
- **THEN** the item keeps the outcome its own work earned, because the comparison measures a tree the run, a
  peer session and every landing also write, and two readings of it are not evidence about one child — and a base
  that is still not clean when the item lands is refused by the landing rule, with the path named; a difference
  the run *can* place in the child's own tree does decide, and that placement is the attribution this requirement
  states
- **Case**: `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem`, `run#dirtyBaseRefusesTheMergeAndNamesThePath`

#### Scenario: A difference is attributed while the writer is alive

- **WHEN** the project's own tree changes while a child runs
- **THEN** the run reads the difference from the heartbeat ticking beside that child and places it — it marks the
  processes of this child's own process tree, its process group or its descendants, whose working directory is
  inside the project and outside the worktree *and* which came out of the worktree — because a writer is gone by the
  time the child's transcript is written, and a difference nobody can place is not evidence about the child
- **Case**: `run#aWriterTheRunCanPlaceBlocksTheItem`

#### Scenario: A host that cannot read the child's tree refuses the run

- **WHEN** the run starts on a host where the command the placement reading calls (`pgrep`) is not on `PATH`
- **THEN** it refuses with a usage error that names the missing command and the reading it serves, before any
  worktree, branch or child exists, because the reading's calls swallow their stderr and a failure to read would
  otherwise record every difference and place none while the run reported success
- **Case**: `run#aHostWithoutThePlacementReadingIsRefused`

#### Scenario: The run's own child chain is not a writer

- **WHEN** a difference in the project's own tree is read while the child is alive and only the child's own shells —
  which the run starts in the project and which stand there for the whole item — are processes of that child's tree
  standing in the project
- **THEN** the difference is recorded and the item keeps the outcome its own work earned, because a run that marked
  its own child chain would decide every difference in the project's tree, which is the verdict no measurement of
  that tree supports
- **Case**: `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem`

#### Scenario: A shell the child forks in the project is found and not placed

- **WHEN** a shell the child forked stands in the project — the tree `run_child` starts every child in — and never
  `cd`s into the worktree, so no ancestor of it worked there, and it writes the project's own tree while the child
  is alive
- **THEN** the difference is recorded with the path that changed, the reading taken while the child was alive names
  that shell among the processes standing in the project and places no process of that tree, and the item keeps the
  outcome its own work earned, because the narrow rule is the deliberate trade that keeps a peer's write from
  deciding an item whose work was never touched
- **Case**: `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`

#### Scenario: `--strict-outside-write yes` makes the difference decide the item

- **WHEN** a worker's difference is recorded and the run was started with `--strict-outside-write yes`
- **THEN** the item is recorded as blocked with that reason and none of that write is committed into the
  item's change
- **Case**: `run#strictOutsideWriteBlocksTheItem`

#### Scenario: The reviewer is not the writer

- **WHEN** a review is run
- **THEN** it is a separate process from the one that produced the work
- **Case**: none — the separation is structural: `run_child` is invoked once for the worker and once for the
  reviewer (`tools/loop/run.sh:1543`, `:1710`), and no harness can tell a second process from a stub that claims
  to be one; what a case can pin, `run#announcesAndRecordsTheReviewRound` pins

#### Scenario: A reviewer that writes is discarded

- **WHEN** the working tree changed while the reviewer ran and the run was started with `--strict-outside-write yes`
- **THEN** that review round is discarded with a reason, because a reviewer that wrote cannot have been
  the reviewer
- **Case**: `run#strictOutsideWriteDiscardsTheReviewRound`

#### Scenario: Review rounds are bounded, and a corrective refusal earns one more

- **WHEN** a change fails its review round and the budget (`--max-review-rounds`, default
  `LOOP_MAX_REVIEW_ROUNDS_DEFAULT`) is spent
- **THEN** the item is recorded as needing a decision and no further round is attempted — unless the
  reviewer's report states that every finding is subtractive (`LOOP-CORRECTIONS-ONLY: yes`), in which case
  one more round is run and only its failure blocks the item
- **Case**: `run#reviewBudgetIsARecordedValue`, `run#aCorrectiveRefusalEarnsOneMoreRound`

#### Scenario: A refused review travels with the branch

- **WHEN** a review round ends `FAIL`
- **THEN** the run commits the verdict into the change's own evidence directory on the item's branch, and
  a later run refuses to adopt that branch — a finished-looking branch whose change and verification are
  otherwise green cannot be merged on a decision the reviewer denied
- **Case**: `run#recordsAFailedReviewOnTheBranch`, `worktree#refusesABranchWhoseReviewFailed`

#### Scenario: An escalation names the decision

- **WHEN** the run escalates an item after a failed review
- **THEN** the comment it posts carries the reviewer's `LOOP-DECISION` — the question, the options and the
  recommendation — so the owner decides from the tracker rather than reconstructing the question
- **Case**: `run#escalationCarriesTheDecision`

### Requirement: The run verifies rather than trusts

The run SHALL determine whether an item closed from artifacts it reads itself — the working tree, the
change's archive and the gate's evidence — and SHALL NOT accept an agent's claim of success. The tree's
own state SHALL be read as soon as the run can read it, and before any child the reading would make
pointless is started. Each phase's own report SHALL be read as that phase's answer, and a phase whose
child answers nothing is a failed phase.

#### Scenario: A claim without a commit is blocked

- **WHEN** an agent process reports success but the working tree shows nothing committed
- **THEN** the item is recorded as blocked and the item is not closed
- **Case**: `run#childClaimWithoutCommitIsBlocked`

#### Scenario: An empty tree is read at the diff, before the child that runs the gate

- **WHEN** the change child answered that its change is ready for the gate and the commit the run makes
  leaves HEAD at the run's base commit
- **THEN** the reading is recorded as a step of the item, no reviewer, gate child or tail is started, and
  the item is recorded as blocked with the reason the run writes for that tree — not with the reason it
  writes for a child that never read the item
- **Case**: `run#anEmptyTreeIsRefusedBeforeTheGateChild`

#### Scenario: A phase that answers nothing is that phase's failure

- **WHEN** the child of a phase exits 0 and its report carries no closing line — no `LOOP-GATE` and no
  `LOOP-VERDICT` — and the run did not itself end that child at a bound
- **THEN** the phase is asked once more, the same prompt contract written to a name of its own, and an
  answer that is still absent records the phase `fail` with the note `exit 0 and no report`; no tail is
  started, and the item is recorded `blocked` with a reason that names the phase and not the item's tree —
  a reason that is neither `no archived change appears in the run's diff` nor `the tree is still at the base
  commit`
- **Case**: `run#aPhaseThatAnswersNothingIsThatPhasesFailure`

#### Scenario: A change child that answers nothing is recorded as that phase's failure

- **WHEN** the change child exits 0 and leaves a report that carries no `LOOP-DIFF-READY`, no `LOOP-GATE`
  and no `LOOP-VERDICT` — no line the run's own arms read — and the commit the run makes of its tree leaves
  HEAD past the run's base commit
- **THEN** the round before the gate and the gate child are not started and no tail is written, the item's
  steps record the phase `fail` with the note `exit 0 and no answer`, the run prints that the change child's
  answer is not an answer, and the item is recorded `blocked` with a reason that names the change phase —
  not `no archived change appears in the run's diff`, not `the tree is still at the base commit` and not
  the run's own `could not start`, and the branch that carries the child's committed tree is kept
- **Case**: `run#anEmptyAnswerOverACommitIsTheChangePhasesFailure`

#### Scenario: The gate's evidence is read by the run

- **WHEN** an item claims a gate result
- **THEN** the run quotes the evidence it found and does not repeat the claim
- **Case**: `run#claimedGateWithoutEvidenceIsBlocked`

#### Scenario: Evidence outlives the run

- **WHEN** an item cites a test result
- **THEN** the result is carried into the change's own directory, because the build directory is
  overwritten by the next run
- **Case**: `run#citedEvidenceMustTravelWithTheChange`

### Requirement: Closure follows a passing review and carries evidence

An item SHALL be closed only after its review passed and its change was archived — or, when the review
confirmed that the item's premise does not hold on an unchanged tree, after that confirmation — with one line
of root cause and the evidence attached as a comment first; the run SHALL be able to be started so that closure
is left to a person instead. A review that confirms the item's premise does not hold SHALL close it on the same
terms — reviewed, with its measurement as the evidence, and only when closure is allowed.

#### Scenario: A passing item is closed with evidence

- **WHEN** the review passed and the change was archived
- **THEN** the item is closed after a comment carrying the root cause and the evidence

#### Scenario: Closure can be left to a person

- **WHEN** the run was started with closure disabled
- **THEN** the item keeps its fixed state, the evidence comment is still posted and the item stays open

#### Scenario: A reviewed falsification is closed on the same terms

- **WHEN** the worker answered that the item's premise does not hold, the tree is unchanged, the measurement
  is recorded with how it was taken, and the review did not make it blocking
- **THEN** the item is closed with the contradicted mechanism as the reason and the measurement as the
  evidence, and the run's state records the item with its own result so the report can count it apart from a
  fix and from a skip
- **Case**: `run#falsifiedSkipClosesWithItsMeasurement`

#### Scenario: A closure takes the decision label off the item

- **WHEN** an item that carries the decision label is closed, by any path — the run, a person, a check
- **THEN** the closure removes that label in the same write that records the settled state, and an item that
  does not carry it is written exactly as before
- **Case**: `labels#closeRemovesTheDecisionLabel`

#### Scenario: Closure disabled keeps a falsification open

- **WHEN** the run was started with closure disabled and the item's falsification was reviewed
- **THEN** the evidence comment is posted, the item stays open, and no decision label is written — the run
  does not reopen the question it already answered
- **Case**: `run#mayCloseNoKeepsAFalsificationOpen`

#### Scenario: A blocked item is never closed

- **WHEN** an item ended blocked — for any reason the run determined, not only a failed review round
- **THEN** it is not closed, and the reason is written on the item in the same pass, while the run still holds
  it — the decision label, the status that holds it, and one comment naming the reason and the work already
  done, with the evidence directory and the branch as the evidence
- **Case**: `run#blockedItemIsEscalatedWithItsReason`, `run#childClaimWithoutCommitIsBlocked`

### Requirement: The run survives interruption

The run's state SHALL have exactly one writer, SHALL live where it cannot be committed, and SHALL be
sufficient to continue after an interrupt without repeating work already done.

#### Scenario: State is written only between agent processes

- **WHEN** an agent process is running
- **THEN** no state is written, so a bookkeeping edit cannot be attributed to that process's work
- **Case**: `labels#writesOnlyBetweenChildren`

#### Scenario: An interrupted item leaves no item looking in progress

- **WHEN** the run is interrupted while an item is in flight
- **THEN** the in-progress marking is removed, the item is recorded as blocked, and the next run can take
  it up again
- **Case**: `labels#trapRemovesInProgressOnSignal`

#### Scenario: A kept worktree a previous run left dirty is attributed and refused

- **WHEN** a run starts, no run holds the run lock, and the worktree it keeps from a previous run is not clean
- **THEN** the run names the files and the item whose branch the tree's marker names, records the refusal
  as a blocked item under it, escalates it with the branch as evidence and prints its report — before the
  pre-screen is paid for — and it leaves the tree exactly as it found it, unless the run asked for
  `--reset-worktree`, which names what it discards before discarding it and leaves the commits on the
  previous branches alone
- **Case**: `run#dirtyKeptWorktreeIsARecordedRefusal`, `run#resetWorktreeDiscardsWhatItNames`, `state#secondRunRefusesWithExit5`

#### Scenario: A refused start is the run's own recorded end

- **WHEN** a run ends at one of its refusals before the first item — a kept worktree it may not clean, the repair
  `--reset-worktree` that cannot clean it, or the decision cap — and that refusal never reached an item it works
- **THEN** the refusal is written as the run's stop reason, naming what the refusal printed, so a report read after
  that run's process is gone prints the refusal and not "it was killed and recorded no stop" (#787)
- **Case**: `run#dirtyKeptWorktreeIsARecordedRefusal`, `run#resetWorktreeNamesTheDirtItCannotDiscard`,
  `run#theDecisionCapRecordsItsStopReason`

#### Scenario: A skipped item leaves no item looking in progress

- **WHEN** an item ends with a skip verdict
- **THEN** the in-progress marking is removed with it, so the tracker never shows an item as being worked
  while no run holds it
- **Case**: `run#aSkipClearsTheInProgressMark`

#### Scenario: A block's reason is not left to the run's state

- **WHEN** the run blocks an item and writes the reason into its own state
- **THEN** the reason is also written on the item itself, between agent processes, because the state is
  replaced wholesale by the next run — so the decision list, the cap that counts it and the next attempt all
  read a block the state no longer holds
- **Case**: `run#blockedItemIsEscalatedWithItsReason`

#### Scenario: State is not committed

- **WHEN** the run ends
- **THEN** its state file is not part of the repository's history
- **Case**: `state#initAndShowRoundTrips`

### Requirement: A pile of open decisions stops the run

The run SHALL refuse to start when more candidate items awaiting a person's decision are open than the configured
cap allows, and SHALL report them instead of producing more work. An item is a candidate only when it carries
both the decision label and the loop's opt-in, so a decision parked by removing `agent/eligible` SHALL NOT count
(#745).

#### Scenario: The cap is reached

- **WHEN** the number of candidate items awaiting a decision reaches the cap
- **THEN** the run refuses to start, prints those items and states the count it compared, so nothing new is
  produced while decisions accumulate, and records that refusal as the run's own stop reason — naming both counts
  it compared and the items it printed — so a later report prints the refusal instead of the sentence a killed
  process leaves (#787)
- **Case**: `run#decisionCapRefusesToStart`, `run#decisionCapCountsOnlyCandidates`,
  `run#theDecisionCapRecordsItsStopReason`

#### Scenario: An item needing a decision is escalated, not abandoned

- **WHEN** an item turns out to need a decision while it is being worked
- **THEN** the work already done is preserved and named on the item — the root cause, the failing case and
  the branch — and the item is marked as awaiting a decision, so the work is not thrown away
- **Case**: `run#needsDecisionIsEscalatedWithTheWork`

#### Scenario: The report names the decisions the run itself wrote

- **WHEN** a run ends after escalating an item — a skip whose verdict means a decision, a branch a previous
  attempt left, an item that already awaited a decision, or a review round that failed
- **THEN** the report's list of decisions the owner owes names that item, read from the label the run's
  escalation wrote and recorded with the item rather than from the verdict the worker chose, and names no
  item this run did not escalate — so the run that fills the cap is the run whose report shows the cap
  coming
- **Case**: `report#escalatedSkipNamesTheLabelWritten`, `report#failedReviewEscalationIsNamed`, `report#aVerdictAloneIsNoEscalation`

### Requirement: A state the run writes is readable from the item

Every state label the run writes SHALL be accompanied, on the same item and in the same call, by the one line
that accounts for it — the verdict and the clause of evidence that produced it — because the run's own state is
machine-local and is replaced wholesale by the next run, so a state whose reason lives only there cannot be
answered, re-checked or even understood by the item's reader (#192).

#### Scenario: A hold verdict carries its reason

- **WHEN** a worker skips an item with a hold verdict — `unspecifiable`, `too-wide`, `not-spec-shaped` or
  `needs-target` — and the run puts it on hold
- **THEN** the item carries a comment naming the verdict and the one clause of evidence the worker gave, and no
  decision label is added for it
- **Case**: `labels#holdCarriesItsReason`

#### Scenario: Nothing is written when the reason is missing

- **WHEN** a call that must record a reason and its evidence is given neither
- **THEN** it stops as a usage error before it writes anything, so no item is left in a state nobody can account
  for
- **Case**: `labels#holdCarriesItsReason`

### Requirement: A hold is not a decision

An item SHALL count as awaiting a decision only when it carries the decision label an escalation writes. The hold
state has several writers — the worker's hold verdicts (`unspecifiable`, `too-wide`, `not-spec-shaped`,
`needs-target`), the device verdict, the citation repair's park and a person's own hold — and none of them states
a question, so a bare hold SHALL be reported as a hold, SHALL NOT be escalated, and SHALL NOT count against the
decision cap (#193). A `stale` skip SHALL write no bare hold: that verdict leaves a question, so the escalation
writes the decision label beside the hold, as the requirement "A stale skip asks its own question" states.

#### Scenario: A bare hold is reported for what it is

- **WHEN** the pre-screen meets an eligible item on hold that carries no decision label
- **THEN** it records the item as held, writes no label and posts no escalation, and the report prints it as held
- **Case**: `prescreen#aHoldWithNoDecisionIsNotADecision`, `run#awaitingDecisionIsNotEscalatedAgain`

#### Scenario: The cap counts the questions a person can answer

- **WHEN** the run counts the open decisions before it starts
- **THEN** only items that carry the decision label **and** the loop's opt-in are counted, so an item nobody
  asked a question about — and an item a person parked by removing `agent/eligible` — cannot stop a run, while
  the refusal still states how many items carry the label beside the count it compared
- **Case**: `run#decisionCapCountsOnlyCandidates`, `run#decisionCapRefusesToStart`,
  `prescreen#aHoldWithNoDecisionIsNotADecision`

### Requirement: The run's mechanics are the project's configuration, not its code

The run SHALL take the command it starts an agent process with, the skills it tells that process to use and
the command it runs the project's gate with from configuration, and SHALL name none of them in its phases.

#### Scenario: A project with no gate still runs

- **WHEN** the configured gate command is empty
- **THEN** the phases complete and the report says the gate was skipped, so the phases call the project's
  gate rather than owning one

#### Scenario: The agent command is one indirection

- **WHEN** an agent process is started
- **THEN** it is started through the configured command and through no other, so how an agent is started
  can change without touching a phase

#### Scenario: No phase names a project fact

- **WHEN** the run's phases and prompts are inspected
- **THEN** they name no skill, command, path or project value of their own

### Requirement: Running an agent elsewhere is a configuration, not a rewrite

The run SHALL be able to start its agent processes somewhere other than the host — a sandboxed or isolated
environment — by changing the configured command alone, and SHALL NOT depend on such an environment being
present.

#### Scenario: The run works with no isolation environment installed

- **WHEN** the run is started on a machine with no sandbox tooling
- **THEN** it works, because the sandbox is one possible value of the configured command

#### Scenario: The environment holds no tracker credential

- **WHEN** an agent process runs, wherever it runs
- **THEN** it cannot write to the tracker: the run's script owns every write, and the process is given a
  prepared package rather than credentials

#### Scenario: Work needing a device stays on the host

- **WHEN** an item's remaining evidence needs a device attached to the host
- **THEN** that is recorded as a device verdict and not attempted inside the agent's environment

### Requirement: A review round reads the change, not the tree it sits in

The run SHALL hand a review round the change — source, the specification delta, the design, the tasks and the
change's own prose — and SHALL NOT hand it the generated output an item archives beside that change. The
evidence SHALL reach the reviewer by path, through the item's evidence directory, which the round's prompt
names. Which directories hold generated output SHALL be a recorded value of the project's configuration and
not a rule of the phase.

#### Scenario: The round's diff carries the change and not the generated evidence

- **WHEN** the run writes the diff a review round reads, and the item's commit carries both a source change and
  a generated artefact under a configured evidence tree
- **THEN** the diff carries the source file and its hunk, and carries neither the artefact nor any line of its
  content
- **AND** a file outside the excluded trees is still in the diff whatever its extension, so a `.txt` the
  reviewer has to read survives an artefact whose name looks like data
- **Case**: `run#reviewDiffExcludesTheEvidenceArchive`

#### Scenario: The exclusion is a recorded value, and an empty one is no exclusion

- **WHEN** a project configures no exclusion pathspec, or configures one
- **THEN** the round's diff is the whole change in the first case and the change without the excluded trees in
  the second, from the same function, with no path of the evidence convention written into the phase
- **Case**: `run#reviewDiffExcludesTheEvidenceArchive`

#### Scenario: The reviewer is told where the evidence went

- **WHEN** the reviewer's prompt is rendered
- **THEN** it names the evidence directory the change cites as the route to the evidence, and says that the
  diff excludes the generated trees, so the absence in the diff is not read as a missing artefact
- **Case**: `run#reviewDiffExcludesTheEvidenceArchive`

### Requirement: A run reports the wait it cannot get from the child

A run SHALL print what it is doing while a child process is in flight, from what the run can observe itself,
because a child's answer arrives when it exits. It SHALL name the wait when a child starts, report the child's
progress on a configured cadence, and report the end with the child's exit status, so a working child and a hung
one stay distinguishable.

#### Scenario: The wait is announced when the child starts

- **WHEN** the run starts a child process — the worker, a review round or the gate
- **THEN** it prints one line naming the phase and the transcript's path before the child can have written
  anything, so the console never shows a start of the item followed by silence whose cause is unknown
- **Case**: `run#childReportsWhileItWaits`

#### Scenario: A running child is reported on a cadence, with numbers read from the tree

- **WHEN** the child has not yet ended and the configured interval (`LOOP_PROGRESS_SECONDS`, default 30) has
  elapsed
- **THEN** the run prints a line carrying the time already spent, the transcript's size, the worktree's
  changed-file count and its commit count since the run's base
- **AND** every one of those numbers is read from the file system and from git in the tree the child writes,
  not from the child's own claim, so the line can be printed while the child is unable to say anything
- **AND** a configured interval that is empty, non-numeric or zero falls back to the default rather than
  stopping the reporting
- **Case**: `run#childReportsWhileItWaits`

#### Scenario: The child's end is reported with its own exit status

- **WHEN** the child process ends, however it ends
- **THEN** the run prints one line naming the elapsed time, the exit status and the final transcript size, so
  the phase that follows can be read against what the previous one actually did
- **Case**: `run#childReportsWhileItWaits`

### Requirement: A review finding is blocking or it is not, and the round counts

A review round SHALL grade every finding as **blocking** or **note**, and state one machine-readable count of
its blocking findings (`LOOP-BLOCKING: <n>`). Blocking means the contract is contradicted, a scenario is not
exercised by the case that names it, or the gate or red-case evidence is missing or false; a note is anything
the change does not depend on. The run SHALL refuse on that count, not on the verdict's word.

#### Scenario: A finding that blocks nothing does not refuse the round

- **WHEN** a review round reports `VERDICT: FAIL` and counts zero blocking findings
- **THEN** the change is not refused: the item closes, exactly one round was taken, and the round's `NOTE:`
  findings are recorded on the item — in its closure comment and in its state note — so a note is recorded
  rather than absorbed
- **AND** a report that also carries `LOOP-CORRECTIONS-ONLY: yes` earns no extra round for it, because that line
  describes the shape of a refusal and there is no refusal to describe
- **Case**: `run#aZeroBlockingRefusalClosesWithItsNotes`

#### Scenario: A blocking finding refuses the change as it always has

- **WHEN** a review round counts one or more blocking findings
- **THEN** the round is a refusal: another round is taken within the budget, and a second refusal records the item
  `blocked`, escalates it and writes the reviewer's `LOOP-DECISION` into the escalation
- **Case**: `run#aBlockingRefusalStillTakesTheRoundTwoPath`

#### Scenario: A report that states no count is read as a refusal

- **WHEN** a reviewer report carries no `LOOP-BLOCKING` line, as every report written before this rule does
- **THEN** the round is read as blocking whatever its verdict says, because a run cannot tell an older review from
  a quiet one, so no review can pass by saying nothing
- **AND** a report that carries no closing `VERDICT:` line either is not read by this rule at all: it arrived
  cut, and the retry below establishes whether there was anything to grade
- **Case**: `run#reviewRoundsCapAtTwo`

#### Scenario: An unchanged pass still passes

- **WHEN** a review round reports `VERDICT: PASS` with no count line or a count of zero
- **THEN** the item closes exactly as before, with no notes recorded and no second round taken
- **Case**: `run#metricsRecordedPerItem`

#### Scenario: A report that arrived cut is retried, not read as a refusal

An answer can reach the file the run decides from short: on 2026-10-20 an item's round-2 report stopped
mid-word (`… and the post-restore gre`, 6 067 bytes, ending on the newline the child's adapter appends) while its
own text recorded all seven scenarios PASS, and the run blocked and escalated work that had passed (issue #224).
The shape of the answer is the discriminator, and it is checkable without a model: the child's adapter prints one
newline after every answer it hands the run, and the prompt asks for a `VERDICT: PASS|FAIL` line in the report's
closing block. The missing line is the fact that caught the measured incident; the missing newline covers an
answer whose tail was lost outright.

- **WHEN** a review round's report does not carry the `VERDICT: PASS|FAIL` line its prompt asks for last, or does
  not end with the newline the adapter prints after an answer, and the round's child was not ended by the run's
  own wall-clock (`LOOP-TIMEOUT`)
- **THEN** the run states that the answer was cut short, naming the report's last line, and asks the same round
  once more — the same prompt contract and the same diff, written to `reviewer-prompt-<n>-retry.md`, with the cut
  attempt kept beside the retry as `reviewer-report-<n>.md` and `reviewer-report-<n>-retry.md` — and the retried
  answer decides the round, so the retry spends no second review round and no worker round and the item is not
  escalated
- **Case**: `run#aCutReportIsRetriedInsteadOfRefused`

#### Scenario: Two cut answers in one round block it as the transport's fault

- **WHEN** the retried answer is cut as well
- **THEN** the item is recorded `blocked` with the reason that the reviewer's report was cut short twice — that
  round produced no verdict at all, so no reviewer refused the change — no worker round is taken for it, and no
  review-failure marker is written into the change on the branch, because a later run must not refuse to adopt a
  change no reviewer refused
- **Case**: `run#aReportCutTwiceBlocksAsTheTransportsFault`

### Requirement: A run's state outlives the run that replaces it

The run SHALL keep the state a new run replaces, SHALL be able to report on a kept run afterwards, and SHALL
name the run its numbers belong to rather than print a budget left computed from another run's clock, and
SHALL tell a run whose process is gone from one that is still going.

#### Scenario: The state a run replaces is kept, named by that run's start

- **WHEN** `state.sh init` is asked to start a run and a state file already exists
- **THEN** the existing file is copied, before anything is written, to `<state-dir>/runs/<run.startedAt>.json`
  — the start time the state carries itself, under a directory beside the state file unless the project
  records another one (`LOOP_RUNS_DIR`) — and the new state is the one in place, so no finished run's counters,
  verdicts or stop reason are lost to the next run
- **Case**: `state#initKeepsTheStateItReplaces`

#### Scenario: A run that never recorded a stop is announced, not refused

- **WHEN** the state being replaced carries no `run.stop.reason` — a run that was killed or crashed
- **THEN** `init` says so on stderr in one line and still starts the run, so a crashed run stays restartable
  without a flag, while a state that did record its end is replaced without the warning
- **Case**: `state#initAnnouncesARunWithoutAStop`

#### Scenario: A kept run is reported on after the next run started

- **WHEN** `report.sh --state <file>` is given a state a later run replaced
- **THEN** the report is that run's — its budget, its counters, its item table, its skips and its stop reason —
  and without `--state` the report is the run in place, exactly as before
- **Case**: `report#archivedStateIsReported`

#### Scenario: The budget line belongs to the run whose state it reads

- **WHEN** the report prints the budget left, and the state's window (`run.startedEpoch` + `run.minutes`) closed
  without a recorded stop, so the elapsed time since its start cannot be its spend
- **THEN** the line says the numbers belong to another run —
  `budget left unknown: these numbers belong to another run — its window closed at <run.deadlineAt> and it
  recorded no stop` — instead of printing a budget left below zero, and a run that recorded its stop reports its
  own spend to that stop however long ago it ended
- **Case**: `report#impossibleBudgetNamesAnotherRun`

#### Scenario: A run whose process is gone is not a run that is going

- **WHEN** the state names the process that wrote it, that process is not running, and the run recorded no
  stop — a run that was killed
- **THEN** the report prints no budget left for it, and its stop line names the process that is gone instead
  of saying the run is still going, so a killed run and a running one cannot be read alike
- **Case**: `report#aKilledRunIsNotALiveRun`

### Requirement: A kept worktree carries the sources, not the build output

A run that keeps its worktree SHALL remove the module build directories the project records, so the tree the
next run inherits is a checkout whose next build regenerates what it needs — not a growing archive of every
build the loop has paid for. Which directories those are SHALL be recorded configuration, never a path in an
engine, and the removal SHALL stay inside the worktree: it SHALL NOT follow a symlink out of it and SHALL NOT
remove a path git tracks.

#### Scenario: The run removes the recorded build directories from the tree it keeps

- **WHEN** a run ends, keeps its worktree, and the tree carries a directory the project recorded in
  `LOOP_WORKTREE_SCRATCH_PATHS` (measured: 1.9 GB of `app/build` in a 3.2 GB kept worktree)
- **THEN** the run removes that directory before it reports the tree as kept, the change's source files and the
  item's branch and commit are still there, the tree is clean, and the run prints the bytes it removed
- **Case**: `worktree#keepsSourcesNotBuildOutput`

#### Scenario: The removal stays inside the worktree and leaves what is not build output

- **WHEN** a recorded path inside the kept worktree is a symlink whose target lies outside the tree, or a path
  git tracks
- **THEN** the path is left exactly where it is and the report says why, and the file outside the tree is not
  touched — so a kept worktree loses what its next build regenerates and nothing else
- **Case**: `worktree#keepsSourcesNotBuildOutput`

#### Scenario: The list of build directories is the project's, not the engine's

- **WHEN** the loop needs to know which directories a kept worktree loses
- **THEN** it reads them from `LOOP_WORKTREE_SCRATCH_PATHS` in its configuration, and no script or prompt under
  `tools/loop/` names one, so the same engine cleans another project's tree by that project's list
- **Case**: `run#engineNamesNoProjectFact`

### Requirement: A suite ended by a signal removes its scratch tree

The loop's self-test SHALL create its scratch tree under the loop's recorded temporary root and SHALL remove
it when a signal ends the suite, exactly as it does when a case fails — and it SHALL NOT report a run it did not
finish as a pass. `SIGKILL` cannot be trapped; the trees it leaves SHALL be the leftovers the clean command
reports.

#### Scenario: A suite interrupted in the middle of a case leaves nothing behind

- **WHEN** a case is running (a case that sleeps is what that looks like) and the suite receives `TERM` (and the
  same holds for `INT` and `HUP`)
- **THEN** the scratch tree under the recorded root is gone, and the suite's exit status is not 0 — an
  interrupted run never reads as a pass
- **Case**: `clean#interruptedSuiteRemovesItsScratchTree`

### Requirement: One command reports and removes what the loop left

The tooling SHALL have one entry point that answers what the loop left on disk and how much it is: without
`--apply` it SHALL name every path it would remove with its size and remove nothing, with `--apply` it SHALL
remove exactly those paths, and a second run SHALL find nothing. It SHALL remove the recorded build paths of a
worktree, the branches whose patches the base already holds, the worktrees under the loop's own tree that the run
does not keep, the scratch trees old enough that no suite can be writing into them — in **both** the scratch
root the invocation resolves and the one the kept worktree resolves — and the item directories no open issue still
cites, and it SHALL NOT touch a worktree's tracked or untracked source. A worktree it keeps SHALL be named with the
reason it is kept: uncommitted changes, or a commit the base does not carry. An item directory SHALL be released
only when nothing inside it has been written for `LOOP_ITEM_ARCHIVE_DAYS` **and** the run recorded the item landed
and its issue closed, and SHALL be kept and named with that reason otherwise; a `0` retention SHALL release none.

#### Scenario: The report removes nothing

- **WHEN** `loop-clean` is run without `--apply` on a kept worktree with build output and a scratch root with a
  stale tree
- **THEN** it prints one line per path with its size and a total, and the build output and the stale tree are
  still on disk afterwards
- **Case**: `clean#reportsWithoutApplyAndRemovesWithIt`

#### Scenario: With `--apply` it removes what it reported, and nothing more

- **WHEN** `loop-clean --apply` is run on the same tree
- **THEN** the recorded build paths and the stale scratch tree are gone, a scratch tree younger than
  `LOOP_TMP_STALE_MINUTES` is left (a live suite may be writing into it), the worktree's source files are intact
  and its git state unchanged, and the bytes it removed are the bytes it reported
- **Case**: `clean#reportsWithoutApplyAndRemovesWithIt`

#### Scenario: A second run is a no-op

- **WHEN** a ` --apply` is run again on a tree and a scratch root it has already cleaned
- **THEN** it finds no path to remove, says `nothing to remove`, and exits 0
- **Case**: `clean#reportsWithoutApplyAndRemovesWithIt`

#### Scenario: The branches whose work is in the base are removed with it

- **WHEN** `loop-clean --apply` runs on a repository that holds a branch whose patches the base already has
- **THEN** that branch is removed, a branch that still carries unlanded work and a branch a worktree holds
  are kept and named, and a second run finds nothing left to remove
- **Case**: `clean#sweepsOnlyWhatIsInTheBase`

#### Scenario: A worktree no run keeps is reported, and removed only with `--apply`

- **WHEN** the report runs on a tree that holds the one worktree the run keeps and another worktree under
  `.pi/worktrees/` that no configuration, no state field and no marker names
- **THEN** the tree no run keeps is named with its size and removed only with `--apply`, the tree the run keeps is
  not touched, and a tree holding uncommitted changes is kept in both runs and named with that reason
- **Case**: `clean#reportsAWorktreeNoRunKeeps`

#### Scenario: The kept worktree's own scratch root is scanned beside the invocation's

- **WHEN** the kept worktree carries a stale `loop-selftest.*` tree under its own `.pi/tmp` — where a gate run
  inside the worktree creates one — and a process stands inside it, while the invocation's own scratch root is
  empty
- **THEN** the report names that root as its own line, names the stale tree and the process, keeps a tree of that
  root younger than `LOOP_TMP_STALE_MINUTES`, and `--apply` removes the stale tree and ends the process
- **Case**: `clean#reportsTheWorktreesScratchTreesToo`

#### Scenario: `--worktree .` scans the one root it names once

- **WHEN** `loop-clean --worktree .` is run from the project, so the tree it is pointed at is the one the invocation
  resolves and the two scratch roots are one directory written two ways
- **THEN** the report prints one heading for that root, lists each scratch tree once and counts its bytes once — a
  tree is never listed or counted twice
- **Case**: `clean#worktreeDotSweepsTheOneRootOnce`

#### Scenario: An item directory is reported by its age and by what its issue still says

- **WHEN** the report runs over item directories older than `LOOP_ITEM_ARCHIVE_DAYS`, one whose item landed and was
  closed, one whose item landed but could not be closed, one whose item never landed, and one written a minute ago
- **THEN** the landed and closed directory is named with its size and removed only with `--apply`, the other three
  are kept and named with the reason, and a `0` retention releases nothing
- **Case**: `clean#reportsAnAgedItemDirectory`

### Requirement: A suite ended by a signal ends the case it was in, and its processes

The loop's self-test SHALL run each case in a process group of its own and SHALL, on every exit path, leave no process of the cases it ran standing in its own scratch root: a signal SHALL end the whole group of the case it was in, and a suite that ends normally SHALL sweep that root before removing it. The signal SHALL be the one that lets a member run its own cleanup, the suite SHALL NOT report a run it did not finish as a pass, and `SIGKILL`'s leftovers SHALL be the clean command's.

#### Scenario: A suite interrupted in the middle of a case ends that case and what it started

- **WHEN** a case is running (a case that starts a child and waits is what that looks like) and the suite
  receives `TERM` — and the same holds for `INT` and `HUP`
- **THEN** the suite is gone at once rather than when its case happens to finish, its exit status is not 0, and
  neither the case's own process nor the child it started is running afterwards
- **Case**: `clean#interruptedSuiteEndsItsProcessGroup`

#### Scenario: A suite that ends normally ends the process its case left

- **WHEN** a case starts a process in a process group of its own at a working directory inside the suite's own
  scratch root and returns while it polls, and the suite then reaches its normal end — no signal is sent
- **THEN** that process is not running once the suite has exited, and the suite's exit status is still 0
- **Case**: `clean#suiteThatEndsNormallyEndsTheProcessItsCaseLeft`

### Requirement: An interrupted run ends the child it started

A run that takes a signal SHALL end the child it was waiting for, together with everything that child started,
before it records the item's outcome — the child is put in a process group of its own so the wall-clock cap can
bound it, and the same group SHALL be ended by the run's own traps.

#### Scenario: A run ended by TERM leaves no child working

- **WHEN** a run is waiting for a child that is in the middle of its phase (a child blocked on a fifo is what
  that looks like) and the run receives `TERM`
- **THEN** the run exits with the signal's status rather than a finished run's, the child it started is
  no longer running, and the run's own record names `TERM` as the reason it stopped
- **Case**: `run#interruptedRunEndsTheChildItStarted`

#### Scenario: A run a vanished terminal ended takes the same path and leaves no item looking in progress

- **WHEN** a run is waiting for a child mid-item and receives `HUP`, the signal a terminal or a harness sends when
  it goes away
- **THEN** the run exits with status `129`, the child it started is no longer running, the item it was working is
  recorded `blocked`, its in-progress marking is removed, the run's record names `HUP` as the reason it stopped, and
  no lock is left behind
- **Case**: `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`

### Requirement: The leftover report names the processes, not only the trees

The clean command SHALL report the processes whose working directory lies inside a scratch tree it is about to
remove, and SHALL end exactly those with `--apply`, before the tree goes. It SHALL NOT signal a process whose
working directory is inside a tree it keeps — that one belongs to a live suite — and the paths and bytes it
already reports SHALL keep their shape, with the process count added only when there is one.

#### Scenario: The report names a process and ends nothing

- **WHEN** `loop-clean` is run without `--apply` on a scratch root holding a stale tree with a process standing
  in it and a fresh tree with a process of its own
- **THEN** it names the stale tree's process beside the tree it would remove, names no process in the fresh tree,
  ends neither process and removes neither tree
- **Case**: `clean#reportsAndEndsAProcessLeftInAScratchTree`

#### Scenario: With `--apply` it ends that process and leaves a live suite's alone

- **WHEN** `loop-clean --apply` is run on the same scratch root
- **THEN** the process standing in the stale tree is gone and so is the tree, the process in the fresh tree and
  the fresh tree itself are untouched, and the summary counts the one process it ended
- **Case**: `clean#reportsAndEndsAProcessLeftInAScratchTree`

### Requirement: A block asks its own question

An escalation SHALL hold a decision field that a person can answer: when the reviewer wrote no decision, the
run SHALL state the choice the block leaves — retry the item or abandon it — rather than escalating a reason
and calling it a decision.

#### Scenario: A block with no reviewer decision states the choice

- **WHEN** an item is blocked and the reviewer's report carries no `LOOP-DECISION` — the run stopped before a
  review round, or the cause is the run's own child
- **THEN** the escalation's comment carries a `The decision:` field naming both options and the run's reason
  as the ground for either, so the decision queue holds something answerable
- **Case**: `run#blockedItemAsksItsOwnQuestion`

#### Scenario: A reviewer's own decision is not replaced

- **WHEN** the reviewer's report carries a `LOOP-DECISION`
- **THEN** that text is the escalation's decision field, unchanged, and the run states no choice of its own
- **Case**: `run#escalationCarriesTheDecision`

#### Scenario: A block's reason is still on the item

- **WHEN** any block is escalated
- **THEN** the reason stays in the comment beside the decision field, because the state the run keeps is
  replaced wholesale by the next run
- **Case**: `run#blockedItemIsEscalatedWithItsReason`

### Requirement: A skip that falsifies the premise is closed as mis-diagnosed

A skip SHALL distinguish an item whose premise the run disproved from the items it could not finish: the
former is a finding the run holds the evidence for, and SHALL be closed with that evidence when closure is
allowed; a falsification without a measurement SHALL be escalated as a question, and a skip on a hold verdict
(`unspecifiable`, `too-wide`, `not-spec-shaped`, `needs-target`) SHALL keep the bare hold it writes. A `stale`
skip writes no such hold: it is escalated as the requirement "A stale skip asks its own question" states.

#### Scenario: A measured falsification closes the item

- **WHEN** the worker reports that the mechanism the item blames does not hold, names that mechanism, records
  the number it measured instead together with how the measurement was taken, and leaves the tree unchanged
- **THEN** the item is closed as mis-diagnosed with that measurement as its evidence, its comment names the
  contradicted mechanism, and the run does not escalate it
- **Case**: `run#falsifiedSkipClosesWithItsMeasurement`

#### Scenario: A falsification without a measurement is a question, not a finding

- **WHEN** the worker reports a falsification but leaves the measured field empty
- **THEN** the item is escalated exactly as before — the decision label, the hold, the reason on the item —
  and nothing is closed
- **Case**: `run#falsificationWithoutAMeasurementIsEscalated`

#### Scenario: The claim is read again after the review

- **WHEN** a falsification reaches its closure and the tree no longer stands where the claim said it stood —
  a corrective review round's worker committed
- **THEN** the item is blocked instead of closed, the note says the tree moved, and the reason is written on
  the item
- **Case**: `run#falsificationWithAChangedTreeIsBlocked`

#### Scenario: The skips this change does not re-route are unchanged

- **WHEN** an item is skipped because it is too large for one fix, is not spec-shaped, names no target, needs a
  device, or needs diagnosis
- **THEN** each leaves the state it wrote before this change existed — the bare hold with its one comment for
  the hold verdicts, `agent/needs-device` with the hold for the device verdict, and `agent/needs-decision` with
  the hold for `needs-diagnosis` — and none of them is closed
- **Case**: `labels#verdictToLabelMatrix`, `prescreen#deviceItemIsAVerdictNotWork`

#### Scenario: The reviewer reads the claim, not only the diff

- **WHEN** a falsification reaches the review round with an empty diff
- **THEN** the review still happens on that empty diff, and an item whose tree no longer stands where the claim
  said it stood is escalated instead of closed
- **Case**: `run#falsificationWithAChangedTreeIsBlocked`

### Requirement: A blocked attempt's branch is kept under a name of the run's own, and its item is worked

An item whose branch carries commits the base does not have and adds no archived change of its own SHALL not be
recorded as awaiting a decision; nor SHALL one whose branch adds an archived change whose last round was answered by
a refusal and whose rework the run's own bound ended, because nothing on that branch states a question and no
reviewer graded the phase the bound cut. Its branch SHALL be renamed outside the `fix/issue-<N>-` prefix, so its
commits stay reachable and no later run reads it as an attempt, and the item SHALL be worked on a fresh branch. A
branch the run cannot classify SHALL be the owner's, never work; and an item that carries a stated question of its
own remains its owner's whatever its branch holds.

#### Scenario: The rework-cut branch is queued as work

- **WHEN** an eligible item's branch carries an archived change, a green verification, and a round whose rework the
  run's own bound ended, and the item awaits no decision of its own
- **THEN** the pre-screen's verdict is not a decision, the item is queued as work with the branch as its evidence,
  and `branch-state` reads `rework-cut` rather than the refusal that round recorded
- **Case**: `prescreen#aReworkCutBranchIsWork`

#### Scenario: The branch whose rework the bound ended is set aside and its item worked

- **WHEN** an item starts and its branch carries an archived change whose last round was refused and whose rework
  the run's own bound ended
- **THEN** the branch is renamed under the run's leftover prefix with its commits still reachable there, and the
  item is worked on a fresh branch from the run's base with the set-aside printed instead of a refusal
- **Case**: `worktree#setsAsideAReworkCutBranchAndStartsFresh`

#### Scenario: The pre-screen queues the item and names the branch it will set aside

- **WHEN** an eligible item's branch carries commits the base does not have and adds no archived change of its
own — a change another item's commit added does not count
- **THEN** the verdict is not a decision, the item is queued as work, and the run's record names the branch as
  the evidence and the set-aside as the action
- **Case**: `prescreen#aBlockedAttemptBranchIsWork`

#### Scenario: The start keeps the commits and works the item

- **WHEN** an item starts and its branch is in that state, whether the branch was there before the run or
  appeared while the run was open
- **THEN** the branch is renamed under the run's leftover prefix, its commit is still reachable there, the item
  is worked on a fresh branch from the run's base, and the run prints the rename instead of refusing
- **Case**: `worktree#setsAsideABlockedAttemptAndStartsFresh`,
  `run#branchThatAppearsWhileTheRunIsOpenIsSetAside`

### Requirement: The mutation harness leaves the tree exactly as it found it

The harness SHALL keep the backups of the files it mutates beside the tree it mutates, on the tree's own
filesystem, unless the caller names another place. A backup it could not take SHALL stop the harness before
anything is mutated, and a restore SHALL never write a backup that is empty or missing.

#### Scenario: The backups are not the host's tmpfs

- **WHEN** the harness runs with no `TMPDIR` set
- **THEN** its scratch tree lies under the project's gitignored scratch root, it is named once, and it is
  removed when the harness exits; `TMPDIR` still moves it
- **Case**: `tools/loop/tests/loop-mutations-selftest.sh` (`the scratch tree is the project's, not the host's tmpfs`)

#### Scenario: A backup that did not land stops the harness instead of emptying the file

- **WHEN** a backup cannot be taken, or the restore is offered a backup that is empty or missing
- **THEN** the harness exits 2 with the reason, the file it protects still holds its content, and nothing is
  mutated after that point
- **Case**: `tools/loop/tests/loop-mutations-selftest.sh` (`a restore from an empty backup exits 2`, via
  `tools/loop/tests/loop-mutations.sh --probe empty-backup`)

### Requirement: A tracker failure the transport caused is not an answer

Every call the tooling makes to the tracker SHALL pass one seam that classifies the failure. A failure the
transport caused — a TCP or TLS timeout, a DNS failure, a reset or refused connection, a 5xx, a secondary
rate limit — SHALL be retried with a backoff before the caller sees it, and a failure that survives the
retries SHALL be reported as *unknown* through a distinct exit status and a `tracker-unreachable` reason, so
a caller can tell an outage from an answer instead of reading one as the other.

A failure the tracker itself produced — a validation refusal, an authorisation refusal, a not-found — SHALL
NOT be retried, because repeating it spends the run's time to hear the same answer.

An append SHALL NOT be retried either, because the tracker may already have accepted it: posting a comment
and filing a new issue are exempt, and their transport failure reaches the caller unchanged.

#### Scenario: One timeout is retried instead of becoming an empty answer

- **WHEN** a read fails the way a dropped connection fails, and a later attempt answers
- **THEN** the caller receives that answer, and no caller sees the outage
- **Case**: `lib#retriesATransientFailure`

#### Scenario: A failure that outlasts the retries is unknown, not empty

- **WHEN** every attempt of one call fails transiently
- **THEN** the call reports `tracker-unreachable` and exits 75, so the caller can tell it from an answer
- **Case**: `lib#reportsAnUnreachableTrackerAfterTheLastRetry`

#### Scenario: An authoritative refusal is passed through, not retried

- **WHEN** the tracker refuses a call rather than failing to carry it
- **THEN** the refusal reaches the caller unchanged and at once
- **Case**: `lib#doesNotRetryAnAuthoritativeFailure`

#### Scenario: An append is attempted once

- **WHEN** a comment or a new issue fails the way a dropped connection fails
- **THEN** the seam attempts it exactly once and hands the failure back, so nothing is posted or filed twice
- **Case**: `lib#doesNotRetryAnAppend`

### Requirement: An item the tracker does not answer for is unknown, not the end of the run

A read of the item a run is about to work SHALL be read the way the pre-screen reads the same seam's same failure:
as an *unknown* about that item, never as a verdict about the run. When the read fails, the run SHALL record the
item — `blocked`, with the verdict `unreadable` and the CLI's own words — and SHALL continue with the next candidate
in its queue, and SHALL NOT end on that read. The failure it records is the one the seam handed back — a refusal the
seam passes through without retrying, or a transport failure it retried and reported as `tracker-unreachable` — and such
a record is a refusal, so a tracker that answers nothing SHALL still end the run, through the refusal streak the run's
own `next` reads (`LOOP_REFUSAL_STREAK_STOP`, `tools/loop/loop.conf`; 4 in this tree), and never through the first
item's socket.

#### Scenario: An unreadable item is recorded and the queue moves on

- **WHEN** the tracker fails the read of the item the run selected, with a failure the seam passes through rather than
  retries
- **THEN** the run records that item `blocked` with the verdict `unreadable` and the CLI's own words in the record,
  prints what the CLI said, and ends on its own item bound rather than on that read
- **Case**: `run#anUnreadableItemIsNotTheEndOfTheRun`

### Requirement: An identity that could not be read is not a refusal

An identity that could not be read SHALL NOT be reported as `author-not-allowlisted`: an unreachable tracker
SHALL answer `tracker-unreachable` with no verdict, and only an answer that names no identity may be reported
as a policy refusal. The identity SHALL be read once, in the shared preflight every write path calls, so no
call site can fold the distinction back into an empty string; a decision that needs no identity — `check`,
which judges the issue's own author from the record it is handed — SHALL NOT read one.

#### Scenario: An unreachable tracker is not a policy refusal

- **WHEN** the identity read cannot reach the tracker
- **THEN** the preflight reports `tracker-unreachable` with decision `unknown` and exit 75, and neither it nor
  the text form reports `author-not-allowlisted`
- **Case**: `policy#preflightReportsAnUnreachableTracker`

#### Scenario: Every write path reports the outage, not a refusal

- **WHEN** a write path's identity read cannot reach the tracker
- **THEN** it exits 75 with `tracker-unreachable` instead of the `author-not-allowlisted` an empty login
  produced
- **Case**: `update#reportsAnUnreachableTrackerInsteadOfARefusal`

#### Scenario: A decision the issue record answers does not depend on the tracker

- **WHEN** `policy.sh check` judges an issue record while the identity read would fail
- **THEN** it decides from the record — an allowlisted author with the opt-in label allowed, a foreign author
  refused — and reads no identity at all
- **Case**: `policy#checkAnswersWithoutAnIdentityRead`

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

#### Scenario: A phase line is read from the stream, not at a fixed instant

- **WHEN** a case that guards a run's phase line reads the console while the run is still working, to assert
  that the line has reached it
- **THEN** it waits, within a bound, for the line the phase itself prints, and does not read at an instant
  fixed before the run can have reached the phase — because the driver's compile of the issue tooling's core,
  the run's state write and its decision count all precede the phase, and a fixed instant reports their cost as
  a verdict about the run
- **AND** the wait does not soften the guard: the console of a run that captured the phase's output and
  replayed it after the call returned still fails the assertion, because the moment the waited-for line is read
  then carries the line the phase had not printed yet
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

### Requirement: The item is closed on a base that carries its fix

A run SHALL merge the branch it committed an item on onto the base it recorded before closing that item, and a
merge it cannot take SHALL leave the item open: `status/fixed-by` and "the fix is in the tree" SHALL be one
claim, so a closure the tree does not back is a blocked item with its branch named, never a settled one.

#### Scenario: The branch the run committed is merged before the item is closed

- **WHEN** an item passed its review with an archived change and the run had already committed that change and
  its evidence on the item's own branch
- **THEN** the branch's commits are on the base before the item is closed, the base the run recorded advances
  to the merge, and the branch with its commits is named in the run's record and in the closure's evidence
- **Case**: `run#mergesItsCommittedBranchBeforeClosing`

#### Scenario: A merge over a project tree that is not clean is refused, and nothing is closed

- **WHEN** an item passed its review and the run is about to close it, and the project tree the base branch is
  checked out in is not clean
- **THEN** the merge is refused with the reason it found and the base stays where it was: it does not merge over
  work it would attribute to itself, the item is blocked with its branch named instead of closed, and the
  closure that follows a merge is never reached
- **Case**: `run#doesNotCloseOnAMergeTheBaseCannotTake`

#### Scenario: A branch the base does not descend from is not replayed

- **WHEN** the merge of an item's branch is asked for and the branch's commits are not on top of the base the
  run recorded
- **THEN** the action refuses with the reason it found and the base stays where it was: it does not replay the
  branch onto a tree nobody reviewed, so the branch's commits stay off the base
- **Case**: `worktree#refusesToReplayABranchTheBaseDoesNotDescendFrom`

#### Scenario: The landing step verifies the tree it merges into

- **WHEN** the merge of an item's branch is asked for — by the run's success path or by the pre-screen's
  adoption — and the project's own tree, which the merge lands in, has a branch other than the base branch
  checked out, or a detached HEAD
- **THEN** the merge is refused before anything is merged, naming the branch the tree is on and the base branch
  it would have landed on, so neither the base branch nor the branch the tree holds moves and no item is closed
  against a tree that never carried its commits
- **Case**: `worktree#refusesToMergeIntoATreeOffTheBaseBranch`

#### Scenario: The recorded base is a commit the base branch carries

- **WHEN** the run records a new base after a merge and the commit it hands the record is not one the base branch
  descends from
- **THEN** the record refuses it, naming the base branch, and keeps the base it already names: a closure is
  recorded against a base the base branch carries, never against a commit the tree that closed the item does not
  have
- **Case**: `state#setBaseRefusesACommitTheBaseBranchDoesNotCarry`

### Requirement: The package carries the last recorded decision and its question

A work package SHALL carry the item's last recorded decision together with the question that decision answered,
inside a delimited data field of its own. The item's body keeps stating a settled question as open, so a package
that omits the answer leaves a fresh process to re-derive it.

#### Scenario: The last recorded decision travels with the question it answered

- **WHEN** a work package is rendered for an item whose last answer comment exists
- **THEN** the package names the answer, the question it answered and the timestamp the answer was recorded at,
  in a block a reader can tell apart from the item's claim
- **Case**: `WorkPackage#recordedDecisionIsCarried`

#### Scenario: The run asks the tracker for the comments

- **WHEN** the run prepares an item's package
- **THEN** it reads the item with its comments, so the block has something to carry — the pre-screen keeps
  reading candidates without them
- **Case**: `run#workPackageCarriesTheRecordedDecision`

#### Scenario: An answer names the question it answers

- **WHEN** an answer is recorded on an item
- **THEN** the comment carries the question that stood when the answer was written, so a later escalation that
  numbers its own options from 1 cannot re-point a bare answer at a different option
- **Case**: `answer#theAnswerNamesTheQuestion`

### Requirement: An unanswered item says so

A work package SHALL state that no decision is recorded when the item carries none, and SHALL NOT infer an
answer from a question. The field is present rather than omitted, so a reader does not have to tell an empty
decision from a package that cannot carry one.

#### Scenario: No answer, no inference

- **WHEN** a work package is rendered for an item with no answer comment, or nothing but escalations
- **THEN** the package states that no decision is recorded and derives no answer from the question it carries
- **Case**: `WorkPackage#noDecisionIsRecorded`

### Requirement: A recorded decision is settled for the worker

A worker SHALL treat a recorded decision as the owner's settled policy — implement it rather than re-derive the
fork — and an escalation that follows one SHALL name that decision and say what it leaves open.

#### Scenario: The prompt says what a decision means

- **WHEN** a worker reads its prompt
- **THEN** it is told that a recorded decision is settled, where that decision is delimited in the package, and
  that an escalation following one names it and what it leaves open
- **Case**: `prompt#workerTreatsARecordedDecisionAsSettled`

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

### Requirement: A change names its surface before its plan is fixed

A change that rewrites a rule rather than an artifact SHALL measure the rule's surface before its plan is
fixed — the cases, fixtures and prompt or engine files that assert the behaviour being changed — and SHALL
carry that count in its proposal under a `surface:` line, with the command that found it. The count, not the
first file the plan names, is what the estimate is set against; a plan made before it exists SHALL NOT be read
as the change's cost.

#### Scenario: The worker counts the surface before it plans

- **WHEN** a run renders the worker's task for an item whose change rewrites a rule
- **THEN** the task asks for the surface count before the plan is fixed, names the search that produces it, and
  names the `surface:` record the change's proposal carries
- **Case**: `prompt#workerNamesTheChangeSurfaceBeforeThePlan`

#### Scenario: The protocol states the pass a session outside the loop owes

- **WHEN** a session that changes this project reads the iteration protocol
- **THEN** the protocol states the surface measurement beside the symptom measurement it states first, and
  states the record it leaves, so a session without the loop's task meets the same rule
- **Case**: `prompt#iterationProtocolNamesTheChangeSurface`

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

### Requirement: A mutation run is scoped by the files the change touches

The mutation harness SHALL select its records by the file each record mutates as well as by its label: a
named file set SHALL run exactly the records whose file is one of the named paths, and a base commit SHALL
name that set by the paths the diff against it touches, together with the working tree. A filtered run SHALL
state what it selected against the declared set (`selected N of M records`), and a selection that names no
record SHALL exit 2 without mutating anything. A run that names neither a file nor a label SHALL run the whole
set, exactly as before.

#### Scenario: A file set lists exactly the records those files own

- **WHEN** the harness lists the selection of a file set whose path one or more records mutate
- **THEN** it prints those records and no other, and the list is the set the run would execute
- **Case**: `tools/loop/tests/loop-mutations-selftest.sh` (`a file set lists exactly the records its file owns`)

#### Scenario: A scoped run runs exactly those records and says how much of the set it ran

- **WHEN** the harness runs a file set whose path one or more records mutate, against an engine copy
- **THEN** exactly those records are mutated and go red, each restore goes green, the harness prints
  `selected N of M records`, and the copy is byte-identical to the pristine engine afterwards
- **Case**: `tools/loop/tests/loop-mutations-selftest.sh` (`a file set runs the records its file owns`)

#### Scenario: A selection no record owns is refused

- **WHEN** the named file set contains no file any record mutates
- **THEN** the harness exits 2, says the selection names no record of the declared set, and runs no record — a
  green exit for having done nothing is the silent lie the harness exists to prevent
- **Case**: `tools/loop/tests/loop-mutations-selftest.sh` (`a file set no record owns exits 2`)

#### Scenario: A base commit names the set by the diff it touches

- **WHEN** the harness is given a base commit in a tree where one record's file is changed since that commit
- **THEN** the selection is that record, derived from the diff and the working tree, and no other
- **Case**: `tools/loop/tests/loop-mutations-selftest.sh` (`a git diff names the records its file owns`)

### Requirement: The round a corrective refusal buys is handed the corrections

A run SHALL hand the round it grants after a corrective refusal — a report that states every finding is
subtractive (`LOOP-CORRECTIONS-ONLY: yes`) at the round budget — the correction round alone: the diff between
the refusing round's own HEAD and the current HEAD, under the same recorded exclusion of generated output every
other round uses. The same round's prompt SHALL carry the refusing round's report by path and quoted, and
SHALL state that the diff is that range rather than the change. Every other round SHALL keep reading the
change from the run's base.

#### Scenario: The extra round reads the corrections, not the change again

- **WHEN** a corrective refusal at the round budget buys one more round and the correction worker commits
- **THEN** that round's diff is taken from the refusing round's HEAD, so the line the corrections replaced
  appears in it only as context and not as a change, while the round at the cap carries the change from the
  run's base
- **Case**: `run#aCorrectiveRoundIsHandedTheCorrections`

#### Scenario: The refusing findings travel into the extra round

- **WHEN** the run renders the prompt of the round a corrective refusal bought
- **THEN** the prompt names the refusing round's report, quotes its findings, and states that the diff under
  review is the correction round alone — and a first round's prompt carries no such section
- **Case**: `run#aCorrectiveRoundIsHandedTheCorrections`

### Requirement: A feature is planned before it is applied

A `class/feature` item the profile works SHALL be queued like a defect. It SHALL be worked by the
propose-first path: the worker SHALL write an OpenSpec change named `add-<slug>` whose proposal, capability
delta, design and tasks exist before production code, and whose acceptance scenario is the red case that fails
on HEAD because the capability is absent. A feature SHALL NOT be refused merely because it is a feature.

#### Scenario: A feature is queued, not refused

- **WHEN** the profile's `workedClasses` names `feature` and an eligible feature is in the backlog
- **THEN** the pre-screen gives it an eligible verdict and a rank in the profile's class order, instead of a
  `class-feature` refusal
- **Case**: `prescreen#ranksBugsFirstAndKeepsFeatures`

#### Scenario: A feature's plan precedes its apply

- **WHEN** the worker takes a `class/feature` item
- **THEN** its prompt carries the item's class, names the `add-<slug>` change and the acceptance scenario it
  owes as the red case, and the proposal, capability delta, design and tasks are written before production code
- **Case**: `prompt#workerCarriesTheFeaturePath`

#### Scenario: A profile may still refuse features

- **WHEN** the profile's `refusedClasses` names `feature`
- **THEN** the item is refused with `class-feature` and the profile's refusal label, exactly as before
- **Case**: `prescreen#aRefusedClassStillStopsTheScreen`

### Requirement: One run at a time holds the repository

One run at a time SHALL hold a repository: the lock that keeps one run per repository SHALL be taken before
the run writes anything, a run that finds a live lock SHALL be refused without writing state, branch or
label, and a lock whose owner is no longer running SHALL be taken over and announced.

#### Scenario: A second run is refused before it writes anything

- **WHEN** a run is started while another run holds the lock
- **THEN** the second run is refused with a message that names the lock and its owner, it writes no state, no
  branch and no label, and the live run's state is the one left in place — the run that was refused never
  replaced the record of the run that is working
- **Case**: `state#aRefusedRunLeavesTheLiveStateAlone`

#### Scenario: A lock left by a run that is gone is taken over

- **WHEN** a run finds a lock whose owner is no longer running
- **THEN** it takes the lock over, says so in one line naming the process that is gone, and starts — a run
  that was killed leaves no tombstone that refuses every run after it
- **Case**: `state#aDeadOwnersLockIsTakenOver`

### Requirement: A branch the loop made is removed when its work is in the base

A branch the run committed for an item and merged SHALL be removed once no worktree holds it, a branch whose
patches the base already holds SHALL be named as removable, and a branch that still carries unlanded work
SHALL be left alone and named as work to land. A branch the run renamed aside as a previous attempt's leftovers
SHALL be judged by that same reading — a candidate of both removal paths, never a name nothing enumerates — so it
SHALL be removed once the base carries its patches and SHALL be kept, and named with the work it carries, while the
base does not.

#### Scenario: A branch the run merged is removed

- **WHEN** the run merges the branch it committed for an item
- **THEN** the worktree is released from that branch and the branch is removed, so a closed item leaves no
  branch behind, and the next item still starts its own branch from the base
- **Case**: `run#aMergedBranchIsRemoved`

#### Scenario: The merged branch is gone before the run's report, not by the run's end sweep

- **WHEN** the run merges the branch it committed for an item and prints the report it ends with — the report
  being printed before the run sweeps the branches it left behind
- **THEN** that report names no branch of the item, because the branch is already removed at the merge, so the
  end-of-run sweep is not what a reader is observing and the release's own deletion is falsifiable by itself
- **Case**: `run#aMergedBranchIsRemoved`

#### Scenario: A branch that still carries work is never removed

- **WHEN** the sweep of what the loop left behind meets a branch whose patches the base does not have, or a
  branch a worktree holds
- **THEN** that branch is kept and named with the reason it was kept, and only a branch that carries nothing
  is removed
- **Case**: `clean#sweepsOnlyWhatIsInTheBase`

#### Scenario: The report names the branches that can be removed

- **WHEN** a run ends and the branches it sees carry no patch the base does not have
- **THEN** the report names those branches apart from the ones that carry work, so a reader can tell the
  leftovers from the work without asking git
- **Case**: `report#landedBranchesAreNamedAsRemovable`

#### Scenario: A set-aside branch whose work the base carries is swept

- **WHEN** a branch the run set aside as a blocked attempt's leftovers carries only patches the base already holds
- **THEN** both removal paths name it as removable, a read-only run removes nothing, and `--apply` removes it — so a
  set-aside branch whose work was landed by hand does not stay for ever
- **Case**: `worktree#aSetAsideBranchWhosePatchesLandedIsRemoved`

#### Scenario: A set-aside branch that still carries work is kept and named

- **WHEN** the sweep of what the loop left behind meets a set-aside branch whose patches the base does not have
- **THEN** the branch is kept and named as work the base does not have, and its commit is still reachable from it, so
  the attempt's only copy is never the thing a leftovers rule throws away
- **Case**: `worktree#aSetAsideBranchThatCarriesWorkIsNamedNotRemoved`

### Requirement: A declared mutation record is checked against the file it names

The mutation harness SHALL offer a check that applies every selected record's expressions to a copy of the file the
record names, and reports each record where one of them changes nothing — running no case, mutating nothing. It
SHALL name a stale record by its label and its file, exit 1 when one is stale, and exit 0 only when every selected
record applies. The project's gate SHALL run it for a delta that touches a path under the engine's directory or the
set itself.

#### Scenario: A record that names no current line is reported

- **WHEN** one declared record's expressions are pointed at a file in which one of them changes nothing, and the
  check is run for that record
- **THEN** it exits 1 and prints `MUTATION-PATTERN-STALE`, the record's label and the file the record names, having
  run no case
- **Case**: `mutation#aStaleRecordIsNamed`

#### Scenario: A set in which every record applies passes

- **WHEN** the check is run over the whole declared set against the engine the set is declared in
- **THEN** it exits 0, prints no `MUTATION-PATTERN-STALE` line, and its summary counts the records the harness
  declares
- **Case**: `mutation#everyDeclaredRecordApplies`

#### Scenario: The gate owes the check for an engine path

- **WHEN** a delta touches a path under the engine's directory and the gate is asked what that delta owes without
  running anything
- **THEN** the record check is one of the owed checks, named with the command that runs it
- **Case**: `gate#anEnginePathOwesTheRecordCheck`

### Requirement: A review round is a record on the branch, from its announcement to its answer

A review round SHALL be readable from the item's branch in both directions: the round SHALL be recorded on the
branch before its reviewer starts, and its answer SHALL be recorded when the answer arrives, so that a round the
reviewer never answered is a round a later run can see is unanswered rather than one that reads like a pass. A
refusal the run's own bound then cut the rework of SHALL be recorded as that: the announcement stays, and beside it
`review-round-<n>-rework-cut.md` says the round was asked and answered and what did not finish is the answer to the
refusal — neither `cut` (the run ended the round, not its rework) nor silence (which reads as a question) describes
it.

#### Scenario: The rework of a refusal the bound ended is recorded beside the round

- **WHEN** the reviewer refuses the change in round `<n>` and the run starts the rework worker of that round
- **AND** the run ends that worker at a bound it enforces
- **THEN** the branch carries the round's announcement beside `evidence/review-round-<n>-rework-cut.md`, and the
  round reads as neither a pass nor a silence
- **Case**: `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`

#### Scenario: The round is announced before the reviewer runs, and its answer is recorded when it arrives

- **WHEN** a review round of an archived change ends `PASS`
- **THEN** the item's branch carries the round's announcement, written as a commit before the reviewer started, and
  the pass as a second commit written when the answer arrived — both inside the change's own evidence directory —
  so a stranger to the run reads the round's whole life from the branch and not from a machine-local directory
- **Case**: `run#announcesAndRecordsTheReviewRound`

#### Scenario: A branch whose last review round never answered is not adopted

- **WHEN** an item's branch carries one archived change of its own with a green verification record, and the
  highest review round the branch names was announced and holds no answer — neither a pass nor a refusal
- **THEN** the branch is refused, named and escalated as the owner's decision exactly as a refused review is, and
  the refusal names the round that stayed silent and the announcement it reads, so a change no reviewer graded is
  never merged and its item never closed on it
- **Case**: `worktree#refusesABranchWhoseReviewRoundNeverAnswered`

### Requirement: A process the child's group signal cannot reach is ended by the tree it works in

The child cap SHALL end everything the child left running, not only the members of its process group: a
descendant that gave itself a session of its own is found by the working directory the kernel reports for it,
ended with the loop's own signal discipline, and named in the transcript before the item's outcome is written.

#### Scenario: A child the cap ends has left a writer working in the change root

- **WHEN** the wall-clock cap ends a child, and that child has started a process whose working directory lies
  inside the change root and which is a member of no group the cap signalled
- **THEN** the run ends that process too, so nothing of the child is still writing the tree when the item's
  outcome is written, and the transcript names each process it ended
  (`LOOP-ENDED: ended <pid> — its working directory is inside <tree>`)
- **Case**: `run#detachedProcessInTheWorktreeIsEndedByTheCap`

### Requirement: The gate streams a suite's output while it runs

A gate SHALL print a step's output as the step writes it and derive the numbers it reports and keeps from that
stream, because a captured output is silent for the suite's whole wall clock, and a red suite's failure text
survives only as the tail of a tally. That stream SHALL be live where nothing can interleave with it — a step the
engine runs alone, and a wave of one — and a wave of several steps SHALL print each step's output whole, under that
step's own `gate: <command>` header, once the wave has drained: two writers on one console interleave their bytes,
and a failing case would then belong to the step that happened to print next. A step SHALL end with the command it
ran, not with a writer that outlived it, and the suite's exit status SHALL stay the verdict, never the stream's.

#### Scenario: A suite's output reaches the console before the suite exits

- **WHEN** the gate runs a suite that writes a line at once and keeps working after it, alone
- **THEN** that line is on the gate's console while the suite is still running, and a line the suite has not
  written yet is not — so a suite that runs for minutes shows its own progress instead of a two-line log an
  operator cannot tell from a stopped one
- **Case**: `gate#theSuitesOutputReachesTheConsoleWhileItRuns`

#### Scenario: A wave of several prints each step whole when it ends

- **WHEN** the gate runs two or more suites in one wave, each writing a line at once and keeping working after it
- **THEN** nothing of either step's output is on the console while the wave runs, and when the wave has drained each
  step's output is there whole, between that step's own `gate: <command>` header and its own `— <n>s` line, in the
  profile's order — so a failure and the step that spent it are read from one block no neighbour's bytes crossed
- **Case**: `gate#aPooledWavePrintsEachStepWholeWhenItEnds`

#### Scenario: The console and the tally are one observation

- **WHEN** a suite fails and writes its failing case and its own summary
- **THEN** the gate's verdict is that suite's exit status, the summary's numbers are the `tests=`/`failures=` it
  reports, and the tally under the configured directory carries those numbers together with the failure text —
  so what an operator saw and what the run's own check reads are the same bytes, not two observations
- **Case**: `gate#aFailingSuiteIsReadFromTheSameStream`

#### Scenario: A step ends with the command it ran

- **WHEN** a step's command prints its verdict, starts a writer that outlives it, and exits — the shape
  `LOOP_CASE_MAX_SECONDS`'s watcher has when a suite ends before its watchers do
- **THEN** the step ends with its command, so the gate's console loses nothing and the wall clock the step records
  covers the command it ran rather than the writer the command left behind
- **Case**: `gate#aStepEndsWithItsCommandNotWithItsWatchers`

#### Scenario: A suite's line is read from the stream, not at an instant

- **WHEN** a case that guards the stream reads the gate's console while the gate is still working, to assert that
  a suite's line has reached it
- **THEN** it waits, within a bound, for the line the suite itself prints, and does not read at an instant fixed
  before the gate can have reached the suite — because the profile reads, the `git` calls over the tree and the
  de-duplication of the owed commands all precede the suite, and a fixed instant reports their cost as a verdict
  about the stream
- **AND** the wait does not soften the guard: the console of a gate that captured the suite's output and replayed
  it after the command returned still fails the assertion, because the moment the waited-for line is read then
  already carries the summary the suite had not printed yet
- **Case**: `gate#theSuitesOutputReachesTheConsoleWhileItRuns`

### Requirement: A suite that reported no summary invents no number

The gate SHALL take a suite's case and failure counts only from that suite's own summary line. A suite that
fails without reporting one SHALL be recorded as an error instead: its tally carries `tests="0"`,
`failures="0"` and `errors="1"`, and the gate's own line carries the error count beside its case and failure
counts. A tally meant to prove what executed SHALL NOT state a case or a failure the suite did not report.

#### Scenario: A suite that failed before it reported anything

- **WHEN** a suite exits non-zero without writing either summary line
- **THEN** its tally carries zero cases, zero failures and one error, the gate's own line carries the same
  three facts and still names the suite as failing, and the gate's exit status stays non-zero
- **Case**: `gate#aFailingSuiteWithNoSummaryInventsNoTest`

#### Scenario: A suite that reported its numbers keeps them

- **WHEN** a suite fails and writes `<name>: M of N cases failed`
- **THEN** its tally carries N cases and M failures with no error, and the gate's line carries the same numbers,
  so a reported count is what the run's own check reads
- **Case**: `gate#aFailingSuiteIsReadFromTheSameStream`

### Requirement: The self-test compiles the Java core when a case asks for it

The loop's self-test SHALL compile the issue tooling's Java core at the first selected case that asks for it, at
most once per suite, and SHALL NOT compile it before the case list is known: an invocation whose selected cases
ask for none SHALL pay no compile. A case that asks for the core and cannot have it SHALL fail with exit 1 and the
line `loop-selftest: the issue tooling's Java core does not compile`, and the run SHALL end with exit 1.

#### Scenario: A selection that asks for no core compiles none

- **WHEN** the driver runs one case that never asks for the core, and `javac` on `PATH` is a probe that records its
  invocation and fails
- **THEN** the case runs and passes, and the probe records no invocation
- **Case**: `driver#aCaseThatAsksForNoCoreCompilesNothing`

#### Scenario: A case that asks for the core is handed it, or the suite refuses

- **WHEN** a selected case asks for the core
- **THEN** it is handed the class directory the driver compiled for the suite; and when the core cannot compile,
  the case fails with exit 1 and the line that names the cause, and the run ends with exit 1
- **Case**: `driver#aCaseThatAsksForTheCoreGetsItOrRefuses`

#### Scenario: A suite whose selection asks twice compiles once

- **WHEN** the driver runs a suite of two cases that both ask for the core, and `javac` on `PATH` is a probe that
  records each invocation and succeeds
- **THEN** both cases are handed the compiled class directory and the probe records exactly one invocation
- **Case**: `driver#theCoreIsCompiledOncePerSuite`

### Requirement: A suite run selects what it was asked for and the gate records what it cost

The loop's self-test SHALL select over its declared cases: `--group <g>` for one group, `--case` for a name or a
pattern, both at once, and `--list` printing the same selection. A filter that names no declared case SHALL be
refused with exit 2 before any case runs. The gate SHALL record each suite's wall clock in seconds, on its own
line, in the `LOOP-GATE:` summary and in the tally.

#### Scenario: A group filter runs that group's cases and no other group's

- **WHEN** the driver is run with `--group <g>` against a declared set of cases in two groups, one of which fails
  whenever it is executed
- **THEN** only the group's cases run, the suite exits 0, and the selection is named in the summary line
- **Case**: `driver#aGroupFilterRunsOnlyItsOwnCases`

#### Scenario: A filter that names no case is refused before a case runs

- **WHEN** `--group <unknown>` or `--case <pattern matching nothing>` is passed
- **THEN** the driver exits 2 naming the group or the pattern it could not resolve, and no case has been executed
- **Case**: `driver#aFilterThatNamesNoCaseRefuses`

#### Scenario: The list answers the same question the run does

- **WHEN** `--list` is asked with a filter, and then without one
- **THEN** it prints exactly that filter's cases, and then the whole declared set, and runs nothing either time
- **Case**: `driver#theListIsFilteredTheSameWayASuiteRunIs`

#### Scenario: The gate's record names what each suite cost

- **WHEN** the gate runs a suite whose own wall clock is known, and it passes
- **THEN** the suite's line, the `LOOP-GATE:` summary beside the command and the tally's `time` attribute all carry
  that number of seconds
- **Case**: `gate#theSummaryNamesWhatEachSuiteCost`

### Requirement: A path is compiled by the tree it sits in or a build input's convention

The gate SHALL decide that a path is compiled from the tree it sits in — a module's `src/` tree or its `lint.xml` —
or from a name that is the convention for a build input (a `.gradle.kts` or `.gradle` script, a `.toml`, `CMakeLists.txt`), which classify wherever they sit; an extension that names anything else — `*.xml`, `*.kt`, `*.java`, `*.c`, `*.h` — SHALL NOT be enough. Every pattern SHALL be compared as a pattern, not expanded against the directory it runs in.

#### Scenario: An archived artefact owes no compiled command

- **WHEN** a delta's only paths are an archived change's evidence tally, and then a gate fixture that mimics a test
  report, and the gate is asked what each delta owes without running anything
- **THEN** the plan names no compiled command for either, and names the checks each delta does owe — the archived
  tally's delta owes `check-classification` — so a delta that touches no compiled file is not made to run the build
- **Case**: `gate#anArchivedArtefactOwesNoBuild`

#### Scenario: A module's source tree and its lint configuration still owe the build

- **WHEN** a delta touches a resource in a module's `src/` tree, and then that module's `lint.xml`, in a tree that
  holds the directory the pattern names
- **THEN** the plan names the compiled command for each, because a pattern is matched as a pattern and a module's
  sources and lint configuration are what the build reads
- **Case**: `gate#aModuleResourceOwesTheBuild`

### Requirement: A tally's name is a file a reader finds

The gate SHALL name each suite's tally so that no reader misses it: the name SHALL NOT begin with a dot, and the
`./` of a command run from the project root SHALL be dropped before the name is built. The rest of the name SHALL
stay the command that wrote it, so a reader can still match a tally to its suite.

#### Scenario: A compiled step's tally is not hidden

- **WHEN** the profile's compiled command is written with the `./` a command run from the project root needs, a
  compiled path owes that command, and the gate has written its tally
- **THEN** a plain `ls` of the configured tally directory prints the tally, and a `*.xml` glob over it matches the
  file named after the command without its `./` prefix
- **Case**: `gate#aCompiledStepsTallyIsNotHidden`

### Requirement: The gate reads the profile of the tree it measures

The gate SHALL resolve its configuration from the profile beside its own engine — the tree whose paths it measures — unless `LOOP_CONF` pins one, and SHALL name that profile in the log it writes, calling a profile read from outside that tree pinned, beside the one the engine ships. A run SHALL resolve its own configuration and SHALL NOT hand it to a child that works in another tree.

#### Scenario: A gate names the profile it read

- **WHEN** the gate runs, once with the profile beside its own engine and once with a profile pinned from outside it
- **THEN** the log carries a `LOOP-GATE-PROFILE:` line naming the file each run read, and the pinned one says it is not the profile beside the engine
- **Case**: `gate#theGateNamesTheProfileItRead`

#### Scenario: A child's gate reads its own tree's profile

- **WHEN** a run starts a worker whose change root is a worktree carrying its own engine, and the worker runs that engine's gate there
- **THEN** the gate names the profile beside the worktree's engine, and the run's own profile is not in the child's environment
- **Case**: `gate#theChildReadsTheProfileOfTheTreeItWorksIn`

### Requirement: The publish publishes what still exists

The run's only push SHALL publish what still exists when it runs: a branch the run has already merged and released
SHALL be a named skip rather than a git error, and a push that fails for another reason SHALL say so on one line.

#### Scenario: A branch the run merged and released is a named skip

- **WHEN** a run is asked to publish and the item's branch has already been merged into the base and removed by the
  run's own release
- **THEN** the run prints that there is nothing to publish for that branch and why, and hands git no refspec at all
  — the closure it belongs to is unaffected, and no `does not match any` reaches the run log
- **AND** the remote carries no branch of a closed item
- **Case**: `worktree#pushSkipsABranchItAlreadyMerged`

#### Scenario: A push that fails says what it means

- **WHEN** the branch still exists and the push does not succeed
- **THEN** the run prints one line naming the branch and where its commits stayed — the run tree, not the remote —
  so a caller reads a publishing failure as one instead of as git's own error text

### Requirement: A case's files select it, and the gate hands a suite the base it measured

A case SHALL declare the files it can be affected by in the first line of its body (`# files: <token> …`), and
the self-test SHALL select on that declaration with `--files <path> …` and `--from-git <base>`. A file set that
names no case SHALL be refused with exit 2 before any case runs — unless the invocation names `--allow-empty`,
which SHALL instead run no case, state the file set it read and the declared set it selected against, print
`LOOP-SELFTEST: no case declares the delta`, and exit 0, so an empty intersection of a real delta is told apart
from a file set a typo made unresolvable. The selection SHALL be stated against the declared set, a core file
SHALL owe every case, and a profile's `{base}` SHALL be replaced by the base the gate resolved.

#### Scenario: A file set runs the cases that declare it and no others

- **WHEN** the driver is run with `--files` naming a script one case declares and a path below the directory
  another declares, against a declared set of four cases
- **THEN** exactly those two cases run, the suite's summary counts two, and the selection is stated as two of four
- **Case**: `driver#aFileFilterRunsOnlyTheCasesTheDeltaOwns`

#### Scenario: A file set that names no case is refused

- **WHEN** `--files` names a path no case declares, and then a path the tree has but no case declares
- **THEN** the driver exits 2 naming the declared set it could not resolve, and no case has been executed
- **Case**: `driver#aFileSetThatNamesNoCaseIsRefused`

#### Scenario: An empty intersection is stated when the caller allows it

- **WHEN** a file set no case declares is run twice, once with `--allow-empty` and once without it
- **THEN** the run with the flag executes no case, prints `LOOP-SELFTEST: no case declares the delta` with the file
  set it read, and exits 0, while the run without it exits 2 exactly as it does today
- **Case**: `driver#anEmptyDeltaIsStatedWhenTheCallerAllowsIt`

#### Scenario: A core file owes every case

- **WHEN** `--files` names the library every engine script sources
- **THEN** every declared case runs, however narrow the cases' own declarations are
- **Case**: `driver#aCoreFileOwesEveryCase`

#### Scenario: A declaration is checked against the case it describes

- **WHEN** every declared case's body is read against its `# files:` declaration
- **THEN** each path the body names, and each engine script a helper it calls starts, is covered by the
  declaration, and every declared token names a path this tree has
- **Case**: `driver#everyCaseDeclaresTheFilesItCanReach`

#### Scenario: The gate hands a suite the base it measured

- **WHEN** a profile's suite command carries `{base}` and the gate is asked what a delta of an engine path owes
- **THEN** the plan carries that command with the resolved base substituted, and no `{base}` token reaches a shell
- **Case**: `gate#theDeltaHandsASuiteTheBaseItMeasured`

### Requirement: The run's state names the item it is working on

The run SHALL record an item in its state when it picks it, before that item's first agent process starts:
`.current` SHALL carry the item's number, the phase the run last recorded and the change the run asked for, every step
the run records SHALL be attributed to that item, and the mark SHALL come off when the item ends — so a state
kept from a killed run names the item that was in flight instead of a phase alone.

#### Scenario: A state read while an item is in flight names that item

- **WHEN** the run has picked an item and its first agent process is still running, so no later state write has
  happened yet
- **THEN** the state file carries that item's number and phase in `.current`, the live view the run's header
  names (`state.sh show`) prints it as its first line, and every step recorded so far is attributed to that
  item rather than to `0`
- **AND** once the item ends, `.current` is `null` again and no step the run recorded carries another item's
  number or `0`
- **Case**: `run#stateNamesTheItemInProgress`

### Requirement: A label write the tracker refuses is recorded, not swallowed

A label transition the run could not write SHALL be recorded with the item, and the report SHALL name that
item and the transition, because the item's state on the tracker is then stale in a way a reader of the
tracker cannot see. The run SHALL NOT record a decision label it did not write.

#### Scenario: An escalation the tracker refused is named

- **WHEN** the run blocks an item and the escalation's label write fails, so neither the decision label nor the
  hold reaches the tracker
- **THEN** the item is still recorded blocked with no decision label claimed, the refused transition is
  recorded with it, and the report's action list names the item and the write a person has to repeat by hand
- **Case**: `run#aFailedEscalationWriteIsNamed`

#### Scenario: An in-progress mark the tracker refused to clear is named

- **WHEN** the run ends an item and the write that removes the in-progress mark fails
- **THEN** the escalation that did land stands, the refused transition is recorded with the item, and the
  report names the mark nobody is holding any more
- **Case**: `run#aFailedUnpickIsNamed`

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

### Requirement: The gate's suite map covers every script command the pipeline's check steps run

The profile SHALL name the pipeline its suite map is pinned to. `LOOP_GATE_SUITES` SHALL carry every
`bash`/`sh` command that pipeline's `Check …` steps run, and every path those steps' tools read, so that a
landing the pipeline would refuse is refused by the run instead of by `main`. A step whose check is inline
shell or a Gradle task, and a reader edge a later step adds, are residuals named beside the rule in
`guidelines/Build.md` §3.

#### Scenario: A pipeline step the map does not carry fails the loop's suite

- **WHEN** the map names none of the `bash`/`sh` commands a `Check …` step of the pipeline runs
- **THEN** the loop's suite SHALL fail, naming each missing command
- **AND** a profile that records no pipeline key at all SHALL fail, because its map is pinned to nothing
- **AND** a profile that records the key empty SHALL pass, having said it has no pipeline
- **Case**: `gate#theMapCoversEveryPipelineStep`

#### Scenario: A path the pipeline checks owes that step's command set

- **WHEN** a change's delta touches a path the map names for a pipeline check step
- **THEN** the gate's plan SHALL name every command the map carries for that step, the check and its
  self-test among them
- **AND** it SHALL NOT name a command of another step
- **Case**: `gate#aPipelineCheckIsOwedByItsPaths`

#### Scenario: A path a check reads owes that step's commands

- **WHEN** a change's delta touches a path one of the pipeline's `Check …` steps reads — a tool's configuration
  or an engine that step scans
- **THEN** the gate's plan SHALL name every command of the step that reads it, so the run refuses what the
  step would refuse
- **AND** touching `tools/loop/loop.conf` SHALL owe `Check portability`'s check, its self-test and its mutation
  list among them, and touching `tools/issues/create.sh` SHALL owe the same step's check
- **AND** the three configurations `tools/check-portability.sh` opens and the three engine globs it scans
  SHALL each owe that step's commands, not only a tool's own glob
- **AND** an engine another step's case greps SHALL owe that step as well — a delta touching
  `tools/loop/worktree.sh` owes `Check issue tooling`'s self-test and its mutation sets
- **Case**: `gate#aPathACheckReadsOwesThatStepsCommandSet`

### Requirement: A child never outlives the run's deadline

A run's time budget SHALL bound the child it is waiting for, not only the item it starts next, so that a
process can never spend a budget the run was not given; and a run the deadline ends SHALL leave the deadline
as its recorded stop.

#### Scenario: The run's deadline ends the child it is waiting for

The deadline was consulted between items, so one wait was the place a spent budget could not be noticed:
measured 2026-10-20, eleven of fifteen stuck runs were past their own `--minutes 90`, the longest heartbeat
read `291m30s`, and none carried a `LOOP-TIMEOUT` line (issue #227).

- **WHEN** the run's own recorded deadline passes while an agent process is still running
- **THEN** the run ends that process and everything it started, writes one machine-readable line naming the
  deadline that ended it (`LOOP-DEADLINE: the run's deadline passed at <deadlineAt> …`) beside the
  `LOOP-TIMEOUT` line the run already reads as its own timeout, and records the item `blocked` with that
  transcript instead of waiting for a clock it was never given
- **Case**: `run#childIsEndedAtTheRunsDeadline`

#### Scenario: A deadline-ended child leaves a recorded stop, and the deadline when it is the binding one

A run that ends without a stop reason is replaced as `the state being replaced never recorded a stop reason`,
which is how the 2026-10-20 stretch was found hours later (issue #227).

- **WHEN** the run's deadline ended a child and the run then leaves its item loop
- **THEN** the state records a stop reason, and it names the deadline — `deadline passed at <deadlineAt>` — when
  the deadline is the condition that ends the run; when another recorded condition was reached first (the item
  cap), that condition is the stop, exactly as `state.sh next` already orders them
- **Case**: `run#childIsEndedAtTheRunsDeadline`

### Requirement: One suite at a time runs in a tree

The loop's self-test SHALL hold a lease in the scratch root the tree it runs in resolves, and SHALL refuse — exit 2,
naming the lock's owner and the moment it was taken — a second invocation that may run more than one case in that
tree. A lease whose owner is not running SHALL be taken over and named. An invocation that runs no case, or exactly
one named case, SHALL take no lease.

#### Scenario: A second suite in one tree is refused before it runs a case

- **WHEN** a whole suite is running in a tree and a second invocation that may run more than one case starts in that
  same tree
- **THEN** it exits 2, names the rule and the process that holds the lock, runs no case, and leaves that lock in
  place
- **Case**: `driver#aSecondSuiteInOneRepositoryIsRefused`

#### Scenario: One named case is not a suite, and a pattern is

- **WHEN** the lock is held and an invocation runs one named `--case`, then a `--case` pattern, then a `--group`
- **THEN** the named case runs, and the pattern and the group are refused with exit 2 having run no case
- **Case**: `driver#aSecondSuiteInOneRepositoryIsRefused`

#### Scenario: A lease whose owner is gone is taken over and released

- **WHEN** the lock stands with the pid of a process that is not running any more
- **THEN** the next suite takes it over in one line naming the process that is gone, runs its cases, and releases
  the lock so the repository is free for the suite after it
- **Case**: `driver#aSecondSuiteInOneRepositoryIsRefused`

### Requirement: The base a run records is the base branch's commit

A run SHALL record the base branch's commit as the base every item descends from, never the tree's checked-out
commit. `LOOP_BASE_REF` SHALL be a key of the project's own configuration, `main` when it names none, written so
an inherited value wins over it. A run whose project tree is on any other branch, or on a detached HEAD, SHALL be
refused before the pre-screen, and so SHALL a run whose project tree carries uncommitted work — a modified, staged or
untracked path git does not ignore — because the landing step merges into that tree and both of its merges refuse
dirt there. A stop on a base that no longer matches SHALL name the two commits and SHALL make no claim about when the
base moved.

#### Scenario: A checkout off the base branch is refused before the pre-screen

- **WHEN** a run starts and the project tree is checked out on a branch other than the base branch, or on a
  detached HEAD
- **THEN** it is refused with the branch the tree is on, the base branch and the command that switches to it, and
  the pre-screen is not run, so no item is worked on a tree the landing step cannot merge into
- **Case**: `run#aCheckoutOffTheBaseBranchIsRefusedBeforeThePreScreen`

#### Scenario: A project tree carrying uncommitted work is refused before the pre-screen

- **WHEN** a run starts and the project tree it would merge the item into carries one uncommitted file
- **THEN** it is refused before the pre-screen is run, before any item is started and before any previous attempt's
  branch is adopted, the path it found is printed and is the stop reason the run records — so the printed report names
  it too — and nothing is discarded: the refusal states that the run cannot tell its own leftover from an edit its
  owner made while no run was live, which is why the kept worktree's `--reset-worktree` repair is not offered here
- **Case**: `run#aDirtyProjectTreeIsRefusedBeforeThePreScreen`

#### Scenario: A checkout on another branch is not a moved base

- **WHEN** the project tree is checked out on a branch other than the base branch, whose commit is not the base
  branch's commit, and an item starts
- **THEN** the base the run recorded is the base branch's commit, the worktree is cut from it, and the item starts
  instead of being refused as a stale base
- **Case**: `state#recordsTheBaseBranchNotTheCheckout`

#### Scenario: A stale base names both commits and nothing it cannot know

- **WHEN** an item starts and the base branch has moved past the commit the run recorded
- **THEN** the item is refused, the recorded reason names the base branch's commit and the run's recorded base,
  and it states nothing about when the base moved
- **Case**: `run#staleBaseStopsTheRunWithItsReason`

#### Scenario: The base branch is the project's to name

- **WHEN** a project whose base branch is not `main` records it, and an unrelated reader of the engine is
  inspected
- **THEN** the branch is read from that project's `loop.conf` key, no engine file names a branch, and the base the
  run records is that branch's commit
- **Case**: `state#baseBranchIsAConfigurationKey`

#### Scenario: An inherited base branch wins over the recorded one

- **WHEN** a run starts with `LOOP_BASE_REF` set in its environment, and the configuration records another branch
- **THEN** the base the run records is the inherited branch's commit, because the configuration is sourced after
  the environment and fills only what the environment left unset
- **Case**: `state#baseBranchIsAConfigurationKey`

### Requirement: A block records the mechanism that stopped it

An item recorded `blocked` SHALL carry the mechanism that stopped it, from a closed vocabulary — `refused` when a
review round refused the change, `unmergeable` when the base would not take the branch, `bound` when the run ended a
child at a bound it enforces — and a block none caused SHALL carry none. `bound` is also the one mechanism an item
recorded `cut` carries, because the same bound is the phase the next run resumes. The vocabulary SHALL carry one
second home: `unmergeable` SHALL be written beside the pre-screen's exclusion of an item whose admissible finished
branch the project tree, the base or the replay refused to merge, with the refusal's own text, and no other mechanism
SHALL be written on a result that is not a block. A value outside the vocabulary, and one on any other result than a
block, that exclusion or a `cut` carrying `bound`, SHALL be refused before anything is written, so a mechanism's
spend is totalled without the note's prose. An arm that records a cut for the item's own worker, its gate child, its
tail or the rework worker of a refusing round SHALL leave `bound` beside the result when a bound the run enforces
ended that child, so no arm of one rule turns the run's own bound into a question its owner owes an answer.

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

#### Scenario: A rework worker the run's bound ended spends no decision slot

- **WHEN** a review round refuses the change, the run starts the rework worker of that round, and that worker
  reaches its role's turn bound or outlives the child clock so the run ends it
- **THEN** the item is recorded `cut` with `bound` as its stop reason and nothing else is written for it: no
  `agent/needs-decision` label, no `status/on-hold`, no comment, and no line in the report's decisions the owner
  owes, so the decision cap's population does not grow and the next run is not refused by it
- **Case**: `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`

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

### Requirement: The self-test runs its cases in a bounded pool, and what a case leaves ends with it

The loop's self-test SHALL run its selected cases in a bounded pool — the worker count the invocation names, else the
profile's, else the host's CPU count — with each case in a process group and a scratch root of its own. A process a
case leaves behind SHALL be ended when that case ends, before its slot is handed on, and the `PASS`/`FAIL` line, the
counts, the summary and the exit status SHALL keep their shape.

#### Scenario: Two cases run at the same moment

- **WHEN** a suite of four stub cases that each record when they start and stop runs without a worker count given
- **THEN** at least two of them were running at the same moment, every case prints its own `PASS` line, and the
  summary counts the whole selection
- **Case**: `driver#theSuiteRunsItsCasesInAPool`

#### Scenario: The pool is bounded by the worker count

- **WHEN** the same suite is run with the pool bounded to one worker
- **THEN** one case runs at a time — the log shows no two of them overlapping — and the same four `PASS` lines and
  the same summary are printed
- **Case**: `driver#theSuiteRunsItsCasesInAPool`

#### Scenario: A process a case leaves behind ends with the case

- **WHEN** a case leaves a process in a group of its own, at a working directory inside the scratch root the driver
  gave it, and the next case then looks for that process
- **THEN** the process is gone before the next case runs, and the suite still ends normally
- **Case**: `driver#aCaseThatLeavesAProcessBehindIsSweptWithIt`

### Requirement: A wait ends when the child does, not on the next whole second

A run SHALL look for a child's completion record at a fraction of a second (`LOOP_POLL_SECONDS`, default
`0.05`) and SHALL derive the progress cadence and the elapsed time it prints from seconds lived, never from the
number of looks, so a child that answers at once costs the wait its granularity rather than a whole second and
`LOOP_PROGRESS_SECONDS` keeps its meaning. The child's cap and the run's deadline SHALL stay whole-second clocks.

#### Scenario: A child that answers at once does not cost the wait a whole second

- **WHEN** a child writes its completion record 150 ms after it starts
- **THEN** the run announces the child's end less than 900 ms after announcing its start, because the wait looks
  several times inside that second instead of sleeping through it
- **Case**: `run#theWaitEndsWhenTheChildDoes`

#### Scenario: The progress line keeps its configured interval

- **WHEN** a child that runs for 4 s is watched with `LOOP_PROGRESS_SECONDS=2`
- **THEN** the run prints one `running` line per interval — between one and three of them — and not one per look,
  because the heartbeat reports elapsed seconds and not the wake-ups the finer granularity produces
- **Case**: `run#theProgressLineKeepsItsInterval`

### Requirement: A self-test case is held to a bound of its own

The self-test SHALL bound every case it starts with `LOOP_CASE_MAX_SECONDS` (default `900`), measured from the
case's own start. A case that exceeds it SHALL have its process group ended, be recorded
`FAIL <case> (exceeded <n> s)` with its own log kept, and let the suite run the cases after it, so one case that
never returns cannot hold the suite — and the gate behind it — for ever. The value SHALL be validated like the
profile's other bounds.

#### Scenario: A case that never returns is failed by its own bound

- **WHEN** a case in the pool does not return within `LOOP_CASE_MAX_SECONDS`
- **THEN** the suite ends that case's process group, prints `FAIL <case> (exceeded <n> s)` naming the bound and
  the case, keeps that case's log, runs the cases queued after it, and ends with its own summary and a non-zero
  status — instead of waiting for the case
- **Case**: `driver#aCaseThatBlocksIsEndedByItsOwnBound`

#### Scenario: A case that returns inside its bound is not bounded away

- **WHEN** a case finishes before `LOOP_CASE_MAX_SECONDS`
- **THEN** its result is reported as its own exit status decided — `PASS` or `FAIL` from the case, never the
  bound's line — and the suite's own wall clock is not lengthened by the bound
- **Case**: `driver#aCaseInsideItsBoundKeepsItsOwnVerdict`

### Requirement: A case's own output reaches the suite's log while it runs

The self-test SHALL write what a running case prints to the suite's own output as it arrives, each line prefixed
with the case's name, and SHALL still keep that output in the case's `out` file for its failure text, so a case
that has not ended names itself and what it was doing. Nothing about a case's protocol changes: the prefix is
added to the line, the case's own file is unaltered.

#### Scenario: A case that prints and then blocks is named while it blocks

- **WHEN** a case prints a line and then does not return
- **THEN** that line reaches the suite's own output, prefixed with the case's name, before the case ends — the
  suite a reader watches is not silent between the case's start and its bound
- **Case**: `driver#aRunningCasePrintsItsOwnLines`

### Requirement: A run that takes a signal takes its heartbeat with it

A run ended by `INT` or `TERM` SHALL end the heartbeat subshell its wait started before it exits, by pid, so no
process of a run that is gone keeps reading its tree: the wait loop's `$stop` file is only created on the path
where the wait returns, and the heartbeat has no other end.

#### Scenario: No process of a signalled run outlives it

- **WHEN** a run is inside its wait for a child and takes `SIGTERM`
- **THEN** the run records its interrupted item `blocked` with the reason, removes the in-progress label, unlocks
  the state, exits — and no process whose working directory lies under the run's tree is left behind
- **Case**: `run#noProcessOfASignalledRunOutlivesIt`

### Requirement: The evidence check is the loop's own tool, and the paths it reads are the project's

The check a change's evidence pointers are verified with SHALL be a tracked tool in the loop's directory, named by
the worker prompt, so a worktree carries it from its checkout without a seed step. The source roots and the tally
directory it reads SHALL be the project's configuration and never a literal in the tool, which is an engine.

#### Scenario: The check scans the roots the configuration names

- **WHEN** the evidence check runs over a tree whose configuration names its own scan roots
- **THEN** a `REVERT-CHECK MUTATION` marker under a configured path is reported, and a configuration that names
  no scan root is refused instead of being read as clean
- **Case**: `evidence#scansWhatTheProfileNames`

### Requirement: A gate step is bounded, and the step that overran is named

A gate SHALL bound every step it runs with `LOOP_GATE_STEP_MAX_SECONDS` (default `1800`), measured from the moment the
step starts, and SHALL end a step that reaches it together with everything it started. The step SHALL then be the
gate's failure: named on the console and in a `LOOP-GATE-STEP:` line of its own tally, counted as an error, and
invented as no case. A step that returns inside the bound SHALL keep its own status and numbers.

#### Scenario: A step that does not return is ended and named

- **WHEN** a step's command does not return within `LOOP_GATE_STEP_MAX_SECONDS`, the bound being a recorded profile
  value
- **THEN** the gate ends that step with the processes it started, its console line says the step overran the bound,
  its tally carries the `LOOP-GATE-STEP:` line naming the command and `errors="1"` with no case invented, and the
  gate exits non-zero naming the step — so a worker reads which suite overran instead of being ended with its budget
  spent
- **Case**: `gate#aStepIsBoundedAndTheOverrunNamesIt`

#### Scenario: A step that returns inside the bound keeps its own status

- **WHEN** a step's command ends before the bound, passing or failing, and writes its own summary
- **THEN** the step's exit status is the verdict, its case and failure counts are the summary's, its tally carries no
  error, and its console line is the plain `gate: <command> — <n>s` the console already carried
- **Case**: `gate#aFailingSuiteIsReadFromTheSameStream`

### Requirement: The repair a refusal names repairs the tree it was refused for

`--reset-worktree` SHALL leave a kept worktree that the next start accepts: a submodule whose worktree `HEAD` differs from the gitlink the superproject records SHALL be checked out at that gitlink, and dirt the reset cannot discard SHALL stop the run naming it instead of being reported as a clean tree.

#### Scenario: A submodule left on another commit is checked out at the recorded one

- **WHEN** the only dirt in the kept worktree is a submodule whose worktree `HEAD` differs from the gitlink the
  superproject records, and the run is asked for `--reset-worktree`
- **THEN** the module is checked out at that gitlink, the worktree's own status is empty, and the run starts
- **Case**: `run#resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard`

#### Scenario: The repair names dirt it cannot discard

- **WHEN** dirt that the reset and the module's checkout cannot remove survives both
- **THEN** the run stops with the remaining paths named, and it does not print that the tree is clean
- **Case**: `run#resetWorktreeNamesTheDirtItCannotDiscard`

### Requirement: The dirt a run ended itself does not stop it

A start refused because the worktree is not clean SHALL be retried instead of ending the run when the branch marker names an item **this run recorded** whose work this run wrote to `<item dir>/leftover-from-killed-run.patch`: that tree SHALL be discarded, the start retried once, and the run SHALL print and record which item's work it discarded and where the record is. A retry refused again SHALL stop the run with that refusal's own reason, and SHALL NOT replace the record the discard relied on.

#### Scenario: The item after the run's own ended child starts

- **WHEN** a run ends one of its items at the child cap, writes what that child left to the item's patch, records the
  item `blocked`, and the next item's start is refused because the worktree is still not clean
- **THEN** the run names the item whose work it discards and the patch that holds it, discards the tree, starts the
  next item on it, and ends with a tree the next start accepts — the stop it records is the count of items it spent,
  not the dirt
- **Case**: `run#theStartAfterTheRunsOwnEndedChildIsNotRefused`

#### Scenario: An item this run recorded blocked leaves its tree dirty

- **WHEN** a run records an item `blocked` — a refused `end-item` leaves its work staged, say — writes that work to
  the item's patch, and the next item's start is then refused because the worktree is not clean
- **THEN** the run discards that tree, records the discard as a step naming the patch, starts the next item, and the
  blocked item keeps the single record and the escalation its own arm wrote
- **Case**: `run#aRecordedItemsLeftoversAreKeptAndTheStartGoesOn`

#### Scenario: The dirt another item left still stops the run

- **WHEN** an item's start is refused because the worktree carries the uncommitted work of an item this run did **not**
  record, so no record of this run's holds it
- **THEN** nothing is discarded, the item the branch marker names is recorded blocked with the leftovers and
  escalated, the stop reason names that item and the files it found, and the item that could not start keeps no
  record of its own
- **Case**: none — the branch marker always names the item the run is working, so no case can construct an owner this
  run has not recorded; the mutation set records the gap instead (`271.1`, `271.2`)

#### Scenario: A retry refused for a reason of its own

- **WHEN** the run discards the work its own ended child left and retries the start, and the retry is refused for
  something other than the dirt — the branch it would start on carries commits the base does not, say
- **THEN** the stop reason is that refusal's own, the dirt the run removed is not named, and the patch the discard
  relied on still holds the work it held
- **Case**: `run#aRetriedStartStopsWithItsOwnReason`

### Requirement: Both clocks a run refuses by hold what one item's gate owes

The profile SHALL record what one item's gate owes, and the child's wall-clock bound SHALL NOT be recorded below
it: a bound smaller than an item's own gate ends work the run has already decided it can pay for. The configured
`LOOP_ITEM_FLOOR_MINUTES` SHALL NOT be recorded below it either, because that value is the whole floor of a run
with no measurement. Once a run has measured an item, the floor follows the cheapest measured outcome regardless
of what one item's gate owes — the accepted exposure being an item started in that tail, which the run's own
deadline ends and leaves for the next run with its work kept (issue #674).

#### Scenario: The child's bound follows what one item's gate owes

- **WHEN** the profile records `LOOP_ITEM_GATE_SECONDS` and a child wall-clock floor below it
- **THEN** the run gives each child the larger of the two, so an item owing a red round and a green one is not ended
  inside its own cost
- **Case**: `run#theChildCapIsNeverBelowWhatOneItemsGateOwes`

#### Scenario: The item floor is not below what one item's gate owes

- **WHEN** the profile records `LOOP_ITEM_GATE_SECONDS` and an `LOOP_ITEM_FLOOR_MINUTES` whose minutes are fewer than
  it, or records a floor that is not a number
- **THEN** the suite fails naming both numbers, because a run with no measurement would start an item that pays for
  its gate and loses the phases after a green round — while a derived floor below the gate's cost is the accepted
  exposure of a cheap tail and is asserted by `state#floorIsDerivedFromTheFinishedItemsWallTimes`
- **Case**: `state#theItemFloorIsOnlyBelowWhatOneItemsGateOwes`

### Requirement: The reading that places a write has its own cadence

A run SHALL read the project's own tree while a child runs at least once per whole second of that child's
life until the first difference is recorded, whatever `LOOP_PROGRESS_SECONDS` is, because that reading is the only
evidence that can place a transient write and a reporting interval is not a resolution for evidence. A case that
pins a placed write SHALL keep its write standing for a wide multiple of that cadence, so the pin does not depend
on the phase between the two.

#### Scenario: A transient write is placed whatever the reporting interval is

- **WHEN** a child writes the project's own tree, leaves a process of its own standing beside the write in it,
  and takes the write back before the child ends, while the configured reporting interval is far longer than the
  write's life
- **THEN** the run reads the tree inside the writer's life, marks the process that left the worktree as the one
  that made the difference, and records the item as blocked — without the reporting interval having been lowered
  for it, and with the write standing for a wide multiple of the reading cadence so the outcome does not depend
  on the phase between the reading and the write
- **Case**: `run#aWriterTheRunCanPlaceBlocksTheItem`

### Requirement: A report warns when an item outspends a recorded band

The report printed an item's tokens with nothing to read them against, so a run whose per-item spend doubled read exactly like a healthy one (issue #662). It SHALL read each item against a recorded band, and that band SHALL only ever warn: no part of the run reads it, so no outcome is derived from it.

#### Scenario: An item above the bound is flagged, and one below it is not

- **WHEN** an item's recorded `metrics.tokens` — the sum of its input, output, cache read and cache write — is above `LOOP_ITEM_TOKENS_WARN`, and another item's is below it
- **THEN** the row above the bound carries how far above it is in a column of its own (`2.5x`), the run-level line names that item beside the bound it crossed, the row below prints `-` there and is not named, and both items keep the outcome their work earned — the flag is a warning, so a bound that is `0` prints the flag as off and flags nothing
- **Case**: `report#anItemOverTheTokenBoundIsFlagged`

### Requirement: A child carries its own route, and not the project's whole document

An agent process SHALL be started without the project's context files, because a context file found in the
project root becomes a system-prompt section charged on every turn of a child whose work is one item. Its phase
prompt SHALL carry the routing it needs — where the rules, capabilities and change artifacts live, and how a
document's section list is read — and the process SHALL still start in the project, so the project's own policy
governs it.

#### Scenario: The child is started without the project's context files

- **WHEN** an agent process is started for an item
- **THEN** its argv disables the agent's context-file discovery and carries no word that moves its trust, replaces
  its system prompt or narrows its tools, so the directory it starts in is still what resolves the project's policy
- **Case**: `adapter#theChildIsStartedWithoutContextFiles`

#### Scenario: The phase prompt carries the route the child needs

- **WHEN** the worker's or the reviewer's prompt is rendered
- **THEN** it names the project's document map, the recipe that prints a document's section list, and where the
  rules, the capabilities and the change artifacts live, and every repository path it names resolves in the project
  root the child is started in
- **Case**: `prompt#thePhasePromptsCarryTheirOwnRoute`

### Requirement: A child is told what its own shell and tools refuse

The phase prompt a child is started with for work that writes the tree SHALL name what the process it runs in
refuses, before its first tool call:
that the commit is the run's and the child neither commits nor stages, which git calls come back refused for an
approval no unattended process can be asked for, that inline code is refused and how a number is taken instead, that
an edit call needs the text as it stands now, and which shell verbs over the tree are refused. The verb list SHALL
name every verb the refusal policy module exports, and the case for this requirement SHALL read that export and fail
when the prompt's list and the policy differ, so a verb the policy gains is a case that fails rather than a child that
learns it by being refused. A reviewer's prompt is out of scope: that child is bounded to reading.

#### Scenario: The phase prompt names what the child's shell refuses

- **WHEN** a phase prompt is rendered for work that writes the tree
- **THEN** it carries that block — the commit that is the run's, a git call that comes back refused, inline code, the
  edit that needs the text as it stands now, and the shell verbs a tree search is refused for — every verb the
  refusal policy exports is named in it, and each of the three skeletons a child can be handed for such work states
  that block's heading
- **Case**: `prompt#thePhasePromptNamesWhatItsShellRefuses`

### Requirement: A child's tool results are bounded and a trim is named

A result a child's tool returns SHALL enter that child's context whole only while it is no longer than the bound
the project records (`LOOP_TOOL_RESULT_MAX_CHARS`); above it, the result SHALL be replaced by its first
`<bound>` characters and one line naming the tool, the size the result had, the bound it was cut to and how to read
the rest. The trim SHALL be recorded once, with the tool and both sizes, beside the child's own event stream in the
item's directory, so a reader of the item can re-read the range the notice names. The bound SHALL be applied where a
result passes from the tool to the child — not by asking the child to read less — and `0` SHALL mean no bound.

#### Scenario: A result above the bound is trimmed and named

- **WHEN** a child's tool returns more characters than the recorded bound
- **THEN** the first `<bound>` characters are handed over unchanged, no character of the result past the bound
  reaches the context, and one line names the tool, the size it had, the bound and how to read the rest
- **Case**: `trim#aResultAboveTheBoundIsTrimmedAndNamed`

#### Scenario: A result at or under the bound is handed over whole

- **WHEN** a child's tool returns at most the recorded bound
- **THEN** the result reaches the context byte for byte, with no notice and no record, so the bound costs nothing
  to the nine results in ten the archive measured under it
- **Case**: `trim#aResultUnderTheBoundIsHandedOverWhole`

#### Scenario: The trim is recorded for the item

- **WHEN** a result is trimmed
- **THEN** one line names the tool, the size it had and the bound it was cut to, beside the child's event stream in
  the item's directory, so a reader of the item can re-read the range instead of trusting a cut line
- **Case**: `trim#theTrimIsRecorded`

#### Scenario: Every child is started with the bound

- **WHEN** an agent process is started for an item
- **THEN** its argv loads the file that applies the bound, immediately before the mode words, and that file is a
  hook on the tool result rather than a flag the agent happens to accept
- **Case**: `adapter#theChildIsStartedWithTheResultBound`

### Requirement: The pre-screen refuses only a fix stated to land in the submodule

The pre-screen SHALL refuse as a decision an item whose fix has to be committed inside the submodule — work
neither the run nor `worktree.sh end-item` may deliver — whenever the profile records a `LOOP_SUBMODULE_PATH`. The
signal SHALL be the author's stated claim: a line of its own beginning `The fix lands in the submodule`. The
submodule's path and its directory name SHALL NOT decide the verdict, because a body names them as evidence.

#### Scenario: A body that states the fix lands there is a person's patch

- **WHEN** an item's title or body carries a line beginning `The fix lands in the submodule`
- **THEN** the verdict is `needs-decision`, its reason names the configured submodule path and the route that remains
  (a person lands the patch there and the gitlink bump that follows), and the item does not reach the queue
- **Case**: `prescreen#aFixInTheSubmoduleIsAPersonsPatch`

#### Scenario: A body quoting the path or the marker is evidence, not the claim

- **WHEN** an item's body names the submodule's path or its directory name as the file a dirty tree was seen on, or
  quotes `The fix lands in the submodule` inline or in backticks
- **THEN** the verdict stays `eligible` and the item reaches the queue — the claim is a line of its own, so a
  quotation of the phrase and a path quoted as evidence are neither of them the claim
- **Case**: `prescreen#aQuotedSubmodulePathIsEvidenceNotAClaim`

### Requirement: A reused worktree is the tree the new base records

A kept worktree that `create` switches to a new base SHALL be left with the configured submodule checked out at the gitlink that base records, so the tree the next item starts on is the tree `git status` reports clean. The path SHALL be the project's configuration, never one an engine names.

#### Scenario: The pin the new base records replaces the previous branch's

- **WHEN** a kept worktree whose submodule sits on the previous branch's pin is reused for a new base that records another pin
- **THEN** the module is checked out at the new base's gitlink, the tree is clean, and the item starts on it
- **Case**: `worktree#checksOutTheNewPinWhenItReusesTheWorktree`

### Requirement: The run's end sweeps the branches it made

When a run ends it SHALL remove every branch the loop names whose patches the base already holds, unless a
worktree holds it or the change it carries has a reviewer's refusal. It SHALL sweep those names on the remote as
well as locally, against the fetched `origin/<base branch>`, and SHALL keep and name a remote ref that is the head
of an open pull request — every remote ref when the open pull requests cannot be read. It SHALL print what it
removed and what it kept.

#### Scenario: A landed branch no longer outlives the run that made it

- **WHEN** a run ends and one of its own `loop-*` branches, or an earlier `fix/issue-*` branch, carries no patch the
  base does not have
- **THEN** the run removes that branch, keeps and names a branch that still carries work, a branch a worktree
  holds, and a branch whose archived change carries a reviewer's refusal, and prints the number it removed
- **Case**: `run#theEndSweepsTheLandedBranchesItMade`

#### Scenario: A landed head is swept on the remote, never a pull request's

- **WHEN** a run ends and a branch the loop published carries no patch the fetched `origin/<base branch>` lacks
- **THEN** the run removes that remote ref at the tip the remote publishes, keeps and names a ref that is the head
  of an open pull request, and keeps every remote ref when the open pull requests cannot be read
- **Case**: `run#theRemoteSweepKeepsAnOpenPullRequest`

#### Scenario: The base the sweep judges against is the published one

- **WHEN** a branch landed on the base after the run recorded the commit it started on
- **THEN** the sweep compares against the fetched `origin/<base branch>` and removes that branch, so a branch
  landed during or after the run is not kept as work the base does not have
- **Case**: `run#theSweepComparesAgainstTheFetchedBase`

### Requirement: A stale skip asks its own question

A skip whose verdict is `stale` — the item's claim no longer holds in the tree — SHALL be escalated, not parked
on a bare hold: the run SHALL write the decision label and the hold together with one comment carrying the
verdict's reason and the question a person answers, and SHALL NOT close the item, because a stale skip carries
neither the measurement nor the review a falsification closure requires. The label writer SHALL keep no `stale`
verdict of its own: what it can still write as a hold SHALL be a verdict that states no question.

#### Scenario: A stale skip leaves a question, not a bare hold

- **WHEN** a worker skips an item with the sub-verdict `stale` and the run records that verdict
- **THEN** the item carries the decision label and the hold, its one comment names the worker's reason and the
  choice between retiring it as stale and re-diagnosing it — the worker's own question when it stated one, the
  run's otherwise — and nothing is closed
- **Case**: `run#staleSkipAsksItsQuestion`

#### Scenario: The worker's own question is carried unchanged

- **WHEN** a stale skip's report carries a `LOOP-DECISION`
- **THEN** that text is the escalation's decision field, unchanged, and the run states no choice of its own
- **Case**: `run#staleSkipKeepsTheWorkersQuestion`

#### Scenario: The label writer refuses the verdict the run no longer writes

- **WHEN** the label writer is passed `stale` as a verdict of its own
- **THEN** it stops as a usage error naming the verdict unknown, and writes nothing on the item
- **Case**: `labels#verdictToLabelMatrix`

### Requirement: A closure names the settled-state transition it makes

The write that records the loop's closure SHALL name the settled-state transition it makes: the item it closes
may carry the other settled value of the profile, and the guard every routine writer meets refuses a write that
displaces a settled value without naming the transition. The closure SHALL displace the settled value it
replaces, SHALL report that value, and SHALL land as one act instead of being refused between its two writes.

#### Scenario: A closure over the other settled value lands and names it

- **WHEN** the loop closes an item that carries the other settled value of the profile's
  `roles.settledStatuses`
- **THEN** the write records the settled state the closure means, displaces the settled value it replaced, and
  reports that value, so the item is closed rather than left half attempted
- **Case**: `labels#closeNamesTheSettledTransition`

### Requirement: A bound is clamped by the ceiling the caller named

An invocation that applies a bound SHALL read the ceiling of the process that started it from
`LOOP_CALL_CEILING_SECONDS`, the caller naming it at the call site, and SHALL never let one of its bounds outlive
that ceiling. An absent key, or an empty, non-numeric or `0` value, SHALL mean no ceiling: every bound keeps its
recorded value, validated like the profile's other bounds, and the invocation starts no watcher.

#### Scenario: A suite that cannot finish inside the ceiling ends itself, and says what it ran

- **WHEN** a caller names a ceiling smaller than the time the selected cases need, and the ceiling is reached with
  cases still queued or running
- **THEN** the suite prints `LOOP-SUITE-CEILING:` naming the ceiling it ended by, how many of the selected cases
  started and how many never started; ends the cases still running together with what they started; removes its
  scratch tree and every watcher it started; and exits non-zero — a cut selection is never a suite that passed
- **Case**: `driver#aSuiteEndsInsideTheCeilingItsCallerNamed`

#### Scenario: A gate step's clock is the ceiling when the ceiling is smaller

- **WHEN** the gate runs with a ceiling named below `LOOP_GATE_STEP_MAX_SECONDS` and a step reaches it
- **THEN** `LOOP-GATE-STEP-BOUND:` on the console names the ceiling as the clock in force before the first step
  starts, the gate ends that step with everything it started, its console line reads `the step overran its bound of
  <n>s and was ended`, its tally carries the `LOOP-GATE-STEP:` line and an error, and the gate exits non-zero —
  instead of being killed mid-step with no line written
- **Case**: `gate#theStepBoundIsClampedByTheCeilingTheCallerNamed`

#### Scenario: A ceiling the selection fits inside changes nothing

- **WHEN** a caller names a ceiling the selected cases finish well inside
- **THEN** every case keeps its own verdict, the suite prints the summary it always printed, no
  `LOOP-SUITE-CEILING:` line appears, and the suite leaves no process behind
- **Case**: `driver#aCeilingTheSuiteFitsInsideChangesNothing`

### Requirement: The report states whether the loop is getting better

A run's report SHALL also state what one run cannot show: a run that cost what the last one cost prints the same
shapes as one that is improving. It SHALL total the share of items whose review round 1 ended in a pass — read
from the verdict the round recorded, never from the round count alone — and the item wall median, minimum and
maximum, from the state it already reads, and SHALL print the share of its landings that reddened `main` from the
score the state records for each landing, never from the tracker (issues #247, #709).

#### Scenario: The two figures the state holds are totalled

- **WHEN** a run's report is printed for a state whose items record the verdict their last review round ended on
- **THEN** it prints the share of the items whose recorded verdict is a pass at round 1 against the items whose
  round ended in a verdict, and the median, minimum and maximum of the item wall times, both over the run's own
  items, and it says so, because a rate is not settled by one run
- **Case**: `report#trendFiguresFromState`

#### Scenario: A refused round 1 is not a pass

A round's count says the round was attempted, not how it ended, so an item whose round 1 refused and which the
run then blocked carries the same count as one whose round 1 passed.

- **WHEN** an item's round 1 ended in a refusal and it is the last round the item got, or the round after it
  passed
- **THEN** the first counts as a refusal and the second as no first-round pass, because the verdict decides and
  the count does not
- **Case**: `report#trendFiguresFromState`

#### Scenario: A round that ended without a verdict is in no rate

- **WHEN** a round ended without a verdict — the reviewer's answer was cut, the run's clock ended it, it wrote, or
  the state was written before the verdict was recorded — while another round of the run did end in one
- **THEN** that item is named apart as undecided and is in neither the numerator nor the denominator, and a state
  whose items carry no verdict at all prints `-` with the reason instead of a rate
- **Case**: `report#trendFiguresFromState`, `report#noRoundOrWallPrintsDashNotZero`

#### Scenario: A never-reviewed item is not a refused round

- **WHEN** an item ended before a review round ran — the run blocked it, or review was off — and its record holds
  `reviewRounds` 0
- **THEN** that item is counted as never reviewed and is not in the denominator of the round-1 pass rate
- **Case**: `report#trendFiguresFromState`

#### Scenario: A figure nobody measured prints as absent, not as zero

- **WHEN** no item of the run recorded a wall time
- **THEN** the report prints `-` with the reason beside it rather than a zero that reads as a measurement that was
  made
- **Case**: `report#noRoundOrWallPrintsDashNotZero`

#### Scenario: The figure the state does not hold is named where it is

- **WHEN** the report prints the landings that reddened `main` for a state whose landings record no commit — a state
  written before a landing kept one
- **THEN** it prints no rate over those landings and names that they record no commit, and it makes no tracker read
  of its own, because the report is still built from the state file and from git
- **Case**: `report#landingsAreTheStoredNumber`

#### Scenario: The landings that reddened main are the stored score

- **WHEN** the report prints the number of landings that reddened `main`
- **THEN** it prints the number of landings whose record carries the score `true` against the landings that carry
  a score at all, names apart the landings that carry a commit and no score yet and those that carry no commit,
  and makes no tracker read of its own — the report is still built from the state file and from git
- **Case**: `report#landingsAreTheStoredNumber`

### Requirement: A landing records the commit it landed, and a run scores it

A landing SHALL record the commit that carried it onto the base, and the score of the push run that commit caused
SHALL be read once per landing and stored on the item — the read belongs to the state's writer, never to the report.
A landing whose run is not answered for yet SHALL be recorded unscored and named, never read as green, and a value
that is not a commit SHALL be refused.

#### Scenario: The record carries the commit the landing made

- **WHEN** a run records a landing with the commit its merge produced
- **THEN** the item's record carries that commit beside the branch, the commits' count and the evidence, and a
  value that is not a commit is refused without writing the record
- **Case**: `state#doneRecordsTheCommitItLanded`

#### Scenario: The score is read once per landing and stored

- **WHEN** the run scores its landings and an item records a commit the tracker answers for
- **THEN** one read per unscored landing writes `true` when the push run that commit caused failed and `false`
  when it succeeded, and a second scoring pass reads nothing, because the scored item is no longer unscored
- **Case**: `state#theLandingScoreIsReadOnceAndStored`

#### Scenario: A landing with no run yet is unscored, not green

- **WHEN** the tracker answers for no push run of the landing's commit, or does not answer at all
- **THEN** the item stays unscored, the reason is printed beside it, and no false is written
- **Case**: `state#aLandingWithNoRunIsUnscored`

#### Scenario: The run records the commit of the merge it made

- **WHEN** a run merges the branch an item finished on and closes the item
- **THEN** the item's record carries the base it advanced to, which is the commit the landing made
- **Case**: `run#mergesItsCommittedBranchBeforeClosing`

### Requirement: A change the item names is handed to its worktree

When an item's body records a change, the run SHALL copy that change's directory from the project's tree into the
item's worktree before the worker starts, whole, and SHALL record the copy. A change the tree does not hold SHALL
refuse the item, never produce an empty directory, because the run cuts the worktree from the base and the change
is gitignored, so nothing else would carry it.

#### Scenario: The named change is in the worktree the worker gets

- **WHEN** an item's body records `<!-- change: add-x -->` and the project's tree holds `openspec/changes/add-x/`
- **THEN** the item's worktree carries that directory with every file it had, before the worker process starts, and
  the run records the source path and the copy's file count
- **Case**: `worktree#theNamedChangeIsCopiedIntoTheItem`

#### Scenario: A change the tree does not hold refuses the item

- **WHEN** an item names a change no directory of that name holds
- **THEN** the item is refused with the path the run looked for, before a branch or a child exists, and no empty
  change directory is created in the worktree
- **Case**: `worktree#aMissingChangeRefusesTheItem`

#### Scenario: A change that is not ready refuses the item

- **WHEN** the profile's readiness check reports any refusal for the named change at the moment the item is selected
- **THEN** the item is left open with the checker's verdict line recorded on it, and no child is started
- **Case**: `prescreen#aChangeThatIsNoLongerReadyIsRefused`

### Requirement: A tier-B item is verified, never planned again

A worker handed a change SHALL verify it against the tree and SHALL NOT write a second change for the item. A fork
the change does not answer SHALL be reported as a decision naming that fork, and a change whose claims no longer
hold SHALL be reported with what moved, so no worker can silently replace the plan a person authorised.

#### Scenario: The worker applies the change it was handed

- **WHEN** the package carries a change and the change still fits the tree
- **THEN** the change's tasks are applied in order and no second change directory is created for the item
- **Case**: `run#aTierBItemWritesNoSecondChange`

#### Scenario: A fork the change does not answer is named

- **WHEN** applying the change requires a decision its `## Open decisions` declaration does not settle
- **THEN** the item is skipped with `needs-decision`, and the question names the fork and the change's line, not a
  re-derived option list
- **Case**: `run#aForkTheChangeDoesNotAnswerIsNamed`

#### Scenario: A change whose premise moved is not re-planned

- **WHEN** a `file:line` the change cites no longer shows what it claimed
- **THEN** the item is skipped with `needs-diagnosis`, naming the path that moved, and the worker writes no
  replacement change
- **Case**: `run#aChangeWhosePremiseMovedIsNotRePlanned`

### Requirement: The package carries the change beside the item's text and its decision

The package a worker receives SHALL carry the change as a delimited data block of its own — its name, its path in
the worktree, the readiness verdict line and the recorded decisions — and SHALL keep that block distinct from the
item's own text and from its decision, so that no field of one can close a block it does not own.

#### Scenario: The change reaches the worker as its own block

- **WHEN** a worker is started for a tier-B item
- **THEN** the package contains exactly one `<<<ITEM_CHANGE` opening and one `ITEM_CHANGE` closing marker, the
  change's name and worktree path inside them, and the block is the same text the evidence-check reads
- **Case**: `prompt#thePackageCarriesTheChange`

#### Scenario: A tier-A item's package carries no change block

- **WHEN** a worker is started for an item that records no change
- **THEN** the package's change block is present and states `change: none`, rather than being absent, because an
  absent block is a half-prepared package
- **Case**: `prompt#aTierAItemSaysSo`

#### Scenario: A comment cannot close a block it does not own

- **WHEN** an item's body or one of its comments contains the text `ITEM_CHANGE` or its opening marker
- **THEN** the rendered package carries exactly one opening and one closing marker for that block, and the item's
  text cannot end it
- **Case**: `prompt#inlineInjectionCannotReachTheInstructionPosition`

### Requirement: The consumed change is cleaned from the project's tree, and the run does not write it there

The source directory of a change an item consumed SHALL be reported by the loop's clean command and removed by its
`--apply`, together with the worktree's recorded build paths, because the run writes nothing in the project's own
tree and a consumed change left behind can be read as work still owed. The item's closure, not the leftover, SHALL
be what prevents a second consume.

#### Scenario: A consumed change is reported before it is removed

- **WHEN** the clean command runs without `--apply` after an item whose change was copied landed
- **THEN** it names the source change directory and its size and removes nothing
- **Case**: `clean#aConsumedChangeIsReported`

#### Scenario: The second run finds nothing to remove

- **WHEN** the clean command runs with `--apply` and is then run again
- **THEN** the first run removes exactly the paths it reported and the second finds nothing of them
- **Case**: `clean#aSecondRunFindsNothing`

#### Scenario: A closed item cannot consume its change twice

- **WHEN** a change's source directory is still present and the item that consumed it carries the settled state
- **THEN** the run does not select that item again, and the leftover is only disk hygiene
- **Case**: `prescreen#aClosedItemIsNotSelected`

### Requirement: A report reports the shape a run spent its budget in

The report printed what an item cost and nothing about the shape it spent it in, so a session that made one tool call per turn read exactly like one that batched its independent reads (issue #757). It SHALL derive each item's calls per turn from the counts its event stream records, print the run's rate, and name the items below a recorded floor — a floor that SHALL only ever warn.

#### Scenario: An item below the recorded floor is named, and one above it is not

- **WHEN** an item's event stream records 6 calls in 2 turns and another's records 2 calls in 4 turns, and `LOOP_ITEM_CALLS_PER_TURN_MIN` records a floor between them
- **THEN** the trend prints the run's own rate with the two counts it came from (`1.33 calls per turn (8 calls / 6 turns)`) and names the item below the floor with its own ratio (`#22 0.5`) while the item above it is not named, the table's last column reads `3` and `0.5`, a floor of `0` prints as off and names no item, an item that recorded no turn prints `-` with its reason, a state written before the two counts existed says so rather than naming a run of items that answered without a tool, and both items keep the outcome their work earned
- **Case**: `report#anItemBelowTheCallsPerTurnFloorIsNamed`

### Requirement: The report says which phase and which role spent the run's clock

A run's spend SHALL be split where it was spent: the clock by the phase that spent it — the model time, the gate,
and the other tools the children ran — and the tokens by the role that spent them (worker, reviewer, tail) and by the
model that answered them, both read from what an item's own event stream records when its record is written, never
from a stream the report would have to find again. It SHALL also state the share of the tokens the run spent after an
item's first gate. Each figure SHALL print `-` with its reason where nothing was measured, never a zero that reads as
a measurement, and a role whose turns named no model SHALL keep its totals and name no model. A call SHALL count as
the gate only where its command starts the gate's program, past the assignments and wrappers a child writes, and a
command whose argument is the gate's own path, and a `--plan` call, SHALL be neither.

#### Scenario: The clock and the tokens are split where they were spent

- **WHEN** an item's stream records turns with durations and tool calls with durations, one of them running
  `gate.sh`, and sessions whose prompt paths name a worker, a reviewer and a tail
- **THEN** the state records the sum of the turn durations as the model time, the durations of the calls that ran
  the gate as the gate time and every other tool's durations beside them, and the tokens of each session under the
  role its prompt names, the tail told apart from the worker; and the trend prints `model … · gate … · other …`
  with their total and `worker … · reviewer … · tail …` with the tokens they are a share of
- **Case**: `report#theSpendIsSplitByPhaseRoleAndGate`

#### Scenario: The share spent after the first gate is stated with both its numbers

- **WHEN** a session of the item runs the gate at a known turn and spends a known number of tokens from that turn on
- **THEN** the trend prints the share of that session's tokens spent from its first gate on, with the tokens after
  the gate and the session total it is a share of, counted over the sessions that ran a gate rather than over every
  session of the item
- **Case**: `report#theSpendIsSplitByPhaseRoleAndGate`

#### Scenario: A call that names the gate is not a call that ran it

- **WHEN** a session of an item reads the gate's own file (`sed -n 1,60p tools/loop/gate.sh`, `git show
  HEAD:tools/loop/gate.sh`, a `grep` over it) and asks the gate for a plan (`gate.sh --plan --base <sha>`), and
  another session runs the gate behind a `cd … &&` prefix, a `timeout <n>` word and `bash` — the wrappers a child
  writes are `bash`, `sh`, `exec`, `command`, `timeout <n>`, `env` and `nohup`
- **THEN** the reading session records no gate time and no turn to split at — its two commands are the other tools
  they are and the trend prints `-` with its reason for the run that ran no gate — and the session that started the
  gate behind those words records the call's duration as the gate time and the item is split at that turn
- **Case**: `report#aCallThatNamesTheGateIsNotAGateRun`

#### Scenario: A role's tokens name the model that answered them

- **WHEN** an item's stream records a reviewer session and a worker session whose turns name a model — the one the
  provider answered with, or the requested one when it reports none
- **THEN** the state records each role's tokens under the model that spent them, beside the role's own total, and the
  trend prints each tally as `role (model) tokens` with the role totals it is over, so a run that prices a role apart
  can be read back from the item's own record rather than from the configuration
- **Case**: `report#aRolesTokensRecordTheModelThatAnswered`

#### Scenario: A session that switched model is two tallies

- **WHEN** two turns of one session of an item name different models and spend different numbers of tokens
- **THEN** the state records each model's own tokens rather than one row priced at whichever model came first, and the
  trend prints one `role (model) tokens` row per model
- **Case**: `report#aSessionThatSwitchedModelRecordsBothTallies`

#### Scenario: A state that records no model keeps its role totals

- **WHEN** an item's stream records usage but no turn names a model, or a state was written before the model was
  recorded
- **THEN** the item records no model tally rather than an empty or zero one, and the trend prints the role totals it
  always printed and names no model
- **Case**: `report#aStateWithoutAModelKeepsItsRoleTotals`

#### Scenario: A role whose turns named no model keeps a row of its own

- **WHEN** one role of an item answered on a named model and another role's turns named none, so part of the item's
  spend no model explains
- **THEN** the explained tally prints as `role (model) tokens` and the unexplained part prints as the role alone, so
  the rows the line prints still add up to the total it is over
- **Case**: `report#aRoleWhoseTurnNamedNoModelKeepsItsRow`

#### Scenario: A figure nobody measured prints as absent, not as zero

- **WHEN** an item's stream records no duration and no usage, or the state was written before the split was
  recorded
- **THEN** each of the three lines prints `-` with its reason — no duration measured, no gate to split at, or a
  state written before the report read the figure — rather than a zero that reads as a measurement that was made
- **Case**: `report#aStreamWithoutDurationsPrintsDash`

### Requirement: The diff is read before the gate, and a fresh child runs the one gate

An item's gate SHALL be run by a child of its own, after the diff has been read by a reviewer that is not the child
that wrote it, so a finding a reader can make from the diff is not paid for by a gate and a second full pass.

#### Scenario: The round is read before the gate child is started

- **WHEN** a run with the pre-gate review on reads a change child that ended at the diff (`LOOP-DIFF-READY`) and left
  no gate line
- **THEN** the run commits that diff, starts a reviewer on it, and starts the gate child only after that round
  answered — the reviewer's round is observable before the gate is reached, and the item still spends one gate child
- **Case**: `run#thePreGateReviewRunsBeforeTheGate`

#### Scenario: The findings reach the child that runs the gate

- **WHEN** the round before the gate refuses the diff with a blocking finding
- **THEN** the findings are appended to the gate child's prompt, which applies them in one pass before running the
  gate, and no rework worker is started for them because the item was not refused
- **Case**: `run#thePreGateReviewRunsBeforeTheGate`

#### Scenario: The round before the gate costs no round of the budget

- **WHEN** the round before the gate refuses the diff and the item's change then passes the round after the gate
- **THEN** the item records one review round, because `--max-review-rounds` bounds the rounds after the gate and the
  round before it refuses nothing
- **Case**: `run#thePreGateReviewRunsBeforeTheGate`

#### Scenario: A round the run cut before the gate is left for the next run

- **WHEN** the round before the gate never answers — cut by the deadline, ended at its role's turn bound or at the
  item's context bound, or cut
  twice on its way to the file
- **THEN** the item is recorded `cut` when a bound the run enforces ended that round and `blocked` when the
  transport did, with that round named as the one before the gate, no gate child is started on a diff nobody read,
  and the round after the gate is not asked either
- **Case**: `run#aPreGateReviewThatIsCutLeavesTheChangeForTheNextRun`

#### Scenario: A change child that ran the gate itself is read as it always was

- **WHEN** the change child's report carries a gate line and no `LOOP-DIFF-READY`
- **THEN** the run starts no round before the gate and no gate child, because a gate the run did not run is not
  re-run and a round before a gate that has already run has nothing to read
- **Case**: `run#childUsageIsRecordedPerItem`, `run#announcesAndRecordsTheReviewRound`

#### Scenario: A phase's checklist is the prompt of the child that performs it

- **WHEN** the phase state is printed for phase E
- **THEN** the checklist it prints is the gate child's own prompt and the call names that file, because the run
  defers the gate to that child
- **Case**: `phase#oneCallPrintsThePhaseState`

### Requirement: A child is bounded by its own role's turns

Each child SHALL be ended when the turns **it** has taken reach the bound its role records, and the item's own
turn total SHALL NOT end a child. The bound SHALL be read through the child's role, counted from the turns the
child added to the item's event stream, and named in the transcript with the role and the key — because one count
over the stream every child appends to decides which phase is discarded, not whether the item is converging.

#### Scenario: A phase's spend does not end the phase after it

- **WHEN** the worker takes ten turns, more than the item's old total but below its own role's bound, and the
  round after it takes the eight turns its role records
- **THEN** the worker's transcript carries no `LOOP-TIMEOUT`, the reviewer is ended at its own bound of eight turns
  with a line naming `LOOP_CHILD_TURNS_MAX_REVIEWER` and its role, and the item is recorded `cut` with that
  round marked cut
- **Case**: `run#eachChildIsBoundedByItsOwnRolesTurns`

#### Scenario: A role whose key is `0` runs unbounded, and the item's total is still reported

- **WHEN** a role's key records `0` and its child takes three turns
- **THEN** no turn bound ends it, while the item's total is still counted over its children (`metrics.turns`) and
  printed by the report's `calls:` line beside the calls it was made of
- **Case**: `run#aRoleWithNoRecordedTurnBoundRunsUnbounded`

### Requirement: An item the run's own bound cut is recorded `cut`, not `blocked`

An item a bound the run enforces ended SHALL be recorded `cut`: the run's deadline or a role's turn bound
ending a review round, and the role's turn bound, the context bound, the child clock, a vanished writer or the run's deadline ending the item's own child.
It SHALL carry `bound` as its stop reason when the bound ended the item's own child, while a round the run ended
carries none, as it did before — so the mechanism's spend is totalled beside the result — SHALL write no
decision label and no comment, SHALL NOT enter the two-blocked streak `state.sh next` stops on, and SHALL be named on
a line of its own in the report and counted there as a cut rather than a block, so no reader needs the mechanism's
vocabulary to tell work from a refusal. The work an ended child left SHALL stay where the child left it, SHALL be
named in the item's own record, and SHALL NOT be committed.
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

### Requirement: The suite runs as legs on separate runners, and a leg is its own verdict

The self-test SHALL be runnable as several independent legs on separate runners, each leg the cases of one group
selection, so the suite's wall clock is bounded by its slowest leg rather than by one runner's core count. A leg
SHALL state the selection it ran against the declared set in its own summary and SHALL keep its repository and its
scratch root to itself, and the workflow SHALL take a leg's own exit status as a verdict of the run.

#### Scenario: A leg runs the cases of its own selection

- **WHEN** the suite is invoked with a group selection and a declared set larger than that group
- **THEN** only that group's cases run, the summary counts them against the declared set, and no case of another
  group prints a line
- **Case**: `driver#aGroupFilterRunsOnlyItsOwnCases`

#### Scenario: Two legs do not contend for the suite's lease

- **WHEN** two legs of one suite run at the same moment, each in its own checkout
- **THEN** neither refuses the other, because the lease is per repository and a leg's repository is its own — the
  state `driver#aSecondSuiteInOneRepositoryIsRefused` separates
- **Case**: none — the property is a leg's checkout, which is the workflow's; the lease's own case states the rule
  it rests on

#### Scenario: A red leg fails the run

- **WHEN** one leg fails a case and the other legs pass
- **THEN** the run is red and names the leg, exactly as one red case in a single-leg suite does today
- **Case**: none — a workflow's verdict is the host's; it is read from the dispatch run that recorded the leg split

### Requirement: The bound on a child is priced in what it spends, not in how many turns it takes

The run SHALL bound a child in what a turn costs, not in how many turns it takes: it SHALL read the largest context the child's stream reported (`input + cacheRead`, or a compaction's `tokensBefore`, whichever is greater) in the wait that enforces the clock and the turn count, SHALL print one checkpoint line at `LOOP_ITEM_CONTEXT_WARN`, and SHALL end it and everything it started at the bound the child's **role** records, `LOOP_ITEM_CONTEXT_MAX_<ROLE>`, falling back to `LOOP_ITEM_CONTEXT_MAX` where a role records none. Both keys SHALL be recorded values named in the run's header; `0` or no key is no bound. An item this bound ends SHALL be recorded `cut` with `bound` as its stop reason, exactly as every other bound the run enforces ends one (issue #790): the phase is the next run's to resume, not a finding about the item.

#### Scenario: A child whose context crosses the recorded bound is ended and named

- **WHEN** a child's stream reports a growing context — 120 000, 155 000, then 210 000 tokens — while
  `LOOP_ITEM_CONTEXT_MAX` records 200 000 and `LOOP_ITEM_CONTEXT_WARN` records 143 124, and the wall clock still has
  45 min to run
- **THEN** the run prints the checkpoint once, naming the recorded 143 124, ends the child's process group, writes
  `LOOP-TIMEOUT: the child reached the recorded context bound of 200000 tokens (LOOP_ITEM_CONTEXT_MAX) at 210000
  tokens — the run ended it and everything it started` into the child's transcript with the wall clock's own exit
  status, records the item `cut` with `bound` as its stop reason and that line as its note, and closes nothing
- **Case**: `run#childPastItsContextBoundIsCut`

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

### Requirement: A closing line is read where it stands, fence or no fence

The run SHALL NOT read the fence a child puts around its closing block as the absence of the line: one backtick at
each end of a `LOOP-BLOCKING:` or `VERDICT:` line SHALL be taken off before that line is read as a field, a count or
the closing line. Taking it off SHALL NOT weaken either fact the completeness rule is made of: a report with no
closing line, or whose last byte is not the adapter's newline, is still cut, and one that states no count is still a
refusal.

#### Scenario: A closing block written as inline code is a complete answer

Measured 2026-11-06 on item #788: both of its pre-gate reports ended `` `LOOP-BLOCKING: 3` `` then
`` `VERDICT: FAIL` ``, each file's last byte the newline the adapter prints, and the run read both as cut answers —
it blocked the item and escalated *no reviewer refused this change* while the report in its hand carried three
blocking findings (issue #793).

- **WHEN** a review round's report ends with its `LOOP-BLOCKING:` and `VERDICT:` lines written as inline code, with
  the adapter's newline after the last of them
- **THEN** the answer is complete: the round is not asked again, the count inside the fence is the count the round is
  decided by, and a fenced `VERDICT: FAIL` whose count is zero closes the item with its `NOTE:` findings recorded
- **Case**: `run#aFencedClosingBlockIsACompleteAnswer`

### Requirement: A step that refuses a delta in seconds is owed before the suites

The profile SHALL name the steps the gate owes before its build and before its suites, in the suite map's
`<glob>|<command>` shape, read in that order. A delta that writes a case's first-line `# files:` declaration SHALL
owe the case that reads it before the build and before the suite that would refuse it at its end. The child's step
SHALL send it to the plan's first line, which is what saves the attempt. The case that reads the declaration SHALL
stay the one owner of the reach table.

#### Scenario: A delta that writes a declaration owes its reader first

- **WHEN** a delta writes a case into `tools/loop/tests/loop-cases.sh` and touches a compiled file, and the gate is asked what
  that delta owes
- **THEN** the plan's first line is
  `tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach`, before the suite the declaration narrows
  and before the profile's compiled command
- **AND** the child's own step sends it to that same plan line and names no case of this project, so a project that has
  no such case still has a step to run
- **Case**: `gate#theDeclarationCaseIsOwedFirst`

### Requirement: The suite states what it cost as well as what it ran

The self-test SHALL report the elapsed time of each group and of the whole selection in its summary, so a case set
that grows is visible where it is paid rather than at the next hand-run. A passing case's `PASS` line SHALL NOT
carry a figure unless the invocation asks for one. The report SHALL be that run's own measurement and SHALL NOT be
compared against a recorded baseline.

#### Scenario: The summary names what each group cost

- **WHEN** a suite of stub cases in two groups is run to completion
- **THEN** the summary carries one line per group with that group's elapsed time and its case count, and one line
  for the selection, and the counts and the exit status are what they were
- **Case**: `driver#theSummaryNamesWhatEachGroupCost`

#### Scenario: A passing case's line keeps its shape

- **WHEN** the same suite runs without `--times`, which is how the mutation harness and the leftovers cases invoke
  the driver
- **THEN** each case's line is its `PASS` and its name and nothing else, and with `--times` the figure is added
  without changing the name the line is read for
- **Case**: `driver#theTimesFigureIsAskedForNotAssumed`

#### Scenario: The figure is this run's own

- **WHEN** a run finishes on a host whose previous run was slower, or faster, by minutes
- **THEN** the summary states the elapsed time this run measured, and no line of it compares that number with a
  stored one — a baseline's disagreement is a disagreement about the host
- **Case**: none — a negative: nothing in the report may read a stored figure, and the summary's own text is the
  reading (`design.md`, "Why not the alternatives")

### Requirement: An adoption the tree refuses is recorded as `unmergeable`, not escalated

An adoption of a previous attempt's branch that the branch's own inputs admit — one archived change of its own, a
green verification record, no reviewer's refusal and no review round left unanswered — but whose merge the project
tree, the base or the replay refuses SHALL be recorded with the item's mechanism `unmergeable`. The pre-screen's
entry for that item SHALL keep its reason, its verdict and the branch as the evidence, SHALL carry the adoption's
own refusal beside them, SHALL NOT be reported as a decision the owner owes and SHALL NOT be escalated on the
tracker. No item record SHALL be written for it, so the item neither enters the two-blocked streak nor counts
against the decision cap, and the branch SHALL be left where the next run's pre-screen finds it. An adoption refused
by the branch's own evidence SHALL stay the owner's decision, unchanged. The mechanism SHALL be printed beside the
exclusion in the run's report, so the run's own output says which refusal it decided for itself, and a pre-screen
that runs again during the same run SHALL NOT erase it from the entry it rewrites.

#### Scenario: A tree that refuses the merge leaves work, not a question

- **WHEN** the run adopts an excluded branch that carries exactly one archived change of its own and a green
  verification record, and the project tree the merge would land in is not clean
- **THEN** nothing is merged and nothing is closed, no `agent/needs-decision` label is written for the item and the
  report's decision list names none, while the run's own record carries the adoption's refusal with `unmergeable`
  as the excluded item's mechanism and the branch as its evidence — the item the next run retries, not a question
  only its owner can answer
- **Case**: `run#anAdoptionTheTreeRefusesIsRecordedAsUnmergeable`

#### Scenario: The refusal is recorded on the exclusion and on no other record

- **WHEN** the run records an adoption's refusal for an item the pre-screen excluded and has not worked
- **THEN** the entry keeps its verdict, its reason and the branch as its evidence and gains the mechanism
  `unmergeable` and the refusal's own text — kept there when a second pre-screen of the same run rewrites the
  exclusions — the table prints it as `#21 needs-decision (unmergeable)` and names no
  decision the owner owes, while an issue that is not one of the run's exclusions and an issue that already carries
  the item record the run wrote are both refused with their reason and the state is left unwritten
- **Case**: `state#anAdoptionRefusalIsRecordedBesideTheExclusion`

#### Scenario: A branch without a green verification stays the owner's decision

- **WHEN** the adoption of an excluded branch is refused because the change it carries has no green verification
  record
- **THEN** the exclusion stays the run's decision — the item is escalated and nothing is merged — so the refusal
  that is the branch's own is not read as one the tree made
- **Case**: `run#doesNotMergeAnAttemptWithoutAGreenVerification`

### Requirement: A child is started with a tree search that runs

A process the run starts SHALL be started with a search call it can make: the directory this project's agent keeps
its tools in SHALL be on the child's `PATH`, the project's tree-search wrapper SHALL be able to scope its walk to one
document, and every phase prompt SHALL name that wrapper rather than a shell command the child's own policy refuses.

#### Scenario: A child inherits the agent's tool directory

- **WHEN** the adapter the run's configuration names starts a child, and this project's agent keeps its tools in its
  own directory
- **THEN** that directory is the first entry of the child's `PATH`, the `PATH` the child inherited stands behind it,
  and a directory that does not exist changes nothing
- **Case**: `adapter#theChildIsStartedWithItsToolDirectoryOnPath`

#### Scenario: A document's section list is one scoped call

- **WHEN** a child asks the wrapper for the section list of one document
- **THEN** one call answers it — the matches with their line numbers, no other document searched, and the answer's
  first line naming what was walked — and a path the root does not hold is a refusal and not an empty answer
- **Case**: `look#aPathScopesTheWalkToIt`

#### Scenario: A phase prompt names a call its own policy allows

- **WHEN** a phase prompt tells a child how to read a document's section list
- **THEN** it names the wrapper's scoped call and not the shell `grep` the policy that every child is started with
  refuses
- **Case**: `prompt#thePhasePromptsCarryTheirOwnRoute`

### Requirement: An item's kept event stream is bounded

The adapter that keeps a child's event stream SHALL bound the file it appends to at `LOOP_CHILD_EVENTS_MAX_BYTES`. When
the file outgrows its bound it SHALL rotate it in two passes: the streamed deltas SHALL be dropped, every record line
SHALL be kept, and only a stream still over its bound SHALL have its oldest half cut. The newest event SHALL survive.
`0`, or a project that records no key, SHALL keep every line.

#### Scenario: A stream past its bound is rotated and the rotation is recorded

- **WHEN** a child writes more events than `LOOP_CHILD_EVENTS_MAX_BYTES` allows
- **THEN** the file stops growing, its first line is a `stream_rotation` record naming the lines, bytes and
  `turn_end` events that were dropped, the newest event is still the last line, and the dropped plus the remaining
  turns are every turn the child took
- **Case**: `adapter#theKeptStreamIsBoundedAndItsRotationIsRecorded`

#### Scenario: A stream under its bound is kept whole

- **WHEN** a child writes fewer events than the bound allows
- **THEN** every line is kept and no rotation record is written
- **Case**: `adapter#theKeptStreamIsBoundedAndItsRotationIsRecorded`

### Requirement: The count a rotation reports never falls

A rotation SHALL be recorded in the stream itself — how many lines, how many bytes and how many `turn_end` events
went — so a reader can tell a rotated stream from a short one. The count of turns a stream reports SHALL NOT fall
when the writer rotates: the turn bound a child is ended by SHALL count the turns the record says were dropped as
well as the ones the stream still holds.

#### Scenario: A rotated stream still counts the turns it dropped

- **WHEN** the run bounds a child's turns and the child's stream carries a rotation record plus the turns still
  present, and their sum reaches that bound
- **THEN** the run ends the child at the recorded bound, its transcript names the count it reached, and the item is
  recorded `blocked` with the run's own bound as the mechanism
- **Case**: `run#aRotatedStreamStillCountsTheTurnsItDropped`

### Requirement: The gate runs the steps that share nothing together

The gate SHALL keep the order the profile's lists give it, SHALL run one first step and one compiled step at a time,
and SHALL run the suite steps a delta owes up to `LOOP_GATE_POOL_JOBS` at a time. `LOOP_GATE_POOL_JOBS=1` SHALL be
the serial gate.

#### Scenario: The suites a delta owes run together

- **WHEN** a profile owes three suite steps that share nothing, each of which spends a known wall clock, and the pool
  names a size above one
- **THEN** every step keeps its own output, status, clock and tally, the gate's wall clock is nearer its longest step
  than the sum of the three, and the verdict carries all three steps' numbers in the profile's order
- **Case**: `gate#independentStepsRunTogether`

#### Scenario: A pooled step's numbers are its own

- **WHEN** a pooled wave carries one failing suite between two passing ones
- **THEN** each step's tally carries that step's own case and failure counts, the verdict is their sum, only the
  failing command is named in the `— failing:` list, and the gate exits non-zero
- **Case**: `gate#aFailingSuitesTallyIsUnchangedByItsNeighbours`

### Requirement: A write the run can place is recorded where it is read

The reading taken while a child runs SHALL be recorded **where it is taken**, and not only in the branch that runs
after the gate: when it places a process of the worker's own tree in the project's own tree, the run SHALL record
that reading as a step of the item under the name `outside-write`, and SHALL start no pre-gate reviewer, no gate
child and no tail for that item, because the item's work is not the run's to verify against a tree the run cannot
vouch for. The step's note SHALL name what changed and the child the process was placed in. The item's `blocked`
result and the reason for that shape are the outcome block's, written from that same reading.

#### Scenario: A placed write is recorded before any gate child starts

- **WHEN** the heartbeat's reading of the project's own tree places a process of the worker's own tree outside the
  worktree while the worker runs
- **THEN** the item's steps carry exactly one `outside-write` step holding that reading, its note names the changed
  paths and the child the process was tied to and says the item ends at that reading, and neither a pre-gate
  reviewer nor a gate child is started for the item
- **Case**: `run#aWriterTheRunCanPlaceBlocksTheItem`

### Requirement: A child is given one scratch directory

Every child the run starts to write for an item's change SHALL be handed one directory it may write that is not
the change root — a scratch directory inside that item's own directory, created before the child starts and named in the
prompt beside the change root — and the prompt SHALL say that a write with a relative path lands in the project's own
tree and not in the change root. A file a child writes there SHALL NOT be a difference in the project's own tree,
and SHALL NOT change the outcome the item's work earned.

#### Scenario: The child's prompt names a scratch directory that exists

- **WHEN** a child is started for an item
- **THEN** its prompt names a scratch directory inside the item's own directory, that directory exists before the
  child runs, a file the child writes there is there when it ends, and the item keeps the outcome its own work
  earned
- **Case**: `run#aChildIsGivenOneScratchDirectory`

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

### Requirement: The change child grades its own diff before it reports

The change child SHALL read the diff it is about to hand over against this contract's blocking predicates before
it prints its diff-ready line, and SHALL answer what it finds there or record it as a note: a scenario the case
that names it does not exercise, a claim in the change's own prose the diff contradicts, an artifact the change
cites that is not where it says it is, and the guideline section phase C named.

#### Scenario: The rendered prompt carries the self-check and what answers each predicate

- **WHEN** the worker prompt is rendered for an item
- **THEN** it carries a block that grades the diff before the line that ends the session, names each of this
  contract's blocking predicates in its own words, and names the one check that answers each
- **AND** it says that a finding made there is fixed in the session or recorded as a note, so the reviewer meets it
  already named rather than spending a round to find it
- **AND** the rule it points at is this contract's, so the prompt states no second definition of *blocking*
- **Case**: `prompt#workerGradesItsOwnDiffBeforeItReports`

### Requirement: A finished run is analysed in one read-only pass that ends in items

A run that has ended SHALL be analysable by one pass that reads its state, its item records and its item streams,
quotes the numbers they already hold rather than recomputing them, and ends with a table of changes worth paying
for. The pass SHALL write nothing — no gate, no build, no edit under `tools/loop/`, `.pi/` or `guidelines/` — and a
defect it finds SHALL become a tracker item, never an inline fix. The entry document SHALL name the skill's path.

#### Scenario: The pass reads the run that ended and never the one in place

- **WHEN** the pass starts and a run's lock owner is alive and its state records no stop
- **THEN** it reads the newest archived state and says in its report which run in place it excluded and when that
  run started
- **AND** with no live run it reads the state in place, including the case where the process was killed: the run
  recorded no stop and its state is still the record
- **Case**: `routed-read#theEntryDocumentNamesTheLoopSkillsByPath`

#### Scenario: The numbers are quoted from what the state and the spend tool printed

- **WHEN** the report carries a run's items, outcomes, wall times, roles, tokens or gate tallies
- **THEN** each figure is the one `tools/loop/report.sh`, `tools/loop/state.sh show` or `tools/loop-spend.py`
  printed for that run, and a figure the state does not hold is printed as not measured rather than as zero
- **AND** any claim about the loop rather than about this run is supported by the per-run table over the archived
  states, naming how many runs it covers
- **Case**: `routed-read#theEntryDocumentNamesTheLoopSkillsByPath`

#### Scenario: The skill the entry document names is the read-only pass, not a name

- **WHEN** a reader follows the entry document's path to the analysis skill
- **THEN** the skill states that the pass is read-only and that a `do` row is filed as an item and never with the
  opt-in label, so the pointer cannot resolve to a text that would authorise a write
- **AND** the case that holds the pointer fails when either sentence is dropped, which is what makes the route a
  contract rather than a mention
- **Case**: `routed-read#theEntryDocumentNamesTheLoopSkillsByPath`

### Requirement: A cut item is named to the caller as work to resume

The report SHALL name every item the run's own bound ended as an action, saying that no decision is owed for it and
carrying the mechanism the run recorded, because a bound is the run's clock and not a finding about the item.

#### Scenario: The action list names a cut item for resume and asks for no gate

- **WHEN** a run ends one of an item's children at a bound, records the item `cut` with the mechanism, and the item
  never reached a gate
- **THEN** the report's "actions the caller owes" list carries a `resume #<item>` line naming the mechanism and the
  item's transcript, and it carries no `re-run the gate for #<item>` line
- **Case**: `run#aReviewerEndedAtItsTurnBoundIsNotARefusal`

### Requirement: A gate run in a kept worktree runs under the environment that worktree records

`tools/loop/gate.sh` SHALL read the environment file the worktree it runs in records, before it plans its steps, so a
gate a person runs by hand in that tree measures what the run would have measured.

#### Scenario: A hand-run gate sees the worktree's recorded environment

- **WHEN** a worktree carries `.pi/loop-env.sh` with a value in it, and the gate is invoked from that tree — or from a
  subdirectory of it
- **THEN** the gate's own compiled step runs with that value in its environment, and a tree that carries no such file
  gates exactly as before
- **Case**: `gate#runsUnderTheWorktreeEnvironment`

### Requirement: A prompt whose child reads a long text names the cap that cuts it

The phase prompt a child is started with SHALL name the bound a tool result is cut at, the marker a cut result
carries, the call and the arguments that page the rest until the file's last line, and the rule that a quoted anchor
comes only from a window that arrived whole. The bound is enforced where a result passes
(`tools/pi-trim-result.mjs`), never by asking the child to read less, so the prompt is the only place a child learns
that what it holds may be a window rather than the file — and a review's whole product is a claim about the text it
read, which the gate cannot tell from one written over a file head.

#### Scenario: Every phase prompt names a cut read and how to page it

- **WHEN** the worker's, the gate child's, the tail's or the reviewer's prompt is rendered
- **THEN** it names the recorded bound a tool result is cut at, the marker the cut result carries, the call and the
  range arguments that page the rest until the file's last line, and that only a window that arrived whole may be
  quoted or graded
- **Case**: `prompt#thePhasePromptsCarryTheirOwnRoute`

### Requirement: The numbers a run records name the bounds they were measured against

A run's state SHALL record, once in `.run`, the bounds its children's figures are read against: the character bound a
tool result is cut at (`LOOP_TOOL_RESULT_MAX_CHARS`) and the two bounds on the context an item's turns cost
(`LOOP_ITEM_CONTEXT_WARN`, `LOOP_ITEM_CONTEXT_MAX`). Each SHALL be the number the configuration records, and `0`
where it records none or `0`.

#### Scenario: A run's record and its report name the bounds its figures were read against

- **WHEN** a run is initialised against a configuration that records a result bound and a context warning and stop
- **THEN** its state carries those three numbers in `.run`, each equal to what the configuration records, and the
  live view `state.sh show` prints them beside the budget and flags it already prints
- **AND** the report's header names the same result bound, beside the per-item table whose `context` column is the
  largest context a turn of that item reported, so the column is read against the stop that bounds it
- **AND** a configuration that records no such key, or records `0`, is recorded as `0` — no bound — rather than as a
  number the run invented, and a state written before the fields existed prints them as unrecorded, never as `0`
- **Case**: `state#theRecordNamesTheBoundsItsNumbersWereMeasuredAgainst`

### Requirement: A run counts the reads its sessions repeated

An item's record SHALL carry the file reads its children made whose path the same session had already read, the
characters of those repeats' own results, the characters the repeat rule withheld from those repeats — the repeat's size
less the one line that replaced it — and the reads they are a share of, read from the event stream the item's children
appended to and from the record the rule writes beside it — and the report SHALL print them, per item and for the run,
naming the item that repeated most, and SHALL NOT call the raw repeat volume re-injected. The figure SHALL be a warning:
no key bounds it and no item's outcome reads it.

#### Scenario: The reads a session repeated are recorded, printed and named

- **WHEN** an item's event stream records 3 reads of one path and 1 of another in one session, and a second session
  that reads the first session's path again, and another item's stream records 2 reads of one path, and a third item's
  stream opens no file at all
- **THEN** the record carries the read calls (`metrics.reads`), the ones whose path the same session had already read
  (`metrics.dupReads`) and the characters of those repeats' own results (`metrics.dupChars`) — `ctx_read` and `read`
  alike, a repeat counted per session so the second session's read is a read and not a repeat
- **AND** the table's column appended after the calls-per-turn one prints the item's figure as `repeat/total`
- **AND** the trend's `reads:` line prints the run's repeats out of the run's file reads with the percentage, the
  characters in the repeat results, the characters the repeat rule withheld of them and the item that repeated most, and
  an item whose stream measured no read prints `-` in the column, the trend printing the dash for a run that opened no
  file and the other dash, with its reason, for a state written before the field existed
- **AND** the record carries `metrics.withheld` — the repeat lines of the record the rule writes beside the stream
  counted and summed — and it is `null` for an item whose directory holds no repeat record, so a state written before
  the field prints the dash rather than a zero the run never measured
- **Case**: `report#anItemRecordsItsRepeatReads`

#### Scenario: A repeat whose text was withheld is recorded as the rule's own figure

- **WHEN** a session reads one path, reads the same unchanged content a second time, and the repeat rule replaces that
  second result with its one line
- **THEN** the rule appends `LOOP-TOOL-RESULT-REPEAT: <tool> chars=<n> kept=<k>` to the record the adapter names beside
  the child's stream — one line per repeat, `<n>` the result's size and `<k>` the notice's — and the line carries no
  path, so `chars - kept` is the volume the child was not handed
- **Case**: `repeat#aRepeatIsRecordedAsWhatItWithheld`

### Requirement: An item's record keeps what the run measured to decide it

An item's record SHALL carry the gate tally the run read for it and the verdict of the review round the run read its
diff in, whichever arm the item ended on — not the arm that closed it alone — and the report SHALL NOT name a gate to
re-run for an item whose record carries a tally, and SHALL NOT count an item whose diff was read as never reviewed.

#### Scenario: A blocked item records the gate it ran, and no gate is re-run for it

- **WHEN** a change child stops at the diff, the gate child reports a tally for the gate it ran, and the tail refuses
  the item on that gate
- **THEN** the item's record carries the gate child's tally in `metrics.gate`, in place of the empty field it was
  written with, and the report's action list holds no `re-run the gate` line for that item
- **Case**: `run#aRedGateIsBlockedByTheTail`

#### Scenario: The round that read the diff is recorded on an item that then blocked

- **WHEN** the round before the gate answers `PASS` and the item then ends blocked on its gate
- **THEN** the record carries `metrics.reviewVerdict: PASS` and `metrics.reviewRounds: 1`, and the report's review line
  counts it as a first round that passed rather than as an item never reviewed
- **Case**: `run#aBlockedItemRecordsTheRoundThatReadIt`

### Requirement: A close over a gate that did not pass records its waiver

An item SHALL close over a gate that did not pass only when the record carries the clause it was waived with, and an
item's record SHALL carry that clause and the suite it names beside the tally it waived — so that a close over a red
gate and a close over a green one are told apart by the record, and the report counts the waivers instead of printing
both as the same verdict.

#### Scenario: A close over a red tally that records no waiver is refused

- **WHEN** `tools/loop/state.sh done` is asked to record an item `done` with a tally that carries a nonzero `failures=`
  or `errors=` and no `--gate-waived` clause
- **THEN** the write is refused as a usage error and no item is recorded, so the item stays queued for the next
  attempt; a waiver beside a result that is not `done`, beside no `--gate` at all, or beside a tally that passed is
  refused the same way, and a tally that carries no count (a project with no gate, a gate that never ran) closes
  without one
- **Case**: `state#aCloseOverARedGateWithoutAWaiverIsRefused`

#### Scenario: A waived close keeps its clause and the report counts it

- **WHEN** an item is recorded `done` over a red tally with `--gate-waived` and `--gate-waived-suite`, and a second
  item of the same run closes over a tally that passed
- **THEN** the record carries the clause in `metrics.gateWaived` and the suite in `metrics.gateWaivedSuite` beside the
  tally in `metrics.gate`, the green close carries neither, and the report's `waived:` line prints
  `1 of 2 closed items (<suite> ×1)` with what the count is a count of
- **Case**: `state#aCloseOverARedGateRecordsWhyItWasWaived`

#### Scenario: The run carries the clause its child recorded, and blocks the close that recorded none

- **WHEN** the child whose verdict the run decides from reports a red `LOOP-GATE` with `LOOP-GATE-WAIVED` and
  `LOOP-GATE-WAIVED-SUITE`, and a second run's child reports the same red tally with neither
- **THEN** the first item is recorded `closed` with both fields carried into the record, its report counts one waived
  close, and the second item is recorded `blocked` with the tally kept and the reason naming the line that would have
  recorded the waiver — and nothing is closed on the tracker for it, because the refusal is read before the merge
- **Case**: `run#aCloseOverARedGateIsWaivedOrBlocked`

### Requirement: A next-up label leads the queue and decides nothing else

The pre-screen SHALL rank an item carrying the profile's `roles.nextUpAgent` label ahead of every other item,
in the class order and issue-number order the other streams keep, and SHALL leave every other item's relative
order unchanged. The label SHALL be a rank, never a gate, and SHALL be read from the profile, so a project that
declares it empty ranks exactly as it does without the key.

#### Scenario: A next-up item leads the queue the interval would end

- **WHEN** the pre-screen ranks a backlog whose eligible items include an improvement in a leading category that
  carries the next-up label, beside a leading-category defect and an unpinned app bug
- **THEN** the labelled item takes rank 1 — the position the class order and the issue number give it among the
  labelled items only — and the unpinned items keep their own relative order behind it
- **Case**: `prescreen#aPinnedItemLeadsTheQueue`

#### Scenario: The label is a rank, not a gate

- **WHEN** an item carrying the next-up label is on hold, or its author is not authorised
- **THEN** it is excluded exactly as it would be without the label, writes no other label and takes no rank
- **Case**: `prescreen#aPinnedItemLeadsTheQueue`

#### Scenario: A project that declares no next-up label ranks on the keys alone

- **WHEN** the profile declares the key empty, or omits it
- **THEN** the category key, the class order and the issue number decide the queue alone, without stopping the
  pre-screen on a key the project never declared
- **Case**: `prescreen#aProfileWithNoPinPinsNothing`

### Requirement: A child is started on the command its role and its class record

Each child SHALL be started on the command recorded for the role its prompt names. A role with no key of its own
SHALL inherit the key of the role its name qualifies — the word before its first hyphen — and only a role whose head
records nothing SHALL fall back to `LOOP_AGENT_CMD`; so no role the engine starts is left on the project's single
command when the role it belongs to records one. The rework round a refusal buys SHALL be a role of its own, with its
own command and turn-bound keys, rather than the worker's. An item's class SHALL be able to select a command for the
role it is spelled after, and the most specific key SHALL win: a role's own key beats its head's class key. The run's
header SHALL name every role whose command, resolved without the item's class, is not the project's one; a
class-qualified command is named by the child's own transcript.

#### Scenario: A role inherits the command of the role it belongs to

- **WHEN** a project records `LOOP_AGENT_CMD_REVIEWER` and no command of its own for the round before the gate, and a
  run starts that round
- **THEN** the round before the gate is started on the reviewer's recorded command, the round after the gate on the
  same one, and the run's header names both roles — `agent-roles=reviewer,reviewer-pre-gate`
- **Case**: `state#aRolesChildInheritsTheCommandOfTheRoleItBelongsTo`

#### Scenario: The rework round is a role of its own

- **WHEN** the round after the gate refuses a change and the run starts the rework round
- **THEN** that child is started on `LOOP_AGENT_CMD_WORKER_REWORK` and ended at `LOOP_CHILD_TURNS_MAX_WORKER_REWORK`,
  which the transcript names with its role — neither key is the worker's
- **Case**: `state#theReworkRoundIsARoleOfItsOwn`

#### Scenario: An item's class selects the worker's command

- **WHEN** one run works two items of different classes and the project records a command for each class beside the
  worker's key
- **THEN** each item's worker child is started on the command its own class records, and a role's own key still wins
  over a head's class key
- **Case**: `state#anItemsClassSelectsTheWorkerCommand`

#### Scenario: A project that records nothing runs every child on its one command

- **WHEN** every per-role key is empty and `LOOP_AGENT_CMD` records the command
- **THEN** every child of the run — the change child, the tail and the reviewer — is started on it, and the header
  names no role
- **Case**: `state#agentCommandIsTheOnlyIndirection`

### Requirement: The spend report charges a session to the role its prompt names

`tools/loop-spend.py` SHALL read a session's role the way the engine starts that child — from the name of the prompt
file that is the session's first user message, when that message is the path to a file whose name ends `.md`, the only
form the run renders (`run_child` writes `*-prompt.md`, and every one of the archive's 730 sessions carries that form),
applying the arms in the order `child_role` applies them (`tools/loop/run.sh`) — and SHALL charge the session's turns,
tokens and cost to that role and to no other, so its per-role table and its `--json` `byRole` name the roles the engine
starts: `worker`, `worker-rework`, `worker-gate`, `worker-tail`, `reviewer` and `reviewer-pre-gate`. A first user
message in any other form — prompt text, or a path whose name does not end `.md` — SHALL keep the reading the words
give it (a `reviewer` word, else `worker`); a `.md` prompt name no arm of `child_role` matches SHALL be charged to the
name it carries without its extension rather than folded into the worker; and a role with no session SHALL be absent
from the table rather than printed as a zero.

#### Scenario: Each role the engine starts is charged to its own name

- **WHEN** a stream holds one session started from each prompt the run renders — `worker-prompt.md`,
  `worker-prompt-round2.md`, `worker-gate-prompt.md`, `worker-tail-prompt.md`, `reviewer-prompt-1.md` and
  `reviewer-pre-gate-prompt.md` — each with one turn of its own known size
- **THEN** `--json`'s `byRole` carries those six keys and no seventh, each holding that session's one turn, its one
  session and its own token total
- **Case**: `spend#eachRoleTheEngineStartsIsChargedToItsOwnName`

#### Scenario: The round before the gate is charged to neither the reviewer nor the worker

- **WHEN** a session's first user message is a `reviewer-pre-gate-prompt.md` path, beside a reviewer session and a
  worker session in the same stream
- **THEN** its turn and its tokens are counted under `reviewer-pre-gate`, and the `reviewer` and `worker` entries carry
  only their own sessions' turns and tokens
- **Case**: `spend#theRoundBeforeTheGateIsChargedApart`

#### Scenario: The per-role table prints the roles the JSON names

- **WHEN** the report prints its per-role table over a stream that holds a session of each of the six roles
- **THEN** the table carries one row per role, each of them a key of `--json`'s `byRole`, and no row for a role the
  stream holds no session of
- **Case**: `spend#thePerRoleTableNamesWhatTheJsonCarries`

#### Scenario: A prompt outside the engine's arms keeps the reading it had

- **WHEN** a session's first user message is prompt text rather than a path, or a prompt file whose name no arm of
  `child_role` matches
- **THEN** the text form is charged to `reviewer` where its words name a reviewer and to `worker` otherwise, and the
  unmatched name is charged to the name it carries rather than to the worker
- **Case**: `spend#anUnmatchedPromptKeepsItsOwnReading`

### Requirement: The loop's closure is refused when the base does not carry the commit

The loop's `close` verdict SHALL refuse a closure whose commit the base branch does not carry, and SHALL write nothing
when it does, so that `status/fixed-by` and "the fix is in the base branch" are one
claim on the loop's own path rather than two claims a refused call can leave disagreeing.

#### Scenario: A closure on a commit the base does not carry writes nothing

- **WHEN** the loop's `close` verdict is handed the commit the landing made (`--landed`) and the base branch the
  profile names does not carry that commit
- **THEN** the call exits 3 with the reason `not-on-base`, naming the commit and the base branch, and writes nothing:
  no `status/fixed-by`, no comment, no close, so the item is not left settled on a fix the tree does not have
- **AND** the same verdict handed a commit the base carries records the settled state and closes the item
- **Case**: `labels#closeRefusesACommitTheBaseDoesNotCarry`

### Requirement: A spent quota pauses the run instead of ending it

The run SHALL ask the project's recorded quota command before each item and, when the command holds, SHALL wait
until the instant it names and ask again, moving its own deadline with the wait, so that a condition outside the
run costs it no budget and needs no new command. The check SHALL be switchable for one run — by the run's own
arguments and by the environment — without editing the configuration, and the run's header SHALL name the reader it
armed or that it armed none.

#### Scenario: A window the reader holds on is a pause, not a stop

- **WHEN** the recorded quota command answers with the status its protocol reserves for a hold
- **THEN** `state.sh next` exits 6 — not 3 — with the reader's reason and the instant it named on stdout, and it
  records no stop, so the run's end is still its own condition rather than the provider's
- **Case**: `state#aQuotaWindowIsAHoldNotAStop`

#### Scenario: The run waits and then works the item

- **WHEN** a run reads a hold at the boundary before an item, the reader having named an instant seconds away, and
  the reader lets the run go on its next call
- **THEN** the run prints the pause with the reader's own reason, records it in the run's state with the seconds it
  waited, sleeps until that instant, and works the item — ending on its own item cap
- **Case**: `run#aQuotaHoldPausesTheRunAndResumesIt`

#### Scenario: A pause is not spent budget

- **WHEN** the run waits for a condition outside it, and its budget would otherwise have been spent by the time the
  wait ends
- **THEN** the run's deadline moves by exactly the seconds the wait takes — after which the same run may start
  another item — and the wait is kept in the run's record with the reason it was taken for
- **Case**: `state#aHoldMovesTheDeadlineWithIt`

#### Scenario: A reading that could not be made is not a refusal

- **WHEN** the quota command fails, or is not there at all
- **THEN** the run does not hold: it is named on stderr as a check that could not be made, and the candidate the
  queue holds is handed out as usual
- **Case**: `state#aQuotaCheckThatCannotBeMadeIsNotARefusal`

#### Scenario: The check is a switch the operator holds

- **WHEN** a run is started with the command-line switch that turns the check off, or with the key set to an empty
  value in the environment
- **THEN** no reader is asked for that run — while the same run without the switch asks the one the configuration
  names — and the run's header records which reader it armed, or that it armed none
- **Case**: `run#theQuotaSwitchIsExplicit`

#### Scenario: The report reads the wait as neither spend nor budget

- **WHEN** a run that recorded a wait is reported
- **THEN** the summary's spend figure excludes the wait, the wait and how many there were are named beside it, and
  the figure floors at zero — so a paused run never reports a negative budget left, and its clock and its work are
  two numbers
- **Case**: `report#theSpendIsWorkNotWaiting`

#### Scenario: No recorded command asks nothing

- **WHEN** the project's configuration records no quota command
- **THEN** no process is started, no question is asked, and the run's own conditions are its whole authority — the
  behaviour the engine had before the command existed
- **Case**: `state#noQuotaCommandAsksNothing`

### Requirement: The run's stop reason names the refusal it stopped on

A run that stops because a child could not start SHALL name, beside the status and the answer's size it already
names, the refusal the child's own event stream recorded — the provider and the model of the last refused request
and the refusal's own text — so a person reading the stop tells a provider that refused every call from a child that
broke. A stream that is absent, unreadable or carries no refusal SHALL leave the reason as the base wrote it.

#### Scenario: A refused provider is named in the stop

- **WHEN** a child ends with no verdict line and leaves the item's tree at the run's base commit, and its event
  stream carries a refused request — a `turn_end` on `stopReason: error` or an `auto_retry_end` with a `finalError`
- **THEN** the run's recorded stop reason names the provider and the model of the last refused request and quotes the
  refusal's own text, beside the status and the answer's size it already names, and a stream that carries no refusal
  leaves the reason exactly as the base wrote it
- **Case**: `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault`

### Requirement: A child that never started is retried before the run stops

A failure whose cause lies outside the item SHALL NOT end the run on its first occurrence. The run SHALL spend a
recorded, capped retry on the class before it records its own stop, naming each attempt, the delay it waited and the
keys it read them from. The cap (`LOOP_RETRY_MAX`, `LOOP_RETRY_DELAYS`) SHALL be one budget per transient class per
run, and `LOOP_RETRY_MAX=0` a project that records no retry. The run's own `state.sh` stops SHALL stay final.

#### Scenario: A child that never started is retried and the item is then worked

- **WHEN** a child ends without a verdict line and leaves the item's tree at the run's base commit, and the retry cap
  has not been spent
- **THEN** the run waits the delay the configuration records for that attempt, re-runs the item with the same work
  package — the failed attempt read nothing of it — and names the attempt and the keys it read in the line it prints,
  so the item is worked instead of the window being spent on one transient refusal
- **Case**: `run#aChildThatNeverStartedIsRetriedBeforeTheRunStops`

#### Scenario: The retry cap is a cap

- **WHEN** the class keeps failing past `LOOP_RETRY_MAX` retries
- **THEN** the run stops exactly as it did before the retry existed — recording its own stop reason with no item
  record and no escalation — and that reason names the retries it spent and the keys it read them from
- **Case**: `run#theRetryCapIsSpentAndTheRunStops`

### Requirement: The rate of items the run's own bound ended is printed beside the archive's

A run's report SHALL print how many of the items it attempted the run's own bound ended — read from the record's own **result**, `cut`, which exists only for a bound the run enforced, and from the `stopReason: bound` a record written before that result came into use carries — and the archive's rate beside it, counted the same way over every archived record. It SHALL name each such item with the turns it spent and the files its leftover patch carries, and an item's record SHALL carry that count, read from the patch, when a leftover path is recorded.

#### Scenario: The run's rate and the archive's are printed together, with what each item kept

- **WHEN** a run records 12 items, 3 of them `cut` — one with `bound` as its stop reason having kept a patch carrying
  3 files, one with `bound` and no patch, and one a review round's own reading bound ended, which carries no
  mechanism — and the archive beside it holds 25 item records, 3 of them bound and the rest carrying neither
- **THEN** the report's `bound:` line prints `3 of 12 items (25.0 %)` for the run and
  `3 of 25 items a bound ended (12.0 %)` for the archive, names all three items with their turns and, for the one
  that kept a patch, its file count, and names no item that closed
- **AND** a run the bound ended nothing in prints `0 of 3 items (0.0 %)` and an archive of no finished run prints
  `-`, because a rate over no item is not a measurement and `0 %` would read as one
- **Case**: `report#theBoundRateIsNamed`

#### Scenario: A kept patch is priced with the files it carries

- **WHEN** an item is recorded `cut` with `bound` as its stop reason and the path of a patch carrying three files, a
  second with no leftover path, and a third naming a path that does not resolve
- **THEN** the first record carries `leftoverFiles: 3` beside the path and beside the mechanism it already carried,
  and the second and third carry none — never `0`, which would read as a patch that was measured and was empty
- **Case**: `state#aCutRecordsTheFilesItsPatchCarries`

### Requirement: A start resumes the work a bound-ended child left

The start of an item SHALL apply `<item dir>/leftover-from-killed-run.patch` — what a child an earlier run ended at a bound left — onto the branch it cut from the base, before a worker exists, and SHALL commit none of it: the item is worked on that work and the branch's `HEAD` stays the base commit. Where neither a plain nor a 3-way apply takes it, the tree SHALL be put back on the base and the non-apply recorded in the item's note, so it stays workable and nothing is discarded unrecorded.

#### Scenario: The work a bound-ended child left is applied before the worker starts

- **WHEN** an earlier run's bound ended an item's child, leaving that child's work in the item's
  `leftover-from-killed-run.patch`, the branch at the base, and the next run starts the item
- **THEN** the start applies that patch onto the branch it cut from the base, before a worker exists — plain, and 3-way
  when the base moved under the diff — so the tree the worker is handed carries the earlier work, the branch is the
  item's and its `HEAD` is still the base commit, and the item's record names the resume
- **Case**: `run#aBoundEndedItemsKeptWorkIsResumedByTheNextStart`

#### Scenario: A kept patch the base does not take is recorded, not discarded

- **WHEN** the patch neither applies plainly nor 3-way — the measured shapes are `git apply --check` exit 1 on
  `items/791` and `items/729` and exit 128 on `items/16`
- **THEN** the tree is put back on the base, including whatever the 3-way attempt had already staged, the item is
  worked from the base, and the item's note names the patch that did not apply, so a reader learns why the attempt
  began from zero and the work is not discarded unrecorded
- **Case**: `run#aKeptPatchThatDoesNotApplyIsRecordedAndTheItemStartsFromTheBase`

### Requirement: A rotation keeps the record lines the spend is read from

A rotation of a child's event stream SHALL remove its streamed deltas and keep every record line: `message_update` and `tool_execution_update` go, `message_update` only where the stream holds a `turn_end` and every one of them carries a `message.usage`, and the oldest-half cut runs only on a stream still over `LOOP_CHILD_EVENTS_MAX_BYTES` afterwards. Its record keeps its shape, with a cumulative `dropped_turn_ends` and the `dropped_lines`/`dropped_bytes` it dropped.

#### Scenario: A rotated stream keeps every record the spend is read from

- **WHEN** a child's stream outgrows `LOOP_CHILD_EVENTS_MAX_BYTES` and every `turn_end` of it carries a `message.usage` —
  the archived shape: 747 of 747 turns over six streams, 746 byte-identical to the `message_update` before them
- **THEN** the rotation drops the `message_update` and `tool_execution_update` lines and keeps every record line — every
  `turn_end`, `tool_execution_end`, `session` and `message_end` in its own order — so no turn the child took is lost,
  and the record states the lines and bytes it removed: measured on `items/833/events.jsonl` at a 4 MiB bound,
  8 593 950 B → 3 590 953 B with all 90 `turn_end` records kept, where the line-position cut kept 57
- **Case**: `adapter#aRotatedStreamKeepsEveryTurnEnd`

#### Scenario: A turn whose update a rotation took still records what it spent

- **WHEN** an item's stream holds no `message_update` because a rotation dropped them, and its `turn_end` records carry
  `message.usage`
- **THEN** the item's `metrics.turns`, `metrics.tokens` and `metrics.context` are the figures the undelta'd stream
  records — one turn's usage is its last update's, or its own record's when no update survives it, whether or not the
  stream rotated — so a rotation changes no measurement of spend
- **Case**: `state#theSpendSurvivesTheLossOfItsDeltaLines`

#### Scenario: An update-only stream keeps the usage records it has

- **WHEN** a stream outgrows `LOOP_CHILD_EVENTS_MAX_BYTES` and holds no `turn_end` at all, so no record of it carries a
  usage for a turn to be read from
- **THEN** the rotation drops the `tool_execution_update` lines and keeps every `message_update` — the only usage record
  the stream has — instead of taking them on a precondition no `turn_end` can meet
- **Case**: `adapter#anUpdateOnlyStreamKeepsItsUpdates`

#### Scenario: A stream over its bound after the delta pass is cut, and its turns counted

- **WHEN** a stream's record lines alone outgrow the bound, so the delta pass leaves it over
- **THEN** the oldest half of what remains is cut as before, the rotation record's `dropped_turn_ends` names the turns
  that went cumulatively, and one record stands for every rotation the stream went through
- **Case**: `adapter#aStreamStillOverItsBoundAfterTheDeltaPassIsCutAndRecorded`

### Requirement: A truncated spend says so

A rotated stream SHALL record what the rotation dropped — the `stream_rotation` record's `dropped_turn_ends` and `dropped_bytes` — as a figure of the item's own metrics, and the report's trend SHALL name every item whose figures are a lower bound, with the figure each dropped. A key the state does not hold means a record written before the field existed; `null` means this era records it and the item has none. It is a warning, never a stop.

#### Scenario: A rotated item records the rotation it survived

- **WHEN** an item's stream carries a `stream_rotation` record — the residue #840 leaves on a stream whose records
  alone outgrow `LOOP_CHILD_EVENTS_MAX_BYTES`: nine of fifteen streams in the run of 2026-11-09T21:42:41Z, 1 697
  dropped turns against the 1 372 the state recorded
- **THEN** the item's `metrics.streamRotation` holds that record's `droppedTurnEnds` and `droppedBytes` as numbers —
  `null` for a stream with no rotation, and the key absent only in a state written before the field existed — so what
  the short figures are short by is in the state and not only in the stream
- **Case**: `report#aTruncatedStreamSaysSo`

#### Scenario: The report names the items whose spend is a lower bound

- **WHEN** the trend block prints the run's figures and at least one item's stream rotated
- **THEN** the `truncated:` line names each of those items with the figure it dropped, beside the run's totals in the
  three-part shape the `reads:` and `calls:` lines print, and an item whose stream did not rotate is not named; the
  turns are the items that lost one, while the bytes are the records' sum over **every** rotated stream, so a rotation
  that dropped only deltas is counted in the bytes and named by no turn
- **Case**: `report#aTruncatedStreamSaysSo`

#### Scenario: Nothing rotated is no rotation, and a state that predates the field says so

- **WHEN** no item of the run has a rotated stream, or the state was written before the field existed
- **THEN** the line prints `-` with its reason and never a zero that reads as a measurement — the two dashes told
  apart as the repeat-reads line tells them apart — and no figure of the run ends because of it: it is a warning,
  never a stop
- **Case**: `report#aTruncatedStreamSaysSo`

### Requirement: Every role's turn bound is the one the run's header prints

The run SHALL print one `child-turns-max=` token naming every role's turn bound, in the order `agent-roles=` names the roles, so the bound a `LOOP-TIMEOUT` line applies is one the header already carries. The rework round's value SHALL be 150, a measured raise from 100 (issue #842); no other role's value and no context bound moves with it.

#### Scenario: The header names all six roles' bounds

- **WHEN** a run starts on a project that records a turn bound for every role — the rework round among them, whose 150 is
  a measured raise rather than that role's p95
- **THEN** its header's `child-turns-max=` token names `worker`, `worker-gate`, `worker-tail`, `worker-rework`,
  `reviewer` and `reviewer-pre-gate` in the order `agent-roles=` lists them, each at the figure its key records, so no
  role the run can end at a bound is missing from the line that records the bounds it applies
- **Case**: `run#eachChildIsBoundedByItsOwnRolesTurns`

#### Scenario: The bound a rework transcript names is the bound the header printed

- **WHEN** the round a refusing review bought reaches its role's recorded turn bound and the run ends it there
- **THEN** its transcript carries `LOOP-TIMEOUT: the child reached the recorded turn bound of <n> turns
  (LOOP_CHILD_TURNS_MAX_WORKER_REWORK for the worker-rework role)` and the run's header printed that same figure for
  that role, the two readings being one bound
- **Case**: `state#theReworkRoundIsARoleOfItsOwn`

#### Scenario: The raise leaves the context stop where it was

- **WHEN** the rework round's turn bound is raised, because the item a run cut there died 0.4 % under the token stop
- **THEN** `LOOP_ITEM_CONTEXT_MAX` stays 200 000 — the refusal #833 recorded and #836's re-proposal, closed
  `NOT_PLANNED`, stand — and only turns inside the provider's window are what the raise spends
- **Case**: `run#childPastItsContextBoundIsCut`

### Requirement: A refused start is attributed, and a recorded item's work is discarded

A start refused because the worktree is not clean SHALL NOT end the run when the item the branch marker names was recorded by this run: its leftovers SHALL be written to its own `<item dir>/leftover-from-killed-run.patch`, the tree discarded, and the start retried. For an owner this run did not record, or one whose work cannot be written, the refusal SHALL keep the leftovers, escalate that item, and stop naming it and the files.

#### Scenario: A recorded item's leftovers are kept and the run goes on

- **WHEN** an item's start is refused mid-run because the worktree is not clean, and the item the branch marker names was recorded by this run — as the run of 2026-11-10T08:19:24Z recorded #807 3 s before it refused #100's start and then stopped with 262 of its 480 minutes unspent
- **THEN** that item's remaining work is written to `<its dir>/leftover-from-killed-run.patch`, the line the run prints names the record it relies on, the tree is discarded, the refused start is retried, the next item is worked, and the item keeps the single record and the escalation its own arm wrote
- **Case**: `run#aRecordedItemsLeftoversAreKeptAndTheStartGoesOn`

#### Scenario: A refused start over unrecorded work still ends the run, naming it

- **WHEN** an item's start is refused mid-run and the item the branch marker names is not one this run has recorded, or its work cannot be written to a patch
- **THEN** the leftovers are kept, the item is recorded blocked with them and escalated, the stop reason names that item and the files — not the refusal's first line — and the item that could not start keeps no record of its own
- **AND** no case exercises this path: the branch marker always names the item the run is working, and an unwritable item directory breaks the run earlier. The gap is stated beside the mutation set, which pins the discard (`271.1`, `271.2`) instead

### Requirement: The change is completed before the gate, and a fresh child records the gate's result

The change child SHALL end at its diff. A fresh child SHALL apply the reviewer's findings and complete the change —
cited results, the pointer check, the delta and the archive — before it runs the gate; the child after the gate SHALL
write only the change's `evidence/`. A gate whose tally is red, or says it was not run, SHALL leave the change under
its unarchived name, and the run SHALL restore it before it commits. An item's recorded spend SHALL be the sum of its
children's streams.

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
  archive of is refused rather than reported as restored, and a tally that passed moves nothing
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
