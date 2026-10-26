## ADDED Requirements

### Requirement: A row's filings in one run are bounded

One run of one row SHALL file at most the `maxFilings` its registry declares, or the count the row declares in its place, in condition items, and the conditions it did not file SHALL reach the tracker as one summary item carrying the count, the bound and each unfiled condition's identity and measurement — so a mis-fitted bound costs one item instead of one per condition it happens to hold.

#### Scenario: A row past its bound files the bound and one summary

- **WHEN** a check's command reports more conditions than its bound, none of them carrying an item already
- **THEN** the run files its bound's worth of items, one summary item names each condition it did not file with what
  that condition measured, and the run reports every condition it did not file — while a condition that already
  carries an item is annotated and spends nothing of the bound
- **Case**: `run#aRowPastItsBoundFilesItsBoundAndOneSummary`

#### Scenario: A row inside its bound files nothing extra

- **WHEN** a check reports no more conditions than its bound
- **THEN** each of them is filed as its own item and no summary is filed
- **Case**: `run#aRowUnderItsBoundFilesNoSummary`

#### Scenario: A row may declare its own bound

- **WHEN** a row declares `maxFilings`
- **THEN** that count bounds the row instead of the registry's, and a bound that is not a positive count is refused
  by name rather than read as no bound at all
- **Case**: `run#aRowDeclaresItsOwnBound`

#### Scenario: The summary clears when a run leaves nothing over

- **WHEN** a row whose conditions were left unfiled completes a run that leaves nothing over
- **THEN** its summary item is closed, naming what the run filed against the bound
- **Case**: `run#theSummaryClearsWhenTheRowIsBackUnderItsBound`

#### Scenario: A summary a person closed is not reopened

- **WHEN** a row is past its bound again and its summary item was closed as a decision not to act
- **THEN** the overflow is recorded on that item and the closure is left alone
- **Case**: `run#aSummaryClosedByAPersonIsNotReopened`

#### Scenario: A second over-bound run names its own overflow

- **WHEN** a row that already carries a summary item is past its bound again
- **THEN** the conditions this run did not file are named on that item, so the identities reaching the tracker are the ones the run measured
- **Case**: `run#aSecondOverBoundRunListsWhatItDidNotFile`

#### Scenario: A failed check clears nothing

- **WHEN** a run holds a row's summary item and one of its findings failed to determine anything
- **THEN** the summary is neither closed nor annotated, because a check that determined nothing measured no overflow either
- **Case**: `run#aFailedCheckDoesNotClearTheSummary`
