## ADDED Requirements

### Requirement: Map follows host day/night

The system SHALL render the car map surface using the host's day/night state: the dark style sheet variant (the `daylight` flag unset) when the host reports night mode, and the daylight variant when the host reports day mode. The host's state SHALL be read from the car environment (`CarContext.isDarkMode()`), not from the phone's system night mode. A change in the host's day/night state while the car app is running SHALL re-render the visible map without user interaction and SHALL NOT show cached tiles or patterns from the previous variant.

#### Scenario: Map renders dark at night

- **WHEN** the car app starts while the host reports night mode
- **THEN** the map surface is rendered with the dark style sheet variant

#### Scenario: Map renders light during the day

- **WHEN** the car app starts while the host reports day mode
- **THEN** the map surface is rendered with the daylight style sheet variant

#### Scenario: Map switches live on host change

- **WHEN** the host changes its day/night state while the car app is running (e.g. entering a tunnel)
- **THEN** the map surface re-renders with the new variant without user interaction

#### Scenario: Map darkens from the start

- **WHEN** a map database is opened while the host reports night mode
- **THEN** the first render uses the dark style sheet variant

#### Scenario: Phone night mode does not drive the car map

- **WHEN** the phone's system night mode differs from the host's day/night state
- **THEN** the car map surface follows the host's state, not the phone's
