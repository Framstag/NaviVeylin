# Spec Delta — map-styles

## ADDED Requirements

### Requirement: A style switch loads the stylesheet set once

A style switch SHALL cost one load of the stylesheet set. The native load installs the new style together with
the stylesheet flags that are currently enabled (`daylight` included), so the app SHALL NOT apply a stylesheet
flag after a load it performed: the flag is a property of the load, not a second call. Changing the day/night
value SHALL still apply the flag once, and a flag application SHALL NOT be re-issued while the value is
unchanged.

#### Scenario: Car style switch loads the stylesheet set once

- **WHEN** the shared map style changes and a started car map screen applies it
- **THEN** the stylesheet set is loaded once for the new style
- **AND** no stylesheet flag is applied after that load
- **AND** the map keeps the day/night variant that was in effect before the switch
- **Case**: `auto: MapScreenTest` "a style switch loads the stylesheet set once"
