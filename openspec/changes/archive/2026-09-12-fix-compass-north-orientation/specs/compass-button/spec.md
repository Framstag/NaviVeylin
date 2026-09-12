## Purpose

Provides a visual compass widget on the map screen that shows north direction, indicates GPS fix quality via the button fill color, and lets users toggle map orientation mode or re-center on their location.

## ADDED Requirements

### Requirement: North pointer points at rendered north
The north pointer (the phone compass needle in "always north" mode; the Android Auto compass rose in free driving and navigation) SHALL point at the screen position where north actually renders on the map, using the same rotation convention as the map projection: north's screen direction is `screenBearing(0°, mapAngle)`, i.e. the viewport angle measured clockwise from screen-up (0° = straight up). In heading-up follow mode (map angle = −bearing), the north pointer SHALL sit at `360 − bearing` degrees (mod 360), so the pointer and the map agree in every rotation state — a 180° sign flip is a defect. In the phone's follow-direction mode no north needle is drawn (the travel-direction triangle replaces it, see "Follow-direction triangle shows travel direction"); the north direction stays visible there only on Android Auto via the compass rose.

#### Scenario: North-up mode keeps needle up
- **WHEN** "always north" orientation is active on the phone (map angle = 0)
- **THEN** the compass needle SHALL point straight up (0°)

#### Scenario: Westbound heading, north on the driver's right
- **WHEN** a north pointer is visible (Android Auto compass rose) with heading-up rotation
- **AND** the vehicle heading is 270° (driving west), so the map is rotated heading-up (map angle = −270° ≡ +90°)
- **THEN** the north pointer SHALL point 90° clockwise from screen-up — to the driver's right, where true north is
- **AND** the pointer SHALL NOT point 180° from that position (south)

#### Scenario: Eastbound heading, north on the driver's left
- **WHEN** a north pointer is visible (Android Auto compass rose) with heading-up rotation
- **AND** the vehicle heading is 90° (driving east), map angle = −90° ≡ +270°
- **THEN** the north pointer SHALL point 270° clockwise from screen-up — to the driver's left, where true north is

### Requirement: Follow-direction triangle shows travel direction
In "follow direction" mode the phone compass triangle SHALL point at the screen direction of travel, computed as `screenBearing(bearing, mapAngle)` — straight up while heading-up follow is active, and following the travel direction after the user manually rotates the map. It SHALL be independent of the north pointer's rotation sign.

#### Scenario: Triangle points up while heading-up
- **WHEN** follow-direction mode is active with heading-up rotation
- **THEN** the compass triangle SHALL point straight up (0° on screen), the same screen direction as the travel direction on the rotated map

#### Scenario: Triangle follows travel direction after manual rotation
- **WHEN** the user manually rotates the map in follow-direction mode so the map angle is no longer −bearing
- **THEN** the compass triangle SHALL point at the travel direction's screen position (`bearing + mapAngle`, mod 360)
