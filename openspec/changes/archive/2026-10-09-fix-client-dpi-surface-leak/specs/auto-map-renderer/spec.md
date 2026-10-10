# Spec Delta

## ADDED Requirements

### Requirement: Car renders carry the surface DPI

The car map renderer SHALL pass the DPI delivered with the car surface on every native render request it issues, and surface delivery SHALL NOT mutate client-wide render state.

- The DPI SHALL be the value delivered with the surface by the Car App Library surface callback (`SurfaceCallback`), the same value the car renderer uses for its marker, anchor and gesture projection
- A change of the delivered surface DPI SHALL apply to the car renderer and its subsequent frames only
- The surface callback SHALL remain state-retaining: it SHALL NOT reach the native client, and applying the DPI SHALL NOT require a client call from the host thread

#### Scenario: First car frame after surface delivery

- **WHEN** the host delivers a surface with DPI D and the car renderer becomes ready
- **THEN** the first frame is projected at D
- **THEN** the vehicle marker sits on the map content it was projected against

#### Scenario: Surface DPI change

- **WHEN** the host delivers a car surface with a different DPI
- **THEN** subsequently rendered car frames use the new value
- **THEN** no other surface's frames are affected

#### Scenario: Host callback does not touch the native client

- **WHEN** the surface callback retains the delivered DPI
- **THEN** no native client call is made from the host thread to apply it
