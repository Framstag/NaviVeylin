## ADDED Requirements

### Requirement: Favorite add/remove from details screen
The favorite provider SHALL support adding and removing favorite locations, and the details screen SHALL use these operations to save or remove the displayed destination. Adding SHALL persist the destination (name, coordinates) into the favorites store; removing SHALL delete the matching favorite.

#### Scenario: Add favorite from details screen
- **WHEN** the user activates "Add to Favorites" on the details screen
- **THEN** the destination SHALL be added to the favorites store
- **AND** the details screen SHALL show the "Remove from Favorites" action afterwards

#### Scenario: Remove favorite from details screen
- **WHEN** the user activates "Remove from Favorites" on the details screen
- **THEN** the matching favorite SHALL be removed from the favorites store
- **AND** the details screen SHALL show the "Add to Favorites" action afterwards

#### Scenario: Favorite state reflects the store
- **WHEN** the details screen is open
- **AND** the favorites store changes
- **THEN** the details screen SHALL reflect the destination's current favorite state
