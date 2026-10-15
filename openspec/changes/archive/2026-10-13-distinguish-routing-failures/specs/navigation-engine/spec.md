# Spec Delta

## MODIFIED Requirements

### Requirement: Errors carry the surface that caused them

An error raised on behalf of a surface SHALL name that surface, and a surface SHALL present only errors
originating from itself or from the engine as a whole — never an error another surface caused. A surface
that is presenting navigation SHALL present an engine-wide error; a surface SHALL NOT leave an engine-wide
error unshown while it is the surface the user is looking at.

#### Scenario: Car deep-link failure does not appear on the phone
- **WHEN** a car-only destination request fails to resolve and raises an error
- **THEN** the car presents the error
- **AND** the phone navigation UI does not show that error

#### Scenario: Engine-wide error reaches every surface
- **WHEN** the engine raises an error that is not tied to one surface (e.g. route calculation failed)
- **THEN** every surface that is presenting navigation shows the error

#### Scenario: Engine-wide error reaches the phone
- **WHEN** the engine raises an engine-wide error while the phone is presenting navigation
- **THEN** the phone SHALL show that error
- **AND** the phone SHALL NOT depend on a route-panel interaction having started the calculation

#### Scenario: Engine-wide error raised with no surface presenting navigation
- **WHEN** the engine raises an engine-wide error and no surface is presenting navigation
- **THEN** no surface SHALL show it, and the error SHALL be observable in the shared navigation state
