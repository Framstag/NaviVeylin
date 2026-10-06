# Spec Delta

## MODIFIED Requirements

### Requirement: Route acquisition independent of a surface UI

The engine SHALL acquire a route on request, both when a surface UI drives the acquisition (vehicle
profile, alternatives) and when no surface UI is available, and SHALL re-acquire on reroute with the
vehicle profile the active navigation started with. An acquisition whose result carries no usable
polyline coordinates SHALL NOT fail the surface that reflects it: the surface's route views SHALL be
left without geometry while navigation continues.

#### Scenario: Reroute without a surface UI

- **WHEN** the engine detects a confirmed off-route condition in a process with no phone screen present
- **THEN** the engine re-acquires a route to the retained destination with the retained vehicle profile
- **AND** navigation continues on the new route without any surface action

#### Scenario: Surface-driven acquisition

- **WHEN** a surface acquires a route with a chosen vehicle profile
- **THEN** the engine starts navigation with that route and profile
- **AND** the surface's route views reflect the acquired route

#### Scenario: Acquisition without usable polyline geometry

- **WHEN** the engine acquires a route whose polyline coordinates are absent
- **THEN** navigation SHALL continue on that route with its destination and vehicle profile retained
- **AND** an observing surface SHALL adopt the acquisition without raising an exception
- **AND** that surface SHALL publish no route geometry for drawing
