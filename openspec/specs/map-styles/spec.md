# Map Styles Specification

## Purpose

Lets users choose which map stylesheet the renderer uses, from every top-level
stylesheet bundled with the app, on both the phone and Android Auto surfaces.

## Requirements

### Requirement: All bundled styles are selectable

The app SHALL offer every top-level `*.oss` stylesheet bundled with the application as a selectable map style, in addition to `standard.oss`, except `basemap-render.oss` which is the basemap's internal stylesheet and SHALL NOT be offered as a user-selectable map style. The selection list MUST be derived from the stylesheets actually present in the bundled application assets, so a future submodule stylesheet addition is picked up without app changes.

#### Scenario: All bundled styles listed

- **WHEN** the user opens the map style picker
- **THEN** the picker lists every top-level `*.oss` stylesheet bundled with the app, including `standard.oss`

#### Scenario: Basemap stylesheet not offered

- **WHEN** the user opens the map style picker
- **THEN** `basemap-render` is not listed as a selectable style

#### Scenario: New upstream stylesheet appears

- **WHEN** a new top-level `*.oss` file is added to the bundled stylesheets and the app is updated
- **THEN** the picker lists the new style without any app code change

### Requirement: Style display name is file name without postfix
Each selectable style SHALL be shown with the stylesheet file name without the
`.oss` postfix (e.g. `cycle.oss` is shown as `cycle`).

#### Scenario: Display name derived from file name
- **WHEN** the picker shows the style for the file `winter-sports.oss`
- **THEN** the displayed name is `winter-sports`

### Requirement: Style selection is persisted
The selected map style SHALL be persisted across app restarts. The default
selection SHALL be `standard`.

#### Scenario: Selection survives restart
- **WHEN** the user selects `cycle` as the map style and restarts the app
- **THEN** `cycle` remains the selected map style

#### Scenario: Default on first start
- **WHEN** the user has never changed the map style
- **THEN** the selected map style is `standard`

### Requirement: Phone settings entry
The phone app SHALL provide a map style picker in its settings. Selecting a
style in the picker SHALL switch the map rendering to that style immediately.

#### Scenario: Switching style from phone settings
- **WHEN** the user selects `motorways` in the phone settings picker
- **THEN** the map re-renders using the `motorways` stylesheet

#### Scenario: Switch is applied immediately
- **WHEN** the user changes the style in the phone settings picker
- **THEN** the change takes effect on the visible map without an app restart

### Requirement: Android Auto settings entry
The Android Auto app SHALL provide a map style picker in its settings. The
selection SHALL be shared with the phone app through the common persisted
setting.

#### Scenario: Switching style from Android Auto settings
- **WHEN** the user selects `winter-sports` in the Android Auto settings picker
- **THEN** the car map re-renders using the `winter-sports` stylesheet

#### Scenario: Phone selection applies in the car
- **WHEN** the user selected `cycle` on the phone and later starts an Android Auto navigation session
- **THEN** the car map renders with the `cycle` stylesheet

### Requirement: Persisted style applied on start
On app start, the app SHALL apply the persisted map style to the renderer
before or as part of the first map display.

#### Scenario: Applied style at startup
- **WHEN** the app starts with `winter-sports` as the persisted selection
- **THEN** the first map display renders with the `winter-sports` stylesheet

### Requirement: Failed style switch keeps previous style
If a stylesheet cannot be loaded — the interactive style switch, the persisted style applied at app start, a style-flag change (e.g. `daylight`) or the on-device stylesheet refresh — the style that was active before the attempt SHALL remain the style in effect for the user, and the app SHALL report the failure: a non-blocking message and a `DiagnosticsLog` entry, naming the style that is active after the attempt. The report SHALL be the same wording on the phone and on the car surfaces.

#### Scenario: Unparsable stylesheet keeps current style
- **WHEN** the renderer fails to parse the newly requested stylesheet
- **THEN** the map keeps rendering with the previously active style

#### Scenario: Failure is visible
- **WHEN** a style switch fails to load
- **THEN** the app reports the failure through its error reporting channel

#### Scenario: Persisted style fails at startup
- **WHEN** the app starts with a persisted style whose stylesheet fails to parse
- **THEN** the map renders with the default `standard` style and the failure is reported

#### Scenario: Style flag change fails
- **WHEN** a style flag change (daylight/night) makes the active stylesheet fail to load
- **THEN** the previously active style stays in effect and the failure is reported

#### Scenario: Failure is reported once per attempt
- **WHEN** a stylesheet load fails on any of the paths above
- **THEN** exactly one failure report reaches the app's error reporting channel and the diagnostics log

### Requirement: Style load failure is visible on both surfaces
The failure SHALL be reported with the same wording on the phone and on the car surfaces, and the report SHALL be non-blocking: a message, never a dialog that interrupts navigation guidance. Platform-specific placement of the message is allowed.

#### Scenario: Phone reports the failure
- **WHEN** a stylesheet fails to load while the phone map is visible
- **THEN** the app shows a non-blocking message and writes a diagnostics log entry

#### Scenario: Car reports the failure
- **WHEN** a stylesheet fails to load during a car session
- **THEN** the car session surfaces the same wording and navigation guidance continues uninterrupted

#### Scenario: Failure without a visible map
- **WHEN** a stylesheet fails to load while no map surface is visible (app in the background)
- **THEN** the failure is recorded in the diagnostics log and the message is shown at the next opportunity the map is visible
