# auto Delta

## REMOVED Requirements

### Requirement: Stop navigation action

The system SHALL provide a stop navigation action on the `NavigationTemplate` that ends the active navigation.

#### Scenario: Stop navigation from car

- **WHEN** user taps the stop navigation action on the car screen
- **THEN** navigation stops on both the car screen and the phone

**Reason**: The explicit stop action is removed from the navigation view — system back is the single stop affordance (defined in `auto/navigation-view` — "Leave navigation at any time"). Two redundant stop affordances on the same screen were confusing.

**Migration**: System back on the navigation screen stops navigation (see `auto/navigation-view` — "Leave navigation at any time"); the session then returns the display to the root menu.
