# agent-issue-loop delta

## MODIFIED Requirements

### Requirement: A review round is a record on the branch, from its announcement to its answer

A review round SHALL be readable from the item's branch in both directions: the round SHALL be recorded on the
branch before its reviewer starts, and its answer SHALL be recorded when the answer arrives — the pass beside the
announcement and the refusal beside it too, for every round and not only the round the run leaves on — so that a
round the reviewer never answered is a round a later run can see is unanswered rather than one that reads like a
pass, and a round that refused and was then repaired is a round that says it refused. The write the run makes when
it ends on a refusal SHALL be the same record, written once: a round already recorded SHALL add no second commit.
A refusal the run's own bound then cut the rework of SHALL be recorded as that: the announcement stays, and beside
it `review-round-<n>-rework-cut.md` says the round was asked and answered and what did not finish is the answer to
the refusal — neither `cut` (the run ended the round, not its rework) nor silence (which reads as a question)
describes it.

#### Scenario: A round that refuses and is repaired leaves its refusal beside its announcement

- **WHEN** a run's round 1 refuses the change and its round 2 accepts the rework, so the refusal is not the round
  the run ends on
- **THEN** the change's evidence carries the announcement of round 1 and `review-failed-round-1.md` beside it, the
  pass of round 2 is still recorded as it was, and no refusal is written for the round that passed
- **Case**: `run#aRefusedRoundLeavesAnAnswerBesideItsAnnouncement`

#### Scenario: A round's answer is written once

- **WHEN** a run ends on a round that refused, so both the arm that read the answer and the arm that ends the run
  record that round
- **THEN** the branch carries one commit for that round's refusal, the run prints no failure to record it, and the
  second write reports the record as already there instead of adding a second one
- **Case**: `run#recordsAFailedReviewOnTheBranch`

#### Scenario: The rework of a refusal the bound ended is recorded beside the round

- **WHEN** the reviewer refuses the change in round `<n>` and the run starts the rework worker of that round
- **AND** the run ends that worker at a bound it enforces
- **THEN** the branch carries the round's announcement beside `evidence/review-round-<n>-rework-cut.md` and the
  `review-failed-round-<n>.md` the round answered with, and the round reads as neither a pass nor a silence
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

### Requirement: The run's end sweeps the branches it made

When a run ends it SHALL remove every branch the loop names whose patches the base already holds, unless a
worktree holds it or the **last** review round the change it carries answered refused it — a refusal a later round
did not repeat is not the branch's verdict. It SHALL sweep those names on the remote as well as locally, against
the fetched `origin/<base branch>`, and SHALL keep and name a remote ref that is the head of an open pull request —
every remote ref when the open pull requests cannot be read. It SHALL print what it removed and what it kept.

#### Scenario: A refusal a later round repaired does not keep a landed branch

- **WHEN** the base carries a branch's work, and the branch's archived change carries a round-1 refusal beside the
  pass of round 2
- **THEN** the sweep removes the branch and prints it as removed, and does not keep it or name it a branch the
  reviewer refused, because the answer that counts is the round's last one
- **Case**: `worktree#aRepairedRefusalDoesNotKeepALandedBranch`

#### Scenario: A landed branch no longer outlives the run that made it

- **WHEN** a run ends and one of its own `loop-*` branches, or an earlier `fix/issue-*` branch, carries no patch the
  base does not have
- **THEN** the run removes that branch, keeps and names a branch that still carries work, a branch a worktree
  holds, and a branch whose archived change's last round carries a reviewer's refusal, and prints the number it
  removed
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
