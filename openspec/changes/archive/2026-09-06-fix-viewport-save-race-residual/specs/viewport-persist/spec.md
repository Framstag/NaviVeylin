## MODIFIED Requirements

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
