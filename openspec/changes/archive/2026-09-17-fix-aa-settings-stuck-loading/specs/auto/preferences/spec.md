## ADDED Requirements

### Requirement: Settings screens never wedge on a loading placeholder
The system SHALL ensure the preferences screen and the picker screens opened from it (overspeed delta, vehicle position) never remain stuck on a loading placeholder: loaded content replaces the placeholder once available; a failed or dropped template refresh is recovered without user interaction; a settings load failure shows an explicit error with retry; and the screen re-renders its current state when the user returns to it.

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
