# osmscout-jni

## ADDED Requirements

### Requirement: Live guidance publishes route steps

Live guidance SHALL publish, on every position update, the step of the manoeuvre ahead as the same structured
record a calculated route publishes, extended with the distance and time remaining to that manoeuvre and, when
the following manoeuvre is within the close-manoeuvre window, that manoeuvre as a nested step. It SHALL publish
no guidance text.

#### Scenario: The step ahead carries its manoeuvre and remaining distance

- **WHEN** the vehicle is 300 m before a roundabout exit while navigating
- **THEN** the published next step SHALL describe that roundabout leave with its exit ordinal
- **AND** SHALL report 300 m, within the tolerance of the position's progress, as remaining distance
- **Case**: `app(device): RouteStepLiveGuidanceDeviceTest` "the step ahead carries its manoeuvre and remaining distance"

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

## MODIFIED Requirements

### Requirement: A route instruction carries the roundabout exit ordinal

The navigation bridge SHALL expose, on every published route step that leaves a roundabout, the ordinal of the
exit the route takes: its 1-based position counted in circulation order. It SHALL be readable without parsing
any text, because no text is published. A step that is not a roundabout manoeuvre SHALL NOT carry an ordinal.

#### Scenario: A roundabout exit instruction carries its ordinal

- **WHEN** a route leaves a roundabout at the third exit
- **THEN** the step for that manoeuvre SHALL carry exit ordinal 3
- **AND** the ordinal SHALL NOT have to be recovered from any description text
- **Case**: `app(device): RouteInstructionRoundaboutDeviceTest` "a roundabout exit instruction carries its ordinal"

#### Scenario: A non-roundabout instruction carries no exit ordinal

- **WHEN** the step is a plain turn, a motorway manoeuvre, the start or the arrival
- **THEN** its exit ordinal SHALL be absent
- **AND** a surface reading the ordinal SHALL be able to tell "no exit" from "exit 1"
- **Case**: `osmscout-client-java: RouteInstructionFieldsTest` "a plain turn carries no exit ordinal"

#### Scenario: The instruction text is unchanged

- **WHEN** a roundabout exit step carries its ordinal
- **THEN** the bridge SHALL publish no description or short description for that manoeuvre
- **AND** the ordinal SHALL be the only thing a client reads it from
- **Case**: `osmscout-client-java: RouteInstructionFieldsTest` "the native roundabout text is unchanged"

### Requirement: A route instruction carries the roundabout circulation direction

The navigation bridge SHALL expose whether the roundabout's traffic circulates clockwise, on every published
route step that leaves a roundabout, and SHALL NOT leave it to be inferred from text.

#### Scenario: The circulation direction is carried

- **WHEN** a route leaves a roundabout whose traffic circulates counter-clockwise
- **THEN** the step SHALL report the circulation direction as counter-clockwise
- **Case**: `osmscout-client-java: RouteInstructionFieldsTest` "a counter-clockwise instruction reports its direction"

#### Scenario: A counter-clockwise roundabout is not reported as clockwise

- **WHEN** a route leaves a clockwise roundabout and another route leaves a counter-clockwise one
- **THEN** the two steps SHALL report different circulation directions
- **Case**: `app(device): RouteInstructionRoundaboutDeviceTest` "circulation direction follows the roundabout"

### Requirement: The next-next hint carries its own exit ordinal

The bridge SHALL expose the roundabout exit ordinal of the manoeuvre the "next next" hint describes inside the
nested step that carries the hint, separately from the enclosing step's own exit ordinal, so two roundabouts in a
row can be told apart.

#### Scenario: The hint's ordinal is its own

- **WHEN** a step leaves a roundabout at exit 3 and its next-next hint is a roundabout exit taken at
  exit 1
- **THEN** the enclosing step's own exit ordinal SHALL be 3
- **AND** the nested hint step's exit ordinal SHALL be 1, not the enclosing step's
- **Case**: `osmscout-client-java: RouteInstructionFieldsTest` "the next-next hint carries its own exit ordinal"

#### Scenario: No hint means no hint ordinal

- **WHEN** a step carries no next-next hint
- **THEN** no nested step SHALL be published for it
- **AND** its own exit ordinal SHALL be distinguishable from exit 1
- **Case**: `osmscout-client-java: RouteInstructionFieldsTest` "no exit is distinguishable from exit one"

### Requirement: An unbuildable instruction never reaches a listener as null

The navigation bridge SHALL deliver only steps it built: a delivered step list SHALL contain no null element, and
when it could build none of a list's steps it SHALL deliver no list at all. A listener SHALL NOT have to defend
against a null element, because one surfaces far from the bridge where its cause is no longer visible.

#### Scenario: Every delivered instruction is non-null

- **WHEN** a route is calculated and navigation starts, including when the bridge cannot build a step
- **THEN** every step the listener receives SHALL be non-null
- **AND** the bridge SHALL deliver no step list at all rather than a list whose elements are null
- **Case**: `app(device): RouteInstructionRoundaboutDeviceTest` "a route start delivers a non-empty instruction list of non-null instructions"

#### Scenario: A method lookup that fails degrades instead of aborting

- **WHEN** a class or method the bridge looks up cannot be resolved, including a descriptor that matches no
  constructor of the Java class it names
- **THEN** the lookup SHALL leave a null method id and SHALL NOT leave an exception pending
- **AND** the bridge SHALL degrade to delivering no step list rather than aborting the process, because
  a pending lookup exception aborts on the *next* JNI call and so reaches the guard before it can run
- **Case**: `app(device): RouteInstructionRoundaboutDeviceTest` "a route start delivers a non-empty instruction list of non-null instructions", falsified by the task-7.9 descriptor mutation — with the descriptor mutated to `…;III)V` and no exception clear, the abort landed on the following `FindClass` for `TurnType` and the instrumentation reported `Process crashed`; with the clear in place the same mutation fails the case with "the route was calculated but the bridge delivered no instruction list"
