# roundabout-renderer Specification

## Purpose

Renders roundabout navigation symbols as Compose Canvas drawings that visually represent the roundabout geometry — showing the roundabout as a circle, exit positions at correct angles, and highlighting the exit the driver should take.

## Requirements

### Requirement: Roundabout rendered as circle with exits

The roundabout renderer SHALL draw the roundabout as a circle with exit markers positioned around its circumference. Exit count and positions SHALL be configurable.

#### Scenario: Roundabout with 3 exits
- **WHEN** the renderer receives a roundabout with 3 exits
- **THEN** it SHALL draw a circle with 3 exit markers evenly distributed around the circumference
- **AND** one exit SHALL be visually highlighted as the selected exit

#### Scenario: Roundabout with 4 exits
- **WHEN** the renderer receives a roundabout with 4 exits
- **THEN** it SHALL draw a circle with 4 exit markers evenly distributed

#### Scenario: Roundabout with 2 exits
- **WHEN** the renderer receives a roundabout with 2 exits
- **THEN** it SHALL draw a circle with 2 exit markers

#### Scenario: Roundabout with 6+ exits
- **WHEN** the renderer receives a roundabout with 6 or more exits
- **THEN** it SHALL draw a circle with all exit markers, scaling down proportionally to fit

### Requirement: Exit position reflects actual geometry

Exit positions on the roundabout SHALL be placed at angles that reflect the actual road geometry, not just evenly spaced. When angle data is available from `RouteInstruction`, exits SHALL be positioned accordingly.

#### Scenario: Exits at specific angles
- **WHEN** the renderer receives exit angle data (e.g., exit 1 at 45°, exit 2 at 180°, exit 3 at 315°)
- **THEN** exit markers SHALL be placed at those specific angles on the circle

#### Scenario: No angle data — evenly spaced fallback
- **WHEN** the renderer receives no angle data
- **THEN** exit markers SHALL be evenly distributed around the circle

### Requirement: Selected exit visually distinct

The exit the driver should take SHALL be visually emphasized — different color, size, or style from non-selected exits.

#### Scenario: Selected exit highlighted
- **WHEN** the renderer receives a selected exit index
- **THEN** that exit marker SHALL be drawn in the primary/accent color
- **AND** non-selected exits SHALL be drawn in a muted color

#### Scenario: No selected exit
- **WHEN** the renderer receives no selected exit index
- **THEN** all exit markers SHALL be drawn in the same style

### Requirement: Entry direction indicator

The roundabout renderer SHALL show the entry direction — where the driver enters the roundabout — as a distinct marker or gap on the circle.

#### Scenario: Entry direction shown
- **WHEN** the renderer receives entry angle data
- **THEN** the entry point SHALL be marked on the roundabout circle

### Requirement: Roundabout composable API

The renderer SHALL expose a `@Composable` function that accepts exit count, exit angles, selected exit index, entry angle, size, and color parameters.

#### Scenario: Composable renders roundabout
- **WHEN** the composable is called with exit count and selected exit
- **THEN** it SHALL draw the roundabout visualization

#### Scenario: Composable with all parameters
- **WHEN** the composable is called with exit count, exit angles, selected exit, entry angle, size, and color
- **THEN** it SHALL draw the full roundabout visualization with all data applied
