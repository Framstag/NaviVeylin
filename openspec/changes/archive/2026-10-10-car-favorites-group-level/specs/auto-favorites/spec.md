# Spec Delta — auto-favorites

## RENAMED Requirements

- FROM: `### Requirement: PlaceListTemplate displays favorites`
- TO: `### Requirement: ListTemplate displays favorites`

## MODIFIED Requirements

### Requirement: ListTemplate displays favorites

The system SHALL display a `ListTemplate` on the Android Auto and Android Automotive OS screen showing the user's favorite locations when they select the favorites option. A place list is not usable here: it requires a distance span on every row, which a saved favourite has not. The all-favorites option SHALL open the group level first (requirement "The all-favorites mode lists groups first"); a group's own favorites are shown when that group is selected.

#### Scenario: Favorites list shown

- **WHEN** user selects favorites from the car screen
- **THEN** a `ListTemplate` is displayed listing the favorites store, starting at the group level in the all-favorites mode

#### Scenario: Empty favorites state

- **WHEN** the user has no saved favorites
- **THEN** the screen shows a "No favorites saved" message

#### Scenario: Favorites list hidden on navigation start

- **WHEN** user starts navigation from a favorite selection
- **THEN** the `ListTemplate` is replaced by the `NavigationTemplate`

#### Scenario: Favorites appear without re-entering the screen

- **WHEN** the favorites screen is open before the favorites store has finished loading
- **THEN** the list updates in place with the loaded favorites once the store is ready, without the user leaving and re-entering the screen

### Requirement: Favorites grouped by category

The system SHALL list favorites grouped by their category/group name when the screen lists all favorites: the group is the listing's unit, and a car list row carries no background tint, so the car's group row shows the group name and the group's favorite count under one wording the phone shares. The starred-favorites mode has its own ordering and header rule (requirement "Starred favorites render as one ordered list").

#### Scenario: Group headers shown

- **WHEN** favorites from multiple groups are displayed in the all-favorites mode
- **THEN** each group SHALL be presented under its group name
- **AND** it SHALL show the group's favorite count
- **AND** it SHALL NOT carry a color indicator, because a car list row has no tinted background

## ADDED Requirements

### Requirement: The all-favorites mode lists groups first

The car's all-favorites mode SHALL present one row per group, in the stored group order, and selecting a group row SHALL show that group's favorites in the stored favorite order. Groups holding no favorites SHALL NOT be listed on the car, because no car affordance can move a favorite into one.

#### Scenario: Group rows follow the stored group order

- **WHEN** the all-favorites mode opens
- **THEN** each group SHALL be one row, in the stored group order, showing the group name and its favorite count
- **AND** a group reorder received while the list is open SHALL reorder the rows in place

#### Scenario: A group row opens that group's favorites

- **WHEN** the driver selects a group row
- **THEN** that group's favorites SHALL be listed in the stored favorite order
- **AND** selecting a favorite SHALL keep starting the destination-picker flow
- **AND** back navigation SHALL return to the group level

#### Scenario: A group holding no favorites is not listed

- **WHEN** a group holds no favorites
- **THEN** the car's group level SHALL NOT list it
- **AND** the phone's group grid SHALL keep listing it

#### Scenario: Groups stay read-only

- **WHEN** the group level is displayed
- **THEN** it SHALL offer no drag, move, rename or color action for a group
