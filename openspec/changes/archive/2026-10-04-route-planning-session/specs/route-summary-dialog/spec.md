# Spec Delta

## REMOVED Requirements

### Requirement: Route summary dialog via Show Route action
**Reason**: The session has no second surface any more. On the phone the overlay is a fixed two-state bottom card without a step list (spec `route-analysis` — the step navigator moves through the steps), and in a docked wide layout the step list is inline in the panel. A "Show Route" dialog had nothing left to show but a duplicate list, and on the device it was the surface that did not reach the screen bottom and whose rows were not clickable (2026-10-03 device findings).
**Migration**: The route statistics stay inline in the session card below the calculate button. Move through the steps with the step navigator; a docked wide layout shows the whole list inline.

### Requirement: Dialog dismiss returns to route panel
**Reason**: Removed with the dialog. There is no second surface whose dismissal could preserve or end the session.
**Migration**: Dismissing the session card still ends the session (spec `route-panel-ui` — Session overlay dismissal ends the session).

## MODIFIED Requirements

### Requirement: Route summary dialog shown after calculation
When a route calculation completes successfully, the route summary SHALL be shown inline in the session overlay, below the calculate button. The session SHALL remain open. No dialog and no other second surface SHALL open: the summary is part of the session card.

#### Scenario: Dialog appears after successful calculation
- **WHEN** route calculation completes successfully
- **THEN** the route summary SHALL be shown inline in the session overlay below the calculate button
- **AND** the session SHALL remain open
- **AND** no dialog SHALL be shown

#### Scenario: Dialog not shown on calculation failure
- **WHEN** route calculation fails
- **THEN** the route summary SHALL NOT be shown
- **AND** an error message SHALL be displayed in the session overlay
