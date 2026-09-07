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
The system SHALL keep the map centered on the GPS position while free driving (follow mode active).

#### Scenario: Map re-centers on GPS position
- **WHEN** the GPS position moves while free driving and the user does not pan the map
- **THEN** the map viewport re-centers on the new position

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
The system SHALL display the current street name on the free-driving view, derived from the map data at the GPS position, centered at the bottom of the view, and SHALL keep the label within the area the host guarantees visible.

#### Scenario: Street name displayed while driving
- **WHEN** the free-driving view is visible and the GPS position is on a named street
- **THEN** the view shows that street name centered at the bottom

#### Scenario: Street name updates on street change
- **WHEN** the vehicle moves onto a different named street while free driving
- **THEN** the displayed street name updates to the new street

#### Scenario: No street name when unnamed
- **WHEN** the GPS position is not on a named street while free driving
- **THEN** the view shows no street name (or an empty placeholder) and does not show stale text from a previous street

#### Scenario: Street name stays within host-visible area
- **WHEN** the free-driving view is visible and the host has not delivered a stable area
- **THEN** the street-name label is still drawn within the host's visible area, not at the raw surface bottom

### Requirement: Current driving speed shown
The system SHALL display the current driving speed on the free-driving view, on the right side below the compass.

#### Scenario: Speed shown below compass
- **WHEN** the free-driving view is visible and the GPS fix reports a ground speed
- **THEN** the view shows the current speed (km/h) below the compass rose on the right side

#### Scenario: Speed hidden when unknown
- **WHEN** the GPS fix does not report a ground speed
- **THEN** the view shows no speed readout

#### Scenario: Speed derived from movement without GPS speed
- **WHEN** the GPS fix has no speed (e.g. GPX track replay) but the vehicle moved since the previous fix
- **THEN** the speed readout and auto-zoom use the speed implied by the movement between fixes

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
