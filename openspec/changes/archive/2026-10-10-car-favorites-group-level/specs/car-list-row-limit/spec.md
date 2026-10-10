# Spec Delta — car-list-row-limit

## Purpose

Keeps every row of a car list reachable: a car screen never presents more rows than the connected host will display, and the items beyond that capacity stay reachable through a further page. Car hosts impose this ceiling (a phone screen has no equivalent limit), so the capability is car-only by platform constraint.

## ADDED Requirements

### Requirement: A car list never presents more rows than the host displays

A car list screen SHALL present at most as many rows as the connected host displays for that content type, and SHALL take that capacity from the host rather than from a fixed constant. A host that cannot be asked SHALL fall back to the Car App Library's own default capacity instead of an unbounded list.

#### Scenario: The list fits the host's capacity

- **WHEN** a car list holds more items than the host's reported capacity
- **THEN** the template SHALL carry at most that many rows
- **AND** no item SHALL be dropped without the next page offering it

#### Scenario: The capacity follows the host

- **WHEN** the host reports a capacity larger than the library's default
- **THEN** the screen SHALL present up to the host's capacity

#### Scenario: A host that cannot be asked uses the library's default

- **WHEN** the connected host does not support the capacity query
- **THEN** the screen SHALL use the Car App Library's default capacity for a list rather than presenting an unbounded list

#### Scenario: Capacity is re-read per screen

- **WHEN** two car list screens are shown in the same session
- **THEN** each SHALL use the capacity the host reports for its own content type and moment, rather than a value cached from the first

### Requirement: Items beyond the capacity stay reachable

A car list whose items exceed the host's capacity SHALL offer the remaining items through a clickable final row that opens the next page in the same order, so every item can be reached while driving, and the way back to the previous page SHALL exist.

#### Scenario: The final row opens the next page

- **WHEN** the list's items exceed the capacity
- **THEN** its final row SHALL open the next page
- **AND** that page SHALL begin with the first item the previous page did not show

#### Scenario: Every item is reachable exactly once

- **WHEN** the driver follows the page rows to the end of the list
- **THEN** every item SHALL have been presented, each on exactly one page

#### Scenario: The last page offers no further row

- **WHEN** the shown page holds the list's last items
- **THEN** no page row SHALL be presented

#### Scenario: Back navigation returns to the previous page

- **WHEN** the driver is on a page opened by a page row
- **THEN** the host's back navigation SHALL return to the page that opened it
