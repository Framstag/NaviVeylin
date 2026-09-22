# Spec Delta

## ADDED Requirements

### Requirement: An observation fault is confined to its observation

A fault inside a car screen's observation SHALL be confined to that observation: the failure SHALL be
logged, the observation SHALL end, the screen's other observations SHALL keep running, and the app
process SHALL survive. No exception from an observation body SHALL reach the process's main thread or
the scope that owns the car session. A screen that lost one observation SHALL still render, still
answer host callbacks and still build its templates.

#### Scenario: An observation body throws

- **WHEN** the body of one of a car screen's observations throws while the screen is started
- **THEN** the failure is logged with the observation's key, that observation ends, and the process keeps running

#### Scenario: Siblings keep running after a fault

- **WHEN** one observation of a started car screen throws and the screen receives a later update from another observed source
- **THEN** the surviving observations still deliver their updates, and the screen still renders and still answers host callbacks

#### Scenario: Faulting observation while navigating

- **WHEN** an observation of the car navigation screen throws while a route is active
- **THEN** guidance continues, trip metadata keeps being published, and the host session stays alive

#### Scenario: A fault does not consume the started period

- **WHEN** an observation threw during the current started period and the screen is stopped and started again
- **THEN** the observation is established again for the new period, alongside the other observations
