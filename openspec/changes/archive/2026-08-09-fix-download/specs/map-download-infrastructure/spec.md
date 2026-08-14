# Map Download Infrastructure — Delta

## Purpose

Fixes the download lifecycle so a completed download is always visible in the installed list, re-downloads start from a clean state, and delete fully removes a map from the native map manager.

## MODIFIED Requirements

### Requirement: Download directory registered with native MapManager
The system SHALL register the download directory with the native `MapManager` via `OSMScoutClientBuilder.withMapLookupDirectories()` so previously downloaded maps are discovered on app restart.

#### Scenario: Installed maps persist across restarts
- **WHEN** app restarts and MapManagerScreen opens
- **THEN** previously downloaded maps appear in the installed list
- **AND** no Refresh is required

#### Scenario: Newly downloaded map visible immediately
- **WHEN** a map download completes and the directory is registered
- **THEN** the map appears in the installed list without requiring an app restart or manual Refresh
- **AND** the installed-list refresh reflects the completed native map lookup

### Requirement: Installed list reflects completed lookup
The system SHALL ensure the installed map list is refreshed only after the native map lookup has finished scanning the registered directories, so a completed download is never missing from the list due to an in-flight asynchronous scan.

#### Scenario: No race between registration and list refresh
- **WHEN** a download completes and triggers a native map lookup
- **THEN** the installed list refresh waits for the lookup to finish before reading the installed directories
- **AND** the newly downloaded map is present in the list

### Requirement: Multiple maps usable simultaneously
All downloaded maps SHALL be usable at the same time: opening a map adds it to the set of loaded databases, and the renderer SHALL display whichever loaded map(s) cover the current viewport — no switching between maps is required. When a map is opened, the initial viewport SHALL center on that map's bounding box unless a viewport was previously saved for that map.

#### Scenario: Opening second map keeps first usable
- **WHEN** user opens map B after map A
- **THEN** both map databases remain loaded
- **AND** panning to a region covered by map A renders map A
- **AND** panning to a region covered by map B renders map B

#### Scenario: Initial viewport centers on selected map
- **WHEN** user opens a map that has no saved viewport
- **THEN** the viewport centers on the map's bounding box
- **AND** the map is visible without manual panning

#### Scenario: Per-map viewport resumes
- **WHEN** user reopens a map that has a saved viewport
- **THEN** the viewport resumes at the saved position for that map

### Requirement: Clean redownload
The system SHALL ensure that re-downloading a map that was previously downloaded and deleted starts from a clean state, so no state from the previous download can cause the re-download to fail or the map to remain invisible.

#### Scenario: Delete then re-download same map
- **WHEN** user deletes an installed map
- **AND** downloads the same map again
- **THEN** the download completes without error
- **AND** the map appears in the installed list

#### Scenario: Failed download does not poison next attempt
- **WHEN** a download fails or is cancelled
- **THEN** any partial files and metadata from that attempt are removed
- **AND** a subsequent download of the same map starts from a clean directory
- **AND** the subsequent download is not affected by the previous failure

### Requirement: Delete removes map manager registration
The system SHALL remove a deleted map's directory from the native map manager's lookup directories, not just delete its files, so a later re-download of the same map triggers a fresh lookup.

#### Scenario: Deleted map removed from lookup set
- **WHEN** user deletes an installed map
- **THEN** the map's directory is removed from the map manager's lookup directories
- **AND** the map no longer appears in the installed list

#### Scenario: Re-registration always triggers lookup
- **WHEN** a map directory is registered with the map manager
- **AND** the directory is already present in the lookup set (e.g., after a delete that left it registered)
- **THEN** a fresh lookup is still triggered
- **AND** the map appears in the installed list
