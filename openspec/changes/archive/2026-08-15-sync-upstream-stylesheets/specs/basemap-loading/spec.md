# basemap-loading Specification (Delta)

## ADDED Requirements

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
