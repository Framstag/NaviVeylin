## ADDED Requirements

### Requirement: A row whose condition recurs by design declares its own recurrence bound

A row MAY declare its own recurrence bound, and a row whose condition returns as a matter of course SHALL declare one rather than inherit the registry's: for that row the recurrence is the cycle, not a fix that failed to hold, so escalating it hands maintenance to a person as a design problem. A declared bound SHALL be a count of at least one, refused by name otherwise. A row that declares none keeps the registry's number.

#### Scenario: A routine condition recurs past the registry's number

- **WHEN** a row that declares its own bound has its condition return past the number the registry declares
- **THEN** the item is reopened and stays a work item, and no escalation happens until the row's own bound is reached
- **Case**: `run#aRowDeclaresItsOwnRecurrenceBound`

#### Scenario: A row that declares no bound keeps the registry's

- **WHEN** a row declares no bound of its own
- **THEN** the number the registry declares decides when reopening stops
- **Case**: `run#escalatesPastKRecurrences`

#### Scenario: A declared bound is a count of at least one

- **WHEN** a row declares a bound of zero, or one that is not a count
- **THEN** the registry is refused with a reason naming that row and that field
- **Case**: `lib#aRowBoundIsACount`
