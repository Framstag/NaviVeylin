## ADDED Requirements

### Requirement: Smooth follow-mode scrolling during navigation

The system SHALL scroll the navigation map smoothly to follow the vehicle between GPS fixes while navigating in follow mode (no per-fix snap), using the same display extrapolation and correction easing as free driving, and SHALL keep the navigation engine fed exclusively with real fixes.

#### Scenario: Map glides between fixes

- **WHEN** the vehicle moves during navigation in follow mode and GPS fixes arrive about once per second
- **THEN** the map scrolls continuously toward the predicted vehicle position between fixes
- **AND** it does not jump to the raw fix position in a single frame

#### Scenario: Fix correction eased

- **WHEN** a new GPS fix arrives while the navigation map is scrolled ahead of the true position
- **THEN** the map eases smoothly onto the fix position over approximately 200-300 ms
- **AND** the vehicle marker stays visually attached to the scrolled map

#### Scenario: Smooth follow without GPS speed

- **WHEN** the vehicle moves during navigation but the fixes carry no GPS speed
- **THEN** the map still scrolls smoothly using the speed derived from the movement between fixes

#### Scenario: Manual pan suspends smooth follow

- **WHEN** the user pans the map during navigation
- **THEN** smooth follow is suspended and the map stays where the user left it
- **AND** it resumes smoothly without a snap when the user exits pan mode
