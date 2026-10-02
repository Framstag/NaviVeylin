# location-updates-lease Specification

## Purpose

Defines how several consumers in one app process share the device's location updates: subscriptions
are leased by named consumers, updates run while at least one lease is held, and one consumer's release
never silences another.

## Requirements

### Requirement: Location updates are leased, not toggled globally

The system SHALL provide a location-update lease that a consumer acquires and releases by name, and
SHALL request device location updates while at least one lease is held. A bare start/stop pair that
affects every consumer SHALL NOT be the API surfaces use.

#### Scenario: Updates start with the first lease

- **WHEN** the first consumer acquires a lease and permission is granted
- **THEN** device location updates are requested

#### Scenario: Updates stop with the last release

- **WHEN** the last remaining lease is released
- **THEN** device location updates are stopped
- **AND** no further fixes are requested on that consumer's behalf

#### Scenario: Only one provider path is ever active

- **WHEN** several leases are held at the same time
- **THEN** exactly one provider path (Fused or LocationManager) is active, as today
- **AND** no additional provider request is made per lease

### Requirement: One consumer's release never silences another

Releasing a lease SHALL stop location updates only when no other lease remains. A consumer that stops
its own use of location SHALL NOT stop fixes another consumer still relies on.

#### Scenario: Phone UI pauses while the car is driving

- **WHEN** the phone map leaves the foreground and releases its lease
- **AND** a car session or active navigation still holds a lease
- **THEN** location updates continue
- **AND** the car keeps receiving fixes

#### Scenario: Car session ends while the phone map is visible

- **WHEN** the car session releases its lease
- **AND** the phone map still holds its lease
- **THEN** location updates continue for the phone

#### Scenario: Repeated acquire is not a double subscription

- **WHEN** the same consumer acquires a lease while it already holds one
- **THEN** the device request is not started twice
- **AND** the consumer's single release does not stop updates another consumer still needs

### Requirement: A lease is released when its owner ends

A lease SHALL be released when its owning component is destroyed or its work ends intentionally. A
destroyed surface SHALL NOT keep location updates running.

#### Scenario: Destroyed surface releases its lease

- **WHEN** a surface or component that owns a lease is destroyed while holding it
- **THEN** its lease is released
- **AND** updates stop if no other lease remains

#### Scenario: Process death leaves nothing running

- **WHEN** the app process ends
- **THEN** no location request of the app remains registered

### Requirement: Lease state is diagnosable

Every lease acquire and release SHALL be recorded in the diagnostics stream with the consumer name and
the resulting number of held leases, so a missing or unexpectedly retained subscription can be
identified from a log without a debugger.

#### Scenario: Acquire and release are recorded

- **WHEN** a consumer acquires and later releases a lease
- **THEN** both events appear in the diagnostics stream with the consumer name and lease count

#### Scenario: Last release is visible as the stop decision

- **WHEN** the last lease is released
- **THEN** the diagnostics stream shows the stop with the consumer that caused it
