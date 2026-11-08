# Spec Delta — car-session-presence

## ADDED Requirements

### Requirement: The presence publish is one host-free seam

The car session SHALL publish whether it is live through a single seam a unit case drives without a car
host: the presence collaborator and the direction are its inputs, so the start publish (a live session)
and the end publish (no live session) are asserted off-device. The seam SHALL confine a fault like every
other session lifecycle step — a rejected publish is a logged no-op that never escapes into the car-app
library's dispatch.

#### Scenario: The session start publishes through the seam

- **WHEN** the publish seam runs for a starting session
- **THEN** the presence reports a live car session
- **Case**: `SessionPresencePublishTest#sessionStartPublishesLive`

#### Scenario: The session end publishes through the seam

- **WHEN** the publish seam runs for an ending session
- **THEN** the presence reports no live car session
- **Case**: `SessionPresencePublishTest#sessionEndClearsTheSignal`

#### Scenario: A rejected start publish is confined and named

- **WHEN** the publish the seam makes for a starting session throws
- **THEN** the fault does not escape, the seam reports the publish did not land, and the rejection is
  recorded under the step's name
- **Case**: `SessionPresencePublishTest#aFaultingPublishIsConfinedAndNamed`

#### Scenario: A rejected end publish is named for the clear

- **WHEN** the publish the seam makes for an ending session throws
- **THEN** the fault does not escape and the rejection is recorded under the clearing step's own name
- **Case**: `SessionPresencePublishTest#clearingAFaultingPublishIsNamedForTheClear`
