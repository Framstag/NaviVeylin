# Spec Delta — map-canvas-screen

## MODIFIED Requirements

### Requirement: Navigation right column includes location options

During NAVIGATION, the right-side widget column SHALL include the location options button in addition to the compass, speed widget, and zoom controls, so the quick settings sheet is reachable mid-route.

#### Scenario: Location options button in navigation column

- **WHEN** the map state is NAVIGATION
- **THEN** the right-side widget column SHALL show the location options button
- **AND** tapping it SHALL open the quick settings sheet
- **Case**: none — the entry-point rows' cases are written in tasks.md group 2

## REMOVED Requirements

### Requirement: Menu extensible for future settings
**Reason**: The placeholder is replaced by the real entry. The menu now carries a working "Settings" entry
(`map-menu`), and the surface it opens is owned by `settings-surface`; a placeholder scenario describing a
disabled entry would contradict both.

**Migration**: The entry itself is asserted by `map-menu`, "Menu entries with leading icons" (scenario
"Settings entry"), and the surface's entry points and back handling by `settings-surface`, "Three entry
points, one surface at a time" and "The surface opens at the root and dismisses to the map".
