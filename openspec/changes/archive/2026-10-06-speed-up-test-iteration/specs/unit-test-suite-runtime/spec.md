# Spec Delta — unit-test-suite-runtime

## ADDED Requirements

### Requirement: Wall-clock waits in test sources are refused by a build check

A unit test or test helper SHALL NOT pace itself with a fixed sleep, SHALL NOT compute an await
deadline from the system clock, and SHALL NOT block a thread on a condition an injected time source or
the test scheduler can drive. A build check SHALL refuse those patterns in every test-bearing module,
naming the file and line. The check has no exception list: a wait that cannot be converted SHALL be
replaced by driving the seam, never declared as an allowed wait.

#### Scenario: A new fixed sleep fails the build

- **WHEN** a test source paces a case with a fixed sleep
- **THEN** the check fails the build and names the offending file and line

#### Scenario: A system-clock deadline loop fails the build

- **WHEN** a test helper awaits a condition until a deadline computed from the system clock
- **THEN** the check fails the build and names the offending helper

#### Scenario: Converting a wait keeps the behaviour covered

- **WHEN** a waiting case is converted to advance an injected time source or the test scheduler
- **THEN** the case exercises the same behaviour as before, with its result decided by that controlled
  time rather than by elapsed real time

#### Scenario: The refused patterns stay refused

- **WHEN** the check is run against the test sources of the test-bearing modules
- **THEN** it names every remaining wait it finds and passes only for the sources that contain none
