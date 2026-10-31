# Spec Delta

## ADDED Requirements

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
