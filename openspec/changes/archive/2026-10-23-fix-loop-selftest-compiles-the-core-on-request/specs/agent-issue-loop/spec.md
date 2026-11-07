# agent-issue-loop — deltas

## ADDED Requirements

### Requirement: The self-test compiles the Java core when a case asks for it

The loop's self-test SHALL compile the issue tooling's Java core at the first selected case that asks for it, at
most once per suite, and SHALL NOT compile it before the case list is known: an invocation whose selected cases
ask for none SHALL pay no compile. A case that asks for the core and cannot have it SHALL fail with exit 1 and the
line `loop-selftest: the issue tooling's Java core does not compile`, and the run SHALL end with exit 1.

#### Scenario: A selection that asks for no core compiles none

- **WHEN** the driver runs one case that never asks for the core, and `javac` on `PATH` is a probe that records its
  invocation and fails
- **THEN** the case runs and passes, and the probe records no invocation
- **Case**: `driver#aCaseThatAsksForNoCoreCompilesNothing`

#### Scenario: A case that asks for the core is handed it, or the suite refuses

- **WHEN** a selected case asks for the core
- **THEN** it is handed the class directory the driver compiled for the suite; and when the core cannot compile,
  the case fails with exit 1 and the line that names the cause, and the run ends with exit 1
- **Case**: `driver#aCaseThatAsksForTheCoreGetsItOrRefuses`

#### Scenario: A suite whose selection asks twice compiles once

- **WHEN** the driver runs a suite of two cases that both ask for the core, and `javac` on `PATH` is a probe that
  records each invocation and succeeds
- **THEN** both cases are handed the compiled class directory and the probe records exactly one invocation
- **Case**: `driver#theCoreIsCompiledOncePerSuite`
