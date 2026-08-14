## Purpose

Lets the user control whether the app uses dark presentation — dark UI controls and a dark map style sheet — with automatic switching based on the environment (system night mode today, car dimming later).

## ADDED Requirements

### Requirement: Three-state dark mode preference

The system SHALL expose a dark mode preference with exactly three states: **On**, **Off**, and **Automatic**. The preference SHALL default to **Automatic** and SHALL be persisted across app restarts.

#### Scenario: Default preference is automatic

- **WHEN** a user installs and launches the app without changing settings
- **THEN** the dark mode preference is **Automatic**

#### Scenario: Preference persists across restarts

- **WHEN** a user sets the preference to **On** and restarts the app
- **THEN** the preference is still **On** and the app starts in dark presentation

#### Scenario: Old settings files remain valid

- **WHEN** a user upgrades from a version whose persisted settings contain no dark mode field
- **THEN** the preference defaults to **Automatic** and no other settings are lost

### Requirement: Automatic mode follows environment dimming

When the preference is **Automatic**, the system SHALL resolve dark presentation from the environment. The system SHALL use the system night mode as the environment signal. The system SHALL support additional environment signal sources (such as car environment dimming) behind a single resolution path without changing user-visible behavior.

#### Scenario: System night mode active resolves dark

- **WHEN** the preference is **Automatic** and the system night mode is active
- **THEN** dark presentation is active

#### Scenario: System night mode inactive resolves light

- **WHEN** the preference is **Automatic** and the system night mode is inactive
- **THEN** dark presentation is inactive

#### Scenario: Environment signal changes while app is running

- **WHEN** the preference is **Automatic** and the environment signal changes (e.g. the system switches night mode on or off)
- **THEN** the app applies the new presentation without requiring a restart

### Requirement: Dark presentation applies to UI controls

When dark presentation is active, the system SHALL render all app controls (buttons, sheets, menus, dialogs) with the dark color scheme. When it is inactive, the system SHALL render them with the light color scheme.

#### Scenario: Controls darken in dark presentation

- **WHEN** dark presentation becomes active while the user is on the map screen
- **THEN** all visible app controls are rendered with the dark color scheme

#### Scenario: Controls lighten back

- **WHEN** dark presentation becomes inactive
- **THEN** all visible app controls are rendered with the light color scheme

### Requirement: Dark presentation applies to map rendering

When dark presentation is active, the system SHALL render the map using the style sheet's dark variant (the `daylight` flag unset). When it is inactive, the system SHALL render the map using the daylight variant. A change in presentation SHALL reload the map style and re-render the visible map without user interaction, and SHALL NOT show cached tiles or patterns from the previous variant.

#### Scenario: Map renders dark in dark presentation

- **WHEN** dark presentation becomes active with a map loaded
- **THEN** the visible map is re-rendered using the dark style sheet variant and shows no light-variant tiles

#### Scenario: Map renders light after switching back

- **WHEN** dark presentation becomes inactive
- **THEN** the visible map is re-rendered using the daylight style sheet variant and shows no dark-variant tiles

#### Scenario: Map darkens from the start

- **WHEN** a map database is opened while dark presentation is active
- **THEN** the first render uses the dark style sheet variant

### Requirement: Manual dark mode control

The system SHALL allow the user to change the dark mode preference via the settings control, which offers all three states (On, Off, Automatic). Changing the preference SHALL apply immediately to controls and map.

#### Scenario: Settings control switches to On

- **WHEN** the user sets the preference to **On** in settings
- **THEN** the preference is **On** and dark presentation is applied immediately

#### Scenario: Settings control switches to Off

- **WHEN** the user sets the preference to **Off** in settings
- **THEN** the preference is **Off** and dark presentation is applied immediately

#### Scenario: Settings control switches to Automatic

- **WHEN** the user sets the preference to **Automatic** in settings
- **THEN** the preference is **Automatic** and dark presentation follows the environment
