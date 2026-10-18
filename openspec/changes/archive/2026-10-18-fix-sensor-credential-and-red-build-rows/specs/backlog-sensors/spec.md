# backlog-sensors — deltas

## MODIFIED Requirements

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

#### Scenario: A red build is not a failed check

- **WHEN** the record published for the commit under test comes from a build that did not complete
  successfully
- **THEN** the rows that read the build's own outputs SHALL NOT be attempted for it, so no such row reports
  the third outcome and the run fails for a reason that belongs to a check rather than to the build
- **Case**: `workflow#aRedRecordRunsNoRecordRow`

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
