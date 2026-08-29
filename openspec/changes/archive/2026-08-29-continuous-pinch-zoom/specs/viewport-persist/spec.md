# viewport-persist

## MODIFIED Requirements

### Requirement: Save viewport state

The system SHALL save the current viewport state (center latitude, center longitude, magnification) to a JSON file when the map screen is paused or stopped.

- The file SHALL be written to `filesDir/maps/viewport.json`
- The file SHALL use JSON format with fields: `centerLat`, `centerLon`, `magnification`
- The `magnification` field SHALL be a JSON number storing the fractional magnification (e.g. `15.34`); integer levels remain valid values
- Saving SHALL use `kotlinx.serialization` for JSON encoding
- Saving SHALL run on `Dispatchers.IO`
- The system SHALL also save the viewport state after a pan or zoom gesture completes (in addition to lifecycle-based save)

#### Scenario: Save on pause

- **WHEN** user presses the home button while on the map screen
- **THEN** the current viewport state is written to `viewport.json`
- **THEN** the file contains valid JSON with the correct lat, lon, and magnification (fractional if the last zoom was a pinch commit)

#### Scenario: Save after gesture

- **WHEN** user finishes a pan gesture (lifts finger)
- **THEN** the current viewport state is written to `viewport.json`

#### Scenario: Save fractional magnification after pinch

- **WHEN** user finishes a pinch zoom ending at magnification ~15.34
- **THEN** `viewport.json` contains `magnification` ≈ 15.34
- **WHEN** the app restarts
- **THEN** the map renders at magnification 15.34

### Requirement: Load viewport state

The system SHALL load the viewport state from `filesDir/maps/viewport.json` when the map screen starts.

- If the file exists and contains valid JSON, the system SHALL use its values as the initial viewport
- If the file does not exist or is malformed, the system SHALL use a default viewport (Dortmund: lat=51.5136, lon=7.4653, magnification=8)
- Loading SHALL run on `Dispatchers.IO`
- Files written by older versions with integer magnification SHALL load unchanged (an integer is a valid fractional value)

#### Scenario: Load saved state on startup

- **WHEN** `viewport.json` exists with valid data
- **THEN** the map renders at the saved center and magnification

#### Scenario: Load legacy integer magnification

- **GIVEN** a `viewport.json` written by an older version contains `magnification: 14`
- **WHEN** the map screen starts
- **THEN** the map renders at magnification 14.0

#### Scenario: No saved state uses default

- **WHEN** `viewport.json` does not exist
- **THEN** the map renders at default center (Dortmund: 51.5136, 7.4653) and magnification 8

#### Scenario: Corrupted file uses default

- **WHEN** `viewport.json` contains invalid JSON
- **THEN** the system logs a warning
- **THEN** the map renders at default center (Dortmund: 51.5136, 7.4653) and magnification 8