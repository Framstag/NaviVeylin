## Purpose

Persist the map viewport state to a JSON file so the map opens at the same location on app restart.

## Requirements

### Requirement: Save viewport state

The system SHALL save the current viewport state (center latitude, center longitude, magnification) to a JSON file when the map screen is paused or stopped.

- The file SHALL be written to `filesDir/maps/viewport.json`
- The file SHALL use JSON format with fields: `centerLat`, `centerLon`, `magnification`
- The `magnification` field SHALL be a JSON number storing the fractional magnification (e.g. `15.34`); integer levels remain valid values
- Saving SHALL use `kotlinx.serialization` for JSON encoding
- Saving SHALL run on `Dispatchers.IO`
- The system SHALL also save the viewport state after a pan or zoom gesture completes (in addition to lifecycle-based save)
- The system SHALL NOT save the viewport state while the map is still initializing, before the persisted viewport restore has been applied to the map state — a lifecycle save in that window must not overwrite the previously persisted viewport with the uninitialized default

#### Scenario: Save on pause

- **WHEN** user presses the home button while on the map screen
- **THEN** the current viewport state is written to `viewport.json`
- **THEN** the file contains valid JSON with the correct lat, lon, and magnification

#### Scenario: Save after gesture

- **WHEN** user finishes a pan gesture (lifts finger)
- **THEN** the current viewport state is written to `viewport.json`

#### Scenario: Save fractional magnification after pinch

- **WHEN** user finishes a pinch zoom ending at magnification ~15.34
- **THEN** `viewport.json` contains `magnification` ≈ 15.34
- **WHEN** the app restarts
- **THEN** the map renders at magnification 15.34

#### Scenario: Pause during restore does not clobber

- **WHEN** the map screen starts and the user backgrounds the app while the persisted viewport restore is still in progress
- **THEN** the viewport file is not written or overwritten with the default viewport
- **THEN** the persisted viewport file still contains the previously saved center and magnification

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

### Requirement: Reject invalid viewport states on save

The system SHALL NOT persist a viewport state whose coordinates or magnification are invalid (NaN, infinity, latitude outside [-90, 90], longitude outside [-180, 180], non-positive or NaN magnification, NaN angle), so an uninitialized viewport can never overwrite a previously restored one on disk.

#### Scenario: NaN center is not saved

- **WHEN** a view-change event or lifecycle save carries a viewport with NaN or infinite coordinates
- **THEN** the viewport file is not written or overwritten
- **THEN** a warning is logged

#### Scenario: Out-of-range coordinates are not saved

- **WHEN** a view-change event or lifecycle save carries a latitude outside [-90, 90] or a longitude outside [-180, 180]
- **THEN** the viewport file is not written or overwritten

#### Scenario: Invalid save does not clobber a valid file

- **WHEN** a valid viewport file exists on disk and an invalid viewport state is saved
- **THEN** the existing file keeps its previous valid content

### Requirement: Apply restored viewport before wiring the view-change listener

The system SHALL apply the restored viewport to the map state before the view-change listener (which persists every completed render) is wired, so no render triggered during map initialization can persist an uninitialized viewport over the restored one.

#### Scenario: Restore survives an early render

- **WHEN** the map screen starts and a render completes before the restored viewport is applied
- **THEN** the persisted viewport file still contains the restored center and magnification

#### Scenario: Restore survives an early screen-size render

- **WHEN** the map screen starts and the composable reports its size while the viewport restore is still in progress
- **THEN** no render with the default viewport is submitted
- **THEN** the persisted viewport file still contains the restored center and magnification
