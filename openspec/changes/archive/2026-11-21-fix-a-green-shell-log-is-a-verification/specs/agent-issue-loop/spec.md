## ADDED Requirements

### Requirement: A green verification record has two shapes, and the shell suite's log is one of them

The run SHALL read a finished attempt's verification record from the archived change's `evidence/` directory in
either of the two shapes this project's gate leaves: a JUnit testsuite that ran at least one test, failed none and
raised no error, and a shell suite's console log carrying a `PASS <group>#<case>` verdict line and no `FAIL` line —
the verdict line the suite prints and the gate derives its tally from. A record of neither shape SHALL NOT make a
branch's change verified, and the shell log SHALL be read only by that predicate, so a log that names a failing case
is the change's red proof and leaves the branch unverified.

#### Scenario: A branch whose green proof is the shell suite's log is adoptable

- **WHEN** the run learns of a previous attempt's branch that carries a commit the base does not have and adds one
  archived change of its own, and that change's `evidence/` holds a log with a `PASS <group>#<case>` line and no
  `FAIL` line, and no JUnit testsuite at all
- **THEN** the change's verification record is read as green: `branch-state` answers `adoptable` and names the log it
  read, the branch is merged onto the base with the log named as the evidence it was checked for, and a log that
  names a failing case instead is not read as green, so a red proof never adopts the branch it reports on
- **Case**: `worktree#aShellSuiteLogIsAVerificationRecord`
