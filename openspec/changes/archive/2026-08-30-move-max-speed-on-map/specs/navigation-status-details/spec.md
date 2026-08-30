# navigation-status-details Specification

## MODIFIED Requirements

### Requirement: Full-screen view shows status content

The expanded view SHALL keep the routing status content visible: current road name and the ETA / remaining time / remaining distance stats. The current and max speed are no longer part of the status content — they are shown by the on-map speed widget.

#### Scenario: Status content preserved

- **WHEN** the full-screen view is open
- **THEN** the current road name SHALL be shown
- **AND** the ETA, remaining time, and remaining distance SHALL be shown

#### Scenario: Speed not shown in expanded view

- **WHEN** the full-screen view is open
- **THEN** the current and max speed SHALL NOT be shown in the expanded view
