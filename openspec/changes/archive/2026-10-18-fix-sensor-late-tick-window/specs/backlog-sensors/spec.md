# backlog-sensors — deltas

## MODIFIED Requirements

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
