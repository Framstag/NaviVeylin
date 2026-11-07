## MODIFIED Requirements

### Requirement: Save viewport state

The system SHALL save the current viewport state (center latitude, center longitude, magnification) to a JSON file per map when the map screen is paused or stopped.

- The file SHALL be written to `filesDir/maps/viewport-<key>.json`, where `<key>` is the map database path's last segment (for `/data/maps/germany.osm` the key is `germany.osm`)
- Every character of `<key>` outside `A-Za-z0-9._-` SHALL be replaced by `_` (for the key `Europe map.osm` the file is `viewport-Europe_map.osm.json`)
- The system SHALL skip a save while no map key is known, so no viewport file is written that no load path reads
- The file SHALL use JSON format with fields: `centerLat`, `centerLon`, `magnification`
- The `magnification` field SHALL be a JSON number storing the fractional magnification (e.g. `15.34`); integer levels remain valid values
- Saving SHALL use `kotlinx.serialization` for JSON encoding
- Saving SHALL run on `Dispatchers.IO`
- The system SHALL also save the viewport state after a pan or zoom gesture completes (in addition to lifecycle-based save)
- The system SHALL NOT save the viewport state while the map is still initializing, before the persisted viewport restore has been applied to the map state — a lifecycle save in that window must not overwrite the previously persisted viewport with the uninitialized default

#### Scenario: Save on pause

- **WHEN** user presses the home button while on the map screen
- **THEN** the current viewport state is written to `viewport-<key>.json`
- **THEN** the file contains valid JSON with the correct lat, lon, and magnification

#### Scenario: Save after gesture

- **WHEN** user finishes a pan gesture (lifts finger)
- **THEN** the current viewport state is written to `viewport-<key>.json`

#### Scenario: Save fractional magnification after pinch

- **WHEN** user finishes a pinch zoom ending at magnification ~15.34
- **THEN** `viewport-<key>.json` contains `magnification` ≈ 15.34
- **WHEN** the app restarts
- **THEN** the map renders at magnification 15.34

#### Scenario: Pause during restore does not clobber

- **WHEN** the map screen starts and the user backgrounds the app while the persisted viewport restore is still in progress
- **THEN** the viewport file is not written or overwritten with the default viewport
- **THEN** the persisted viewport file still contains the previously saved center and magnification

#### Scenario: Save under the map's key

- **WHEN** the map whose database path ends in `/data/maps/germany.osm` is displayed and its viewport is saved
- **THEN** the file `filesDir/maps/viewport-germany.osm.json` is written

#### Scenario: Characters outside the safe set are escaped

- **WHEN** the displayed map's database file name is `Europe map.osm` and its viewport is saved
- **THEN** the file `filesDir/maps/viewport-Europe_map.osm.json` is written
- **THEN** no file whose name carries the space is written

#### Scenario: Save without a map key is skipped

- **WHEN** a viewport save runs while no map key is known
- **THEN** no viewport file is written

### Requirement: Load viewport state

The system SHALL load the viewport state from `filesDir/maps/viewport-<key>.json` when the map screen starts, with `<key>` the map database path's last segment, escaped the same way as on save.

- If the file exists and contains valid JSON, the system SHALL use its values as the initial viewport
- If the file does not exist or is malformed, the system SHALL use a default viewport (Dortmund: lat=51.5136, lon=7.4653, magnification=8)
- Loading SHALL run on `Dispatchers.IO`
- Files written by older versions with integer magnification SHALL load unchanged (an integer is a valid fractional value)

#### Scenario: Load saved state on startup

- **WHEN** `viewport-<key>.json` exists with valid data
- **THEN** the map renders at the saved center and magnification

#### Scenario: Load legacy integer magnification

- **GIVEN** a `viewport-<key>.json` written by an older version contains `magnification: 14`
- **WHEN** the map screen starts
- **THEN** the map renders at magnification 14.0

#### Scenario: No saved state uses default

- **WHEN** `viewport-<key>.json` does not exist
- **THEN** the map renders at default center (Dortmund: 51.5136, 7.4653) and magnification 8

#### Scenario: Corrupted file uses default

- **WHEN** `viewport-<key>.json` contains invalid JSON
- **THEN** the system logs a warning
- **THEN** the map renders at default center (Dortmund: 51.5136, 7.4653) and magnification 8
