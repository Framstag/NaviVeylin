# build-test-gate Specification

## Purpose

The routine verification gate: which unit-test suites it runs for a given change, how a run proves that
those tests actually executed, how the run's duration is recorded, and how far its native scope reaches
— so a gate result is comparable between runs and between a workstation and CI.

## Requirements

### Requirement: Gate runs the affected flavors

The routine gate SHALL run the `:app` unit-test suite of exactly those distribution flavors whose
inputs the change can affect, and SHALL run no flavor suite whose inputs it cannot. A change affects a
flavor when it modifies that flavor's sources, manifest, resources, flavor selection, or native inputs;
a change that modifies only sources shared by both flavors affects one flavor.

#### Scenario: Change touches only shared sources

- **WHEN** a change modifies only files that both `:app` flavors share
- **THEN** the gate runs one `:app` unit-test suite and records which flavor it ran

#### Scenario: Change touches a flavor

- **WHEN** a change modifies `app/src/automotive/**`, a flavor's manifest, a flavor's resources, or the
  flavor definitions
- **THEN** the gate runs both `:app` unit-test suites

#### Scenario: Change that cannot affect packaging is not packaged

- **WHEN** a change touches no native source, manifest, resource, or build configuration
- **THEN** the gate runs no APK assembly task

#### Scenario: Completion requires the full gate

- **WHEN** a change under verification is declared complete
- **THEN** both `:app` flavors and every shipped native ABI have been built and tested at least once

### Requirement: A gate run proves that its tests executed

A run offered as evidence of test behaviour SHALL report the number of tasks that executed and the
per-module tallies read from the result XML. A run whose test tasks were found up to date or restored
from the build cache SHALL NOT be offered as evidence. Forcing execution SHALL be limited to the test
tasks: a forced run SHALL NOT re-execute compilation, packaging, or native work whose inputs are
unchanged.

#### Scenario: Evidence quotes tallies, not a verdict

- **WHEN** a gate run is recorded as evidence
- **THEN** the record quotes the executed-task count and, per module, its tests, failures, and errors

#### Scenario: A cached run is not a run

- **WHEN** the test tasks report `UP-TO-DATE` or `FROM-CACHE` and the log shows no test executor
- **THEN** the run proves nothing and the gate is repeated with test execution forced

#### Scenario: Forcing stays on the test tasks

- **WHEN** test execution is forced on a tree whose other inputs are unchanged
- **THEN** the compilation, packaging, and native tasks report up to date rather than executing

#### Scenario: Quoted results are fresh

- **WHEN** a forced run's tallies are quoted for a module
- **THEN** that module's newest result XML timestamp is later than the run's start

#### Scenario: An interrupted run is cleared first

- **WHEN** a suite run was killed before it wrote a verdict
- **THEN** its result outputs are removed before the next run, so the next task does not abort on a
  leftover in-progress result file

### Requirement: Every gate run records its phase timings

Every gate run SHALL leave a machine-readable record of its own duration: per executed task, and per
executed test suite as wall time, class count, and test count. The documented gate procedure SHALL
quote that record's phase breakdown instead of a bare total.

#### Scenario: Record exists after a run

- **WHEN** a gate run completes
- **THEN** a timing record exists under the app module's build directory naming each executed task with
  its duration and each executed suite with its wall time and test count

#### Scenario: A regression is attributable to a phase

- **WHEN** the gate's total duration changes between two runs
- **THEN** the two records attribute the difference to a named phase — configuration, compilation and
  packaging, or one of the suites

#### Scenario: The record needs no device

- **WHEN** the timing record is produced
- **THEN** it requires no connected phone, emulator, or head unit

### Requirement: Iteration runs build no more native artifacts than they test

A run that verifies JVM behaviour SHALL NOT require native artifacts. A run that does build native
artifacts SHALL limit them to the application binary interface that the run tests, unless the change
modifies native sources, the CMake configuration, or the NDK version — such a change SHALL build every
shipped ABI before it is called verified.

#### Scenario: Kotlin or Java only change

- **WHEN** a change modifies only Kotlin or Java sources, test sources, or test resources
- **THEN** the verification run builds no native library

#### Scenario: One ABI for an iteration run

- **WHEN** an iteration run needs a native artifact for one device or emulator
- **THEN** only that device's ABI is built

#### Scenario: Native change covers every shipped ABI

- **WHEN** native sources, the CMake configuration, or the NDK version change
- **THEN** `arm64-v8a`, `armeabi-v7a`, and `x86_64` are all built before the change is called verified

### Requirement: Superseded native configurations are pruned

Superseded native build configurations — one directory per configuration hash under the app module's
native build tree — SHALL be pruned, so that retained native configuration trees stay bounded instead
of accumulating one full native build per configuration hash.

#### Scenario: Superseded configuration is removed

- **WHEN** a new native configuration hash has been built and its artifacts are the current ones
- **THEN** the superseded configuration directories are removed

#### Scenario: Pruning does not break an incremental build

- **WHEN** native sources are edited after a prune
- **THEN** the native build reconfigures and produces current libraries with no manual step

### Requirement: Local and CI gate recipes agree

The documented full gate and the CI unit-test step SHALL run the same task set and the same forcing
rule. The reduced, affected-flavor form SHALL be a local iteration form and SHALL NOT be reported as a
full gate. A difference between what the two exercise SHALL be treated as a defect in the recipe.

#### Scenario: CI runs the same task set as the documented gate

- **WHEN** the CI unit-test step runs
- **THEN** it invokes the same task set as the documented full gate

#### Scenario: CI reports which flavors it exercised

- **WHEN** the CI unit-test step completes
- **THEN** its log names the flavors whose suites executed

#### Scenario: A recipe divergence is a defect

- **WHEN** the local full gate and CI exercise different task sets for the same content
- **THEN** the divergence is fixed in the recipe rather than explained away for the individual change
