# Spec Delta — routing-avoidance

## MODIFIED Requirements

### Requirement: The phone edits the choices and the car reads them

The settings surface SHALL offer the three switches beside the vehicle's maximum speed, and a route
calculated on Android Auto or AAOS SHALL use the values the phone stored. The car SHALL expose no
avoidance control of its own.

#### Scenario: The sheet shows the switches
- **WHEN** the user opens the settings surface's Navigation & Route topic
- **THEN** it shows avoid toll roads, avoid ferry routes and avoid unpaved roads beside the vehicle speed
- **AND** each switch reflects the stored value
- **Case**: none — the switch cases are written when the switches move (tasks.md group 4)

#### Scenario: The car uses the phone's choices
- **WHEN** the phone stores avoid toll roads on and a destination is acquired on the car
- **THEN** the car's route calculation requests toll avoidance
- **Case**: none — the switch cases are written when the switches move (tasks.md group 4)
