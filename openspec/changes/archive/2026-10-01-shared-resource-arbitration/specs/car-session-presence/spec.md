# Spec Delta

## Purpose

Defines a process-wide signal that a car session (Android Auto projection or Android Automotive OS) is
live, so phone-side components can inform the driver instead of having to guess from navigation state.

## ADDED Requirements

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
