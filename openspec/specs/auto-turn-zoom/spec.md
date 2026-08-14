# auto-turn-zoom Specification

## Purpose

Boosts map magnification when approaching a turn during navigation so the driver can see turn geometry and street names clearly, then holds the boost past the turn.

## Requirements

### Requirement: Turn-aware zoom boosting
The system SHALL boost the target magnification when approaching a turn, and hold the boost until 600m past the turn.

#### Scenario: Approaching a turn at 300m
- **GIVEN** the vehicle is 300m from the next turn
- **WHEN** a position estimate arrives
- **THEN** the target magnification SHALL be at least 16.0
- **AND** the zoom SHALL remain boosted until 600m past the turn

#### Scenario: Between 300m and 600m from turn
- **GIVEN** the vehicle is between 300m and 600m from the next turn
- **WHEN** a position estimate arrives
- **THEN** the target magnification SHALL be at least 15.0

### Requirement: Turn zoom boost overrides speed-based zoom
The system SHALL apply the turn zoom boost as a minimum floor on the target magnification — if speed-based zoom would be lower, the turn boost takes precedence.

#### Scenario: Turn boost exceeds speed zoom
- **GIVEN** the vehicle is 200m from a turn at highway speed (100 km/h, speed-zoom target ~14.0)
- **WHEN** a position estimate arrives
- **THEN** the target magnification SHALL be at least 16.0 (turn boost overrides speed zoom)

#### Scenario: Speed zoom exceeds turn boost
- **GIVEN** the vehicle is 200m from a turn at walking speed (5 km/h, speed-zoom target ~16.5)
- **WHEN** a position estimate arrives
- **THEN** the target magnification SHALL be at least 16.5 (speed zoom exceeds turn boost floor)

### Requirement: Turn distance from route instructions
The system SHALL use the distance to the next route instruction as the input for turn-aware zoom calculations.

#### Scenario: No next instruction
- **GIVEN** there is no next route instruction (navigation complete or no route)
- **WHEN** a position estimate arrives
- **THEN** turn-aware zoom SHALL NOT apply any boost
- **AND** only speed-based zoom SHALL determine the target magnification

### Requirement: Turn boost hold distance
The system SHALL continue applying the turn zoom boost for 600m after passing the turn waypoint, then revert to speed-based zoom only.

#### Scenario: Past turn within hold distance
- **GIVEN** the vehicle passed the turn waypoint 300m ago
- **WHEN** a position estimate arrives
- **THEN** the target magnification SHALL be at least 15.0 (still within 600m hold)

#### Scenario: Past turn beyond hold distance
- **GIVEN** the vehicle passed the turn waypoint 800m ago
- **WHEN** a position estimate arrives
- **THEN** the target magnification SHALL be determined by speed-based zoom only
- **AND** no turn boost SHALL be applied
