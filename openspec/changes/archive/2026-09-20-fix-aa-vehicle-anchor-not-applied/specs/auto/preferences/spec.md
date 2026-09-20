# Spec Delta

## MODIFIED Requirements

### Requirement: Preferences displayed with current values
The system SHALL display each supported preference with its current value.

#### Scenario: Current values shown
- **WHEN** the Preferences screen is displayed
- **THEN** each supported preference row shows its current value (e.g., "Follow mode: On")

#### Scenario: Row reflects picker selection on pop-back
- **WHEN** the driver selects a vehicle position preset in the anchor picker opened from the Preferences screen
- **AND** the picker pops back to the Preferences screen
- **THEN** the vehicle position row displays the newly selected anchor
- **AND** the row does NOT show the pre-selection value

### Requirement: Settings screens never wedge on a loading placeholder
The system SHALL ensure the preferences screen and the picker screens opened from it (overspeed delta, vehicle position) never remain stuck on a loading placeholder: loaded content replaces the placeholder once available; a failed or dropped template refresh is recovered without user interaction; a settings load failure shows an explicit error with retry; and when the user returns to a settings screen it SHALL re-read the persisted settings before rendering, so a value changed in a pushed picker appears on the row instead of a stale snapshot from the previous visit.

#### Scenario: Content appears once loaded
- **WHEN** a settings screen opens and the settings load completes
- **THEN** the screen displays the preference rows with their current values instead of the loading placeholder

#### Scenario: Dropped template refresh is recovered
- **WHEN** the host fails or drops the first screen-refresh request
- **THEN** the screen automatically issues a further refresh and displays its content within a short recovery window, without any user interaction

#### Scenario: Settings load failure shows an error
- **WHEN** loading the settings fails on a settings screen
- **THEN** the screen shows an explicit error row offering retry, and the back navigation still works

#### Scenario: Screen re-renders on return
- **WHEN** the user leaves a settings screen and later returns to it
- **THEN** the screen renders its current state, not a stale loading placeholder

#### Scenario: Re-read on re-visibility
- **WHEN** the user returns to a settings screen after a value was changed in a pushed picker (e.g. the vehicle anchor picker)
- **THEN** the screen re-reads the persisted settings before rendering
- **AND** the affected row shows the changed value, not the snapshot from the previous visit
