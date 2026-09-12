## Purpose

Defines the layout and interaction model for the Android Auto map and navigation displays: the app menu lives in the template content slot, host action strips carry the map controls (search, settings, zoom), and navigation shows left-oriented hints plus right-edge visualisation indicators (compass rose, speed limit).

## ADDED Requirements

### Requirement: Compass rose north pointer direction
The system SHALL draw the navigation compass rose's north pointer at the screen direction of true north under the heading-up map rotation, using the same rotation convention as the map projection: with heading-up rotation (map angle = −bearing), the north pointer SHALL sit at `360 − bearing` degrees clockwise from screen-up. East/west headings must show north on the correct side — a 180° flip is a defect.

#### Scenario: North pointer faces true north
- **WHEN** navigation is active
- **THEN** the compass rose on the navigation display rotates so its north pointer faces true north
- **AND** the rose is right-aligned to the display edge, sized 56 dp

#### Scenario: Westbound navigation, north on the driver's right
- **WHEN** navigation is active with heading-up rotation
- **AND** the vehicle heading is 270° (driving west), map angle = −270° ≡ +90°
- **THEN** the rose north pointer SHALL point 90° clockwise from screen-up — the driver's right, where true north is
- **AND** the pointer SHALL NOT point 180° from that position

#### Scenario: Eastbound navigation, north on the driver's left
- **WHEN** navigation is active with heading-up rotation
- **AND** the vehicle heading is 90° (driving east), map angle = −90° ≡ +270°
- **THEN** the rose north pointer SHALL point 270° clockwise from screen-up — the driver's left
