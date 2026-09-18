# Free Driving (auto/free-driving)

## Purpose

Free-driving mode for Android Auto: a destination-free navigation-style view with a live heading-up map, GPS position marker, compass, and current street name, entered from the map menu.

## Requirements

### Requirement: Free driving entered from map menu
The system SHALL provide a "Free driving" entry in the Android Auto map menu that opens a full-screen navigation-style view without a destination.

#### Scenario: Free driving opened from map menu
- **WHEN** the user selects "Free driving" in the map menu content on the Android Auto map screen
- **THEN** the system shows a full-screen free-driving view

#### Scenario: Free driving view fills the surface
- **WHEN** the free-driving view is shown
- **THEN** the system renders a full-screen `NavigationTemplate` with no `NavigationInfo` and no content slot, so no host-rendered panel/box overlays the map (`MapWithContentTemplate`'s mandatory content slot showed a "Free driving" box; `MapTemplate`'s required item list showed a "No items" placeholder). The map surface is drawn by the app; the earlier belief that the emulator host locks the `NavigationTemplate` surface predates the renderer fixes (the old `drawToSurface` had no `finally`-unlock, so one draw exception leaked the lock and every later `lockCanvas` threw "already locked"). The renderer detects a dead surface (`isValid` check + failed `lockCanvas`), stops hammering it, releases it per the car-app API contract, and asks the host for a fresh one (capped `invalidate`). All indicators (compass rose, speed, limit sign, street name) are drawn on the surface by the app; the host draws its own compass (cosmetic overlap accepted)

#### Scenario: Compass rose centered above speed readout
- **WHEN** the free-driving view is visible
- **THEN** the compass rose drawn on the map surface is horizontally centered above the speed readout, with a gap from the right map edge

#### Scenario: Free driving does not start turn-by-turn navigation
- **WHEN** the user selects "Free driving"
- **THEN** the system does not change the active navigation state (`isNavigating` remains false) and no route is computed

### Requirement: GPS position marker shown
The system SHALL show the current GPS position as a marker on the free-driving map surface.

#### Scenario: Position marker drawn on map
- **WHEN** the free-driving view is visible and a GPS position is available
- **THEN** the map surface shows a marker at the current position

#### Scenario: Position marker follows movement
- **WHEN** the GPS position updates while free driving
- **THEN** the position marker moves to the new position without user interaction

### Requirement: Follow mode activated

The system SHALL keep the vehicle marker during free driving at the configured free-driving anchor position while follow mode is active, instead of at the screen center. The anchor is one of 15 positions on a 5×3 grid (horizontal 10/30/50/70/90% of the surface width, vertical 10/50/90% of the surface height). The default free-driving anchor is center/center (50% width, 50% height), which reproduces the pre-feature framing exactly. To place the marker at the anchor, the map render target SHALL be shifted so the vehicle's geographic position projects to the anchor under the heading-up rotation (free driving is always heading-up).

#### Scenario: Map re-centers on GPS position

- **WHEN** the GPS position moves while free driving and the user does not pan the map
- **THEN** the map render target shifts so the map keeps the vehicle marker at the configured anchor position
- **AND** with the default center/center anchor this reproduces re-centering the viewport on the GPS position

#### Scenario: Default anchor reproduces today's framing

- **GIVEN** the free-driving anchor is at its default center/center
- **WHEN** free driving is active in follow mode
- **THEN** the vehicle marker projects to the center of the free-driving surface
- **AND** the map framing is identical to free driving without anchor presets

#### Scenario: Anchor kept under heading-up rotation

- **WHEN** the vehicle bearing changes while free driving
- **THEN** the map rotates heading-up with the bearing
- **AND** the vehicle marker keeps projecting to the chosen anchor position

#### Scenario: Anchor restored after manual pan

- **WHEN** the user pans the map during free driving (follow suspended)
- **AND** the user then stops panning
- **THEN** follow mode re-engages and the vehicle marker returns to the free-driving anchor without a snap

#### Scenario: Auto-zoom keeps the anchored vehicle in view

- **WHEN** speed-driven auto-zoom changes the magnification while free driving
- **THEN** the map zooms around the anchor position
- **AND** the vehicle marker stays at the chosen anchor

### Requirement: Heading-up orientation
The system SHALL orient the free-driving map heading-up, rotating with the vehicle bearing, with north-up disabled.

#### Scenario: Map rotates with vehicle bearing
- **WHEN** the vehicle bearing changes while free driving
- **THEN** the map rotates so the travel direction points up on the surface

#### Scenario: Heading-up independent of settings
- **WHEN** the user changes the shared navigation north-up setting while free driving
- **THEN** the free-driving view keeps rotating heading-up with the bearing

#### Scenario: Heading derived from movement without GPS bearing
- **WHEN** the GPS fix has no bearing (e.g. GPX track replay) but the vehicle moved since the previous fix
- **THEN** the map rotates heading-up using the movement direction between fixes

#### Scenario: Driving direction points up
- **WHEN** the vehicle drives in a direction while free driving
- **THEN** the map rotates so the driving direction points up on the surface (viewport angle = negative bearing, matching the phone app convention)

