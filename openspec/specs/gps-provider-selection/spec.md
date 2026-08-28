# gps-provider-selection Specification

## Purpose

Defines which location provider the app uses for GPS fixes: FusedLocationProviderClient when Google Play Services is available, LocationManager only as a strict fallback, never both simultaneously.

## Requirements

### Requirement: Fused provider preferred when Play Services available

When Google Play Services is available on the device, the system SHALL use `FusedLocationProviderClient` as the sole source of GPS location updates. The system SHALL NOT start `LocationManager` provider requests in this case.

#### Scenario: Play Services device uses Fused only

- **WHEN** Google Play Services is available and location permission is granted
- **THEN** the system SHALL request location updates from `FusedLocationProviderClient` only
- **THEN** the system SHALL NOT request updates from `LocationManager` providers

#### Scenario: Fused fix reaches consumers

- **WHEN** `FusedLocationProviderClient` delivers a location fix on a Play Services device
- **THEN** the fix SHALL be emitted to the shared location flow
- **THEN** no raw `LocationManager` fix SHALL be emitted in the same interval

### Requirement: LocationManager strict fallback without Play Services

When Google Play Services is not available, the system SHALL use `LocationManager` (GPS, NETWORK, and PASSIVE providers) as the sole source of GPS location updates.

#### Scenario: GMS-less device uses LocationManager

- **WHEN** Google Play Services is not available and location permission is granted
- **THEN** the system SHALL request updates from `LocationManager` GPS, NETWORK, and PASSIVE providers
- **THEN** the system SHALL NOT attempt to use `FusedLocationProviderClient`

#### Scenario: Fallback device still gets fixes

- **WHEN** a GMS-less device delivers a fix via any `LocationManager` provider
- **THEN** the fix SHALL be emitted to the shared location flow

### Requirement: No parallel provider operation

The system SHALL NOT run `FusedLocationProviderClient` and `LocationManager` provider requests simultaneously. Exactly one provider path SHALL be active at any time.

#### Scenario: Single active provider path

- **WHEN** location updates are started on any device
- **THEN** exactly one of the two provider paths (Fused or LocationManager) SHALL be active
- **THEN** the inactive path SHALL NOT deliver fixes to the shared location flow

### Requirement: Duplicate fixes filtered on fallback path

When the `LocationManager` fallback path is active and multiple providers deliver the same underlying fix, the system SHALL emit it only once.

#### Scenario: Same fix from multiple providers

- **WHEN** GPS and NETWORK providers deliver the same fix (identical timestamp and position)
- **THEN** the system SHALL emit the fix exactly once to the shared location flow

#### Scenario: Distinct fixes pass through

- **WHEN** two fixes differ in timestamp or position
- **THEN** the system SHALL emit both fixes to the shared location flow
