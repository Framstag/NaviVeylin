# Spec Delta

## ADDED Requirements

### Requirement: A route instruction carries the roundabout exit ordinal

The navigation bridge SHALL expose, on every route instruction, the ordinal of the roundabout exit the route
takes: its 1-based position counted in circulation order. It SHALL be readable without parsing the
instruction's English text. An instruction that is not a roundabout manoeuvre SHALL NOT carry an ordinal.

#### Scenario: A roundabout exit instruction carries its ordinal

- **WHEN** a route leaves a roundabout at the third exit
- **THEN** the instruction for that manoeuvre SHALL carry exit ordinal 3
- **AND** the ordinal SHALL NOT have to be recovered from the instruction's description text
- **Case**: `app(device): RouteInstructionRoundaboutDeviceTest` "a roundabout exit instruction carries its ordinal"

#### Scenario: A non-roundabout instruction carries no exit ordinal

- **WHEN** the instruction is a plain turn, a motorway manoeuvre, the start or the arrival
- **THEN** its exit ordinal SHALL be absent
- **AND** a surface reading the ordinal SHALL be able to tell "no exit" from "exit 1"
- **Case**: `osmscout-client-java: RouteInstructionFieldsTest` "a plain turn carries no exit ordinal"

#### Scenario: The instruction text is unchanged

- **WHEN** a roundabout exit instruction carries its ordinal
- **THEN** its description and short description SHALL still read as they read before this field existed
- **AND** a caller that reads only those strings SHALL observe no change
- **Case**: `osmscout-client-java: RouteInstructionFieldsTest` "the native roundabout text is unchanged"

### Requirement: A route instruction carries the roundabout circulation direction

The navigation bridge SHALL expose whether the roundabout's traffic circulates clockwise, on every route
instruction, and SHALL NOT leave it to be inferred from the instruction text.

#### Scenario: The circulation direction is carried

- **WHEN** a route leaves a roundabout whose traffic circulates counter-clockwise
- **THEN** the instruction SHALL report the circulation direction as counter-clockwise
- **Case**: `osmscout-client-java: RouteInstructionFieldsTest` "a counter-clockwise instruction reports its direction"

#### Scenario: A counter-clockwise roundabout is not reported as clockwise

- **WHEN** a route leaves a clockwise roundabout and another route leaves a counter-clockwise one
- **THEN** the two instructions SHALL report different circulation directions
- **Case**: `app(device): RouteInstructionRoundaboutDeviceTest` "circulation direction follows the roundabout"

### Requirement: The next-next hint carries its own exit ordinal

The bridge SHALL expose the roundabout exit ordinal of the manoeuvre the "next next" hint describes, as a field
of the instruction that carries the hint and separately from that instruction's own exit ordinal, so two
roundabouts in a row can be told apart.

#### Scenario: The hint's ordinal is its own

- **WHEN** an instruction leaves a roundabout at exit 3 and its next-next hint is a roundabout exit taken at
  exit 1
- **THEN** the instruction's own exit ordinal SHALL be 3
- **AND** the hint's exit ordinal SHALL be 1, not the instruction's
- **Case**: `osmscout-client-java: RouteInstructionFieldsTest` "the next-next hint carries its own exit ordinal"

#### Scenario: No hint means no hint ordinal

- **WHEN** an instruction carries no next-next hint
- **THEN** its hint exit ordinal SHALL be absent
- **Case**: `osmscout-client-java: RouteInstructionFieldsTest` "no exit is distinguishable from exit one"

### Requirement: An unbuildable instruction never reaches a listener as null

The navigation bridge SHALL deliver only instructions it built: a delivered instruction list SHALL contain no
null element, and when it could build none of a list's instructions it SHALL deliver no list at all. A listener
SHALL NOT have to defend against a null element, because one surfaces far from the bridge where its cause is
no longer visible.

#### Scenario: Every delivered instruction is non-null

- **WHEN** a route is calculated and navigation starts, including when the bridge cannot build an instruction
- **THEN** every instruction the listener receives SHALL be non-null
- **AND** the bridge SHALL deliver no instruction list at all rather than a list whose elements are null
- **Case**: `app(device): RouteInstructionRoundaboutDeviceTest` "a route start delivers a non-empty instruction list of non-null instructions"

#### Scenario: A method lookup that fails degrades instead of aborting

- **WHEN** a class or method the bridge looks up cannot be resolved, including a descriptor that matches no
  constructor of the Java class it names
- **THEN** the lookup SHALL leave a null method id and SHALL NOT leave an exception pending
- **AND** the bridge SHALL degrade to delivering no instruction list rather than aborting the process, because
  a pending lookup exception aborts on the *next* JNI call and so reaches the guard before it can run
- **Case**: `app(device): RouteInstructionRoundaboutDeviceTest` "a route start delivers a non-empty instruction list of non-null instructions", falsified by the task-7.9 descriptor mutation — with the descriptor mutated to `…;III)V` and no exception clear, the abort landed on the following `FindClass` for `TurnType` and the instrumentation reported `Process crashed`; with the clear in place the same mutation fails the case with "the route was calculated but the bridge delivered no instruction list"
