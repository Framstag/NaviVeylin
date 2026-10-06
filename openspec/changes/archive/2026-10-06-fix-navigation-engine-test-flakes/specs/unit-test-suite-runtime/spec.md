# Spec Delta

## ADDED Requirements

### Requirement: A unit test controls the time its subject waits on

A unit test SHALL decide its outcome from the behaviour under test and from time it controls, never from
wall-clock elapsed time or a real-thread wait. A case whose subject decides from a time window SHALL
advance an injected time source or the test scheduler instead of sleeping; a case that awaits a state
change SHALL await the observable state on that scheduler instead of a deadline computed from the system
clock.

#### Scenario: Injected time instead of a real clock

- **WHEN** a case exercises behaviour that depends on a staleness or throttle window
- **THEN** the case advances the injected time source past that window and the behaviour is observed
  without the case waiting for real time to elapse

#### Scenario: Awaiting state, not a deadline

- **WHEN** a case awaits a state change published by a component that works off the test thread
- **THEN** the case drives the work on the test scheduler and awaits the observable condition
- **AND** no test helper computes a deadline from the system clock or paces itself with a sleep

### Requirement: A module's failure set is independent of host load and concurrency

The module's failing classes SHALL be the same whether its suite runs on an idle host or a loaded one, at
the declared concurrency or in a single JVM.

#### Scenario: Load does not change the failure set

- **WHEN** the module's suite runs at the declared concurrency while the host is otherwise loaded
- **THEN** the failing classes are exactly those of the same suite run on an idle host in a single JVM

#### Scenario: A formerly flaky case is decided by its subject

- **WHEN** the previously load-dependent cases run inside the full module suite
- **THEN** each is green in a forced repeated run whose tallies are recorded, and none of them passes
  or fails on the strength of elapsed real time
