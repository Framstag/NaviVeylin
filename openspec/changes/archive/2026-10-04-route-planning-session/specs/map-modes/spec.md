# Spec Delta

## ADDED Requirements

### Requirement: Planning session suspends the drive preset
While a route-planning session is active and the map mode is FREE_DRIVE, the drive preset SHALL be suspended exactly as a manual map interaction suspends it: follow and auto-zoom SHALL be off, and no follow-triggered camera move SHALL occur during the session. The session SHALL NOT introduce a fourth map mode — the mode SHALL remain BROWSE, FREE_DRIVE or NAVIGATION as derived. Ending the session SHALL leave the preset suspended, with the re-center affordance available to restore it.

#### Scenario: Session suspends free drive

- **WHEN** the map mode is FREE_DRIVE and the user opens a route-planning session
- **THEN** the drive preset SHALL be suspended (follow and auto-zoom off)
- **AND** the session's own camera fit SHALL be the only camera move during the session

#### Scenario: Session is not a map mode

- **WHEN** a route-planning session is active and navigation is not
- **THEN** the reported map mode SHALL still be BROWSE or FREE_DRIVE according to the follow state
- **AND** no fourth mode SHALL be reported

#### Scenario: Ending the session offers re-center

- **WHEN** a route-planning session opened from FREE_DRIVE ends
- **THEN** follow SHALL remain off
- **AND** the re-center button SHALL be visible so the driver can restore the standard drive values
