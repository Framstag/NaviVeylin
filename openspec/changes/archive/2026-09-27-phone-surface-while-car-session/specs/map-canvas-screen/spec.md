# Spec Delta

## ADDED Requirements

### Requirement: The phone map canvas is suspended while a car session is active

While a car session is active in the process and the user has not overridden it, the phone SHALL
present a car-session surface instead of the map canvas: the map canvas SHALL NOT be composed, the
phone surface SHALL request no map renders, and the phone-owned render storage (its bitmap tile cache
and its pooled render targets) SHALL be released. The shared native tile-data cache SHALL NOT be
released, because the car surface renders from it.

#### Scenario: A car session suspends the phone map

- **WHEN** a car session becomes active in the process while the phone map canvas is displayed
- **THEN** the map canvas SHALL stop being composed
- **AND** the phone SHALL request no further map renders
- **AND** the car-session surface SHALL be displayed in its place

#### Scenario: Phone-owned storage is released, the shared cache is not

- **WHEN** the phone map canvas is suspended by a car session
- **THEN** the phone's bitmap tile cache and its pooled render targets SHALL be released
- **AND** the shared native tile-data cache SHALL remain configured and populated for the car surface

#### Scenario: The car keeps rendering while the phone is suspended

- **WHEN** the phone map canvas is suspended and the car surface draws the navigation
- **THEN** the car frames SHALL continue to render without a surface or lock failure

#### Scenario: Ending the session returns the map

- **WHEN** the car session ends
- **THEN** the phone map canvas SHALL be displayed again
- **AND** the phone's mode, viewport and magnification SHALL be the ones it had before the suspension
- **AND** no error state SHALL be shown

#### Scenario: Suspension never loses the navigation state

- **WHEN** the phone map is suspended and the car session continues navigating
- **THEN** the phone's resumed map SHALL show the navigation state the shared engine currently holds

### Requirement: The car-session surface is informative and offers the map back

The car-session surface SHALL state that guidance is being shown on the car display and SHALL present
the current guidance summary from the shared navigation state, with the same labels the car surface
uses for the same state. It SHALL offer an explicit action that returns the phone to the map for the
rest of the session.

#### Scenario: The surface identifies the session and the guidance

- **WHEN** the car-session surface is displayed during an active navigation
- **THEN** it SHALL state that guidance is on the car display
- **AND** it SHALL show the current guidance summary using the labels the car surface shows
- **AND** the text SHALL be available in German and English

#### Scenario: The user brings the map back

- **WHEN** the user activates the surface's map action
- **THEN** the phone map canvas SHALL be displayed again for the rest of the session
- **AND** the phone SHALL render as it does without a car session

#### Scenario: The override does not outlive the session

- **WHEN** the user overrode the suspension and the car session then ends
- **THEN** the override SHALL be cleared
- **AND** a later car session SHALL suspend the phone map again by default

#### Scenario: The override is diagnosable

- **WHEN** the suspension is applied, overridden, or lifted
- **THEN** each transition SHALL be recorded on the diagnostics stream with the session presence it
  followed
