# Spec Delta — gps-fix-quality

## ADDED Requirements

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
