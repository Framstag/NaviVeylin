# Spec Delta — navigation-ongoing-notification

## MODIFIED Requirements

### Requirement: Process survival during background driving
While NAVIGATION or FREE_DRIVE is active, the system SHALL keep the process running in the background for as long as the mode is active; the ongoing notification is the carrier of that protection. Process survival alone SHALL NOT be read as "location fixes continue": fixes run only while a location lease is held (a visible surface, or a running navigation), so a backgrounded FREE_DRIVE holds none and its notification keeps the last known road and speed instead of live values. The system SHALL NOT resurrect a dead driving session: if the process is killed, nothing SHALL claim navigation is still running.

#### Scenario: Process protected while driving in background
- **WHEN** the app drives in the background in NAVIGATION or FREE_DRIVE mode
- **THEN** the operating system does not reap the process, and the mode's session and notification remain available until the mode ends

#### Scenario: Backgrounded free driving is not fed new fixes
- **WHEN** free driving is active, no surface is visible and no navigation is running
- **THEN** the notification stays visible showing the last known road and speed
- **AND** the app requests no new location fixes for it

#### Scenario: No lying notification after a kill
- **WHEN** the process was killed while in the background and the Android system restarts a service
- **THEN** no notification is posted that shows guidance, because the in-process navigation state is gone

### Requirement: Free-driving content
While FREE_DRIVE is active, the notification SHALL show the current street name/ref (the road the vehicle is driving on) and the current speed, updated live while location fixes are arriving. It SHALL NOT show any destination-dependent guidance or an arrival time. Free driving SHALL remain phone-only: the app is not the active navigation app while free driving, so no car turn-by-turn hint and no trip metadata are offered to the car host (documented platform deviation; the car surfaces are specified by the `auto-navigation-hints` capability in the change `car-turn-by-turn-rail-widget`).

#### Scenario: Free-driving street and speed in the shade
- **WHEN** free driving is active and location fixes are arriving (the phone map is in the foreground, or the car's free-driving session holds its lease)
- **THEN** the notification shows the current street/ref and current speed, refreshed as the vehicle moves through the road network

#### Scenario: Free driving has no car surface
- **WHEN** free driving is active and the driver switches to another car app
- **THEN** no car turn-by-turn hint and no trip metadata are published for the free-driving session

## ADDED Requirements

### Requirement: Free-driving content comes from the free-driving status
The notification SHALL take the free-driving street/ref and speed from the same free-driving status the surfaces display, so the shade and the on-screen label cannot disagree.

#### Scenario: Shade and on-screen label agree
- **WHEN** free driving is active with arrivals of fixes and a road known at the current position
- **THEN** the notification's street/ref equals the on-screen label's street/ref
- **AND** the notification's speed equals the speed the map's free-driving readout shows

#### Scenario: A street change refreshes both
- **WHEN** the vehicle moves onto a different road while free driving is active
- **THEN** the on-screen label and the notification both show the new road

#### Scenario: No fix yet means no stale road
- **WHEN** free driving is active and no fix has been processed yet
- **THEN** the notification shows the neutral free-driving title without a street or speed
- **AND** no road from an earlier session is shown

### Requirement: Unknown free-driving values leave no empty fragment
The free-driving notification SHALL omit a value it does not know instead of rendering an empty fragment or a stray separator.

#### Scenario: Unknown speed
- **WHEN** free driving is active and the current speed is unknown while a road is known
- **THEN** the notification shows the street/ref alone, with no trailing separator and no empty slot

#### Scenario: Unknown road
- **WHEN** free driving is active and no road is known at the current position
- **THEN** the notification shows the fallback road label or the neutral title, never an empty text
