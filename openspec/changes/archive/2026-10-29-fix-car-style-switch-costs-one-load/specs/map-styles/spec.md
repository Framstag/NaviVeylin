# Spec Delta — map-styles

## MODIFIED Requirements

### Requirement: A style switch loads the stylesheet set once

A style switch SHALL cost one load of the stylesheet set. The native load installs the new style together with
the stylesheet flags that are currently enabled (`daylight` included), so the app SHALL NOT apply a flag after a
load it performed. The day/night flag SHALL be applied once per changed value, and its first application SHALL
wait for a style load that succeeded — the proof that the map databases are open.

#### Scenario: Car style switch loads the stylesheet set once

- **WHEN** the shared map style changes and a started car map screen applies it
- **THEN** the stylesheet set is loaded once for the new style
- **AND** no stylesheet flag is applied after that load
- **Case**: `auto: MapScreenTest` "a style switch loads the stylesheet set once"

#### Scenario: A flag requested before the databases are open is applied once they are

- **WHEN** a started car map screen resolves a day/night value and no style load has succeeded yet
- **THEN** no stylesheet flag is applied
- **AND** the flag is applied once, after the first style load that succeeds
- **Case**: `auto: MapScreenTest` "the first flag push waits for the databases and lands once"
