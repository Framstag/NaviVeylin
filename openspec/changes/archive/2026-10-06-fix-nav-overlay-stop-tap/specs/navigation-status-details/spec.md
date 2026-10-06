# Spec Delta

## MODIFIED Requirements

### Requirement: Routing status card is clickable

During active navigation, the phone's routing status card SHALL be tappable, and the stop control it carries SHALL be a tap target of its own: a tap inside the control's hit area SHALL end navigation, and a tap anywhere else on the card SHALL open the full-screen route description. The two hit areas SHALL NOT overlap. The control's hit area SHALL be at least 48 dp in each dimension.

#### Scenario: Tap opens full-screen details

- **WHEN** the user taps the routing status card during active navigation
- **AND** the tap is outside the stop control's hit area
- **THEN** a full-screen view SHALL open showing the route description

#### Scenario: Tap does not stop navigation

- **WHEN** the user taps the routing status card outside the stop control's hit area
- **THEN** navigation SHALL continue uninterrupted
- **AND** the stop-navigation button SHALL remain available in the expanded view

#### Scenario: Tap at the stop control's centre ends navigation

- **WHEN** the routing status card shows the stop control during active navigation
- **AND** the user taps the centre of the control's hit area
- **THEN** navigation SHALL end
- **AND** the expanded route description SHALL NOT open

#### Scenario: The tap is received by the control, not by the card

- **WHEN** the user taps the centre of the stop control's hit area during active navigation
- **THEN** the navigation stop action SHALL be the action invoked
- **AND** the expanded route description SHALL NOT be opened
- **AND** the card's own action SHALL be the target of a tap outside the control's hit area

#### Scenario: The two targets do not overlap

- **WHEN** the routing status card is shown during active navigation
- **THEN** the stop control's hit area SHALL lie outside the card's own tap area
- **AND** the hit areas SHALL be exposed as distinct accessibility targets, each with its own bounds

#### Scenario: Stop control is large enough to hit

- **WHEN** the routing status card is shown during active navigation
- **THEN** the stop control's hit area SHALL be at least 48 dp wide and 48 dp high

#### Scenario: The expanded view's stop control is its own target too

- **WHEN** the full-screen route description is open during active navigation
- **AND** the user taps the centre of its stop control's hit area
- **THEN** navigation SHALL end
- **AND** the tap SHALL NOT be handled by any surrounding tap area of the expanded view

#### Scenario: The car card carries no stop control

- **WHEN** the routing status card is shown on the Android Auto / Automotive surface
- **THEN** it SHALL NOT show a stop control
- **AND** this deviation from the phone layout is by design, not a parity defect
