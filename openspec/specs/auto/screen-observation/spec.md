# auto/screen-observation Specification

## Purpose
Defines the state-observation lifecycle of the Android Auto screens: how many instances of each
shared-state observation a car screen runs, when they stop, and what they are allowed to touch while
the screen is not visible.

## Requirements

### Requirement: One instance of each observation per started period

A car screen SHALL run each of its shared-state observations at most once per started period. The
observations are the GPS position feed, the navigation-state feed, favorites, the resolved day/night
presentation, basemap data revisions, and the periodic settings re-read. Stopping a screen SHALL
cancel all of its observations; starting it again SHALL establish exactly one instance of each,
independent of how many stop/start cycles the screen has already gone through.

#### Scenario: Screen stopped and started repeatedly

- **WHEN** a car map screen is stopped and started ten times in one session
- **THEN** exactly one instance of each observation is running after the tenth start, and one emitted GPS fix reaches the map renderer once

#### Scenario: Screen covered by a pushed screen

- **WHEN** a screen is covered by another screen being pushed and is then revealed again by popping it
- **THEN** the revealed screen runs exactly one instance of each observation, and no observation from the covered period is still running

#### Scenario: Screen destroyed

- **WHEN** a car screen is destroyed
- **THEN** none of its observations run any more, and none of them touches the map renderer or the car host afterwards

### Requirement: A stopped screen performs no renderer or host work

While a car screen is not started, none of its observations SHALL touch the map renderer gate or the
car host: no full-render request, no viewport or marker commit, no native map lookup, and no
template invalidation.

#### Scenario: GPS fix arrives while the screen is stopped

- **WHEN** a GPS fix arrives while a car map screen is stopped
- **THEN** the screen's renderer receives no marker or viewport update and no full render is requested by that screen

#### Scenario: Basemap data changes while the screen is stopped

- **WHEN** a basemap download, update or delete completes while a car map screen is stopped
- **THEN** the stopped screen requests no render, and the current basemap revision is applied once after the screen starts again

### Requirement: Observations are re-established with the current state on start

Re-establishing the observations on start SHALL apply the current value of each observed source, so
a change that happened while the screen was stopped is not lost, and each such value SHALL be applied
once per start.

#### Scenario: Favorites changed while the screen was stopped

- **WHEN** a favorite is added while a car map screen is stopped and the screen is then started
- **THEN** the screen's renderer receives the current favorite set once, and the map shows the added favorite

#### Scenario: Day/night presentation changed while the screen was stopped

- **WHEN** the host day/night state or the dark-mode preference changed while a car map screen was stopped and the screen is then started
- **THEN** the screen applies the changed presentation once, and an unchanged presentation is applied no more than once

### Requirement: Work that must continue while stopped is not scoped to the started period

Work that a car screen's own specification requires while the screen is stopped SHALL keep running
when the screen stops; only the state observations are scoped to the started period. In particular
the free-driving speed badge SHALL still decay a stale speed to zero while the screen is stopped.

#### Scenario: Background round trip during free driving with a stale fix

- **WHEN** free driving is active, the last GPS fix is older than the staleness window, and the app is backgrounded and returned
- **THEN** the speed badge shows zero after the return instead of the last speed before the background round trip

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
