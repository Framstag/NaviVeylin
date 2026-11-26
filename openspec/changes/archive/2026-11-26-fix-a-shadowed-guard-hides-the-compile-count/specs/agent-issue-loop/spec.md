## ADDED Requirements

### Requirement: A count record mutates a line the asserting case reaches

Where a record asserts a count — that a step ran once for a suite — the guard it quotes SHALL be the only place the
driver reads the condition that decides the step, so the case that runs after the first passes through the line the
record mutates. The suite driver SHALL read the compile's marker in exactly one place, and the harness's own self-test
SHALL assert that count over the driver, so a second read that shadows the guard fails before any mutation is paid.

#### Scenario: A shadowed guard fails the harness's own self-test

- **WHEN** the suite driver reads the compile's marker in more than one place, so an earlier read returns before the
  guard a count record quotes, and the harness's own self-test is run
- **THEN** it fails, prints the number of reads it found in the driver, and exits 1 without running a mutation
- **Case**: `tools/loop/tests/loop-mutations-selftest.sh` (`the compile's marker is read in one place, so the line record 48.2 mutates is one a case reaches`)
