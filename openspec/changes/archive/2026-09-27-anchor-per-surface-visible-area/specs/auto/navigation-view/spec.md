## MODIFIED Requirements

### Requirement: Vehicle anchor during navigation

The system SHALL keep the vehicle marker on the navigation surface during follow mode at the configured routing anchor position instead of at the screen center. The anchor is one of 15 positions on a 5×3 grid (horizontal 10/30/50/70/90% of the surface width, vertical 10/50/90% of the surface height). The default routing anchor is center/center (50% width, 50% height), which reproduces the pre-feature framing exactly. To place the marker at the anchor, the map render target SHALL be shifted so the vehicle's geographic position projects to the anchor under the current viewport rotation — in heading-up AND north-up navigation.

#### Scenario: Default anchor reproduces today's framing

- **GIVEN** the routing anchor is at its default center/center
- **WHEN** navigation is active in follow mode
- **THEN** the vehicle marker projects to the center of the navigation surface
- **AND** the map framing is identical to navigation without anchor presets


#### Scenario: Road-ahead bias via bottom anchor

- **GIVEN** the user selected the bottom-center anchor (50% width, 90% height) for routing
- **WHEN** navigation is active in follow mode
- **THEN** the vehicle marker stays at the bottom-center anchor
- **AND** more of the map ahead of the vehicle is visible than behind it


#### Scenario: Panel clearance via side anchor

- **GIVEN** the host draws its route-status/turn-instruction UI over the left part of the surface
- **GIVEN** the user selected a right-side anchor (e.g. 70% or 90% width) for routing
- **WHEN** navigation is active in follow mode
- **THEN** the vehicle marker stays clear of the host UI region
- **AND** the visible map area on the open side is used for the road ahead


#### Scenario: Marker stays on anchor during heading-up rotation

- **WHEN** navigation is active with heading-up orientation and the vehicle bearing changes
- **THEN** the vehicle marker keeps projecting to the chosen anchor position
- **AND** the map content rotates about the display with the bearing


#### Scenario: Anchor restored after manual pan

- **WHEN** the user pans the map during navigation (follow suspended)
- **AND** the user then stops panning
- **THEN** follow mode re-engages and the vehicle marker returns to the routing anchor without a snap


#### Scenario: Host panel clearance clamps only the covered presets

- **GIVEN** the host draws its route-status/turn UI over the leading 40% of the surface width
- **WHEN** the driver selects a preset whose fraction falls inside that band (e.g. 10% width)
- **THEN** the resolved anchor SHALL be just inside the visible strip, clear of the host UI
- **AND** a preset outside the band (including the default center/center) SHALL keep its exact fraction
- **AND** with an RTL layout the mirrored (trailing) band SHALL clamp the corresponding opposite-side presets


#### Scenario: Bottom chrome clamps the vehicle above it

- **GIVEN** the host overlays the bottom of the map surface with control chrome (e.g. the AAOS bottom bar) beyond the surface it grants
- **WHEN** the routing anchor preset is in the bottom row and the anchor fraction would land under that chrome
- **THEN** the resolved anchor SHALL move up so the vehicle stays inside the host-guaranteed visible area
- **AND** presets not under the chrome SHALL keep their exact fraction


#### Scenario: Surface uses its own routing anchor

- **GIVEN** the phone's routing anchor differs from Android Auto's routing anchor
- **WHEN** navigation runs on Android Auto
- **THEN** the Android Auto routing anchor SHALL frame the map
- **AND** the phone's value SHALL NOT be applied
