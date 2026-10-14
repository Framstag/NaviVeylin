# Spec Delta

## MODIFIED Requirements

### Requirement: Car hint content

The car rendering of the hint SHALL show the manoeuvre instruction as the primary text, the distance to
the manoeuvre and the arrival time as secondary text, and the manoeuvre direction as a large icon. For a
roundabout exit the large icon SHALL carry the exit ordinal as well as the direction, because this surface
takes a bitmap rather than a host-drawn manoeuvre and therefore cannot fall back on the host's own numbered
roundabout. The instruction wording SHALL be the same wording the on-screen and phone displays use (label
parity); the car MAY use different text roles (instruction first) than the phone notification, which keeps the
destination name first.

#### Scenario: Instruction is the primary car text

- **WHEN** a next manoeuvre is available while navigating
- **THEN** the car hint's primary text is the manoeuvre instruction with the same
  wording as the on-screen next-turn display

#### Scenario: Distance and arrival time as secondary text

- **WHEN** the car hint is rendered while navigating
- **THEN** its secondary text contains the distance to the manoeuvre and the arrival
  time

#### Scenario: Maneuver direction as large icon

- **WHEN** the car hint is rendered while navigating
- **THEN** a large icon shows the manoeuvre direction (turn arrow)

#### Scenario: A roundabout exit hint icon shows the exit number

- **WHEN** the car hint is rendered for a roundabout exit carrying an ordinal
- **THEN** its large icon SHALL carry that exit's ordinal
- **AND** the ordinal SHALL be the one the hint's primary text names
- **Case**: `app: NavigationNotificationBuilderTest` "the car hint icon shows the roundabout exit number"

#### Scenario: A roundabout exit hint without an ordinal

- **WHEN** the car hint is rendered for a roundabout exit with no ordinal
- **THEN** its large icon SHALL be the plain roundabout leave arrow it showed before the number existed
- **Case**: `app: NavigationNotificationBuilderTest` "a car hint for a roundabout exit without an ordinal keeps the plain arrow"

#### Scenario: Neutral hint without a maneuver

- **WHEN** navigation is active but no next manoeuvre is available (for example while
  rerouting or before the first instruction)
- **THEN** the car hint shows neutral navigation text instead of a manoeuvre and
  does not crash
