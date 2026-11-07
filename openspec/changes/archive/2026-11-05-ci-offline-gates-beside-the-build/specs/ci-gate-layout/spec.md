# Spec Delta — ci-gate-layout

## Purpose

Holds what the verification workflow's jobs are, apart from what each job verifies: which checks a runner is asked
to pay for, and whether a check that needs no build input is charged to the build's own clock.

## ADDED Requirements

### Requirement: A check that needs no build input runs beside the build

A check that needs no build input — no Android SDK, no NDK, no vcpkg, no Gradle, no token — SHALL run in a job of
its own, started beside the job that builds and tests the app, so its duration is not added to that build's
critical path. A check whose failure is cheap to detect SHALL stay in front of the build, so a broken artifact is
answered in seconds. The run's verdict SHALL be the conjunction of both jobs, and each job's name SHALL be recorded
where the required checks are read.

#### Scenario: The offline checks are not in the build's job

- **WHEN** the verification workflow is read, and a dispatch run's step clock is read beside it
- **THEN** no step that needs only a checkout, git, jq and a JDK is ordered in front of the step that installs the
  build's dependencies, and the first step needing the Android SDK starts no later than it does today
- **Case**: none — the shape is a property of the workflow file and the cost is the run's own step clock, read per
  run (`design.md`, "Why no case"); the checks it moves are each gated by their own suite

#### Scenario: A cheap check still answers first

- **WHEN** a change breaks the tree the build compiles, or breaks a step that costs seconds to run
- **THEN** that step still runs in the build's own job, in front of its dependency install, so the run fails before
  a 57-minute install is paid
- **Case**: none — as above: the property is a step's job in the workflow file, and the cost it protects is read
  from the run's step clock

#### Scenario: A red job of either kind fails the run

- **WHEN** the gate job is red and the build job is green, and then the other way round
- **THEN** the run is red both times, and the job that is red is named
- **Case**: none — a workflow's verdict is the host's, not a suite's; it is read from the dispatch run named in
  `proposal.md`
