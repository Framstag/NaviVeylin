# search-dialog Specification

## Purpose

Lets users search for places, points of interest, and contacts from a single Material 3 search dialog on the phone map screen, with a mode switch and suggestion sources.

## Requirements

### Requirement: Unified search dialog entry points

The map screen SHALL provide exactly one search dialog, reachable from three entry points: the search button in the left action column, the "Search" entry in the map menu, and the `/` keyboard shortcut. All three SHALL open the same dialog.

#### Scenario: Search button opens unified dialog

- **WHEN** the user taps the search button on the map screen
- **THEN** the unified search dialog SHALL open

#### Scenario: Menu Search entry opens unified dialog

- **WHEN** the user selects "Search" in the map menu
- **THEN** the menu SHALL dismiss
- **AND** the unified search dialog SHALL open

#### Scenario: Slash key opens unified dialog

- **WHEN** the map canvas has keyboard focus
- **AND** the user presses the `/` key
- **THEN** the unified search dialog SHALL open

### Requirement: Full-screen dialog behavior

The search dialog SHALL be a full-screen Material 3 search surface: the search input SHALL receive focus and show the soft keyboard on open, the map SHALL be hidden behind the dialog, and the system back gesture or a back affordance SHALL dismiss the dialog and return to the map.

#### Scenario: Input auto-focused on open

- **WHEN** the search dialog opens
- **THEN** the search input SHALL have focus
- **AND** the soft keyboard SHALL be shown

#### Scenario: Back dismisses dialog

- **WHEN** the search dialog is open
- **AND** the user performs the system back gesture or taps the back affordance
- **THEN** the dialog SHALL close
- **AND** the map canvas SHALL remain visible

#### Scenario: Map hidden while searching

- **WHEN** the search dialog is open
- **THEN** the map SHALL NOT be visible behind the dialog

### Requirement: Search mode switch

The search dialog SHALL offer a mode switch with three mutually exclusive modes: Places (free-text location search), POIs (category-based point-of-interest search), and Contacts (address-book person search). The Contacts mode SHALL be shown only while `READ_CONTACTS` is granted. Switching modes SHALL preserve each mode's own state (query, category, radius, results) for the session.

#### Scenario: Three modes available

- **WHEN** the search dialog opens
- **THEN** the mode switch SHALL show Places, POIs, and Contacts
- **AND** Places SHALL be the initially selected mode

#### Scenario: Contacts mode hidden without permission

- **WHEN** `READ_CONTACTS` is not granted
- **THEN** the Contacts mode SHALL NOT be shown in the mode switch

#### Scenario: Mode switch preserves state

- **WHEN** the user types a query in Places mode
- **AND** switches to POIs mode
- **AND** switches back to Places mode
- **THEN** the Places query SHALL still be present

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

### Requirement: POIs mode search flow

In POIs mode, the dialog SHALL let the user pick one POI category from a searchable dropdown, choose a search radius with a slider, and trigger the search with an explicit search button. The search SHALL run around the current map center and SHALL NOT run automatically on category or radius change.

#### Scenario: Search disabled without category

- **WHEN** the user has not selected a category in POIs mode
- **THEN** the search button SHALL be disabled

#### Scenario: Explicit search button triggers search

- **WHEN** the user selects a category and a radius in POIs mode
- **AND** taps the search button
- **THEN** the app SHALL search for POIs of that category within the chosen radius around the current map center

#### Scenario: No automatic search on selection

- **WHEN** the user changes the selected category or radius in POIs mode
- **THEN** no search SHALL run until the search button is tapped

#### Scenario: Dropdown lists all supported categories

- **WHEN** the user opens the category dropdown in POIs mode
- **THEN** every supported category SHALL be selectable

### Requirement: Contacts mode

In Contacts mode, the dialog SHALL embed the address-book person search: a list of contacts with at least one postal address, filterable by typing part of the person's name (case-insensitive), with clearing the query restoring the full list.

#### Scenario: Contacts list shown in mode

- **WHEN** the user selects Contacts mode
- **THEN** the list SHALL contain only contacts that have at least one postal address

#### Scenario: Filter by name in Contacts mode

- **WHEN** the user types a name fragment in Contacts mode
- **THEN** the list SHALL be filtered to contacts whose name contains the typed text (case-insensitive)

#### Scenario: Clearing restores contacts

- **WHEN** the user clears the search field in Contacts mode
- **THEN** all contacts with addresses SHALL be shown again
