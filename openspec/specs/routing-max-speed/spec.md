# routing-max-speed Specification

## Purpose
Owns the contract between the user's persisted per-vehicle maximum speed and the routing profile: which
speeds exist and what they default to, that every calculation path carries the vehicle's value into the
profile, how each vehicle interprets the value, and what happens when it is missing or unusable.

## Requirements

### Requirement: A per-vehicle maximum speed is a persisted setting

The app SHALL persist a usual maximum speed for each routing vehicle — car, bicycle and pedestrian — in
km/h with 1 km/h precision. The defaults SHALL be 120 (car), 15 (bicycle) and 5 (pedestrian). A settings
file written before these fields existed SHALL decode to the defaults rather than fail or clear.

#### Scenario: Defaults on a fresh install

- **WHEN** no settings file exists
- **THEN** the car maximum speed is 120 km/h
- **AND** the bicycle maximum speed is 15 km/h
- **AND** the pedestrian maximum speed is 5 km/h

#### Scenario: A settings file from before this change decodes to the defaults

- **WHEN** a settings file that contains none of the three maximum speed fields is loaded
- **THEN** loading succeeds
- **AND** the three values are 120 / 15 / 5 km/h

#### Scenario: The values round-trip through the settings file

- **WHEN** the car value is set to 90, the bicycle value to 20 and the pedestrian value to 4, and the app is restarted
- **THEN** the loaded values are 90 / 20 / 4 km/h

### Requirement: The setting is shared by the phone and the car

One stored value per vehicle SHALL serve both surfaces. The phone SHALL be the only surface that edits
them, and a route calculated on Android Auto or AAOS for the car SHALL use the same car value the phone
shows.

#### Scenario: Car routing uses the phone's car value

- **WHEN** the phone stores a car maximum speed of 90 km/h
- **AND** a destination is acquired on the car (Android Auto or AAOS)
- **THEN** the car's route calculation uses 90 km/h as the car's maximum speed
- **AND** the car exposes no separate car-speed value

### Requirement: Every calculation path carries the vehicle's maximum speed

Each place that builds a routing profile — the route panel's calculation, the engine's acquisition for a
car destination, and the engine's reroute — SHALL build it with the stored maximum speed of the vehicle
that calculation uses.

#### Scenario: Route panel calculation

- **WHEN** the route panel's selected vehicle is Bicycle and the stored bicycle maximum speed is 20 km/h
- **AND** the user starts a calculation
- **THEN** the routing profile carries 20 km/h for the bicycle vehicle

#### Scenario: Car destination acquisition

- **WHEN** the engine acquires a route for a destination (car, no vehicle selection)
- **AND** the stored car maximum speed is 110 km/h
- **THEN** the routing profile carries 110 km/h for the car vehicle

#### Scenario: Reroute uses the running session's vehicle value

- **WHEN** navigation runs with the pedestrian vehicle and the stored pedestrian maximum speed is 4 km/h
- **AND** the engine reroutes after an off-route report
- **THEN** the reroute's routing profile carries 4 km/h for the pedestrian vehicle

### Requirement: Vehicle semantics of the maximum speed

For the car the configured value SHALL act as a ceiling on the travel speed and SHALL leave the
per-road-type speed table unchanged, so a value above the fastest road type is permitted and has no
effect. For bicycle and pedestrian the configured value SHALL be the flat travel speed applied to every
routable way type.

#### Scenario: A car value below a road type's speed caps it

- **WHEN** the car maximum speed is 90 km/h
- **THEN** a road type whose table speed would be 110 km/h is travelled at 90 km/h

#### Scenario: A car value above the fastest road type has no effect

- **WHEN** the car maximum speed is 130 km/h
- **THEN** the route and its duration are the same as with the 120 km/h default

#### Scenario: The bicycle value is the flat speed

- **WHEN** the bicycle maximum speed is 20 km/h
- **THEN** every bicycle-routable way type is travelled at 20 km/h regardless of its type

### Requirement: A missing or unusable maximum speed falls back to the built-in profile

When the routing profile carries no maximum speed, or a value outside 1–300 km/h, the calculation SHALL
use the library's built-in profile for that vehicle (car 120, bicycle 30, pedestrian 10 km/h) and SHALL
NOT fail, abort or report an error.

#### Scenario: A profile built without a maximum speed

- **WHEN** a routing profile is built without a maximum speed value (an existing caller of the old constructors)
- **THEN** the calculation runs with the built-in profile for the vehicle
- **AND** no error is reported to the user

#### Scenario: A zero or out-of-range value

- **WHEN** the routing profile carries a maximum speed of 0, or a value above 300 km/h
- **THEN** the calculation runs with the built-in profile for the vehicle

### Requirement: A speed change does not restart a calculation

A maximum speed change SHALL apply to the next calculation and SHALL NOT restart, cancel or alter a
calculation or a navigation session already in progress.

#### Scenario: Changing the value mid-navigation

- **WHEN** navigation is running and the user moves the car speed slider
- **THEN** the running session's route and remaining duration are unchanged
- **AND** the next reroute uses the new value

#### Scenario: Changing the value before calculating

- **WHEN** the user moves a speed slider and then starts a route calculation
- **THEN** that calculation uses the new value
