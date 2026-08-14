## Purpose

Provides keyboard shortcuts for common map actions so power users can zoom and search without reaching for on-screen controls.

## ADDED Requirements

### Requirement: Zoom in via `+` key

The system SHALL zoom in by one magnification level when the `+` or `=` key is pressed while the map canvas has focus.

#### Scenario: Plus key zooms in

- **WHEN** the map canvas has keyboard focus
- **AND** the user presses the `+` key
- **THEN** the magnification level SHALL increase by 1
- **AND** the debounced render pipeline SHALL be triggered (200ms zoom debounce)

#### Scenario: Plus key disabled at max zoom

- **WHEN** magnification is at the maximum supported level
- **THEN** pressing `+` SHALL NOT change the magnification level

### Requirement: Zoom out via `-` key

The system SHALL zoom out by one magnification level when the `-` or `_` key is pressed while the map canvas has focus.

#### Scenario: Minus key zooms out

- **WHEN** the map canvas has keyboard focus
- **AND** the user presses the `-` key
- **THEN** the magnification level SHALL decrease by 1
- **AND** the debounced render pipeline SHALL be triggered (200ms zoom debounce)

#### Scenario: Minus key disabled at min zoom

- **WHEN** magnification is at the minimum supported level
- **THEN** pressing `-` SHALL NOT change the magnification level

### Requirement: Search via `/` key

The system SHALL open the search panel when the user presses the `/` key while the map canvas has focus.

#### Scenario: Slash key opens search

- **WHEN** the map canvas has keyboard focus
- **AND** the user presses the `/` key
- **THEN** the search panel SHALL open
- **AND** the search input SHALL be auto-focused

#### Scenario: Slash key when search is already open

- **WHEN** the search panel is already open
- **AND** the user presses the `/` key
- **THEN** the search input SHALL be focused
- **AND** any existing search text SHALL be selected
