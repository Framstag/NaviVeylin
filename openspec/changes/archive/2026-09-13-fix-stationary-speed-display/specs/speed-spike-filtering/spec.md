## MODIFIED Requirements

### Requirement: Spike filter resets on valid speed
The system SHALL update the last known good speed whenever a valid speed (≤ 150 km/h) is received. When no valid speed is received beyond the staleness window (≈ 3 s), the filter SHALL NOT keep returning the frozen last-known-good value; the speed used by consumers SHALL decay to 0 km/h instead.

#### Scenario: Valid speed after spike
- **GIVEN** the last good speed was 50 km/h
- **WHEN** a spike of 392 km/h arrives (rejected)
- **AND** then a valid speed of 80 km/h arrives
- **THEN** the last good speed SHALL update to 80 km/h
- **AND** subsequent zoom calculations SHALL use 80 km/h

#### Scenario: Unknown speed does not freeze a stale last-good
- **GIVEN** the last good speed was 7 km/h while the vehicle decelerated and stopped
- **WHEN** speed becomes unknown (NaN / negative) for longer than the staleness window
- **THEN** the speed delivered to consumers SHALL decay to 0 km/h
- **AND** SHALL NOT stay pinned at 7 km/h

#### Scenario: Fresh valid speed after decay
- **GIVEN** the filter decayed the speed to 0 km/h after a staleness window
- **WHEN** a fresh valid speed of 60 km/h arrives
- **THEN** the last known good speed SHALL update to 60 km/h immediately
