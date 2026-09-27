# Spec Delta

## MODIFIED Requirements

### Requirement: Dark presentation applies to UI controls

When dark presentation is active, the system SHALL render all app controls (buttons, sheets, menus, dialogs) with the dark color scheme. When it is inactive, the system SHALL render them with the light color scheme. A control that carries status through a fixed hue family (for example the compass GPS-fix fill) SHALL dim in dark presentation by using a dark tone of its own hue family instead of the Material dark scheme's role for that hue, and SHALL NOT keep a light-tone status fill while dark presentation is active.

#### Scenario: Controls darken in dark presentation

- **WHEN** dark presentation becomes active while the user is on the map screen
- **THEN** all visible app controls are rendered with the dark color scheme

#### Scenario: Controls lighten back

- **WHEN** dark presentation becomes inactive
- **THEN** all visible app controls are rendered with the light color scheme

#### Scenario: Status-carrying control dims with its own palette

- **WHEN** dark presentation becomes active and a control shows status through a fixed hue family
- **THEN** that control SHALL switch to a dark tone of its hue family
- **AND** no light-tone status fill SHALL remain visible

#### Scenario: Status stays recognizable in both presentations

- **WHEN** a status-carrying control is rendered in either presentation
- **THEN** the status SHALL remain distinguishable from the other states of the same control
- **AND** any text or symbol drawn on the status color SHALL remain readable against it
