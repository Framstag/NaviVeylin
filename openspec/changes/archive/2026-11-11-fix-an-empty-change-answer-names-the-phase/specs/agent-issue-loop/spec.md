# agent-issue-loop delta

## MODIFIED Requirements

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
