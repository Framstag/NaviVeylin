# Spec Delta — agent-issue-loop

## MODIFIED Requirements

### Requirement: Version control belongs to the run's script, not to the agent

The run SHALL create and manage the working tree and the branch of each item itself, and no phase of an
item SHALL be able to invoke version control on its own behalf. A previous attempt's branch SHALL be decided by
what it *carries*, read by **patch identity** and not by ancestry — the run lands work as `fix: … (#N)`, so such
a branch is not an ancestor of the base: a branch whose attempt **finished** — one archived change of its own,
added by a commit that names its issue — SHALL be merged and its item closed by the run; when that branch's own
evidence is not admissible for adoption it SHALL be refused and escalated as a decision, unless the owner has
answered that question and the run sets that attempt's branch aside — then the attempt's commits SHALL be kept
under a name the run gives them and the item SHALL be worked again on its own branch, the shape a branch that
carries no archived change of its own already gets; a set-aside git refuses — another worktree stands on the
branch — SHALL keep the verdict the decision the run escalates, the reason saying the recorded answer was not
executed, so a set-aside that did not happen does not read as one that did; and when its evidence
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

#### Scenario: An answered attempt is a rework round, not a question asked again

- **WHEN** the pre-screen meets an item whose branch carries a finished attempt that is not admissible for
  adoption — the reviewer refused it, its last round was announced and never answered, or its change is there and
  never verified — and the item records an answer that quotes the question that branch states, naming the branch
  and the head commit it carries
- **THEN** where the run sets the branch aside the item is queued as work instead of awaiting a decision: no
  decision label is written, no exclusion is recorded and no decision slot is spent; the attempt's commits are kept
  under a leftover name the run gives them and the item is worked again on its own branch, cut fresh from the run's
  base, so an answer a person gave is executed rather than re-asked — and where git refuses that rename the verdict
  stays the decision with the reason saying the recorded answer was not executed, so a set-aside that did not
  happen does not read as one that did
- **Case**: `prescreen#anAnsweredBranchIsWorkNotADecision`

#### Scenario: An answer to another attempt's question leaves this attempt's question standing

- **WHEN** the pre-screen meets a branch whose question is still open, and the item's recorded answer quotes a
  question that names that branch and a head commit it no longer carries
- **THEN** the branch is not touched and the verdict stays the decision the run escalates, because the answer speaks
  about an attempt that was set aside and the question about the attempt standing now has not been read by anybody
- **Case**: `prescreen#anAnswerToAnotherAttemptDoesNotReworkTheBranch`

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
