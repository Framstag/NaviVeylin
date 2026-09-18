## MODIFIED Requirements

### Requirement: Current street name shown

The system SHALL display the current street name and ref on the free-driving view, derived from the bearing-aware road lookup at the GPS position (the street the vehicle is actually driving on, not the nearest address point), horizontally centered, and SHALL anchor the label opposite the vehicle anchor row to the edge of the guaranteed-visible map band: the band is the surface minus the chrome insets the host reports — the TOP inset is the host's currently-visible top edge (the real coverage, so the label hugs the visible chrome; the stable-area top is the fallback when no visible rect is known), the BOTTOM inset is the surface height minus the stable-area bottom edge — falling back to the real surface edge when no insets are reported (no visible and no stable area, or one spanning the full surface). A bottom-row anchor preset (`fy = 0.9`) SHALL anchor the label at the top of the band with a small padding from the band's top edge; a top-row anchor preset (`fy = 0.1`) SHALL anchor it at the bottom of the band; a middle-row preset (including the default center) SHALL anchor it at the bottom of the band. The label SHALL NOT sit under an inset band and SHALL NOT be positioned at the band center; the top edge SHALL follow the host's currently-visible area so the label never floats mid-screen and never hides under chrome. Anchor rule is shared with the phone free-driving label and the browse label (same rows, same labels — guideline parity rule).

#### Scenario: Street name displayed while driving

- **WHEN** the free-driving view is visible and the GPS position is on a named street
- **THEN** the view shows that street name horizontally centered, anchored at the bottom edge (default/middle-row preset)

#### Scenario: Ref shown with the street name

- **WHEN** the street at the GPS position has a ref tag
- **THEN** the label shows the ref together with the name (e.g. "B 1 Hauptstrasse")

#### Scenario: Street name updates on street change

- **WHEN** the vehicle moves onto a different named street while free driving
- **THEN** the displayed street name updates to the new street

#### Scenario: Main road preferred over side street

- **WHEN** the vehicle drives on a main road and a side street branches off near the GPS position
- **THEN** the label shows the main road (matching the vehicle bearing), not the side street

#### Scenario: No street name when unnamed

- **WHEN** the GPS position is not on a named street while free driving
- **THEN** the view shows no street name (or an empty placeholder) and does not show stale text from a previous street

#### Scenario: Street name stays within host-visible area

- **WHEN** the free-driving view is visible and the host has delivered no insets (no stable area and no visible area)
- **THEN** the street-name label is still anchored to the real surface edge with the fixed padding — no host area rects move it (no mid-screen floating label)

#### Scenario: Street name at top for bottom-row anchors

- **WHEN** the free-driving anchor preset is in the bottom row (bottom-center, bottom-left, bottom-right, bottom-far-left, bottom-far-right) and the GPS position is on a named street
- **THEN** the street name is displayed horizontally centered at the top of the view with a small padding from the top edge, so it never sits between the vehicle and the way ahead

#### Scenario: Street name at bottom for top-row anchors

- **WHEN** the free-driving anchor preset is in the top row (top-center, top-left, top-right, top-far-left, top-far-right)
- **THEN** the street name is displayed horizontally centered at the bottom edge of the view

#### Scenario: Street name anchored to the real surface edge

- **WHEN** the free-driving view is visible and the host delivers a stable area spanning the full surface (no insets)
- **THEN** the street-name label sits at the real surface top or bottom edge with the fixed padding

#### Scenario: Street name clear of the host/system chrome bands

- **WHEN** the free-driving view is visible and the host stable area excludes the top and/or bottom band (e.g. the AAOS status bar at the top and the AAOS task bar at the bottom)
- **THEN** the street-name label sits inside the guaranteed-visible band: below the top inset with the fixed padding for a bottom-row preset, above the bottom inset with the fixed padding for a top/middle-row preset — never under an inset band
