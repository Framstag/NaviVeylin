## MODIFIED Requirements

### Requirement: The issues carry the measurement history

A check's measurements SHALL be recorded on the issue as comments, and no measurement SHALL be stored in the repository, so the issue's own timeline is the baseline a later measurement is compared against. A filing SHALL carry its marker in the issue's body too, and the newest marker an issue carries — a comment's, or a filing's dated by the issue's own creation — SHALL be the measurement the report prints and the one a later measurement is compared against.

#### Scenario: The baseline is the previous measurement

- **WHEN** a check compares a measurement against its bound
- **THEN** the earlier number it quotes is the one from the issue's own record, not a separate store
- **Case**: `sensors#buildDurationRow`

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
