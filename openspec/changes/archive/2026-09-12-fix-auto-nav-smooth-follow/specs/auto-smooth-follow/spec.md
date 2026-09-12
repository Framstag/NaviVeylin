## ADDED Requirements

### Requirement: Fix feed from follow-mode screens

The system SHALL feed every GPS fix's speed, heading and timestamp to the map renderer from every follow-mode car screen (browse map, free driving, navigation view) so the extrapolation display loop can run between fixes. When the fix carries no GPS speed or bearing, the screen SHALL derive them from the movement between consecutive fixes before feeding the renderer.

#### Scenario: Routing view feeds speed to the renderer

- **WHEN** a GPS fix arrives while the navigation view is active in follow mode
- **THEN** the screen passes the fix speed, heading and receipt time to the renderer's fix API
- **AND** the extrapolation loop runs and the map glides toward the predicted position between fixes

#### Scenario: Speed derived from movement when GPS speed is missing

- **WHEN** a fix arrives with no GPS speed (speed unknown) while a follow-mode screen is active
- **THEN** the screen computes the speed from the distance travelled since the previous fix over the fix interval
- **AND** passes that derived speed to the renderer so the extrapolation loop keeps running

#### Scenario: Bearing derived from movement when GPS bearing is missing

- **WHEN** a fix arrives with no GPS bearing while a follow-mode screen is active
- **THEN** the screen uses the movement direction between the consecutive fixes as the heading for the prediction
- **AND** keeps the last effective bearing when the fix moved too little to yield a direction
