# Basemap Loading

## Purpose

Load the world basemap as an overlay database so borders, country names, and coastlines render underneath regional maps and remain visible in areas no regional map covers.

## Requirements

### Requirement: Register basemap directory at client initialization

The system SHALL pass the basemap directory to the native client builder during client initialization when a basemap is installed.

#### Scenario: Basemap installed at startup

- **WHEN** the app initializes the map client
- **WHEN** a basemap directory exists at `{mapsDir}/basemap/`
- **THEN** the system passes the basemap directory to the client builder
- **THEN** the native layer loads the basemap as an overlay database

#### Scenario: No basemap installed at startup

- **WHEN** the app initializes the map client
- **WHEN** no basemap directory exists
- **THEN** the system does not register a basemap directory
- **THEN** the app starts normally without a basemap overlay

### Requirement: Reload basemap after download or delete

The system SHALL reload or unload the basemap when it is downloaded, updated, or deleted while the app is running — including the first-time case where no basemap was installed when the app started. The current view SHALL re-render so the change is visible without restarting the app.

#### Scenario: Basemap downloaded while app is running

- **WHEN** user downloads or updates the basemap
- **THEN** the system triggers a reload of the basemap database
- **THEN** the current view re-renders with the basemap overlay active

#### Scenario: First-time basemap installation while app is running

- **WHEN** the app started without a basemap installed
- **WHEN** user downloads and installs the basemap while the app is running
- **THEN** the system registers the basemap for the running session
- **THEN** the system triggers a reload of the basemap database
- **THEN** the current view re-renders with the basemap overlay active without an app restart

#### Scenario: Basemap deleted while app is running

- **WHEN** user deletes the basemap
- **THEN** the system unloads the basemap database
- **THEN** the current view re-renders without the basemap overlay

### Requirement: Basemap renders underneath regional maps

The basemap SHALL render as a background layer, with regional maps drawn on top; in viewports no regional map covers, the basemap SHALL remain visible.

#### Scenario: Viewing area with no regional map

- **WHEN** user pans to a region not covered by any installed regional map
- **THEN** basemap borders, country names, and coastlines remain visible
- **THEN** the system does not show a blank map

#### Scenario: Viewing area with regional map

- **WHEN** user views an area covered by an installed regional map
- **THEN** regional map data renders on top of the basemap
- **THEN** the basemap provides context at low zoom levels where regional map detail is sparse

### Requirement: Stylesheet directory fully populated before load

The system SHALL ensure the stylesheet directory passed to the native client (`withStyleSheetDirectory`) contains the full, current set of stylesheets before the client loads them.

- The refresh SHALL run before the native client reads the directory (ordering on the app-start path, not concurrent with native load)
- Failure to refresh SHALL NOT crash the app: stale-but-valid stylesheets SHALL remain usable and the failure SHALL be logged

#### Scenario: Stylesheets present before map init

- **WHEN** the app initializes the map with a downloaded basemap
- **THEN** the stylesheet directory SHALL already contain the complete bundled set (freshly copied or verified unchanged)

#### Scenario: Refresh failure degrades gracefully

- **WHEN** the stylesheet refresh throws (e.g., I/O error) during startup
- **THEN** the app SHALL continue with the existing internal-storage stylesheets
- **THEN** the failure SHALL be logged

### Requirement: Basemap renders with its own stylesheet

The basemap SHALL be rendered with a dedicated stylesheet (`basemap-render.oss`) that references only types present in the basemap database, instead of the user-selected main map style. Loading the basemap stylesheet SHALL NOT produce unknown-type warnings. The basemap stylesheet SHALL follow the same style flags as the main stylesheet (e.g. the `daylight` flag for dark mode). Switching the main map style SHALL NOT change the basemap's stylesheet.

#### Scenario: Basemap loads without unknown-type warnings

- **WHEN** the app loads the basemap database with its stylesheet
- **THEN** the basemap stylesheet references only types present in the basemap database
- **THEN** no "Unknown type" warnings are emitted for the basemap database

#### Scenario: Dark mode applies to the basemap

- **WHEN** the user enables dark mode
- **THEN** the basemap re-renders with its dark-mode colors

#### Scenario: Main style switch leaves basemap unchanged

- **WHEN** the user switches the main map style (e.g. from `standard` to `cycle`)
- **THEN** the basemap continues to render with `basemap-render.oss`

#### Scenario: Basemap labels persist at high zoom

- **WHEN** the user zooms into an area not covered by a regional map
- **THEN** basemap place labels remain visible at the same magnification levels as the standard stylesheet provides for those types
