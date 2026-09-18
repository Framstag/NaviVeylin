## MODIFIED Requirements

### Requirement: Course unavailable

In **follow-direction** orientation the system SHALL keep the previous smoothed course bearing and the previous used follow-direction map angle when there is not enough recent movement to compute a new course-over-ground bearing. It SHALL NOT snap back to North-Up or to an unrotated marker while that orientation mode is active.

In **north-up** orientation the system SHALL commit a map angle of 0 on every update while the course is unavailable, exactly as it does when a course is available: the last-angle fallback is a follow-direction rule only.

#### Scenario: Vehicle is crawling or stationary

- **WHEN** follow-direction orientation is active
- **AND** the last 10 fixes span less than 40 m
- **THEN** the map keeps the previous course bearing
- **AND** the map does not spin from missing or noisy bearing data

#### Scenario: North-up holds 0° while the course is unavailable

- **WHEN** north-up orientation is active
- **AND** fewer than 40 m of movement have been recorded since a start, a turn reset or the mode switch
- **THEN** the committed map angle SHALL be 0
- **AND** the map SHALL NOT adopt a previously used follow-direction angle

### Requirement: Keep last valid course bearing

In **follow-direction** orientation the system SHALL keep the last valid course bearing and the last used follow-direction map angle when no new course-over-ground bearing is available. It SHALL NOT fall back to North-Up (`angle=0`) or to an unrotated marker (`bearing=-1`) in follow mode just because the latest history window is too short.

In **north-up** orientation a missing course bearing SHALL NOT change the map angle: it stays 0. A period spent in north-up SHALL NOT discard the last follow-direction angle, so switching back to follow direction without a usable bearing holds the last driving direction instead of snapping to north.

#### Scenario: GPS jitter briefly breaks the 40 m course window

- **WHEN** follow-direction orientation is active
- **AND** a new fix does not yet provide a 40 m course segment
- **THEN** the map keeps the previous follow-direction angle
- **AND** the marker keeps the previous course bearing so the arrow still points up

#### Scenario: Returning to follow direction keeps the last driving angle

- **WHEN** the map was following the driving direction at a non-zero angle
- **AND** the user switched to north-up for a while
- **AND** the user switches back to follow direction while no new course bearing is available yet
- **THEN** the map SHALL use the last follow-direction angle
- **AND** the map SHALL NOT jump to north-up
