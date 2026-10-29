# Spec Delta — osmscout-jni

## ADDED Requirements

### Requirement: The published route starts at the requested start position

A calculated route SHALL publish the caller's requested start as its first polyline point and as its
first instruction's position, not the node the router snapped that start to. The routed geometry from the
snapped node onward SHALL be unchanged.

#### Scenario: An off-network start is published as the requested position

- **WHEN** a route is calculated from a start position that lies off the routable network, so the router
  snaps it to a node
- **THEN** the published polyline's first point SHALL be the requested start position
- **AND** the position published for the first instruction line SHALL be that same requested start
  position
- **AND** the second and later polyline points SHALL be the routed geometry, unchanged
  (case: `RouteStartPositionDeviceTest.publishedStartIsTheRequestedStartPosition`)

#### Scenario: A start on the network is published unchanged

- **WHEN** a route is calculated from a start position that lies on the routable network within the snap
  tolerance
- **THEN** the published polyline's first point SHALL be the routed first vertex
- **AND** the polyline SHALL NOT carry an additional first point and SHALL NOT carry a zero-length
  leading segment
  (case: `RouteStartPositionDeviceTest.routeWithAnOnNetworkStartIsPublishedUnchanged`)

#### Scenario: A start given as an object is published as that object's position

- **WHEN** a route is calculated from an object reference whose position is off the routable network
- **THEN** the published polyline's first point and the first instruction's position SHALL both be that
  object's position
  (case: `RouteStartPositionDeviceTest.startFromAnObjectOffsetIsPublished`)

#### Scenario: The first instruction position lies on the published polyline

- **WHEN** a route with a snapped start is calculated
- **THEN** the position published for the first instruction line SHALL lie on the drawn polyline within
  the polyline-match tolerance
  (case: `RouteStartPositionDeviceTest.firstStepPositionLiesOnThePolyline`)

#### Scenario: The start is published without a coordinate in the diagnostics

- **WHEN** the bridge publishes a route whose start was snapped
- **THEN** the diagnostic line it emits SHALL report the approach in metres and SHALL NOT contain any
  coordinate
  (case: `checkNoCoordinatesInLogs` build gate over `OSMScoutClient.cpp`)

## MODIFIED Requirements

### Requirement: The published route length is a length of that route

The length a calculated route publishes SHALL be derived from the route's own geometry or from its
description. It SHALL NOT be the straight-line (air-line) distance between the route's start and target.
Where both are available they SHALL agree, since one route has one length of road.

#### Scenario: A published length tracks the route that was drawn

- **WHEN** a calculated route's published total distance and its published polyline are read
- **THEN** the total SHALL agree with the great-circle length of that polyline within the description's
  own agreement with it (measured 2026-10-05: description over polyline 1.0014 / 1.0021 / 0.9964)
- **AND** the total SHALL NOT be the start-to-target air-line estimate (measured 2026-10-05: 0.748 /
  0.795 / 0.552 of the polyline, deficits 24.5 km / 4.3 km / 0.7 km)

#### Scenario: A long intercity route carries a real length

- **WHEN** a long intercity route (tens of kilometres) is calculated
- **THEN** the published total SHALL lie within a few percent of the drawn route's length
- **AND** it SHALL NOT be closer to the great-circle distance between its endpoints than to the drawn
  length (the ~70 km case: 72 771 m published against 97 283 m drawn and ~66 km air-line)

#### Scenario: A route without a description still carries a route length

- **WHEN** a calculated route exposes no per-step description, so the per-step sum cannot state its length
- **THEN** the route's published total SHALL still be a length of the route it publishes, not the
  start-to-target estimate
- **AND** a client that falls back to that total SHALL obtain a route length

#### Scenario: A snapped start does not split one route into two lengths

- **WHEN** a route whose start was snapped from a requested position is read
- **THEN** the published total, the sum of the per-step distances and the drawn polyline's length SHALL
  all include the approach from the requested start to the snapped node
- **AND** the total and the sum of the per-step distances SHALL still agree within rounding
- **AND** the total SHALL NOT be published without the approach while the drawn polyline carries it
  (case: `RouteStartPositionDeviceTest.snappedStartKeepsOneRouteLength`)
