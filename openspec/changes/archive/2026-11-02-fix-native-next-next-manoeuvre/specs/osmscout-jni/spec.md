## MODIFIED Requirements

### Requirement: Live guidance publishes route steps

Live guidance SHALL publish, on every position update, the step of the manoeuvre ahead as the same structured
record a calculated route publishes, extended with the distance and time remaining to that manoeuvre and, when
the following manoeuvre is within the close-manoeuvre window, that manoeuvre as a nested step. It SHALL publish
no guidance text. A described route node SHALL never be published as the manoeuvre ahead or as its nested
step.

#### Scenario: The step ahead carries its manoeuvre and remaining distance

- **WHEN** the vehicle is 300 m before a roundabout exit while navigating
- **THEN** the published next step SHALL describe that roundabout leave with its exit ordinal
- **AND** SHALL report 300 m, within the tolerance of the position's progress, as remaining distance
- **Case**: `app(device): RouteStepModelBridgeTest` "a roundabout leave step carries ordinal and direction"

#### Scenario: A described route node is never the step ahead

- **WHEN** the next described route node carries only a name and a way type and the next manoeuvre lies further ahead
- **THEN** the published step SHALL describe that manoeuvre, not the node
- **AND** the node SHALL NOT be the published step's nested step either
- **Case**: `libosmscout: RouteStepModelTest` "The step ahead is the next manoeuvre, not the next described node"

#### Scenario: A manoeuvre beyond the initial look-ahead is still found

- **WHEN** a long leg carries no manoeuvre within the initial look-ahead distance
- **THEN** the published step SHALL be the manoeuvre that lies further ahead
- **AND** the arrival SHALL be published only when no manoeuvre follows the position
- **Case**: `libosmscout: RouteStepModelTest` "A manoeuvre beyond the collect window is still the step ahead"

#### Scenario: A published step carries no guidance text

- **WHEN** any step is published to a listener
- **THEN** it SHALL carry no description string for its manoeuvre
- **AND** a client SHALL be able to word it from the values alone
- **Case**: `osmscout-client-java: RouteStepModelBridgeTest` "a published step carries no guidance text"

#### Scenario: The next-next hint is a nested step

- **WHEN** two manoeuvres are 120 m apart and the first is ahead of the vehicle
- **THEN** the published step ahead SHALL carry the second manoeuvre as a nested step
- **AND** that nested step SHALL carry its own kind, exit ordinal and circulation direction
- **Case**: `app(device): RouteStepLiveGuidanceDeviceTest` "the next-next hint is a nested step"

#### Scenario: A route change publishes the full step list

- **WHEN** navigation starts on a calculated route, and again when a reroute replaces it
- **THEN** the listener SHALL receive that route's steps in route order
- **AND** the list SHALL NOT contain an element that is null
- **Case**: `app(device): RouteStepLiveGuidanceDeviceTest` "a route change publishes the full step list"

#### Scenario: The published list follows the position

- **WHEN** the vehicle passes the manoeuvre the published list begins with
- **THEN** a later publication SHALL begin with the next manoeuvre
- **AND** the passed manoeuvre SHALL no longer be published as the next step
- **Case**: `app: NavigationEngineStepIndexTest` "the published step advances with the position"
