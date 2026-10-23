# smooth-follow — deltas

## ADDED Requirements

### Requirement: A band the follow anchor reads is one the surface composes while following

The regions the follow anchor resolves against SHALL be the chrome the surface composes **while follow framing
runs**. A chrome band composed in a state that holds the camera itself SHALL therefore not move the anchor.

#### Scenario: The session card is not a follow band in free driving

- **GIVEN** the phone map is following (free driving, no route guidance) and the active anchor is a bottom preset
- **WHEN** the route-planning session opens and composes its card over the bottom of the canvas
- **THEN** following stops and the session owns the camera (spec: `route-planning-session` — Session holds the
  camera while active), so no follow framing runs and the card is not measured into the bottom band
- **AND** the resolved anchor is the one of a surface with no overlay band until follow is re-engaged — the card's
  height never becomes a follow band, whatever anchor it is at
- **AND** when the session ends and follow is re-engaged, the bands of the chrome composed then apply again

#### Scenario: The same card is the band while navigating

- **GIVEN** the phone is navigating and the session card is open over the bottom of the canvas
- **THEN** the card's own measured height SHALL be the published bottom inset, and the bottom anchor SHALL resolve
  above it, exactly as the routing-status card does when the session card is closed
- **AND** the routing-status card SHALL NOT write that inset while the session card is shown — one writer per inset
