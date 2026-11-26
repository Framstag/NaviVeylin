## ADDED Requirements

### Requirement: A suite's log is the change's verification and one case's log is not

The run SHALL read a shell suite log as a verification record only when it states more than one
`PASS <group>#<case>` verdict and no `FAIL` line, so one case's own log — the record a focused run leaves beside the
gate that refused the change — SHALL NOT make a branch `adoptable`. The record the adoption names as the evidence it
checked SHALL be the set of records it accepted, never the archived change's `evidence/` file list (issue #1036).

#### Scenario: One case's own log is not the change's verification

- **WHEN** a branch carries one archived change of its own, an evidence directory holding one case's own `PASS`
  log and the change's own red proofs (`FAIL` lines), and nothing else that states a passing run
- **THEN** `branch-state` answers `archived-not-green` with no record named as the verification, `adopt-item`
  exits `5` without merging the change onto the base, and the item is not closed on the one case its run happened
  to pass
- **Case**: `worktree#aSingleCasesLogIsNotTheChangesGate`

#### Scenario: The state names the records it accepted and not the whole directory

- **WHEN** an archived change's evidence directory holds a suite's `PASS` log and, beside it, the change's own
  `FAIL` red proof
- **THEN** `branch-state` answers `adoptable` and its evidence column names the suite log alone, so the closure's
  `evidence checked:` carries the record the branch was verified on and not the red proofs the directory also
  holds
- **Case**: `worktree#aShellSuiteLogIsAVerificationRecord`
