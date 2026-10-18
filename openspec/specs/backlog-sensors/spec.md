# backlog-sensors Specification

## Purpose

Checks that a project declares run on their own schedule, report a condition as a verdict, and keep the
tracker honest: a broken build, a size that grew past its bound or a coverage that fell below its floor
becomes exactly one issue, which is closed when the condition clears and reopened if it returns.

## Requirements

### Requirement: A check is a declared row, not a script

Every check SHALL be declared as a row naming the command to run, how often it runs, the bound the
measurement is compared against, how many consecutive runs are needed before its verdict counts, the kind
and area its issues carry, and whether it owns the issue's closing.

#### Scenario: A row is the whole definition

- **WHEN** a check is added
- **THEN** nothing but its row changes, and the run's selection, comparison and reporting work from the row

#### Scenario: A row without a bound is refused

- **WHEN** a declared check carries no bound, or no consecutive count
- **THEN** it is refused with a reason naming the missing field, so a check cannot be a bare reporter

#### Scenario: Every declared check names its own label

- **WHEN** the declared checks are inspected
- **THEN** each carries the label that identifies it, so the issues one check produced are one query

### Requirement: A check reports one of three outcomes, and only clearing closes

A check's run SHALL end in exactly one of three outcomes — the condition is present, the condition has
cleared, or the check failed to determine anything — and SHALL NOT close an issue unless the condition has
demonstrably cleared.

#### Scenario: The condition is present

- **WHEN** a check measures past its bound for the required number of consecutive runs
- **THEN** its issue is created, or the existing one is reopened or annotated, with the measurement

#### Scenario: The condition has cleared

- **WHEN** a check measures inside its bound for the required number of consecutive runs
- **THEN** the measurement is recorded on the issue and the issue is closed, naming the clearing
  measurement as its evidence

#### Scenario: A row whose condition is simply absent says so

- **WHEN** a row's command finds no condition to report — the build is green, no change of the owner's is in
  conflict
- **THEN** it reports a clear naming its own identity, so "the condition is absent" reaches the tracker's
  history as a verdict rather than as the row's silence

#### Scenario: The check itself failed

- **WHEN** a check crashes, cannot reach its input, or produces an unreadable result
- **THEN** its run reports failure, nothing is written to the tracker, and no issue is closed — a broken
  check must not read as a cleared condition

#### Scenario: Failing is not clearing

- **WHEN** two runs end, one with a failed check and one with a cleared condition
- **THEN** their effects on the tracker differ, and the report distinguishes them

### Requirement: Identity is finer than the check that produced it

A check's label SHALL scope which issues it owns, and a distinct condition under the same check SHALL be a
distinct issue, so that a second cause is not reported against the first cause's record.

#### Scenario: The same condition returns

- **WHEN** a condition whose fingerprint matches a closed issue reappears
- **THEN** that issue is reopened rather than a second one created, and its recurrence count increases

#### Scenario: A different cause under the same check

- **WHEN** the same check detects a condition whose fingerprint matches nothing
- **THEN** a new issue is created, because it is a different cause with the same owner

#### Scenario: The condition is still present

- **WHEN** a check detects a condition whose issue is already open
- **THEN** the new measurement is recorded on that issue and no second issue is created

### Requirement: A check never reverses a person's decision

A check SHALL reopen only an issue that was closed as a completed fix, and SHALL leave an issue a person
closed as a decision not to act alone, reporting the condition instead.

#### Scenario: A completed fix recurs

- **WHEN** a condition returns and its issue was closed as a completed fix
- **THEN** the issue is reopened, because the fix did not hold, and the recurrence is recorded

#### Scenario: A decision not to act recurs

- **WHEN** a condition returns and its issue was closed as a decision not to act
- **THEN** the issue is not reopened and the measurement is recorded as a comment, so the tooling does not
  overrule the owner

#### Scenario: The recurrence is visible as a count

- **WHEN** an issue has been reopened one or more times
- **THEN** its record carries how many times, because a fix that did not hold is a different problem from
  one that has not been attempted

### Requirement: A repeating condition escalates instead of reopening forever

A check SHALL stop reopening past a declared number of recurrences and SHALL mark the issue as awaiting a
person's decision, because a condition that keeps returning is a specification or design problem.

#### Scenario: The recurrence bound is reached

- **WHEN** a condition recurs past the declared bound
- **THEN** the check stops reopening and marks the issue as awaiting a decision, recording why

#### Scenario: Escalating does not authorise work

- **WHEN** an issue is escalated
- **THEN** it does not become eligible for automated work, so a repeating condition reaches a person
  rather than a machine

#### Scenario: A flapping condition does not churn

- **WHEN** a condition alternates between present and cleared faster than the consecutive count requires
- **THEN** neither a close nor a reopen happens until the count is met in one direction

### Requirement: A check acts only on what it owns

