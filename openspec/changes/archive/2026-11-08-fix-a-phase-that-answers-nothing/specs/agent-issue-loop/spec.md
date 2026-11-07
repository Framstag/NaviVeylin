## MODIFIED Requirements

### Requirement: The run verifies rather than trusts

The run SHALL determine whether an item closed from artifacts it reads itself — the working tree, the
change's archive and the gate's evidence — and SHALL NOT accept an agent's claim of success. The tree's
own state SHALL be read as soon as the run can read it, and before any child the reading would make
pointless is started. Each phase's own report SHALL be read as that phase's answer: a phase whose child
exits 0 and leaves no answer is a failed phase, and the reason recorded for the item SHALL name that phase
rather than the state of the item's tree.

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

#### Scenario: The gate's evidence is read by the run

- **WHEN** an item claims a gate result
- **THEN** the run quotes the evidence it found and does not repeat the claim
- **Case**: `run#claimedGateWithoutEvidenceIsBlocked`

#### Scenario: Evidence outlives the run

- **WHEN** an item cites a test result
- **THEN** the result is carried into the change's own directory, because the build directory is
  overwritten by the next run
- **Case**: `run#citedEvidenceMustTravelWithTheChange`
