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
demonstrably cleared. A clearing finding SHALL carry the identity the item's own body record declares, and
SHALL name the check's own identity only when no item records a condition.

#### Scenario: The condition is present

- **WHEN** a check measures past its bound for the required number of consecutive runs
- **THEN** its issue is created, or the existing one is reopened or annotated, with the measurement

#### Scenario: The condition has cleared

- **WHEN** a check measures inside its bound for the required number of consecutive runs
- **THEN** the measurement is recorded on the issue and the issue is closed, naming the clearing
  measurement as its evidence, and the finding's identity is the one the issue's own condition record
  declares rather than the identity of the measurement that found it green
- **Case**: `sensors#aRecordedConditionParsesToItsObject`

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

#### Scenario: A red build is not a failed check

- **WHEN** the record published for the commit under test comes from a build that did not complete
  successfully
- **THEN** the rows that read the build's own outputs SHALL NOT be attempted for it, so no such row reports
  the third outcome and the run fails for a reason that belongs to a check rather than to the build
- **Case**: `workflow#aRedRecordRunsNoRecordRow`

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
schedule and an explicit dispatch, restricted to the project's main branch by a check at run time, given the
credential its declared rows need and no more — write access to issues, and read access to pull requests for
the row that lists them — and guarded so that two runs cannot act at once.

#### Scenario: Change validation is unaffected

- **WHEN** a change is proposed or validated
- **THEN** no check runs, and the validating pipeline is unchanged, so a check cannot block or influence a
  change

#### Scenario: A dispatch from another branch cannot act

- **WHEN** the pipeline is dispatched against a branch other than the project's main branch
- **THEN** the run refuses before writing anything, because a dispatch can choose its ref

#### Scenario: The schedule fires without a person

- **WHEN** the schedule's tick arrives — in its own minute when it is punctual, and in a later one when
  GitHub fires it late — and no dispatch was made
- **THEN** the rows whose declared cadence falls in the window the tick selects over are selected and
  measured, so a late tick still measures the rows it was scheduled for and a check keeps running while
  nobody is looking
- **Case**: `run#aDelayedTickSelectsTheRowsItWasScheduledFor`

#### Scenario: The window measures a row once

- **WHEN** a tick's window reaches back over the minutes since the previous tick
- **THEN** a row whose minute that previous tick selected is outside this tick's window, so no row is
  measured twice for one of its cadence's minutes and its `consecutive` count is not spent on a repeat
- **Case**: `run#theWindowDoesNotSelectThePreviousTicksRow`

#### Scenario: The pipeline declares the window its ticks select over

- **WHEN** the pipeline's scheduled invocations of the runner are inspected
- **THEN** each one passes a window as wide as the tick's own cadence, so the bound is declared rather than
  assumed and two consecutive ticks' windows tile the hour
- **Case**: `workflow#theTickDeclaresItsWindow`

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

#### Scenario: The credential's scope covers every declared row

- **WHEN** the scope the pipeline's credential is created with is inspected against the rows the registry
  declares
- **THEN** it names the permission every declared row's command needs — the issues scope for filing,
  annotating and closing, and the pull-request read scope for the row that lists pull requests — so a secret
  created as the documentation says can run every declared row
- **Case**: `sensors#theDocumentedCredentialCoversEveryDeclaredRow`

#### Scenario: A red build still runs the condition rows

- **WHEN** the pipeline runs against a record whose build did not complete successfully
- **THEN** the condition and repository rows still run, so the build's redness reaches the tracker as exactly
  one row's verdict while no row reports a failure a person would have to read as "the build was red, the
  check was fine", and the rows that read the build's own outputs are named by the other step, whose two
  lists are together exactly the registry's rows
- **Case**: `workflow#aRedRecordRunsNoRecordRow`

#### Scenario: A step selects the rows it names

- **WHEN** the pipeline runs a step that names the rows it selects
- **THEN** only those rows are candidates, and each one still runs only when its own cadence is due, so a
  later tick reaches it rather than the step running it early
- **Case**: `run#rowsSelectsSeveralRows`

#### Scenario: A row id the registry does not declare is refused

- **WHEN** a step names a row id the registry does not declare
- **THEN** the run refuses before any row runs — naming that id and the ids the registry does declare — so a
  step cannot ask for a check that does not exist and read the silence as a clear
- **Case**: `run#rowsRefusesAnUndeclaredId`

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

#### Scenario: The fetcher reports the run's conclusion

- **WHEN** the checks' run obtains the record for the commit it is examining
- **THEN** the conclusion that commit's build run ended with is reported beside the record, so the pipeline
  can tell a record a red build published from one a successful build did while the fetcher's exit contract
  — fetched, not ready, cannot tell — is unchanged
- **Case**: `inputs#reportsTheRunsConclusion`

### Requirement: A mechanical sweep closes only what its own rule states

A rule that is a function of the tracker's own fields MAY be applied by the scheduled worker instead of by a
person, and SHALL then close only what its rule names, report every group it cannot decide, and print the
candidates it refuses to act on.

#### Scenario: A duplicate group closes the younger item, and then points the survivor at it

- **WHEN** the sweep finds several waiting items carrying one fingerprint marker, and the younger one carries
  the label that authorises automated work
- **THEN** the younger item is closed through `close.sh` with a reason naming the surviving item and the
  surviving item's number as its evidence, **and then** the survivor receives a pointer comment naming the
  closed item, and nothing else is written
- **Case**: `duplicates#youngerDuplicateIsClosedWithAPointer`

#### Scenario: The older item survives

- **WHEN** the sweep picks the survivor of a duplicate group
- **THEN** it is the item with the lowest issue number, because that number is immutable while labels and
  body length are not, and the closure names it
- **Case**: `duplicates#olderItemSurvives`

#### Scenario: A duplicate that may not be closed is made visible once

- **WHEN** the younger item of a group does not carry the authorisation label, so `close.sh` would refuse
- **THEN** the sweep posts the same pointer comment on it and leaves it open, and the run's output names it
  — the authorisation is never widened to make the rule fire; and a duplicate that already carries the
  pointer's marker is not commented on again
- **Case**: `duplicates#unauthorisedDuplicateIsCommentedNotClosed`, `duplicates#aMarkedDuplicateIsNotCommentedTwice`

#### Scenario: An identifier collision is reported, never repaired

- **WHEN** two open items carry the same `nvid` marker
- **THEN** both issue numbers appear in the sweep's output and in the workflow's summary, and no write
  happens — which item legitimately carries the identifier is recorded in the migration table, not derivable
  from the tracker
- **Case**: `duplicates#collisionIsReportedAndNothingIsWritten`

#### Scenario: A candidate is printed and not acted on

- **WHEN** an item's number appears in a commit that is an ancestor of `main`
- **THEN** the sweep prints it as a candidate and closes nothing, because a cherry-picked commit names the
  same item on a branch, and a message may mention an item without delivering it
- **Case**: `duplicates#landedCandidateIsPrintedNotClosed`

#### Scenario: A clean tracker is a pass, not a failure

- **WHEN** no duplicate, no collision and no candidate exist
- **THEN** the sweep prints `nothing to do` and exits 0, and a dry run writes nothing in every case
- **Case**: `duplicates#cleanTrackerExitsZeroWithoutAWrite`
