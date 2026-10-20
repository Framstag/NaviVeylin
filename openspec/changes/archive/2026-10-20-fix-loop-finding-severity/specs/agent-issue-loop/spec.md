# agent-issue-loop — deltas

## ADDED Requirements

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
- **Case**: `run#reviewRoundsCapAtTwo`

#### Scenario: An unchanged pass still passes

- **WHEN** a review round reports `VERDICT: PASS` with no count line or a count of zero
- **THEN** the item closes exactly as before, with no notes recorded and no second round taken
- **Case**: `run#metricsRecordedPerItem`
