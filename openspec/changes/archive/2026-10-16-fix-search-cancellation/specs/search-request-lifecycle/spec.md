# Spec Delta

## Purpose

Gives one search request a lifecycle: it is superseded by newer input on its own surface or abandoned when
nobody wants it any more, and it stops with no result instead of running to completion.

## ADDED Requirements

### Requirement: One search slot per surface

Every search surface SHALL own exactly one search slot. Superseding, cancelling or abandoning a search SHALL
affect only the slot that was addressed, so a search running for one surface continues while another surface
searches.

#### Scenario: A car search does not stop a phone search

- **WHEN** a phone search is running and the car surface starts a search
- **THEN** the phone search SHALL keep running
- **AND** both surfaces SHALL publish their own results

#### Scenario: Supersede stays inside the slot

- **WHEN** two searches are started for the same slot
- **THEN** only the first SHALL be aborted

### Requirement: A request carries a generation

Each started search SHALL carry a generation that is current only until the next request of the same slot
begins. One predicate SHALL cover both supersede and cancel: a search is aborted when its generation is no
longer current, or when its breaker is aborted.

#### Scenario: A superseded generation reads as aborted

- **WHEN** a request of a slot has been replaced by a newer request of that slot
- **THEN** the older request's next abort check SHALL report it as aborted

#### Scenario: A cancelled generation reads as aborted

- **WHEN** the current generation of a slot is cancelled
- **THEN** that request's next abort check SHALL report it as aborted

### Requirement: A cancellation names a generation

A cancellation SHALL carry the generation it means. Cancelling a generation that is not the current one of its
slot SHALL NOT abort the current search of that slot, and SHALL report that it aborted nothing.

#### Scenario: A late cancel does not kill the live search

- **WHEN** a search was aborted by supersede and its caller's cancellation arrives after the newer search of that slot has started
- **THEN** the newer search SHALL keep running
- **AND** its result SHALL NOT be discarded

### Requirement: A superseded or abandoned search stops promptly

When a search is superseded or abandoned, the system SHALL abort it at its next abort check instead of letting
it run to completion. Abandoning SHALL be possible without starting a new search.

#### Scenario: Abandon without a successor

- **WHEN** a running search is abandoned and no new search is started for its slot
- **THEN** the search SHALL stop at its next abort check

#### Scenario: An aborted walk does not continue

- **WHEN** a search is aborted while it walks the installed databases
- **THEN** the databases not yet visited SHALL NOT be searched

### Requirement: An aborted search publishes no result

A search that was aborted SHALL return an empty result set rather than the entries gathered before the abort,
so a caller that ignores the generation cannot display a partial or stale list.

#### Scenario: An aborted search returns nothing

- **WHEN** a search is aborted mid-walk
- **THEN** it SHALL return no entries

### Requirement: The minimum query length is 2 characters

A surface SHALL run no search for a query shorter than 2 characters, SHALL abort a running search of its slot
when the query falls below that length, and SHALL display no result for it.

#### Scenario: A one-character query runs nothing

- **WHEN** the query is one character
- **THEN** no search SHALL be started
- **AND** no result of a previous query SHALL be displayed

#### Scenario: Falling below the minimum aborts the running search

- **WHEN** a search is running and the query is shortened below 2 characters
- **THEN** that search SHALL be aborted
- **AND** the results area SHALL be empty

### Requirement: The minimum length is the same on every surface

The minimum query length and the behaviour below it SHALL be identical on the phone and on the car.

#### Scenario: Car and phone agree below the minimum

- **WHEN** a one-character query is typed on the car search template and on the phone search dialog
- **THEN** neither surface SHALL run a search
- **AND** neither SHALL display results

### Requirement: Search abort is diagnosable

Each completed search SHALL be reported on one diagnostics line carrying the surface's slot, the request's
generation, whether the search was aborted, and its native duration. No line SHALL carry a coordinate.

#### Scenario: An aborted search is visible in the log

- **WHEN** a search of a known generation is aborted
- **THEN** one line SHALL report the slot, the generation, that it was aborted, and the elapsed native time

#### Scenario: No coordinate on the line

- **WHEN** the search line is written
- **THEN** it SHALL carry no latitude, longitude or map position
