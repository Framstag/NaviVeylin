# osmscout-jni

## ADDED Requirements

### Requirement: A calculated route publishes structured route steps

A calculated route SHALL publish one step per described route node, in route order, each carrying that node's
manoeuvre records, its node identity, its position, its distance and time from the route start, and the legs of
its own step — all as values, none as prose. A step SHALL state which manoeuvre it is, so a client can tell a
roundabout leave from a motorway change without reading text. A node with no description SHALL produce no step.

#### Scenario: Steps arrive in route order with their manoeuvre

- **WHEN** a route of 19 manoeuvres is calculated
- **THEN** the route SHALL publish one step per described node, in route order
- **AND** each step SHALL name its manoeuvre kind and carry the records of that manoeuvre
- **Case**: `app: RouteStepModelBridgeTest` "a calculated route publishes steps in route order"

#### Scenario: A roundabout leave states its exit ordinal and direction

- **WHEN** a route leaves a roundabout at the third exit, counter-clockwise
- **THEN** that step SHALL carry exit ordinal 3 and the counter-clockwise direction
- **AND** neither value SHALL have to be recovered from text
- **Case**: `app: RouteStepModelBridgeTest` "a roundabout leave step carries ordinal and direction"

#### Scenario: A motorway manoeuvre is distinguishable from a plain turn

- **WHEN** a route keeps left onto another motorway
- **THEN** the step SHALL report a motorway change with its move
- **AND** a client SHALL NOT have to tell it apart from a plain left turn by its text
- **Case**: `app: RouteStepModelBridgeTest` "a motorway change is not a plain turn"

#### Scenario: Each step carries the leg that ends at it

- **WHEN** a step's leg values are inspected
- **THEN** its leg distance and leg time SHALL describe the run from the preceding step's manoeuvre to its own
- **AND** the legs of all steps SHALL sum to the route's length
- **Case**: `app: RouteStepModelBridgeTest` "step legs sum to the route length"

#### Scenario: A step names the database its node belongs to

- **WHEN** a route crosses two map regions
- **THEN** each step SHALL carry its database identity
- **AND** the route SHALL publish the mapping from that identity to the map directory
- **Case**: `app: RouteStepModelBridgeTest` "steps of a cross-database route resolve their database"
