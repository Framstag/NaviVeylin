# agent-issue-loop delta

## MODIFIED Requirements

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
- **Case**: `run#childWriteOutsideTheWorktreeIsNoted`, `run#mergesOverADifferenceItCannotPlace`

#### Scenario: A difference the run cannot attribute does not decide the item

- **WHEN** a difference is recorded and the run was not started with `--strict-outside-write yes`
- **THEN** the item keeps the outcome its own work earned, because the comparison measures a tree the run, a
  peer session and every landing also write, and two readings of it are not evidence about one child — and the
  landing step that merges the item's branch is handed that same recorded reading: a difference it can place
  nowhere is recorded with the paths and merged over, while one the run placed in its own process tree is refused;
  a difference the run *can* place in the child's own tree does decide, and that placement is the attribution this
  requirement states
- **Case**: `run#aPeerWriteWhileAChildStandsDoesNotDecideTheItem`, `run#mergesOverADifferenceItCannotPlace`,
  `worktree#theLandingDecidesByTheReadingItIsHanded`

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

### Requirement: The item is closed on a base that carries its fix

A run SHALL merge the branch it committed an item on onto the base it recorded before closing that item, and a
merge it cannot take SHALL leave the item open: `status/fixed-by` and "the fix is in the tree" SHALL be one
claim, so a closure the tree does not back is a blocked item with its branch named, never a settled one. The tree
the merge lands in is the project's own, and what the run does about uncommitted work there SHALL be what its own
rule for that tree says: the reading its children took travels with the merge call, a difference that reading
placed in no process of the run's own SHALL be recorded — with the paths — and merged over, and only a difference
the run placed, or one no reading covers, SHALL refuse the merge (issues #286, #862).

#### Scenario: The branch the run committed is merged before the item is closed

- **WHEN** an item passed its review with an archived change and the run had already committed that change and
  its evidence on the item's own branch
- **THEN** the branch's commits are on the base before the item is closed, the base the run recorded advances
  to the merge, and the branch with its commits is named in the run's record and in the closure's evidence
- **Case**: `run#mergesItsCommittedBranchBeforeClosing`

#### Scenario: A difference the run placed nowhere does not stop the landing

- **WHEN** an item passed its review and the run is about to close it, the project tree the base branch is checked
  out in is not clean, and the readings the run took of that tree while its children ran placed the difference in
  no process of its own — the shape of a person's edit, or of a write by a shell that never left the project
- **THEN** the branch is merged, the base advances to it, and the difference is recorded under the item beside the
  readings its children took — one line naming the paths it changed and the reading that placed nothing, printed
  as the landing is taken — and the item is closed on the work its own tree now carries
- **Case**: `run#mergesOverADifferenceItCannotPlace`

#### Scenario: A merge over a project tree that is not clean is refused, and nothing is closed

- **WHEN** the merge is asked for over a project tree that is not clean, and the reading the run handed it placed
  the difference in a process of its own tree, or no reading of that tree was taken at all
- **THEN** the merge is refused with the reason it found and the base stays where it was: it does not merge over
  work it would attribute to itself, no commit of the branch reaches the base, and the branch is left where it was,
  nothing merged into the tree the base branch is checked out in
- **Case**: `worktree#theLandingDecidesByTheReadingItIsHanded`, `worktree#refusesToMergeOverADirtyProjectTree`

#### Scenario: The pre-screen's adoption lands over a difference this run placed nowhere

- **WHEN** a previous attempt's branch carries an archived change and a green verification, and the project's own
  tree carries a difference that arrived after the run's pre-flight read it clean — before any child of this run
  existed — so no process of this run can have placed it
- **THEN** the adoption merges the branch, closes the item naming the branch it merged, and records the difference
  with its paths, exactly as the run's own landing does
- **Case**: `run#anAdoptionLandsOverADifferenceItCannotPlace`

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