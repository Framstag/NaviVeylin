# Spec Delta — map-download-ui

## MODIFIED Requirements

### Requirement: Provider selection and refresh
The system SHALL allow the user to select a map source and refresh the available maps list. The provider row SHALL be an interactive selector offering the built-in karry.cz provider and the libosmscout mapgen repository source, and SHALL show the repository's base URL as an editable field with a test action when that source is selected. Switching source SHALL be confirmed before any data is deleted, as specified by the `map-source-selection` capability.

#### Scenario: Select provider and refresh
- **WHEN** user selects a provider from the dropdown and taps [Refresh]
- **THEN** the system fetches the available maps list from that provider
- **AND** updates the tree view

#### Scenario: Repository source exposes its base URL

- **WHEN** the user selects the repository source
- **THEN** the provider row shows the base URL in an editable field
- **AND** shows the test action next to it
- **AND** [Refresh] fetches the region index from that URL

#### Scenario: Test action reports its outcome in the row

- **WHEN** the user runs the test action for a base URL
- **THEN** the row shows the outcome — success with the region and leaf counts, or the failure reason
- **AND** the outcome stays visible until the next test or source change

#### Scenario: Switching source asks before deleting

- **WHEN** the user picks a different source while maps or a basemap of the active source exist
- **THEN** a confirmation dialog naming the number of maps and their total size appears before anything is deleted
- **AND** the source changes only after the user confirms

#### Scenario: URL field remembers the last value per source

- **WHEN** the user switches away from the repository source and back
- **THEN** the base URL field shows the last URL entered for that source
- **AND** no request is issued by the act of switching back

## ADDED Requirements

### Requirement: Installed maps name their source

The installed-maps list SHALL show which source each installed map came from, so a user who runs both a built-in provider and a repository can tell the two kinds of installation apart without opening the file system.

#### Scenario: Repository map is marked in the installed list

- **WHEN** a map downloaded from the repository source appears in the installed-maps list
- **THEN** its row names the repository source rather than the built-in provider

#### Scenario: Pre-existing installation is marked as the built-in provider

- **WHEN** an installed map carries no source marker
- **THEN** its row names the built-in provider
- **AND** the row offers the same [Delete] action as a marked installation

#### Scenario: Row survives a source switch that kept it

- **WHEN** the user switches source and the map is not deleted because it belongs to the newly active source
- **THEN** its row keeps its source label and its [Delete] action
- **AND** the list is not left showing an entry whose directory no longer exists
