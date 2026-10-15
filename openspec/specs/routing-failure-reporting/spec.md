# routing-failure-reporting Specification

## Purpose

Makes a failed route calculation tell the user what actually went wrong and what they can do about it,
with the message supplied and localized by the surface that shows it.

## Requirements

### Requirement: A failed route calculation reports a reason

A route calculation that fails SHALL report a reason from a closed set that names the cause. The set SHALL
distinguish at least: the start could not be attached to the road network, the target could not be
attached to the road network, no route exists between the two, the routing data could not be used, and the
routing engine failed. One undifferentiated "calculation failed" outcome SHALL NOT be the only report.

#### Scenario: A failure reports which cause applies
- **WHEN** a route calculation fails
- **THEN** the reported failure SHALL identify exactly one reason from the closed set
- **AND** it SHALL NOT report the set as a whole or an unspecified cause

#### Scenario: The two unattachable ends are told apart
- **WHEN** a calculation fails because one end of the route cannot be attached to the road network
- **THEN** the reported reason SHALL be the one for that end
- **AND** a failure at the start SHALL NOT be reported with the reason that belongs to the target

#### Scenario: A cancellation is not a failure
- **WHEN** a calculation is cancelled
- **THEN** no failure reason SHALL be reported for it

### Requirement: Each reason is worded by the surface

Every reason SHALL have a user-actionable message, and each surface SHALL supply that message from its own
Android string resources in the device language. Phone and Android Auto SHALL use the same label for the
same reason. Native code SHALL NOT supply the user-facing wording of a failure.

#### Scenario: Phone message in the device language
- **WHEN** a route calculation fails on the phone and the device locale is German
- **THEN** the route panel SHALL show the reason's German message
- **AND** it SHALL NOT show an English sentence produced outside the resource system

#### Scenario: The car uses the same label for the same reason
- **WHEN** the same reason is reported while a car session is active
- **THEN** the car notice SHALL show the same label the phone shows for that reason

#### Scenario: Wording that names the place is still a resource
- **WHEN** a reason's message names a road, a distance or a map
- **THEN** the wording around that value SHALL come from a string resource with the value substituted
- **AND** no display words SHALL be assembled in code

#### Scenario: A surface-specific wording keeps its own label
- **WHEN** a reason has surface-specific wording by an existing decision (an unreadable shared link reads
differently on the phone and on the car, `guidelines/UI.md` §1)
- **THEN** the surface SHALL show the resource that decision names for it
- **AND** the failure SHALL carry that wording as the message the surface already rendered

### Requirement: A reason whose cause the user can act on offers the action

When the reason is a missing or unusable map or routing data, the surface SHALL offer the action that can
fix it where that surface can carry it: the phone SHALL offer the map manager. A car session SHALL NOT
launch a phone screen, and SHALL state that the action belongs to the phone. A reason that no action can
fix SHALL offer no action.

#### Scenario: Phone offers the map manager
- **WHEN** the phone reports a failure whose reason is missing or unusable routing data
- **THEN** the phone SHALL offer an action that opens the map manager

#### Scenario: Car names the phone and launches no phone screen
- **WHEN** the car reports a failure whose reason is missing or unusable routing data
- **THEN** the car notice SHALL state that the maps belong to the phone
- **AND** the car SHALL NOT launch a map manager or any phone screen

#### Scenario: A missing map region is a map-manager fix too
- **WHEN** no route exists between the two points
- **THEN** the surface SHALL offer the map manager
- **AND** the reason's message SHALL name the map data on the way as the likely cause

#### Scenario: A reason with no action offers none
- **WHEN** a failure's reason is that the destination or the start has no connection to the road network
- **THEN** the surface SHALL show the reason's message
- **AND** it SHALL NOT offer an action

### Requirement: A reason that concerns one end of the route identifies that end

A reason that concerns one end of the route SHALL identify which end it concerns, so the message can say
whether the start or the destination is the problem. When the router found a usable road within the search
radius it reports, the failure SHALL carry the distance from that end to the nearest road it could use.

#### Scenario: The failing end is identified
- **WHEN** a calculation fails because the destination cannot be attached to the road network
- **THEN** the reported failure SHALL identify the destination as the affected end

#### Scenario: The distance to the nearest usable road is carried when known
- **WHEN** the router found no attachable road at the failing end and reports the search radius it used
- **THEN** the reported failure SHALL carry the distance from that end to the nearest road the router could use
- **AND** the message for that reason SHALL be able to state that distance
