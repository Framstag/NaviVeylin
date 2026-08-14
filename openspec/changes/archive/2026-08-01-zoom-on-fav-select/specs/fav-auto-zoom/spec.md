## Purpose

Automatically adjusts map zoom to a reasonable level when user selects a favorite, showing the full extent of area objects and using a fixed zoom for point objects.

## ADDED Requirements

### Requirement: Zoom to favorite on selection

When user selects a favorite from the favorites sheet, the map SHALL center on the favorite location and adjust zoom level based on the object type.

#### Scenario: Select node-type favorite
- **WHEN** user taps a favorite that corresponds to a node (point) object
- **THEN** map centers on the favorite location
- **AND** map magnification is set to a fixed level (e.g., 17)

#### Scenario: Select area-type favorite
- **WHEN** user taps a favorite that corresponds to an area object (building, park, etc.)
- **THEN** map centers on the object's centroid
- **AND** map magnification is computed to fit the object's bounding box within the viewport with padding

### Requirement: Object type detection

The system SHALL determine whether a favorite location corresponds to a node or area object before applying zoom.

#### Scenario: Object has bounding box
- **WHEN** the OSM object at the favorite location has a bounding box (area/way)
- **THEN** system treats it as an area for zoom calculation

#### Scenario: Object has no bounding box
- **WHEN** the OSM object at the favorite location has no bounding box (node)
- **THEN** system treats it as a node and uses fixed zoom level

#### Scenario: No object found at location
- **WHEN** no OSM object is found at the favorite coordinate
- **THEN** system uses fixed zoom level (node fallback)

### Requirement: Bounding box zoom calculation

For area objects, the system SHALL compute a magnification that fits the object's bounding box within the current viewport dimensions with configurable padding.

#### Scenario: Area fits viewport
- **WHEN** object bounding box is computed
- **THEN** magnification is calculated so the bounding box plus padding fills no more than 80% of the viewport

#### Scenario: Magnitude clamped to valid range
- **WHEN** computed magnification is outside valid range (4–18)
- **THEN** magnification is clamped to the nearest valid value

### Requirement: Zoom independent of current zoom

The zoom adjustment SHALL override the current zoom level regardless of how the user previously zoomed.

#### Scenario: User zoomed far out
- **WHEN** user is zoomed to magnification 8 and selects a favorite
- **THEN** zoom changes to the computed level (not kept at 8)

#### Scenario: User zoomed far in
- **WHEN** user is zoomed to magnification 18 and selects a favorite
- **THEN** zoom changes to the computed level (not kept at 18)
