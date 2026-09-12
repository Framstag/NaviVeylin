## MODIFIED Requirements

### Requirement: Places mode suggestions

When the search field is empty in Places mode, the dialog SHALL show suggestion sources above the results area: recent searches as chips (youngest first), favorite locations as rows, and a "Current Location" row when GPS is available. Typing a query SHALL hide the suggestions and show location search results, with favorite hits listed above native results when they match the query (see favorite-search); clearing the field SHALL restore them immediately.

#### Scenario: Empty query shows suggestions

- **WHEN** the search dialog opens in Places mode with an empty query
- **THEN** recent-search chips SHALL be visible
- **AND** favorite rows SHALL be visible
- **AND** a "Current Location" row SHALL be visible when GPS is available

#### Scenario: Typing hides suggestions

- **WHEN** the user types a query in Places mode
- **THEN** the suggestions SHALL be hidden
- **AND** location search results SHALL be listed
- **AND** favorite hits matching the query SHALL be listed above the native results

#### Scenario: Clearing restores suggestions

- **WHEN** the user clears the query in Places mode
- **THEN** the suggestions SHALL reappear immediately

#### Scenario: Current location hidden without GPS

- **WHEN** GPS location is not available
- **THEN** the "Current Location" row SHALL be hidden
- **AND** the recent-search chips and favorite rows SHALL remain visible
