# Spec Delta

## ADDED Requirements

### Requirement: Search suggestions follow the newest query text

While a search for an earlier text is running, a request for newer text SHALL abort it, and the newer text's
results SHALL be the only ones displayed (spec: `search-request-lifecycle`). A query shorter than the minimum
length SHALL leave the results area empty instead of holding the previous query's results.

#### Scenario: Newer text stops the running search

- **WHEN** a search for "Dort" is running and the user types "Dortm"
- **THEN** the search for "Dort" SHALL be aborted at its next abort check
- **AND** the results area SHALL show only the results of the search for "Dortm"

#### Scenario: A superseded search never publishes

- **WHEN** a search for "Dort" completes after the search for "Dortm" has already published its results
- **THEN** the results of the search for "Dort" SHALL NOT be displayed

#### Scenario: A one-character query shows no results

- **WHEN** the user shortens the query to one character while a search is running
- **THEN** that search SHALL be aborted
- **AND** the results area SHALL be empty rather than holding the previous query's results
