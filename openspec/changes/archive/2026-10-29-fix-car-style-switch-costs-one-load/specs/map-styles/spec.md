# Spec Delta — map-styles

## MODIFIED Requirements

### Requirement: A style switch loads the stylesheet set once

A style switch SHALL cost one load of the stylesheet set. The native load installs the new style together with
the stylesheet flags that are currently enabled (`daylight` included), so the app SHALL NOT apply a flag after a
load it performed. The day/night flag SHALL be applied once per changed value, and SHALL NOT be held back until
a style load has succeeded: the native side stores it before any database is open and installs it when one opens.

#### Scenario: Car style switch loads the stylesheet set once

- **WHEN** the shared map style changes and a started car map screen applies it
- **THEN** the stylesheet set is loaded once for the new style
- **AND** no stylesheet flag is applied after that load
- **Case**: `auto: MapScreenTest` "a style switch loads the stylesheet set once"

#### Scenario: A flag requested before the databases are open is applied once they are

- **WHEN** a started car map screen resolves a day/night value before any style load has succeeded
- **THEN** the stylesheet flag is applied once, without waiting for a style load
- **Case**: `auto: MapScreenTest` "the flag is applied before any style load succeeds"
