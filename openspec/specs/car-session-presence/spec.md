# car-session-presence Specification

## Purpose

Defines a process-wide signal that a car session (Android Auto projection or Android Automotive OS) is
live, so phone-side components can inform the driver instead of having to guess from navigation state.

## Requirements

### Requirement: A live car session is observable in the process

The system SHALL publish whether a car session is currently live as an observable process-wide signal,
set when the car session starts and cleared when it ends, and reachable both from the car module and
from the phone app in the same process.

#### Scenario: Session start is published

- **WHEN** a car session starts
- **THEN** the process-wide signal reports a live car session

#### Scenario: Session end is published

- **WHEN** the car session is destroyed
- **THEN** the signal reports no live car session

#### Scenario: Not observable in a car-only process

- **WHEN** the process runs solely as an Android Automotive OS car app with no phone surface
- **THEN** the signal is set while the session lives and no phone-side consumer acts on it

### Requirement: The phone is informed, not disabled

While a car session is live, the phone UI SHALL indicate that navigation is being presented on the car
screen, and SHALL remain usable: the map can be browsed and a destination can be set. The phone UI SHALL
NOT disable map or navigation controls because a car session is live.

#### Scenario: Indicator shown while the car session is live

- **WHEN** the phone UI is visible and a car session is live
- **THEN** the phone shows an indication that navigation is presented on the car screen

#### Scenario: Phone stays usable

- **WHEN** a car session is live and the user browses the phone map or opens search
- **THEN** the map and search remain interactive
- **AND** no control is disabled because of the car session

#### Scenario: Indicator clears with the session

- **WHEN** the car session ends
- **THEN** the phone no longer shows the indication

### Requirement: The signal never claims a session that is gone

A car session that ends — including an app process death, which ends the session with it — SHALL leave
the signal cleared or irrelevant, and a stale "session live" value SHALL NOT persist across a process
restart.

#### Scenario: Process restart starts clear

- **WHEN** the app process is started fresh after a crash while a car session had been live
- **THEN** the signal starts as no live car session

#### Scenario: Session end during a background round trip

- **WHEN** the car session is destroyed while the phone UI is not visible
- **THEN** the signal is cleared and the phone shows no stale indication when it becomes visible again

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
