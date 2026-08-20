## MODIFIED Requirements

### Requirement: Object lookup by coordinate

The system SHALL query the map database for OSM objects (nodes, ways, areas) within a small bounding box around the pressed coordinate. Candidates SHALL be ranked by: (1) has DescriptionService data available, (2) visible at current zoom level, (3) proximity to press point. At equal distance, nodes SHALL be preferred over ways, ways over areas. The lookup SHALL return the full ranked candidate list, not a single best object.

#### Scenario: Objects found near press point
- **WHEN** user long-presses at a location with nearby OSM objects
- **THEN** the system SHALL query objects in a bounding box around the coordinate
- **AND** rank candidates using the selection algorithm
- **AND** return the ranked candidate list

#### Scenario: No objects found
- **WHEN** user long-presses at a location with no nearby OSM objects
- **THEN** the system SHALL return an empty candidate list
- **AND** no candidate picker SHALL open

#### Scenario: Object has description data
- **WHEN** a candidate object has DescriptionService data
- **THEN** the system SHALL call `DescriptionService::GetDescription()` on the object
- **AND** include the structured `ObjectDescription` with entries organized by section in the candidate list

### Requirement: Candidate ranking algorithm

The ranking algorithm SHALL use the following priority-ordered rules with exact constants:

1. **Has DescriptionService data** — objects with non-empty description entries are always preferred over objects without data
2. **Very close way/node (< 5m)** — a way or node within 5 meters of the press point beats any containing area, regardless of area size
3. **Small area contains press point** — an area smaller than 10,000 m² that contains the press point is preferred over non-containing objects
4. **Smaller containing area** — among containing areas, the smaller one wins
5. **Visibility at current zoom** — types with `optimizeLowZoom` flag score higher at low zoom (magnification ≤ 12); types without the flag score higher at high zoom (magnification ≥ 8). Internal types score 0 (excluded).
6. **Type rank** — nodes (rank 2) preferred over ways (rank 1) preferred over areas (rank 0)
7. **Closest distance** — tiebreaker by proximity to press point

The search radius SHALL be 50 meters. The `getDescriptionCandidates()` native method SHALL accept a `magnification` parameter. Each candidate `ObjectDescription` SHALL include the object's center coordinates (`objectLat`, `objectLon`) and identity (`objectRefType`, `objectTypeName`, `objectFileOffset`).

#### Scenario: Object with data beats object without data
- **WHEN** two objects are at equal distance from the press point
- **AND** one has DescriptionService data and the other does not
- **THEN** the object with data SHALL be ranked first

#### Scenario: Nearby way/node beats containing area
- **WHEN** a way or node is within 5 meters of the press point
- **AND** a large area contains the press point
- **THEN** the way/node SHALL be ranked before the containing area

#### Scenario: Small containing area beats non-containing
- **WHEN** a small area (< 10,000 m²) contains the press point
- **AND** a node is farther than 5 meters away
- **THEN** the containing area SHALL be ranked before the non-containing node

#### Scenario: Visible-at-zoom beats invisible-at-zoom
- **WHEN** two objects have equal data/contains/very-close status
- **AND** one has better visibility score at the current magnification
- **THEN** the object with better visibility SHALL be ranked first

#### Scenario: Marker placed at object center
- **WHEN** a long-press returns a non-empty ObjectDescription
- **THEN** the marker SHALL be rendered at `objectLat`/`objectLon` from the description
- **AND** the map SHALL center on those coordinates

#### Scenario: Fallback to press point
- **WHEN** the ObjectDescription has NaN coordinates (no object found)
- **THEN** the marker SHALL be rendered at the original press point

### Requirement: DescriptionService integration via JNI

The JNI bridge SHALL implement `getDescription(lat, lon, magnification)` (single best object, kept for search/POI/favorites callers) and `getDescriptionCandidates(lat, lon, magnification)` (full ranked candidate list) in `OSMScoutClient.cpp`. Both SHALL use `DBThread` to access the database, query objects in a bounding box, rank candidates, and call `DescriptionService::GetDescription()`. The candidates variant SHALL marshal the result as a Java `List<ObjectDescription>`.

#### Scenario: JNI getDescription returns ObjectDescription
- **WHEN** `OSMScoutClient.getDescription(lat, lon)` is called from Kotlin
- **THEN** the JNI implementation SHALL execute on a background thread
- **AND** return a non-null `ObjectDescription` (may be empty if no object found)
- **AND** each `DescriptionEntry` SHALL have sectionKey, subsectionKey, labelKey, and value populated

#### Scenario: JNI getDescriptionCandidates returns candidate list
- **WHEN** `OSMScoutClient.getDescriptionCandidates(lat, lon, magnification)` is called from Kotlin
- **THEN** the JNI implementation SHALL execute on a background thread
- **AND** return a non-null list of `ObjectDescription` (may be empty if no object found)
- **AND** each `ObjectDescription` SHALL carry `objectRefType`, `objectTypeName`, and `objectFileOffset` identity fields

#### Scenario: Description entries preserve section structure
- **WHEN** an object has multiple description sections (e.g., General, Location, Contact)
- **THEN** entries SHALL be ordered by section as returned by `DescriptionService`
- **AND** each entry SHALL carry its sectionKey for UI grouping
