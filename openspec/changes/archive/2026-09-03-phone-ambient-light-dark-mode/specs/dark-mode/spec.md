## MODIFIED Requirements

### Requirement: Automatic mode follows environment dimming

When the preference is **Automatic**, the system SHALL resolve dark presentation from the environment. The system SHALL use the system night mode as the default environment signal. The system SHALL support additional environment signal sources (such as car environment dimming or the ambient light sensor) behind a single resolution path without changing user-visible behavior. When the ambient light sensor option is enabled, the system SHALL use the sensor's light/dark classification as the environment signal instead of the system night mode.

#### Scenario: System night mode active resolves dark

- **WHEN** the preference is **Automatic**, the ambient light sensor option is disabled, and the system night mode is active
- **THEN** dark presentation is active

#### Scenario: System night mode inactive resolves light

- **WHEN** the preference is **Automatic**, the ambient light sensor option is disabled, and the system night mode is inactive
- **THEN** dark presentation is inactive

#### Scenario: Sensor dark resolves dark

- **WHEN** the preference is **Automatic**, the ambient light sensor option is enabled, and the sensor classifies the surroundings as dark
- **THEN** dark presentation is active

#### Scenario: Sensor light resolves light

- **WHEN** the preference is **Automatic**, the ambient light sensor option is enabled, and the sensor classifies the surroundings as light
- **THEN** dark presentation is inactive

#### Scenario: Environment signal changes while app is running

- **WHEN** the preference is **Automatic** and the active environment signal changes (system night mode or sensor classification)
- **THEN** the app applies the new presentation without requiring a restart

#### Scenario: Sensor unavailable falls back to system

- **WHEN** the ambient light sensor option is enabled but no light sensor exists on the device
- **THEN** the system night mode is used as the environment signal

## ADDED Requirements

### Requirement: Ambient light sensor option

The system SHALL expose a persisted config option that enables the ambient light sensor as the environment signal for **Automatic** mode. The option SHALL default to disabled. The option SHALL be visible in the dark mode settings section and SHALL apply immediately when toggled. The sensor SHALL classify the surroundings with hysteresis (a lower threshold to enter dark, a higher threshold to leave it) and SHALL debounce rapid changes so the presentation does not flap near the threshold. Sensor reading SHALL only be active when the preference is **Automatic** and the option is enabled.

#### Scenario: Option defaults to disabled

- **WHEN** a user installs and launches the app without changing settings
- **THEN** the ambient light sensor option is disabled and **Automatic** mode follows the system night mode

#### Scenario: Option persists across restarts

- **WHEN** a user enables the option and restarts the app
- **THEN** the option is still enabled

#### Scenario: Toggle applies immediately

- **WHEN** the user enables the option while the preference is **Automatic**
- **THEN** the environment signal switches to the sensor classification immediately

#### Scenario: No flapping near the threshold

- **WHEN** the ambient light hovers around the dark threshold
- **THEN** the presentation does not alternate repeatedly between dark and light

#### Scenario: Old settings files remain valid

- **WHEN** a user upgrades from a version whose persisted settings contain no ambient light option field
- **THEN** the option defaults to disabled and no other settings are lost
