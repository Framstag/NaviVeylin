# Spec Delta

## MODIFIED Requirements

### Requirement: Route statistics displayed
The route summary dialog SHALL display key route statistics: total distance and estimated travel time. The total distance SHALL be the route's length as its step list sums it (spec: `osmscout-jni` — One route length for a calculated route), so the dialog's statistic and its step list state one length.

#### Scenario: Distance shown
- **WHEN** the route summary dialog is displayed
- **THEN** the total route distance SHALL be shown (e.g., "12.4 km")
- **AND** it SHALL equal the sum of the dialog's listed steps' distances within rounding

#### Scenario: Estimated time shown
- **WHEN** the route summary dialog is displayed
- **THEN** the estimated travel time SHALL be shown (e.g., "~25 min")
