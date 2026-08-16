# long-press-details Specification

## Purpose

Lets users long-press any location on the map to resolve the closest relevant OSM object and retrieve a structured description with type, name, address, contact, and other properties.

## Requirements

### Requirement: Long-press gesture detection
The map canvas SHALL detect a long-press gesture: user presses and holds for 500ms without dragging. On detection, the screen coordinates SHALL be converted to geographic coordinates (latitude, longitude) using the current map projection and viewport, including the viewport rotation angle.

#### Scenario: Long press fires after 500ms hold
- **WHEN** user presses on the map canvas and holds for 500ms without moving
- **THEN** the system SHALL fire a long-press callback with the geographic coordinates of the press point

#### Scenario: Long press in rotated viewport
- **WHEN** user long-presses on the map canvas while the viewport is rotated by a non-zero angle
- **THEN** the system SHALL convert the press point to geographic coordinates using the angle-aware projection
- **AND** the resolved object SHALL be the one under the press point

#### Scenario: Long press in north-up viewport
- **WHEN** user long-presses on the map canvas while the viewport angle is zero
- **THEN** the system SHALL convert the press point to geographic coordinates using the standard north-up projection

#### Scenario: Drag cancels long press
- **WHEN** user presses on the map canvas
- **AND** moves the pointer more than 3px before 500ms elapses
- **THEN** the long-press timer SHALL be cancelled
- **AND** no long-press callback SHALL fire

#### Scenario: Release before timeout cancels long press
- **WHEN** user presses on the map canvas
- **AND** releases before 500ms elapses
- **THEN** the long-press timer SHALL be cancelled
- **AND** no long-press callback SHALL fire

### Requirement: Object lookup by coordinate
The system SHALL query the map database for OSM objects (nodes, ways, areas) within a small bounding box around the pressed coordinate. Candidates SHALL be ranked by: (1) has DescriptionService data available, (2) visible at current zoom level, (3) proximity to press point. At equal distance, nodes SHALL be preferred over ways, ways over areas.

#### Scenario: Objects found near press point
- **WHEN** user long-presses at a location with nearby OSM objects
- **THEN** the system SHALL query objects in a bounding box around the coordinate
- **AND** rank candidates using the selection algorithm
- **AND** return the best-matching object's description

#### Scenario: No objects found
- **WHEN** user long-presses at a location with no nearby OSM objects
- **THEN** the system SHALL return an empty description
- **AND** the details sheet SHALL NOT open

#### Scenario: Object has description data
- **WHEN** the best-matching object has DescriptionService data
- **THEN** the system SHALL call `DescriptionService::GetDescription()` on the object
- **AND** return a structured `ObjectDescription` with entries organized by section

### Requirement: Candidate ranking algorithm
The ranking algorithm SHALL use the following priority-ordered rules with exact constants:

1. **Has DescriptionService data** — objects with non-empty description entries are always preferred over objects without data
2. **Very close way/node (< 5m)** — a way or node within 5 meters of the press point beats any containing area, regardless of area size
3. **Small area contains press point** — an area smaller than 10,000 m² that contains the press point is preferred over non-containing objects
4. **Smaller containing area** — among containing areas, the smaller one wins
5. **Visibility at current zoom** — types with `optimizeLowZoom` flag score higher at low zoom (magnification ≤ 12); types without the flag score higher at high zoom (magnification ≥ 8). Internal types score 0 (excluded).
6. **Type rank** — nodes (rank 2) preferred over ways (rank 1) preferred over areas (rank 0)
7. **Closest distance** — tiebreaker by proximity to press point

The search radius SHALL be 50 meters. The `getDescription()` native method SHALL accept a `magnification` parameter. The `ObjectDescription` returned SHALL include the best object's center coordinates (`objectLat`, `objectLon`) so the marker can be placed on the object, not the press point.

#### Scenario: Object with data beats object without data
- **WHEN** two objects are at equal distance from the press point
- **AND** one has DescriptionService data and the other does not
- **THEN** the object with data SHALL be selected

#### Scenario: Nearby way/node beats containing area
- **WHEN** a way or node is within 5 meters of the press point
- **AND** a large area contains the press point
- **THEN** the way/node SHALL be selected over the containing area

#### Scenario: Small containing area beats non-containing
- **WHEN** a small area (< 10,000 m²) contains the press point
- **AND** a node is farther than 5 meters away
- **THEN** the containing area SHALL be selected over the non-containing node

#### Scenario: Visible-at-zoom beats invisible-at-zoom
- **WHEN** two objects have equal data/contains/very-close status
- **AND** one has better visibility score at the current magnification
- **THEN** the object with better visibility SHALL be selected

#### Scenario: Marker placed at object center
- **WHEN** a long-press returns a non-empty ObjectDescription
- **THEN** the marker SHALL be rendered at `objectLat`/`objectLon` from the description
- **AND** the map SHALL center on those coordinates

#### Scenario: Fallback to press point
- **WHEN** the ObjectDescription has NaN coordinates (no object found)
- **THEN** the marker SHALL be rendered at the original press point

### Requirement: DescriptionService integration via JNI
The JNI bridge SHALL implement `getDescription(lat, lon)` in `OSMScoutClient.cpp`. It SHALL use `DBThread` to access the database, query objects in a bounding box, rank candidates, call `DescriptionService::GetDescription()`, and marshal the result as a Java `ObjectDescription` containing a list of `DescriptionEntry` objects.

#### Scenario: JNI getDescription returns ObjectDescription
- **WHEN** `OSMScoutClient.getDescription(lat, lon)` is called from Kotlin
- **THEN** the JNI implementation SHALL execute on a background thread
- **AND** return a non-null `ObjectDescription` (may be empty if no object found)
- **AND** each `DescriptionEntry` SHALL have sectionKey, subsectionKey, labelKey, and value populated

#### Scenario: Description entries preserve section structure
- **WHEN** an object has multiple description sections (e.g., General, Location, Contact)
- **THEN** entries SHALL be ordered by section as returned by `DescriptionService`
- **AND** each entry SHALL carry its sectionKey for UI grouping
