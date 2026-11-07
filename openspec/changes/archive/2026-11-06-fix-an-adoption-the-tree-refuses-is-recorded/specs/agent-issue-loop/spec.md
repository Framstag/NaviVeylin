# Spec Delta — agent-issue-loop

## MODIFIED Requirements

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

### Requirement: A block records the mechanism that stopped it

An item recorded `blocked` SHALL carry the mechanism that stopped it, from a closed vocabulary — `refused` when a
review round refused the change, `unmergeable` when the base would not take the branch, `bound` when the run ended
a child at a bound it enforces — and a block none caused SHALL carry none. The vocabulary SHALL carry one second
home: `unmergeable` SHALL be written beside the pre-screen's exclusion of an item whose admissible finished branch
the project tree, the base or the replay refused to merge, with the refusal's own text, and no other mechanism
SHALL be written on a result that is not a block. A value outside the vocabulary, and one on any other result than
a block or that exclusion, SHALL be refused before anything is written, so a mechanism's spend is totalled without
the note's prose.

#### Scenario: The refusing round is named

- **WHEN** the last review round at the cap refuses the change and the item is recorded blocked
- **THEN** the item's record carries `refused` as its stop reason, beside the note that names the round
- **Case**: `run#reviewRoundsCapAtTwo`

#### Scenario: The base's refusal is named

- **WHEN** the run cannot merge the item's branch onto the base and the item is recorded blocked
- **THEN** the item's record carries `unmergeable`, beside the note that names the branch
- **Case**: `run#doesNotCloseOnAMergeTheBaseCannotTake`

#### Scenario: A block the run's own bound caused is named as one

- **WHEN** a child of the item reaches its role's turn bound, or outlives the child clock, and the run ends it
- **THEN** the item is recorded `blocked` with `bound` as its stop reason, its note says the attempt is left for the
  next run rather than asked about, and no decision label is written for it — the item is work the next run resumes,
  so it spends no decision slot and stays eligible
- **Case**: `run#childPastItsTurnBoundIsBlocked`

#### Scenario: A mechanism outside the vocabulary is refused

- **WHEN** a stop reason is given that is neither `refused` nor `unmergeable` nor `bound`, or a stop reason is given
  for a result that is not a block
- **THEN** the record is refused as a usage error and the state is not written, so the field cannot turn into a
  second prose channel
- **Case**: `state#aBlockedItemNamesTheMechanismThatStoppedIt`

#### Scenario: The report tables the mechanism beside the spend

- **WHEN** the run's report prints its per-item table
- **THEN** each row carries the mechanism its block stopped on, or `-` when it carries none, in a column of its
  own, so a mechanism's spend is totalled from the table
- **Case**: `report#wallTimeAndReasonInTheTable`

#### Scenario: The tree's refusal of an adoption is named on the exclusion

- **WHEN** the run records an adoption's refusal on an item the pre-screen excluded, and is asked to record one
  for an issue that is not one of the run's exclusions
- **THEN** the exclusion carries `unmergeable` beside the refusal's own text while its verdict, its reason and the
  branch stay as the pre-screen read them, and the second request exits with the refusal naming that the issue is
  not one exclusion of this run and leaves the state unwritten — so the mechanism's second home is the exclusion
  and not a free field
- **Case**: `state#anAdoptionRefusalIsRecordedBesideTheExclusion`

## ADDED Requirements

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
