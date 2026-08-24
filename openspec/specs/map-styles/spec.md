# Map Styles Specification

## Purpose

Lets users choose which map stylesheet the renderer uses, from every top-level
stylesheet bundled with the app, on both the phone and Android Auto surfaces.

## Requirements

### Requirement: All bundled styles are selectable
The app SHALL offer every top-level `*.oss` stylesheet bundled with the
application as a selectable map style, in addition to `standard.oss`.
The selection list MUST be derived from the stylesheets actually present in
the bundled application assets, so a future submodule stylesheet addition is
picked up without app changes.

#### Scenario: All bundled styles listed
- **WHEN** the user opens the map style picker
- **THEN** the picker lists every top-level `*.oss` stylesheet bundled with the app, including `standard.oss`

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
If the renderer cannot load the requested stylesheet, the previously active
style SHALL remain in effect and the failure SHALL be surfaced (e.g. via log
or UI message).

#### Scenario: Unparsable stylesheet keeps current style
- **WHEN** the renderer fails to parse the newly requested stylesheet
- **THEN** the map keeps rendering with the previously active style

#### Scenario: Failure is visible
- **WHEN** a style switch fails to load
- **THEN** the app reports the failure through its error reporting channel