A check SHALL be able to close, reopen or annotate only the issues carrying its own label, and an attempt
to act on any other issue SHALL be refused with a reason and change nothing.

#### Scenario: Another check's issue is refused

- **WHEN** a check attempts to close an issue that carries a different check's label
- **THEN** nothing is written and the refusal names the label it expected

#### Scenario: A person's issue is out of reach

- **WHEN** a check's selection runs
- **THEN** issues filed by a person and issues imported from the previous backlog are outside its reach

#### Scenario: No check can delete

- **WHEN** the checks' capabilities are inspected
- **THEN** none of them can delete an issue, so a mistaken or subverted check can close but cannot destroy

### Requirement: The checks run in a pipeline of their own, pinned and least-privileged

The checks SHALL run in a pipeline separate from the one that validates changes, triggered only by a
schedule and an explicit dispatch, restricted to the project's main branch by a check at run time, given
write access to issues only, and guarded so that two runs cannot act at once.

#### Scenario: Change validation is unaffected

- **WHEN** a change is proposed or validated
- **THEN** no check runs, and the validating pipeline is unchanged, so a check cannot block or influence a
  change

#### Scenario: A dispatch from another branch cannot act

- **WHEN** the pipeline is dispatched against a branch other than the project's main branch
- **THEN** the run refuses before writing anything, because a dispatch can choose its ref

#### Scenario: The schedule fires without a person

- **WHEN** the schedule's tick arrives and no dispatch was made
- **THEN** the rows whose declared cadence is due to run are selected and measured, so a check keeps
  running while nobody is looking

#### Scenario: The identity is a concrete person

- **WHEN** a check files, annotates or closes an issue
- **THEN** the issue's author is an allowlisted person rather than an anonymous pipeline identity, so the
  authorisation rule that governs every other writer governs this one too

#### Scenario: Two runs cannot race

- **WHEN** a second run starts while one is in progress
- **THEN** the second does not proceed, so two runs cannot file or close the same issue concurrently

#### Scenario: The pipeline's own changes are reviewed

- **WHEN** the pipeline definition or a check's configuration changes
- **THEN** the change requires the owner's approval, so a pipeline change is not an unreviewed act

### Requirement: The issues carry the measurement history

A check's measurements SHALL be recorded on the issue as comments, and no measurement SHALL be stored in
the repository, so the issue's own timeline is the baseline a later measurement is compared against.

#### Scenario: The baseline is the previous measurement

- **WHEN** a check compares a measurement against its bound
- **THEN** the earlier number it quotes is the one from the issue's own record, not a separate store

#### Scenario: Nothing is committed by a check

- **WHEN** a check's run ends
- **THEN** it has written no file into the repository, because a check is not a second committer

#### Scenario: Growth is distinguishable from a level

- **WHEN** a measurement has a history on the issue
- **THEN** the report says whether the number is worse than the previous one, so a trend is visible without
  a stored baseline

### Requirement: A check's issues arrive classified and unauthorised

An issue filed by a check SHALL carry the kind and area its row declares, and SHALL NOT carry the
automation opt-in.

#### Scenario: The row's classification is used

- **WHEN** a check files an issue
- **THEN** the issue carries the kind and area declared in its row, so no further interpretation is needed
  before it can be ranked or selected

#### Scenario: Filing is not authorising

- **WHEN** a check files an issue
- **THEN** the issue is not eligible for automated work until a person applies the opt-in, however clearly
  defect-shaped it is

### Requirement: A row's declared input is published, checked against its producer, and read for the commit under test

Every `input` a row declares SHALL be published at that path by the pipeline that measures it, the
publisher SHALL refuse a registry whose row declares an input it does not publish, and the checks' run
SHALL obtain the record published for the commit it is examining — selecting no row, and reporting no
failed check, while that record does not exist yet.

#### Scenario: The producer's own manifest is the bound

- **WHEN** the publisher is asked to check the registry
- **THEN** it compares every declared input against the set of paths it publishes and passes only when each
  one is in that set

#### Scenario: An input no pipeline publishes is refused

- **WHEN** a row declares an input the publisher does not write
- **THEN** the check fails and names that row and that path, so the declaration is repaired before a
  scheduled run reports a failed check an hour later

#### Scenario: A run reads the record of the commit it examines

- **WHEN** the checks' run starts on the main branch
- **THEN** every row that declares an input finds the file published for that commit, so its verdict is the
  measurement rather than a failed check

#### Scenario: No record yet is not a failed check

- **WHEN** the record for the commit under test does not exist yet, because the build that produces it has
  not finished
- **THEN** no row is selected, nothing is written to the tracker, and the run does not report a failed
  check

#### Scenario: The record is one artifact, not a download per row

- **WHEN** the record is transferred to the checks' run
- **THEN** the rows' declared paths are restored as they are declared, so no row's command learns where its
  input came from
