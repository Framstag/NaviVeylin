# Delta — dark-mode

## MODIFIED Requirements

### Requirement: Ambient light sensor option

The system SHALL expose a persisted setting that controls the ambient light sensor as the environment signal for **Automatic** mode with exactly four states: **Off**, **High**, **Medium**, **Low**. **Off** disables the sensor (the system night mode remains the environment signal); **High**, **Medium**, **Low** enable the sensor at increasing levels of caution — **High** enters dark least reluctantly (current behavior), **Medium** and **Low** require progressively darker surroundings to enter dark and progressively brighter surroundings to leave it. The setting SHALL default to **Off** and SHALL apply immediately when changed. The sensor SHALL classify the surroundings with hysteresis (a lower threshold to enter dark, a higher threshold to leave it) and SHALL debounce rapid changes so the presentation does not flap near the threshold. Sensor reading SHALL only be active when the preference is **Automatic** and the sensitivity is not **Off**.

#### Scenario: Option defaults to disabled

- **WHEN** a user installs and launches the app without changing settings
- **THEN** the ambient light sensor sensitivity is **Off** (the option is disabled) and **Automatic** mode follows the system night mode

#### Scenario: Option persists across restarts

- **WHEN** a user sets a sensitivity level and restarts the app
- **THEN** the sensitivity is still set to that level

#### Scenario: Toggle applies immediately

- **WHEN** the user changes the sensitivity while the preference is **Automatic**
- **THEN** the environment signal switches to the sensor classification with the new level's thresholds immediately

#### Scenario: No flapping near the threshold

- **WHEN** the ambient light hovers around the dark threshold for the current sensitivity level
- **THEN** the presentation does not alternate repeatedly between dark and light

#### Scenario: Old settings files remain valid

- **WHEN** a user upgrades from a version whose persisted settings store the ambient light option as a boolean
- **THEN** an enabled boolean maps to **High**, a disabled or missing value maps to **Off**, and no other settings are lost

#### Scenario: Higher sensitivity enters dark earlier

- **WHEN** the sensitivity is **High** and the surroundings dim
- **THEN** dark presentation becomes active at a higher light level than it would under **Medium** or **Low**

#### Scenario: Lower sensitivity is more reluctant to enter dark

- **WHEN** the sensitivity is **Low** and the surroundings dim moderately
- **THEN** dark presentation remains inactive while **High** would have activated it, and only activates when the surroundings are markedly darker

#### Scenario: Off disables the sensor

- **WHEN** the sensitivity is **Off** and the surroundings are dark
- **THEN** dark presentation does not follow the ambient light sensor; it follows the system night mode

#### Scenario: Higher sensitivity still requires meaningful darkness

- **WHEN** the sensitivity is **High** and the surroundings are clearly lit
- **THEN** dark presentation does not activate
