# Spec Delta

## ADDED Requirements

### Requirement: A failed route attempt releases only the lease it took

The engine SHALL release a location lease for a route attempt only when that attempt took the lease itself. A
route attempt that fails while navigation is already active and that reused the running navigation's lease
SHALL NOT release it, so guidance keeps receiving position updates; the failure SHALL still be published to the
surfaces. An attempt that took the lease for its own start position (a surface-less acquisition) SHALL release
it on failure, as it already does on cancellation.

#### Scenario: Failed reroute keeps the running navigation's lease

- **WHEN** navigation is active and the route calculation of a reroute fails
- **THEN** the running navigation's location lease SHALL stay held
- **AND** position updates SHALL keep reaching the guidance
- **AND** the failure SHALL still be published as an engine error

#### Scenario: Failed surface-less acquisition releases its own lease

- **WHEN** a route attempt that took the location lease to obtain its start position fails while no navigation
  is active
- **THEN** the lease taken for that attempt SHALL be released
