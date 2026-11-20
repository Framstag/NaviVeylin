## ADDED Requirements

### Requirement: An option carries a score, and the deltas between options are readable

Every option the sheet offers SHALL carry an integer score from 0 to 10, read off the criteria the sheet already
states and never averaged from its two views, and the sheet SHALL print one table of option, name, points and what set
the number as its last block. The recommended option SHALL hold the highest score, `do nothing` SHALL be scored on the
same scale, and no row SHALL carry a number without the clause behind it.

#### Scenario: Every option is scored, and deciding nothing is one of them

- **WHEN** the sheet presents three options and the fourth outcome
- **THEN** each row carries an integer from 0 to 10 in the score table
- **AND** `do nothing` is one of the rows, on the same scale
- **Case**: `decisions#everyOptionCarriesAScore`

#### Scenario: The deltas are read, and the recommendation holds the highest score

- **WHEN** the sheet's rows are compared
- **THEN** a gap of one point is a tie that names the clause breaking it, a gap of two or three is "clearly worse",
  and a gap of four or more says the row was carried rather than offered
- **AND** a rejected option scoring higher than the recommendation is refused, with the half that moved named
- **Case**: `decisions#theScoreTableReadsItsDeltas`

#### Scenario: The score is read off the sheet's own columns, never averaged from its two views

- **WHEN** a row's number disagrees with the option's own verdict line, risk or strategic reading
- **THEN** the number changes rather than the columns
- **AND** the two views stay two views, with no average of them called a score
- **Case**: `decision-queue#everyOptionCarriesAnExampleAndTwoViews`
