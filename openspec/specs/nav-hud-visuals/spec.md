# nav-hud-visuals Specification

## Purpose

Defines the visual presentation of the navigation HUD during active guidance — road name display, instruction sizing, ETA prominence, and stop button compactness.

## Requirements

### Requirement: Road name excludes type

The navigation status view SHALL display the current road name without the road type (e.g., "motorway", "primary", "residential").

#### Scenario: Road has ref and name but no type
- **WHEN** the vehicle is on a road with ref "A40" and name "Ruhrschnellweg" but typeName is empty
- **THEN** the road label SHALL show "A40 Ruhrschnellweg"

#### Scenario: Road has ref, type, and name
- **WHEN** the vehicle is on a road with ref "B1", typeName "primary", and name "Hauptstrasse"
- **THEN** the road label SHALL show "B1 Hauptstrasse" (typeName omitted)

#### Scenario: Road has only name
- **WHEN** the vehicle is on a road with only name "Main Street" (ref and typeName empty)
- **THEN** the road label SHALL show "Main Street"

#### Scenario: Offroad
- **WHEN** the vehicle is offroad (no road info available)
- **THEN** the road label SHALL show "Offroad" (unchanged)

### Requirement: Navigation instruction text is larger

The turn description in the next-turn overlay SHALL use a larger font than the current `bodySmall`.

#### Scenario: Instruction with description
- **WHEN** a route instruction with description "Turn left onto Hauptstrasse" is displayed
- **THEN** the description text SHALL be rendered at `bodyLarge`

#### Scenario: Instruction distance
- **WHEN** a route instruction with distance "200 m" is displayed
- **THEN** the distance text SHALL remain at `titleLarge` with bold weight (unchanged)

### Requirement: ETA is primary stat with compact labels

The routing status view SHALL display ETA as the primary statistic. Label text SHALL be minimized or replaced with icons to save horizontal space.

#### Scenario: ETA displayed
- **WHEN** navigation is active and ETA is available
- **THEN** the ETA SHALL be shown prominently in the status row

#### Scenario: Compact labels
- **WHEN** the status row displays ETA, distance, and speed
- **THEN** each stat SHALL use a compact label (icon or short text) instead of full label text

### Requirement: Stop button uses icon only

The stop navigation button SHALL use an icon (X/cross) instead of a text label, reducing its width.

#### Scenario: Stop button appearance
- **WHEN** navigation is active
- **THEN** the stop button SHALL display a cross/X icon with no text label
- **AND** the button width SHALL be smaller than the current 72dp

#### Scenario: Stop button action
- **WHEN** the user taps the stop button
- **THEN** navigation SHALL stop (behavior unchanged)

### Requirement: Remaining time until arrival

The routing status view SHALL display the remaining travel time until arrival, computed from the ETA timestamp.

#### Scenario: Remaining time displayed
- **WHEN** navigation is active and ETA is available
- **THEN** the remaining time SHALL be shown as a formatted duration (e.g., "1h 23min" or "45 min")

#### Scenario: No ETA available
- **WHEN** navigation is active but ETA is not available
- **THEN** the remaining time SHALL show "--"
