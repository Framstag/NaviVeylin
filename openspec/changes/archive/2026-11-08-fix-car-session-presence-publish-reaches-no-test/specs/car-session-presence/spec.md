# Spec Delta — car-session-presence

## ADDED Requirements

### Requirement: The lifecycle callbacks select matching presence-publish directions

The production `onStart` callback SHALL publish a live car session and the production `onDestroy`
callback SHALL clear it, so the process-wide signal follows the session's own lifecycle. A rejected
publish SHALL be confined like every other session lifecycle step: a logged no-op that never escapes
into the car-app library's dispatch.

#### Scenario: A started session publishes a live presence

- **WHEN** the start lifecycle's publish runs
- **THEN** the presence reports a live car session
- **Case**: `SessionPresencePublishTest#sessionStartPublishesLive`

#### Scenario: An ended session clears the presence

- **WHEN** the destroy lifecycle's publish runs
- **THEN** the presence reports no live car session
- **Case**: `SessionPresencePublishTest#sessionEndClearsTheSignal`

#### Scenario: A rejected start publish is confined and named

- **WHEN** the start publish throws
- **THEN** the fault does not escape, the publish reports it did not land, and the rejection is
  recorded under the step's name
- **Case**: `SessionPresencePublishTest#aFaultingPublishIsConfinedAndNamed`

#### Scenario: A rejected end publish is named for the clear

- **WHEN** the clear publish throws
- **THEN** the fault does not escape and the rejection is recorded under the clearing step's own name
- **Case**: `SessionPresencePublishTest#clearingAFaultingPublishIsNamedForTheClear`

#### Scenario: The production callbacks select matching publish directions

- **WHEN** the production session lifecycle callbacks are checked
- **THEN** `onStart` selects only the live publish and `onDestroy` selects only the clear
- **Case**: `SessionPresencePublishTest#sessionCallbacksSelectMatchingPublishDirection`
