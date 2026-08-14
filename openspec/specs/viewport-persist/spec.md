## Purpose

Persist the map viewport state to a JSON file so the map opens at the same location on app restart.

## Requirements

### Requirement: Save viewport state

The system SHALL save the current viewport state (center latitude, center longitude, magnification level) to a JSON file when the map screen is paused or stopped.

- The file SHALL be written to `filesDir/maps/viewport.json`
- The file SHALL use JSON format with fields: `centerLat`, `centerLon`, `magnification`
- Saving SHALL use `kotlinx.serialization` for JSON encoding
- Saving SHALL run on `Dispatchers.IO`
- The system SHALL also save the viewport state after a pan or zoom gesture completes (in addition to lifecycle-based save)

#### Scenario: Save on pause

- **WHEN** user presses the home button while on the map screen
- **THEN** the current viewport state is written to `viewport.json`
- **THEN** the file contains valid JSON with the correct lat, lon, and magnification

#### Scenario: Save after gesture

- **WHEN** user finishes a pan gesture (lifts finger)
- **THEN** the current viewport state is written to `viewport.json`

### Requirement: Load viewport state

The system SHALL load the viewport state from `filesDir/maps/viewport.json` when the map screen starts.

- If the file exists and contains valid JSON, the system SHALL use its values as the initial viewport
- If the file does not exist or is malformed, the system SHALL use a default viewport (Dortmund: lat=51.5136, lon=7.4653, magnification=8)
- Loading SHALL run on `Dispatchers.IO`

#### Scenario: Load saved state on startup

- **WHEN** `viewport.json` exists with valid data
- **THEN** the map renders at the saved center and magnification

#### Scenario: No saved state uses default

- **WHEN** `viewport.json` does not exist
- **THEN** the map renders at default center (Dortmund: 51.5136, 7.4653) and magnification 8

#### Scenario: Corrupted file uses default

- **WHEN** `viewport.json` contains invalid JSON
- **THEN** the system logs a warning
- **THEN** the map renders at default center (Dortmund: 51.5136, 7.4653) and magnification 8
