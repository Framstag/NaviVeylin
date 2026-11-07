## ADDED Requirements

### Requirement: A row count is read only once the screen's page capacity is known

A case that asserts how many rows a `DiagnosticsScreen` page holds SHALL await the page the connected
host's capacity builds, never the publication of the entries alone. A case SHALL assert over the rows its own
case wrote, never over a whole diagnostics window the screen also writes into. A case that builds such a
screen SHALL release it before it returns.

#### Scenario: The count waits for the host's capacity

- **WHEN** a case asserts how many rows the diagnostics screen's page holds, and the fake host reports a
  capacity other than the library default
- **THEN** the case SHALL await the page built from the host's capacity, so the count it reads is the page
  the host displays and not the default the screen starts from
- **Case**: `DiagnosticsScreenTest#theDiagnosticsListFitsTheHostsCapacity`

#### Scenario: A page's count waits for its own screen's capacity

- **WHEN** a page row is opened and the case counts the rows of the page it opened
- **THEN** the case SHALL await that page's host-built rows before asserting them, so the page it counts is
  the one the host displays
- **Case**: `DiagnosticsScreenTest#thePageRowOpensTheNextSliceOfTheWindow`

#### Scenario: The newest-first order is asserted over the case's own entries

- **WHEN** the diagnostics window holds the entries the case wrote plus a measurement line the screen wrote
  after them
- **THEN** the case SHALL assert the disclosure opens the list and its own entries follow newest-first, and
  SHALL NOT assert the window's total
- **Case**: `StartupScreensTest#diagnosticsScreenShowsNewestFirst`
