# Delta: auto/browse

## ADDED Requirements

### Requirement: Current street name shown while browsing

The system SHALL display the name and ref of the current street on the browse map, derived from the bearing-aware road lookup at the GPS position (the street the vehicle is actually driving on, not the nearest address point), throttled to avoid a lookup on every GPS tick, updating when the vehicle changes roads. The label SHALL be drawn on the map surface as a separate view, horizontally centered, and SHALL follow the same placement rule as the free-driving label: opposite the vehicle anchor row — a bottom-row anchor preset (`fy = 0.9`) anchors at the top of the guaranteed-visible band with a small padding from the band's top edge; a top-row preset (`fy = 0.1`) anchors at the bottom of the band; a middle-row preset anchors at the bottom of the band. The band is the surface minus the chrome insets the host reports — the TOP inset is the host's currently-visible top edge (the real coverage; the stable-area top is the fallback when no visible rect is known), the BOTTOM inset is the surface height minus the stable-area bottom edge — falling back to the real surface edge when no insets are reported. The label SHALL NOT sit under an inset band and SHALL NOT be positioned at the band center; the top edge SHALL follow the host's currently-visible area so the label never floats mid-screen and never hides under chrome. Anchor rule is shared with the Android Auto free-driving label and the phone free-driving label (same rows, same labels — guideline parity rule).

The browse view SHALL use the shared free-driving anchor setting for the vehicle anchor row.

#### Scenario: Street name shown while browsing

- **WHEN** the browse map is visible, the vehicle is not navigating, and the GPS position is on a named street
- **THEN** the browse map shows the street name and ref horizontally centered as a surface label

#### Scenario: Street name updates on street change

- **WHEN** the vehicle moves onto a different named street while browsing
- **THEN** the displayed street name updates to the new street

#### Scenario: Ref shown with the street name

- **WHEN** the street at the GPS position has a ref tag
- **THEN** the label shows the ref together with the name (e.g. "B 1 Hauptstrasse")

#### Scenario: No street name when unnamed

- **WHEN** the GPS position is not on a named street while browsing
- **THEN** no street-name label is shown and no stale text from a previous street remains

#### Scenario: Street name at top for bottom-row anchors

- **WHEN** the browse follow anchor preset is in the bottom row and the GPS position is on a named street
- **THEN** the street name is displayed horizontally centered at the top of the view with a small padding from the top edge, so it never sits between the vehicle and the way ahead

#### Scenario: Street name at bottom for top-row anchors

- **WHEN** the browse follow anchor preset is in the top row
- **THEN** the street name is displayed horizontally centered at the bottom edge of the view

#### Scenario: Street name anchored to the real surface edge

- **WHEN** the browse map is visible and the host delivers no stable area (or one spanning the full surface)
- **THEN** the street-name label is anchored to the real surface edge with the fixed padding — host-area rects do not move it (no mid-screen floating label)

#### Scenario: Street name clear of the host/system chrome bands

- **WHEN** the browse map is visible and the host stable area excludes the top and/or bottom band
- **THEN** the street-name label sits inside the guaranteed-visible band — below the top inset with the fixed padding for a bottom-row preset, above the bottom inset with the fixed padding for a top/middle-row preset — never under an inset band
