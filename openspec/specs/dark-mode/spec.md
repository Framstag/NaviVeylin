# dark-mode Specification

## Purpose

Lets the user control whether the app uses dark presentation — dark UI controls and a dark map style sheet — with automatic switching based on the environment (system night mode today, car dimming later).

## Requirements

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

### Requirement: Android Auto template chrome is host-owned and host-themed

On Android Auto (projection and Android Automotive OS), the system SHALL NOT theme, color, or otherwise override the host-rendered template chrome (menu, dialog, list, and panel backgrounds; navigation banner; ETA card; headers). Template chrome day/night SHALL follow the host's own display theme control (`Android Auto > Settings > Display > Theme`, or the head-unit/AAOS system theme), which the app cannot influence. App-side color control SHALL be limited to the app-drawn map surface (the `daylight` stylesheet flag driven by the host's day/night state) and, optionally, per-element custom colors on `Action` elements (`CarColor.createCustom(light, dark)`).

#### Scenario: Host chrome switches theme without app code

- **WHEN** the host display theme is set to **Light** (or the head unit reports a light automotive theme)
- **THEN** the host renders template chrome (menus, dialogs, lists, panels, navigation banner) in a light scheme with no app-side change

#### Scenario: Host chrome stays dark

- **WHEN** the host display theme is set to **Dark** (or the head unit's Automotive App Host theme is dark-pinned)
- **THEN** template chrome remains dark regardless of any app-side attempt, and the app SHALL rely on the host setting (or host rollout) instead of overriding

#### Scenario: Map surface still switches independently

- **WHEN** the carrier host reports day state (e.g., `onCarConfigurationChanged` with night UI mode unset)
- **THEN** the app-drawn map surface SHALL render the daylight stylesheet variant (daylight flag set) even while chrome follows the host theme

#### Scenario: Day/night preference does not leak into host chrome

- **WHEN** the user changes the app's dark mode preference (On / Off / Automatic)
- **THEN** only the app-drawn surface and app controls are affected; host template chrome is unchanged