### Requirement: Free-driving compass rose points at true north
The free-driving compass rose SHALL draw its north pointer at the screen direction of true north under the heading-up map rotation, using the same convention as the navigation rose (`auto-map-layout`): with heading-up rotation (map angle = −bearing), the north pointer SHALL sit at `360 − bearing` degrees clockwise from screen-up.

#### Scenario: Westbound free driving, north on the driver's right
- **WHEN** the free-driving view is visible with heading-up rotation
- **AND** the vehicle heading is 270° (driving west), map angle = −270° ≡ +90°
- **THEN** the rose north pointer SHALL point 90° clockwise from screen-up — the driver's right, where true north is

#### Scenario: Eastbound free driving, north on the driver's left
- **WHEN** the free-driving view is visible with heading-up rotation
- **AND** the vehicle heading is 90° (driving east), map angle = −90° ≡ +270°
- **THEN** the rose north pointer SHALL point 270° clockwise from screen-up — the driver's left

### Requirement: Auto-zoom by speed
While free driving, the system SHALL adjust the map magnification from the vehicle speed using the shared speed-to-magnification table when follow mode and the auto-zoom setting are active.

#### Scenario: Speed increases zoom out
- **WHEN** the vehicle speed increases while free driving (auto-zoom enabled)
- **THEN** the map magnification decreases (zooms out) per the speed-to-magnification mapping, staying centered on the vehicle

#### Scenario: Speed decreases zoom in
- **WHEN** the vehicle speed decreases while free driving (auto-zoom enabled)
- **THEN** the map magnification increases (zooms in) per the speed-to-magnification mapping, staying centered on the vehicle

#### Scenario: Manual zoom suspends auto-zoom
- **WHEN** the user zooms manually while free driving
- **THEN** auto-zoom is suspended and the map stays at the manual zoom level while the speed stays in the same speed band

#### Scenario: Band change re-engages auto-zoom
- **WHEN** the speed crosses a speed-table band boundary after a manual zoom
- **THEN** auto-zoom re-engages and adjusts to the speed-appropriate magnification

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

### Requirement: Current driving speed shown
The system SHALL display the current driving speed on the free-driving view, on the right side below the compass. When the current speed exceeds the current road's speed limit, the speed readout SHALL be shown in the warning state — a red badge background with white text — matching the overspeed visual of the navigation speed badge.

#### Scenario: Speed shown below compass
- **WHEN** the free-driving view is visible and the GPS fix reports a ground speed
- **THEN** the view shows the current speed (km/h) below the compass rose on the right side

#### Scenario: Speed hidden when unknown
- **WHEN** the GPS fix does not report a ground speed
- **THEN** the view shows no speed readout

#### Scenario: Speed derived from movement without GPS speed
- **WHEN** the GPS fix has no speed (e.g. GPX track replay) but the vehicle moved since the previous fix
- **THEN** the speed readout and auto-zoom use the speed implied by the movement between fixes

#### Scenario: Overspeed warning in free driving
- **WHEN** the free-driving view is visible and the current speed exceeds the road's speed limit
- **THEN** the speed readout is shown with a red background and white text

#### Scenario: Normal colors at or below the limit
- **WHEN** the free-driving view is visible and the current speed does not exceed the road's speed limit
- **THEN** the speed readout is shown with the normal badge background and white text

### Requirement: Speed limit sign shown
The system SHALL show a speed-limit sign on the free-driving view when the current road has a defined speed limit, placed below the current-speed widget.

#### Scenario: Limit sign below speed widget
- **WHEN** the free-driving view is visible and the road at the GPS position has a defined speed limit
- **THEN** the view shows a speed-limit sign below the current-speed widget with the standard visualisation (black text in a white circle with a big red border)

#### Scenario: Limit sign updates on road change
- **WHEN** the vehicle moves onto a road with a different speed limit while free driving
- **THEN** the sign shows the new limit

#### Scenario: No sign without a limit
- **WHEN** the road at the GPS position has no defined speed limit
- **THEN** the view shows no speed-limit sign

### Requirement: No turn-by-turn guidance shown
The system SHALL show no destination-dependent guidance in the free-driving view.

#### Scenario: No navigation hints shown
- **WHEN** the free-driving view is visible
- **THEN** no next-turn instruction, distance-to-turn, or maneuver arrow is drawn on the map surface or rendered by the host

#### Scenario: No lane guidance shown
- **WHEN** the free-driving view is visible
- **THEN** no lane guidance is drawn, regardless of the shared lane-hints setting

#### Scenario: No travel estimate shown
- **WHEN** the free-driving view is visible
- **THEN** the system does not set a destination travel estimate (no ETA, remaining distance, or remaining time)

### Requirement: Exit free driving
The system SHALL provide an action to leave the free-driving view and return to the map view.

#### Scenario: Exit returns to map view
- **WHEN** the user activates the exit action (an "x" button) on the free-driving view
- **THEN** the system returns to the map view (the previous map screen)

#### Scenario: No menu affordance in free driving
- **WHEN** the free-driving view is shown
- **THEN** the map action strip shows only the exit "x" button (no back/menu affordance)

#### Scenario: System back exits free driving
- **WHEN** the user presses the system back action while in the free-driving view
- **THEN** the system returns to the map view
