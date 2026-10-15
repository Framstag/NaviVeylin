# Spec Delta

## MODIFIED Requirements

### Requirement: Route calculation is measured without coordinates

When a route calculation ends the system SHALL record a diagnostics entry carrying only coordinate-free
numbers — the trigger of the calculation, its duration in milliseconds, the number of progress values
received, its outcome, and, when it failed, the failure's reason with the numbers that classify it (the
distance the search reached, the start-to-target air-line distance, and the distance from a failing end to
the nearest road the router could use) — so the notice delay is set from observed durations.

#### Scenario: Entry on a successful calculation
- **WHEN** a calculation ends successfully
- **THEN** a diagnostics entry with its trigger, duration, progress count and `outcome=ok` SHALL be recorded

#### Scenario: Entry on a failed calculation
- **WHEN** a calculation ends with an error
- **THEN** the entry SHALL report `outcome=error`
- **AND** it SHALL report the failure's reason

#### Scenario: The failing reason is identifiable from the entry alone
- **WHEN** a calculation fails because one end cannot be attached to the road network
- **THEN** the entry SHALL identify that end as the failing one
- **AND** it SHALL report how far the search reached

#### Scenario: Entry on a cancelled calculation
- **WHEN** a calculation is cancelled
- **THEN** the entry SHALL report `outcome=cancelled`
- **AND** it SHALL report no failure reason

#### Scenario: No coordinates in the entry
- **WHEN** the entry is recorded
- **THEN** it SHALL NOT contain a position or a coordinate-formatted string
