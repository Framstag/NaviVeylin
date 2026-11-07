# Auto Favorites (auto-favorites)

## Purpose

Lets drivers browse and select from their saved favorite locations on the Android Auto car screen using `PlaceListTemplate`, backed by the existing `FavoriteRepository`, so they can navigate to favorites without touching the phone.

## Requirements

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

### Requirement: Favorite displays name and address
The system SHALL display each favorite's name and address/description in the `PlaceListTemplate` list item.

#### Scenario: Favorite details shown
- **WHEN** a favorite is displayed in the list
- **THEN** its name and address/description are visible

### Requirement: Favorite selection triggers destination picker
The system SHALL allow the user to select a favorite, which triggers the destination picker flow to start navigation.

#### Scenario: Select favorite
- **WHEN** user taps a favorite in the list
- **THEN** the system transitions to the destination picker with that location as the target

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

### Requirement: AA place list reflects the stored favorite order

The Android Auto favorites `PlaceListTemplate` SHALL list the favorites of each group in the stored favorite order, read-only, and SHALL update when the stored order changes while the screen is open.

#### Scenario: AA list follows a phone reorder

- **GIVEN** the user reordered the favorites of a group on the phone
- **WHEN** the favorites place list is shown on the car screen
- **THEN** the group's rows SHALL appear in the reordered sequence

#### Scenario: AA list updates in place

- **GIVEN** the favorites place list is open on the car screen
- **WHEN** the stored order changes
- **THEN** the list SHALL update in place with the new order, without leaving and re-entering the screen

#### Scenario: No reorder affordance on the car screen

- **WHEN** the favorites place list is displayed
- **THEN** it SHALL offer no drag or move action for individual favorites
- **AND** selecting a favorite SHALL still start the destination-picker navigation flow

### Requirement: AA place list follows the stored group order
The Android Auto favorites list SHALL present the group headers in the stored group order, read-only, and SHALL update in place when the stored order changes while the screen is open. The order SHALL be taken from the provider's group order flow rather than from the iteration order of the group map, so a change that alters only the sequence is rendered.

#### Scenario: AA headers follow a phone reorder
- **GIVEN** the user moved a group to the first position on the phone
- **WHEN** the favorites list is shown on the car screen
- **THEN** that group's header SHALL appear before the other group headers

#### Scenario: AA headers update in place
- **GIVEN** the favorites list is open on the car screen
- **WHEN** the stored group order changes
- **THEN** the header order SHALL update in place, without leaving and re-entering the screen

#### Scenario: An order-only change reaches the car
- **GIVEN** the car screen shows several groups whose contents do not change
- **WHEN** the stored group order changes
- **THEN** the header sequence SHALL follow the new order

#### Scenario: A group the order does not name yet still appears
- **GIVEN** the stored order does not name a group the store holds
- **WHEN** the car screen renders its headers
- **THEN** that group SHALL still be listed, after the named ones

#### Scenario: No reorder affordance for groups on the car screen
- **WHEN** the favorites list is displayed
- **THEN** it SHALL offer no drag or move action for group headers
- **AND** selecting a favorite SHALL still start the destination-picker navigation flow

#### Scenario: Group order change does not disturb the favorite order
- **GIVEN** a group's favorites are listed on the car screen
- **WHEN** the group order changes
- **THEN** each group's rows SHALL keep their stored favorite order

### Requirement: Starred favorites render as one ordered list

In its starred-favorites mode the Android Auto favorites screen SHALL render the starred favorites in the stored starred order (spec `starred-ordering`) as one ordered list without group headers, read-only, and SHALL update in place when that order changes while the screen is open. A row SHALL keep showing its name and address and SHALL keep starting the destination-picker flow when selected.

#### Scenario: Starred list follows a phone reorder

- **GIVEN** the user reordered the starred favorites on the phone
- **WHEN** the starred-favorites screen is shown on the car screen
- **THEN** the rows SHALL appear in the stored starred order
- **AND** they SHALL be presented as one list without group headers

#### Scenario: Starred list updates in place

- **GIVEN** the starred-favorites screen is open on the car screen
- **WHEN** the stored starred order changes
- **THEN** the list SHALL update in place with the new order, without leaving and re-entering the screen

#### Scenario: No reorder affordance on the car screen

- **WHEN** the starred-favorites screen is displayed
- **THEN** it SHALL offer no drag or move action for individual favorites

#### Scenario: Selecting a starred favorite still navigates

- **WHEN** the user selects a row in the starred-favorites list
- **THEN** the system SHALL transition to the destination picker with that location as the target

#### Scenario: Nothing starred

- **WHEN** no favorite is starred
- **THEN** the screen SHALL show the "no starred favorites" message with its hint

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

### Requirement: The favourites list distinguishes a loading store from an empty one

The car favourites screen SHALL report the favourites store's real state. It SHALL show the loading row while the store's initialisation has not completed or its first state has not been published, SHALL NOT present a store that has not completed its initialisation as empty, and SHALL show the store's actual content — its favourites, or the empty-state message when it genuinely holds none — once the store has completed its initialisation and its state is published.

#### Scenario: An uninitialised store is not reported as empty

- **WHEN** the car favourites screen is opened before the favourites store's initialisation has completed
- **THEN** the screen SHALL show the loading row
- **AND** it SHALL NOT show the empty-state message

#### Scenario: A ready store's published state is shown

- **WHEN** the favourites store has already completed its initialisation and holds favourites, and the screen's observation has published its state
- **THEN** the template SHALL list the favourites
- **AND** it SHALL NOT show the loading row

#### Scenario: A published empty store is not reported as loading

- **WHEN** the favourites store's initialisation has completed, it holds no favourites, and the screen's observation has published its state
- **THEN** the screen SHALL show the empty-state message
- **AND** it SHALL NOT show the loading row

#### Scenario: Readiness completes while the screen is open

- **WHEN** the store's initialisation completes while the car favourites screen is open
- **THEN** the screen SHALL show the store's favourites without the user leaving and re-entering the screen

### Requirement: A resolved store's state is published without waiting for the hop

The car favourites screen SHALL publish an already-resolved favourites store's state without waiting for the
background hop that resolves its provider. A screen start SHALL read the provider's current state when the
provider has resolved the store, and SHALL NOT resolve the provider on the host thread to do so. A provider
that has not resolved the store SHALL hand out no state, so an uninitialised store is never presented as
empty.

#### Scenario: A slow hop does not delay the first template

- **WHEN** the favourites store has completed its initialisation before the car favourites screen starts
- **AND** the observation's background resolution is delayed
- **THEN** the screen SHALL show the store's favourites from the start of the period
- **AND** it SHALL NOT show the loading row
- **AND** the screen SHALL still observe later changes once the background resolution completes
- (`FavoritesScreenObservationsTest.aResolvedStoresStateReachesTheScreenBeforeTheBackgroundHop`)

#### Scenario: An unresolved provider hands out no state

- **WHEN** the favourites store's initialisation has not completed
- **AND** the car favourites screen starts
- **THEN** the provider SHALL hand out no state
- **AND** the screen SHALL show the loading row until its background resolution publishes
- (`FavoritesScreenObservationsTest.anUnresolvedProviderPublishesNothingBeforeTheHop`)

#### Scenario: The provider resolves the store only off the host thread

- **WHEN** a car screen asks a provider that has not resolved the store for its state
- **THEN** the answer SHALL be null
- **AND** the store SHALL NOT be resolved by that call
- (`AutoFavoritesProviderImplTest.snapshotIsNullUntilTheProviderHasResolvedTheStore`)
