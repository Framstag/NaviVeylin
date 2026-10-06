# Spec Delta — build-test-gate

## ADDED Requirements

### Requirement: Iteration pre-gate runs the change's declared cases first

A change in flight SHALL have a documented iteration pass that runs, before the module suite, the test
classes the change declares: those its change artifacts name, those its own diff touches, and those
whose source mentions a type its diff changes. The selection SHALL be derived from the change and its
diff, with no hand-maintained list of classes, and the pass SHALL report what it selected and its
measured cost before it executes anything.

#### Scenario: Declared cases run before the module suite

- **WHEN** a change in flight is iterated
- **THEN** its declared test classes run before the module suite, and the selection came from the
  change's artifacts and its own diff

#### Scenario: Selection needs no maintained list

- **WHEN** a test class exists that no in-flight change names and no diff touches
- **THEN** a pre-gate invocation for that change does not execute it, and no maintained selection file
  had to be edited to achieve that

#### Scenario: The selection is visible before it runs

- **WHEN** the pre-gate is invoked for a change
- **THEN** it prints the selected test classes and their last measured class time before executing them

#### Scenario: A change with nothing selectable says so

- **WHEN** a change's diff touches no test source and mentions no test class in its artifacts
- **THEN** the pre-gate selects nothing, reports why, and does not fail the iteration with an empty
  filter

#### Scenario: A pre-gate run is not gate evidence

- **WHEN** the pre-gate run is green
- **THEN** the module suite is still owed before the change is called complete, and no gate record
  quotes the pre-gate as evidence that the change's tests ran
