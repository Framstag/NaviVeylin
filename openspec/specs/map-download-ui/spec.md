# Map Download UI

## Purpose

Unified map management screen combining available maps browsing, active download progress, and installed map management in a single scrollable tree view — no tabs.

## Requirements

### Requirement: Unified map tree view
The system SHALL display available maps in a hierarchical tree grouped by region (e.g., Europe → Germany). Each map entry SHALL show its current state: available (not downloaded), downloading (with progress), or installed.

#### Scenario: Tree shows available maps grouped by region
- **WHEN** user opens the MapManagerScreen
- **THEN** available maps are displayed in a tree grouped by continent/region
- **AND** each leaf entry shows the map name and size

#### Scenario: Installed maps show checkmark
- **WHEN** a map has been downloaded and registered
- **THEN** it displays a checkmark (✅) indicator and a [Delete] button instead of [Download]

#### Scenario: Downloading maps show inline progress
- **WHEN** a map download is in progress
- **THEN** the map entry shows a progress bar and percentage inline
- **AND** a [Cancel] button is visible

### Requirement: Active downloads section
The system SHALL display a collapsible "Active Downloads" section at the top of the screen when downloads are in progress, including basemap downloads. This section SHALL be hidden when no downloads are active.

#### Scenario: Active downloads section appears during download
- **WHEN** a download starts
- **THEN** an "Active Downloads" section appears at the top with the current download(s) and progress
- **AND** the section is collapsible

#### Scenario: Basemap download shown with progress
- **WHEN** a basemap download is in progress
- **THEN** the basemap entry appears in the active downloads section with progress
- **AND** a cancel control is available

#### Scenario: Active downloads section hides when empty
- **WHEN** all downloads complete or are cancelled
- **THEN** the "Active Downloads" section disappears

### Requirement: Provider selection and refresh
The system SHALL allow the user to select a map provider and refresh the available maps list.

#### Scenario: Select provider and refresh
- **WHEN** user selects a provider from the dropdown and taps [Refresh]
- **THEN** the system fetches the available maps list from that provider
- **AND** updates the tree view

### Requirement: Download a map
The system SHALL download a selected map to the device storage when the user taps [Download].

#### Scenario: Download starts
- **WHEN** user taps [Download] on an available map entry
- **THEN** the download begins
- **AND** the entry transitions to "downloading" state with progress indicator

#### Scenario: Download completes
- **WHEN** all files for a map have been downloaded and registered
- **THEN** the entry transitions to "installed" state with checkmark
- **AND** the map becomes available for rendering

### Requirement: Cancel a download
The system SHALL allow the user to cancel an active download.

#### Scenario: Cancel download
- **WHEN** user taps [Cancel] on a downloading entry
- **THEN** the download is cancelled
- **AND** partial files are cleaned up
- **AND** the entry returns to "available" state

### Requirement: Delete an installed map
The system SHALL allow the user to delete an installed map.

#### Scenario: Delete installed map
- **WHEN** user taps [Delete] on an installed map entry
- **THEN** the map directory is removed from storage
- **AND** the entry returns to "available" state

### Requirement: Search/filter available maps
The system SHALL provide a text search field that filters the available maps tree by name or region path.

#### Scenario: Search filters tree
- **WHEN** user types in the search field
- **THEN** the tree view filters to show only entries whose name or path matches the query
- **AND** non-matching entries are hidden

### Requirement: Download button shows progress inline
The system SHALL transform the [Download] button into a progress indicator when a download is active, showing a spinner and [Cancel] button.

#### Scenario: Button transforms on download start
- **WHEN** user taps [Download]
- **THEN** the button immediately changes to a `CircularProgressIndicator` with [Cancel] button
- **AND** the entry shows download progress inline

### Requirement: Installed maps visible without refresh
The system SHALL show previously downloaded maps in the tree view even when the provider has not been queried yet.

#### Scenario: Installed maps appear on screen open
- **WHEN** user opens MapManagerScreen and has previously downloaded maps
- **THEN** those maps appear in the tree with ✅ checkmark and [Delete] button
- **AND** no Refresh is required to see them

### Requirement: Installed maps stay on top after refresh
The system SHALL keep installed maps visible at the top of the tree view after a provider refresh, so users can easily find and delete them.

#### Scenario: Installed maps remain on top after refresh
- **WHEN** user taps [Refresh] and installed maps exist
- **THEN** the installed maps appear at the top of the tree view with ✅ checkmark and [Delete] button
- **AND** newly discovered available maps appear below the installed section

#### Scenario: Installed maps section labeled
- **WHEN** installed maps are shown at the top after refresh
- **THEN** they are visually grouped or labeled as "Installed Maps"
- **AND** the section is distinct from available maps

### Requirement: Download errors shown with explicit OK
The system SHALL display a download error in the download task UI with the error message and an explicit [OK] action to dismiss it. The error SHALL remain visible until the user dismisses it.

#### Scenario: Download failure shows error task
- **WHEN** a map download fails (network error, server issue, preparation failure, or registration failure)
- **THEN** the download task shows the error message
- **AND** an explicit [OK] action is available to dismiss the error

#### Scenario: OK dismisses error and resets entry
- **WHEN** user taps [OK] on a download error task
- **THEN** the error is dismissed
- **AND** the map entry returns to the available state
- **AND** the user can retry the download

#### Scenario: Error persists until dismissed
- **WHEN** a download error is shown
- **THEN** the error remains visible until the user taps [OK]
- **AND** it is not silently cleared by progress updates or list refreshes

### Requirement: Delete and refresh errors surfaced
The system SHALL surface errors from map deletion and installed-map refresh to the user instead of swallowing them silently.

#### Scenario: Delete failure shown
- **WHEN** deleting an installed map fails
- **THEN** the user sees an error message describing the failure
- **AND** the map entry remains in its current state

#### Scenario: Installed refresh failure shown
- **WHEN** refreshing the installed map list fails
- **THEN** the user sees an error message describing the failure
- **AND** the previously known installed maps remain visible

### Requirement: Basemap section in map manager screen
The system SHALL integrate basemap status and controls into the map manager screen without a separate tab or screen.

#### Scenario: Basemap status shown on screen open
- **WHEN** user opens the map manager screen
- **THEN** the screen shows whether the basemap is installed, available, or unavailable
- **AND** no separate navigation step is required to reach basemap controls

#### Scenario: Basemap errors surfaced
- **WHEN** a basemap download, update, or delete fails
- **THEN** the failure is reported to the user with the error message
- **AND** the user can retry the action
