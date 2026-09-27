# Spec Delta

App-visible behaviour only. The client-side contract (safe configuration, never painting a rejected
configuration, per-database load outcome) is specified in the libosmscout repository
(`openspec/changes/client-style-load-resilience`, capability `client-java-style-switching`).

## MODIFIED Requirements

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

## ADDED Requirements

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
