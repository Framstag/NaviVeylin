# routing-avoidance Specification

## Purpose
Owns the contract between the user's persisted avoidance choices — no toll roads, no ferry routes, no
unpaved roads — and the routing profile: which choices exist and what they default to, that every
calculation path carries them into the profile, where they are edited, and what a choice does when the map
data cannot express it.

## Requirements

### Requirement: Avoidance is a persisted per-vehicle-independent setting

The app SHALL persist three avoidance choices as booleans: avoid toll roads, avoid ferry routes and avoid
unpaved roads. All three SHALL default to off, and a settings file written before they existed SHALL decode
to them rather than fail or clear.

#### Scenario: Defaults on a fresh install
- **WHEN** no settings file exists
- **THEN** all three avoidance choices are off

#### Scenario: A settings file from before this change
- **WHEN** a settings file that contains none of the three fields is loaded
- **THEN** loading succeeds and all three are off

#### Scenario: The choices round-trip
- **WHEN** avoid toll roads and avoid unpaved roads are switched on and the app is restarted
- **THEN** both are still on and avoid ferry routes is still off

### Requirement: One builder produces the profile every calculation uses

A single builder SHALL produce a vehicle's routing profile from the stored maximum speed and the stored
avoidance choices, and every place that calculates a route SHALL use it: the route panel's calculation, the
navigation engine's acquisition for a car destination, and the engine's reroute. No call site SHALL map
these values itself.

#### Scenario: Route panel calculation carries the choices
- **WHEN** avoid toll roads is on and the user starts a calculation in the route panel
- **THEN** the routing profile it builds requests toll avoidance

#### Scenario: Engine acquisition and reroute carry the choices
- **WHEN** avoid ferry routes is on and the navigation engine acquires or reroutes a car destination
- **THEN** both calculations the engine starts request ferry avoidance

#### Scenario: A choice cannot be lost on one path
- **WHEN** the call sites that build a routing profile are inspected
- **THEN** each one calls the builder, and none of them constructs a profile from individual values

### Requirement: The phone edits the choices and the car reads them

The options sheet SHALL offer the three switches beside the vehicle's maximum speed, and a route
calculated on Android Auto or AAOS SHALL use the values the phone stored. The car SHALL expose no
avoidance control of its own.

#### Scenario: The sheet shows the switches
- **WHEN** the user opens the options sheet
- **THEN** it shows avoid toll roads, avoid ferry routes and avoid unpaved roads beside the vehicle speed
- **AND** each switch reflects the stored value

#### Scenario: The car uses the phone's choices
- **WHEN** the phone stores avoid toll roads on and a destination is acquired on the car
- **THEN** the car's route calculation requests toll avoidance

### Requirement: A choice the data cannot express changes nothing

When the open map database carries no information about the avoided property, the calculation SHALL
succeed and SHALL return the route it would return with the choice off. The absence of information is not
evidence that a way is free of the avoided property.

#### Scenario: Tolls on a database without toll information
- **GIVEN** an open database whose import recorded no toll information
- **WHEN** a route is calculated with avoid toll roads on
- **THEN** the route is calculated
- **AND** it is the same route the calculation returns with the switch off
