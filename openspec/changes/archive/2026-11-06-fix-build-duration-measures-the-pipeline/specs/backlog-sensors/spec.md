# backlog-sensors — delta for `fix-build-duration-measures-the-pipeline`

## ADDED Requirements

### Requirement: A row that measures a pipeline reads the run's own job and step wall times

A row whose subject is the build pipeline SHALL measure the wall time of the pipeline's jobs, from a record the
checks' own fetcher publishes for the commit under test, and SHALL bound that wall time absolutely rather than as
a multiple of the previous measurement — so a step outside the build's own invocation is inside the row's
measurement, and a growth of it files a condition without waiting for a run to be worse than the run before.

#### Scenario: A step outside the build files, while the build is flat

- **WHEN** a job's steps that run the build are unchanged and a step outside that build is an hour longer, so the
  job's own wall time crosses the bound the row declares
- **THEN** the row measures the job's wall time, files one condition whose identity is that job, and its claim
  names the step that grew
- **Case**: `sensors#buildDurationCoversThePipeline`

#### Scenario: The record belongs to the run the fetcher restored

- **WHEN** the checks' fetcher restores the build record of the commit under test and is asked for the job
  timings
- **THEN** it writes every job's and every step's wall time for that run, carrying the run's identifier and the
  commit, and a fetch that did not ask for the record writes none — so the record can never answer with another
  pipeline's jobs
- **Case**: `inputs#theFetcherPublishesTheJobTimings`

## MODIFIED Requirements

### Requirement: The issues carry the measurement history

A check's measurements SHALL be recorded on the issue as comments, and no measurement SHALL be stored in the
repository, so the issue's own timeline is the baseline a later measurement is compared against where the row's
comparison is a multiple of the previous one; a row that bounds absolutely SHALL read its bound from its own
declaration and quote no baseline. A filing SHALL carry its marker in the issue's body too, and the newest marker
an issue carries — a comment's, or a filing's dated by the issue's own creation — SHALL be the measurement the
report prints and the one a later measurement is compared against.

#### Scenario: The baseline is the previous measurement

- **WHEN** a row that compares as a multiple of its previous measurement compares a measurement against its bound
- **THEN** the earlier number it quotes is the one from the issue's own record, not a separate store
- **Case**: `run#aFilingSetsTheBaseline`

#### Scenario: Nothing is committed by a check

- **WHEN** a check's run ends
- **THEN** it has written no file into the repository, because a check is not a second committer
- **Case**: `run#noBaselineFileWritten`

#### Scenario: Growth is distinguishable from a level

- **WHEN** a measurement has a history on the issue
- **THEN** the report says whether the number is worse than the previous one, so a trend is visible without
  a stored baseline
- **Case**: `report#printsACountingLinePerRow`

#### Scenario: A trend is drawn between one condition's own markers

- **WHEN** a row's issue carries markers of more than one condition, so nothing in its body names the
  fingerprint each marker carries
- **THEN** the previous number a trend is drawn against is the same condition's earlier marker, and a condition
  with no earlier marker of its own reports no trend rather than one drawn across conditions
- **Case**: `report#aTrendIsBetweenOneCondition`

#### Scenario: A filing is a measurement of the row

- **WHEN** a row's newest event is a filing, so its marker is in the new issue's body and no comment carries one
- **THEN** the report prints that marker's verdict and number, dated by the issue's own creation, and does not
  report the row as never measured
- **Case**: `report#aFiledRowIsReported`

#### Scenario: A filing is the baseline the next measurement is compared against

- **WHEN** a row's newest marker is the one its filing wrote into the new issue's body
- **THEN** the next run quotes that marker's number as its baseline, so the comparison starts from the loudest
  event the row can produce rather than from nothing
- **Case**: `run#aFilingSetsTheBaseline`
