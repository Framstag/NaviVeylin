# gps-location-marker — delta

Scope: the phone overlay only. The Android Auto marker is drawn on the host surface by the native
renderer (`auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt`), so a car-side cue is a separate
native change; that is the platform constraint behind this variant deviation (`guidelines/UI.md` §1,
"a documented deviation names its platform constraint").

## MODIFIED Requirements

### Requirement: Marker position tracks map viewport

The marker SHALL project the GPS coordinate to screen pixel coordinates using the current map viewport (center, zoom, rotation). The marker SHALL move correctly when the user pans or zooms the map.

In follow mode the marker SHALL ride the displayed (eased predicted) position, which is derived from the same position source that frames are rendered on (the engine-filtered position during route guidance, the raw GPS fix otherwise). When the display loop stops (speed below the movement threshold), the marker SHALL remain at the frozen displayed position instead of falling back to a raw-fix projection; on resume it SHALL continue from that position without snapping.

When the projected position falls outside the viewport — past the marker's own off-screen margin, so the arrow can no longer reach into the frame — the overlay SHALL draw no marker and exactly one cue, clamped to the viewport edge in the vehicle's direction and kept inside the inset bounds the map band reserves for its chrome, so the cue never sits under the action or widget columns of a phone or foldable-width frame.

#### Scenario: Marker moves during pan

- **WHEN** the user pans the map
- **THEN** the marker SHALL remain at the correct geographic position relative to map features
- **Case**: `app: LocationMarkerOverlayTest` "markerFollowsViewportCenterPan"

#### Scenario: Marker repositions on zoom

- **WHEN** the user zooms in or out
- **THEN** the marker SHALL re-project to the correct screen position at the new zoom level
- **Case**: none — pre-existing scenario, unchanged by this delta: the projection reads `viewport.mag` and no case varies the magnification.

#### Scenario: Marker hidden when off-screen

- **WHEN** the user's GPS position is outside the visible map viewport, past the marker's off-screen margin
- **THEN** the marker SHALL NOT be rendered inside the viewport
- **AND** a cue SHALL be drawn at the viewport edge in the vehicle's direction
- **Case**: `app: LocationMarkerEdgeCueTest` "theOffScreenVehicleDrawsACueAtTheEdge",
  `app: LocationMarkerEdgeCueTest` "theCueSitsOnTheEdgeTowardTheVehicle"

#### Scenario: Marker position is stable during map rotation

- **WHEN** the map rotates while the GPS position is visible
- **THEN** the marker SHALL stay at the same geographic location on screen
- **THEN** the direction arrow SHALL rotate to remain aligned with the direction of travel
- **Case**: `app: LocationMarkerOverlayTest` "markerRotatesWithMapRotation"

#### Scenario: Marker stays put at a brief stop

- **WHEN** the vehicle speed drops below the movement threshold (e.g., traffic light) while route guidance is active
- **THEN** the marker SHALL remain at the frozen displayed position
- **AND** the marker SHALL NOT re-project to the raw GPS fix at a different screen position
- **Case**: none — pre-existing scenario, unchanged by this delta; the frozen display position is a `smooth-follow` predicate and its cases own it.

#### Scenario: Marker resumes without a jump

- **WHEN** the vehicle accelerates after a brief stop and the display loop resumes
- **THEN** the marker SHALL continue from the frozen position along the predicted path
- **AND** the marker SHALL NOT snap back to an anchor or raw-fix position
- **Case**: none — pre-existing scenario, unchanged by this delta; the resume predicate belongs to `smooth-follow`'s cases.

#### Scenario: Off-screen vehicle draws one edge cue

- **WHEN** the GPS position projects outside the viewport and the overlay draws the frame
- **THEN** the frame SHALL carry exactly one cue, on the edge the vehicle lies beyond
- **AND** no marker arrow SHALL be drawn for that position
- **Case**: `app: LocationMarkerEdgeCueTest` "theOffScreenVehicleDrawsACueAtTheEdge",
  `app: LocationMarkerEdgeCueTest` "theCueSitsOnTheEdgeTowardTheVehicle"

#### Scenario: The cue clears the inset columns

- **WHEN** the map band reserves space for its right-side control column (phone, or foldable at the wider width) and the vehicle is off-screen to the east
- **THEN** the cue SHALL be drawn fully left of that reserved column
- **AND** the cue's own extent SHALL stay inside the frame
- **Case**: `app: LocationMarkerEdgeCueTest` "theCueClearsTheColumnInsetBounds"

#### Scenario: On-screen position draws no cue

- **WHEN** the GPS position projects inside the viewport
- **THEN** no cue SHALL be placed
- **AND** the marker SHALL be drawn as it is today
- **Case**: `app: LocationMarkerEdgeCueTest` "theOnScreenMarkerPlacesNoCue"
