# gps-fix-quality Specification

## Purpose

Defines what "a GPS fix is available" means for the app: the accuracy tiers shown to the user and
the conditions under which a previously received fix stops counting as available, so the compass,
the re-center buttons, the speed widget and the search scoping cannot disagree about whether the app
currently has a trustworthy position.

## Requirements

### Requirement: Re-evaluation stays off the main dispatcher while the quality is unchanged

The re-evaluation of the fix quality that runs while the location source is silent SHALL NOT dispatch
onto the main dispatcher as long as the derived quality equals the published one: neither the
re-evaluation itself nor the value it publishes SHALL reach the main dispatcher in that case. A derived
quality that differs from the published one SHALL still reach every consumer through the single
publication path, so the re-evaluation stays observable without a new fix.

#### Scenario: Unchanged quality causes no main-dispatcher dispatch

- **WHEN** the location source is silent
- **AND** the re-evaluated quality equals the published quality
- **THEN** the re-evaluation SHALL NOT dispatch anything onto the main dispatcher
- **AND** the published quality SHALL remain unchanged
- **AND** this SHALL hold for every re-evaluation of a bounded sequence of them, not only the first

#### Scenario: A change still reaches the consumers

- **WHEN** the re-evaluated quality differs from the published quality
- **THEN** the new quality SHALL become visible to consumers through the same publication path a new
  fix uses
- **AND** a change that the location source cannot report (an aged-out fix, a disabled location
  service) SHALL become visible without a further location update

### Requirement: Fix availability and quality tiers

The system SHALL classify the current GPS fix into exactly one quality — GOOD, POOR or NONE — where:

- **NONE** means no fix is available: no fix has been received yet, the platform reports that
  location services are disabled, or the last received fix is older than the fix age limit;
- **POOR** means the last fix is available and its reported horizontal accuracy is worse than
  50 meters;
- **GOOD** means the last fix is available and its reported horizontal accuracy is at most
  50 meters.

A fix that has aged out SHALL NOT be reported as POOR or GOOD, and the classification SHALL NOT
depend on whether the fix source is currently delivering updates for a stationary vehicle.

#### Scenario: No fix received yet

- **WHEN** the app has not received any GPS fix since it started
- **THEN** the quality SHALL be NONE

#### Scenario: Good accuracy

- **WHEN** the last fix reports a horizontal accuracy of 10 meters
- **THEN** the quality SHALL be GOOD

#### Scenario: Accuracy exactly at the threshold

- **WHEN** the last fix reports a horizontal accuracy of exactly 50 meters
- **THEN** the quality SHALL be GOOD

#### Scenario: Poor accuracy

- **WHEN** the last fix reports a horizontal accuracy worse than 50 meters
- **THEN** the quality SHALL be POOR

#### Scenario: Fix ages out without a new fix

- **WHEN** the last fix was classified GOOD
- **AND** no new fix arrives
- **AND** the last fix becomes older than the fix age limit
- **THEN** the quality SHALL become NONE

#### Scenario: Location services switched off

- **WHEN** the last fix was classified GOOD
- **AND** the platform reports that location services are disabled
- **THEN** the quality SHALL become NONE without waiting for the fix age limit

#### Scenario: Stationary vehicle keeps its tier

- **WHEN** the provider delivers no new fix because the vehicle has not moved the requested minimum
  distance
- **AND** the last fix is younger than the fix age limit
- **THEN** the quality SHALL remain the tier of that fix's accuracy
- **AND** it SHALL NOT become NONE

#### Scenario: Quality recovers on the next fix

- **WHEN** the quality is NONE because the last fix aged out or the source was unavailable
- **AND** a new fix arrives within the configured accuracy
- **THEN** the quality SHALL become GOOD or POOR according to its accuracy
- **AND** this SHALL happen without an app restart, a screen change or user interaction

#### Scenario: Fix age limit window

- **WHEN** the fix age limit is configured
- **THEN** it SHALL be at least 30 seconds, so a stationary vehicle under the provider's
  minimum-distance throttling keeps its tier
- **AND** it SHALL be at most 60 seconds, so a lost signal is not presented as a good fix
  indefinitely

### Requirement: Quality is re-evaluated without a new fix

The system SHALL re-evaluate the quality while the location source is silent, so the classification
does not depend on a new fix being delivered. The re-evaluation SHALL publish a new value to
consumers within a bounded delay after a change becomes true, and SHALL NOT publish a value while
the derived quality is unchanged.

#### Scenario: Aged-out quality becomes visible without a new fix

- **WHEN** the last fix passes the fix age limit and no new fix is delivered
- **THEN** the quality SHALL become visible to consumers as NONE without any new location update

#### Scenario: A disabled source becomes visible without a new fix

- **WHEN** the platform reports that location services are disabled
- **THEN** the quality SHALL become visible to consumers as NONE without any new location update

#### Scenario: Unchanged quality causes no new publication

- **WHEN** the quality has been re-evaluated and the derived value is the same as the published one
- **THEN** no new quality value SHALL be published to consumers

### Requirement: One definition for every fix-quality consumer

Every surface whose behaviour depends on whether a GPS fix is available SHALL resolve that condition
from the quality defined here, so that a fix that aged out or whose source was disabled is treated
identically everywhere, and no surface can present a stale fix as available.

#### Scenario: Consumers agree on an unavailable fix

- **WHEN** the quality is NONE
- **THEN** the compass button fill SHALL use the no-fix hue family (spec: `compass-button`)
- **AND** the re-center buttons SHALL be hidden (specs: `map-recenter-button`, `map-modes`)
- **AND** the speed widget SHALL report no GPS availability (spec: `map-speed-widget`)
- **AND** search scoping and the distance reference SHALL fall back to their no-fix behaviour
  (specs: `location-search`, `search-result-ranking`)

#### Scenario: Consumers agree on an available fix

- **WHEN** the quality is GOOD or POOR
- **THEN** the compass button fill SHALL use the GOOD or POOR hue family respectively
- **AND** the location-dependent surfaces SHALL behave as if a fix is available
