## MODIFIED Requirements

### Requirement: FIFO cleared when stationary
The `SpeedAgent` SHALL clear its segment FIFO when GPS-reported speed is below 0.5 m/s, preventing lingering movement history from producing false speed readings after stopping. In the position-difference fallback branch (GPS-reported speed unknown), the `SpeedAgent` SHALL ignore segments whose displacement is below the minimum-displacement threshold (≈ 3 m over the FIFO window), so stationary GPS drift SHALL NOT be reported as speed.

#### Scenario: Stop at traffic light
- **GIVEN** the vehicle was moving (FIFO has segments) and then stops
- **WHEN** a `GPSUpdateMessage` with `currentSpeed = 0.0` arrives
- **THEN** the FIFO SHALL be cleared
- **AND** the output speed SHALL be 0.0 km/h immediately

#### Scenario: Fallback at standstill with position drift
- **GIVEN** the vehicle is stationary and GPS speed is unknown (`currentSpeed < 0`)
- **AND** consecutive position fixes differ by only the minimum-displacement threshold or less (GPS jitter, e.g. 2 m over a 1 s segment)
- **WHEN** `SpeedAgent::Process` computes the fallback speed from position differences
- **THEN** the segment SHALL contribute zero distance
- **AND** the output `CurrentSpeedMessage` SHALL contain speed 0.0 km/h

#### Scenario: Fallback still reports real movement
- **GIVEN** GPS speed is unknown (`currentSpeed < 0`) and the vehicle moves more than the minimum-displacement threshold between fixes (e.g. 10 m over a 1 s segment)
- **WHEN** `SpeedAgent::Process` computes the fallback speed from position differences
- **THEN** the output speed SHALL be the distance/time-derived value (≥ real walking/driving speed), not zero

## ADDED Requirements

### Requirement: Stationary fix reads zero
The displayed speed SHALL read 0 km/h while standing still, independent of presentation mode. Specifically: (a) when no fresh GPS fix arrives within the staleness window (≈ 3 s, ~2–3× the 1 s update interval), the displayed speed SHALL be 0 km/h rather than the last delivered value; (b) while the fix is fresh, if the reported speed is at or below the speed dead-band (≈ 8 km/h — covers observed residuals up to 7 km/h) AND consecutive fixes moved less than the stationary-displacement threshold (≈ 2 m), the displayed speed SHALL snap to 0 km/h — position evidence SHALL override a residual or stale velocity estimate.

#### Scenario: Provider silent while parked
- **GIVEN** the vehicle stops and the location provider stops delivering fixes (min-distance throttling)
- **AND** the last delivered fix reported a nonzero speed (e.g. 7 km/h)
- **WHEN** the staleness window elapses without a new fix
- **THEN** the displayed speed SHALL be 0 km/h
- **AND** it SHALL remain 0 km/h until a fresh moving fix arrives

#### Scenario: Residual velocity ignored with stationary evidence
- **GIVEN** the vehicle is standing still and fresh fixes arrive
- **AND** a fix reports a speed below or equal to the dead-band (e.g. 7 km/h) while consecutive fixes moved less than the stationary-displacement threshold
- **WHEN** the speed value is prepared for display
- **THEN** the displayed speed SHALL be 0 km/h

#### Scenario: Crawling with real movement still shown
- **GIVEN** the vehicle moves slowly (real movement above the stationary-displacement threshold between fixes)
- **AND** the reported speed is at or below the dead-band (e.g. 2 km/h)
- **WHEN** the speed value is prepared for display
- **THEN** the displayed speed SHALL be the reported value (2 km/h), NOT forced to 0

#### Scenario: Stale zero clears on fresh movement
- **GIVEN** the displayed speed was forced to 0 km/h by the staleness window while parked
- **WHEN** a fresh fix with real movement arrives (e.g. 20 km/h)
- **THEN** the displayed speed SHALL update to 20 km/h immediately
