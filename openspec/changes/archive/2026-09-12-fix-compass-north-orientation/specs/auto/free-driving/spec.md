## Purpose

Free-driving mode for Android Auto: a destination-free navigation-style view with a live heading-up map, GPS position marker, compass, and current street name, entered from the map menu.

## ADDED Requirements

### Requirement: Free-driving compass rose points at true north
The free-driving compass rose SHALL draw its north pointer at the screen direction of true north under the heading-up map rotation, using the same convention as the navigation rose (`auto-map-layout`): with heading-up rotation (map angle = −bearing), the north pointer SHALL sit at `360 − bearing` degrees clockwise from screen-up.

#### Scenario: Rose centered above speed readout
- **WHEN** the free-driving view is visible
- **THEN** the compass rose drawn on the map surface is horizontally centered above the speed readout, with a gap from the right map edge

#### Scenario: Westbound free driving, north on the driver's right
- **WHEN** the free-driving view is visible with heading-up rotation
- **AND** the vehicle heading is 270° (driving west), map angle = −270° ≡ +90°
- **THEN** the rose north pointer SHALL point 90° clockwise from screen-up — the driver's right, where true north is

#### Scenario: Eastbound free driving, north on the driver's left
- **WHEN** the free-driving view is visible with heading-up rotation
- **AND** the vehicle heading is 90° (driving east), map angle = −90° ≡ +270°
- **THEN** the rose north pointer SHALL point 270° clockwise from screen-up — the driver's left
