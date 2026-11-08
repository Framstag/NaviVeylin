# Spec Delta — car-session-presence

## ADDED Requirements

### Requirement: The lifecycle callbacks select matching presence-publish seams

The car session SHALL publish its presence through lifecycle-specific host-free seams: the start seam
publishes a live session and the destroy seam clears it. The production `onStart` callback SHALL
select the start seam and the production `onDestroy` callback SHALL select the destroy seam. A unit
case SHALL assert both seam directions and bind the production callbacks to their matching seams.
Each seam SHALL confine a fault like every other session lifecycle step: a rejected publish is a
logged no-op that never escapes into the car-app library's dispatch.

#### Scenario: The session start publishes through the start seam

- **WHEN** the start seam runs for a starting session
- **THEN** the presence reports a live car session
- **Case**: `SessionPresencePublishTest#sessionStartPublishesLive`

#### Scenario: The session end publishes through the destroy seam

- **WHEN** the destroy seam runs for an ending session
- **THEN** the presence reports no live car session
- **Case**: `SessionPresencePublishTest#sessionEndClearsTheSignal`

#### Scenario: A rejected start publish is confined and named

- **WHEN** the start seam's publish throws
- **THEN** the fault does not escape, the seam reports the publish did not land, and the rejection is
  recorded under the step's name
- **Case**: `SessionPresencePublishTest#aFaultingPublishIsConfinedAndNamed`

#### Scenario: A rejected end publish is named for the clear

- **WHEN** the destroy seam's publish throws
- **THEN** the fault does not escape and the rejection is recorded under the clearing step's own name
- **Case**: `SessionPresencePublishTest#clearingAFaultingPublishIsNamedForTheClear`

#### Scenario: The production callbacks select matching publish directions

- **WHEN** the production session lifecycle callbacks are checked
- **THEN** `onStart` selects only the start seam and `onDestroy` selects only the destroy seam
- **Case**: `SessionPresencePublishTest#sessionCallbacksSelectMatchingPublishDirection`
