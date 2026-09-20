# Spec Delta: auto/navigation-view

## MODIFIED Requirements

### Requirement: Smooth follow-mode scrolling during navigation

The system SHALL scroll the navigation map smoothly to follow the vehicle between GPS fixes while navigating in follow mode (no per-fix snap), using the same display extrapolation and correction easing as free driving, and SHALL keep the navigation engine fed exclusively with real fixes. The per-fix viewport commit SHALL anchor the follow render target on the displayed (eased predicted) position, never on the raw fix, so a fix never steps the map by the extrapolation lead; and the rotation SHALL be re-committed only beyond the shared heading deadband, so a sub-degree heading change does not force a full native render. Free driving SHALL observe the same two rules (parity within the car app).

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

#### Scenario: Fix does not step the navigation map

- **WHEN** a GPS fix arrives during navigation while the displayed position leads the fix
- **THEN** the committed frame SHALL be centered on the anchor center of the displayed position
- **AND** the map SHALL NOT step by the lead at the commit
- **AND** free driving SHALL behave identically for the same fix stream
