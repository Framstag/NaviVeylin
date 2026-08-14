# immediate-turn-instruction Specification

## Purpose

First turn instruction appears on navigation start, not after movement.

## Requirements

### Requirement: Show first instruction on route start
The system SHALL display the first turn instruction immediately when a route is calculated and navigation begins, without waiting for the user to move along the route.

#### Scenario: First instruction shown at navigation start
- **WHEN** user starts navigation on a calculated route
- **THEN** system displays the first turn instruction (e.g., "Turn left onto Main St") within 500ms

#### Scenario: No movement required
- **WHEN** navigation starts and the user has not yet moved
- **THEN** the first turn instruction SHALL remain visible

#### Scenario: Instruction matches route start maneuver
- **WHEN** navigation starts
- **THEN** the first turn instruction SHALL show the maneuver at the first decision point along the route

### Requirement: State flag for immediate instruction
The system SHALL maintain a boolean state flag `showFirstInstructionOnStart` that is set to `true` when navigation begins and cleared after the first position update confirms the user is on route.

#### Scenario: Flag set on navigation start
- **WHEN** navigation starts
- **THEN** `showFirstInstructionOnStart` SHALL be `true`

#### Scenario: Flag cleared after first position update
- **WHEN** first position update confirms user is on route
- **THEN** `showFirstInstructionOnStart` SHALL be `false`

### Requirement: No regression for subsequent instructions
The system SHALL continue to show subsequent turn instructions based on position and route progress as before. The immediate-first-instruction behavior SHALL NOT affect later instruction timing.

#### Scenario: Subsequent instructions unchanged
- **WHEN** user passes the first maneuver
- **THEN** subsequent turn instructions SHALL follow existing position-based timing
