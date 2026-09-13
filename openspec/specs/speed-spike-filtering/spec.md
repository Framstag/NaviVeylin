# speed-spike-filtering Specification

## Purpose

Rejects spuriously high speed values from the navigation engine (e.g. after GPS signal loss in tunnels) to prevent jarring zoom jumps during auto-zoom.

## Requirements

### Requirement: Speed spike rejection
The system SHALL reject speed values exceeding 150 km/h and use the last known good speed instead.

#### Scenario: SpeedAgent reports bogus 392 km/h after GPS gap
- **GIVEN** the navigation engine computes a spuriously high speed (e.g. 392 km/h) after a GPS signal gap
- **WHEN** `onCurrentSpeed` delivers this value
- **THEN** the auto-zoom logic SHALL use the last good speed (≤ 150 km/h) instead
- **AND** the zoom level SHALL NOT jump to the bogus speed's target

### Requirement: Spike filter applies to all zoom consumers
The system SHALL apply speed spike filtering before any consumer uses the speed value — both speed-based zoom and turn-aware zoom SHALL receive the filtered speed.

#### Scenario: Filtered speed used for turn zoom
- **GIVEN** the last good speed was 50 km/h
- **WHEN** a spike of 392 km/h arrives
- **THEN** the turn-aware zoom logic SHALL receive 50 km/h as the current speed
- **AND** the turn boost calculation SHALL use 50 km/h

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

### Requirement: No valid speed yet
The system SHALL use a default speed of 20 km/h when no valid speed has ever been received.

#### Scenario: First speed is a spike
- **GIVEN** navigation just started and no speed has been received yet
- **WHEN** the first `onCurrentSpeed` callback delivers 392 km/h (a spike)
- **THEN** the system SHALL use the default speed of 20 km/h
- **AND** the zoom level SHALL be computed from 20 km/h
