# location-search Delta

## MODIFIED Requirements

### Requirement: Search button on map screen

The map screen SHALL display a search button overlay positioned at the top-left area (below status bar padding). Pressing the button SHALL open the unified search dialog in Places mode.

#### Scenario: Search button visible

- **WHEN** the map screen is displayed
- **THEN** a search button (magnifying glass icon) SHALL be visible in the top-left corner

#### Scenario: Search button opens panel

- **WHEN** user taps the search button
- **THEN** the unified search dialog SHALL open in Places mode with the search input auto-focused

### Requirement: Convenience entries on empty query

When the search field is empty in Places mode, the search dialog SHALL show suggestion sources above the results area: recent searches as chips, favorite locations as rows, and a "Current Location" row (if GPS is available). Typing a query SHALL hide all suggestions and show location search results only; clearing the field SHALL restore them immediately.

#### Scenario: Empty query shows convenience entries

- **WHEN** the search dialog opens in Places mode with an empty query
- **THEN** a "Current Location" row SHALL be visible (if GPS is available)
- **AND** favorite rows SHALL be visible
- **AND** recent-search chips SHALL be visible

#### Scenario: Typing hides convenience entries

- **WHEN** the user types a query
- **THEN** the suggestion rows and chips SHALL be hidden
- **AND** only location search results SHALL be listed

#### Scenario: Clearing restores convenience entries

- **WHEN** the user clears the query
- **THEN** the suggestion rows and chips SHALL reappear immediately

#### Scenario: Current location entry hidden without GPS

- **WHEN** GPS location is not available
- **THEN** the "Current Location" row SHALL be hidden
- **AND** the favorite rows and recent-search chips SHALL remain visible

### Requirement: Search scope region name shown in search panel

When an admin region has been resolved for the current GPS position, the search dialog SHALL display the name of the search scope region above the search input: the parent region when the scope is expanded, else the resolved region itself. The displayed name SHALL follow the currently resolved region: it SHALL appear when resolution succeeds, update when the region is re-resolved after movement, and disappear when no usable GPS fix exists or resolution fails.

#### Scenario: Scope region name shown above search field

- **WHEN** an admin region is resolved for the current position
- **AND** the search scope is expanded to the parent region
- **THEN** the parent region's name SHALL be displayed above the search input field

#### Scenario: Resolved region name shown without expansion

- **WHEN** an admin region is resolved for the current position
- **AND** the search scope is the resolved region alone (no expansion)
- **THEN** the resolved region's name SHALL be displayed above the search input field

#### Scenario: No name without resolved region

- **WHEN** no usable GPS fix exists or region resolution failed
- **AND** the search dialog is open
- **THEN** no region name SHALL be displayed above the search input field

#### Scenario: Name follows re-resolution

- **WHEN** the user moves beyond the movement threshold
- **AND** a new admin region is resolved
- **THEN** the displayed name SHALL update to the newly resolved scope region's name

## REMOVED Requirements

### Requirement: Stable sheet height

The search bottom sheet SHALL maintain a fixed minimum height from open to dismiss. Sheet height SHALL NOT change when results load, update, or clear. Content exceeding the allocated space SHALL scroll internally.

**Reason**: The search surface is now a full-screen Material 3 search dialog (see `search-dialog`); there is no bottom sheet whose height could change.

**Migration**: No user action needed — the full-screen dialog replaces the sheet; the fixed-height guarantee is obsolete.

#### Scenario: Sheet height stable during search lifecycle

- **WHEN** the search panel opens
- **THEN** the sheet SHALL display at its minimum height immediately
- **AND** the height SHALL remain constant while the user types, results load, results display, or results clear
- **AND** the sheet SHALL NOT resize when transitioning between empty, loading, results, and no-results states

#### Scenario: Many results scroll internally

- **WHEN** search returns more results than fit in the allocated space
- **THEN** the result list SHALL scroll within the sheet
- **AND** the sheet SHALL NOT expand to show additional items

#### Scenario: Map visible behind sheet

- **WHEN** the search panel is open
- **THEN** the map SHALL remain partially visible behind the sheet
- **AND** the sheet height SHALL leave at least 30% of the screen visible for the map
