# Spec Delta

## ADDED Requirements

### Requirement: A failure from native code is worded by the surface

A failure originating in native code SHALL reach a surface as an identifier, never as user-facing text.
The surface SHALL render the message from its own string resources in the device language, and native code
SHALL NOT supply the user-facing wording of a failure.

#### Scenario: A native failure renders in the device language
- **WHEN** a native component fails and the device locale is German
- **THEN** the message the user reads SHALL be the German string resource for that failure
- **AND** it SHALL NOT be a sentence produced in native code

#### Scenario: No native prose reaches a text position
- **WHEN** the modules that render user-facing text are built
- **THEN** no user-facing text position SHALL hold a string that originated in native code
- **AND** every such position SHALL resolve to a string resource of the surface that renders it

#### Scenario: An unknown failure identifier still has wording
- **WHEN** a failure arrives with an identifier the surface does not know
- **THEN** the surface SHALL show the string resource for an unspecified failure
- **AND** it SHALL NOT show the identifier itself
