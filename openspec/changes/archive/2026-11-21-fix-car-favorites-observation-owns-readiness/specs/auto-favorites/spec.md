# Spec Delta

## ADDED Requirements

### Requirement: The observation establishes the store it reads

The car favourites screen's observation SHALL own the store readiness it depends on: when the store reports
itself unready at the start of the started period, the observation SHALL initialise it once for that period
with the screen's favourites path, inside its background hop. The host thread SHALL NOT be the thread that
initialises the store, and a store that is already ready SHALL NOT be initialised again by the observation.

#### Scenario: An uninitialised store becomes ready while the screen is open

- **WHEN** the favourites store has not been initialised when the car favourites screen's period starts
- **THEN** the observation SHALL initialise it exactly once, with the screen's favourites path, off the host
  thread
- **AND** the screen SHALL render the store's favourites without the user leaving and re-entering the screen
- (`FavoritesScreenObservationsTest.anUnreadyStoreIsInitialisedOnceAndItsRowsRender`)

#### Scenario: A ready store is not initialised again

- **WHEN** the favourites store has already completed its initialisation
- **THEN** the observation SHALL NOT initialise it
- **AND** the screen SHALL observe the store's current state
- (`FavoritesScreenObservationsTest.aReadyStoreIsNotInitialisedAgain`)

### Requirement: A failed resolution is retried once before the screen is told

The car favourites screen's observation SHALL retry the background hop that resolves its provider once after
a fault, before that observation ends and before the screen is given a failure state. A second fault SHALL
end the observation; the next start SHALL establish it again.

#### Scenario: A transient fault still publishes the state

- **WHEN** the first resolution attempt of a started period throws and the second succeeds
- **THEN** the observation SHALL publish the resolved list state
- **AND** the fault SHALL be logged with the observation's key
- (`FavoritesScreenObservationsTest.anObservationFaultIsRetriedOnceAndTheListPublishes`)

### Requirement: A store that cannot be observed is reported, not left loading

The car favourites screen SHALL report a store whose observation could not produce a state, instead of
keeping its loading row. The loading row means "initialising", which the screen cannot claim once its
observation has ended without a state; the screen SHALL show the store as unavailable instead.

#### Scenario: A store that cannot be resolved is shown as unavailable

- **WHEN** the car favourites screen's observation failed to resolve the store, its one retry included
- **THEN** the screen SHALL show the unavailable row
- **AND** it SHALL NOT show the loading row
- (`FavoritesScreenObservationsTest.aStoreThatFailsTwiceIsReportedAsUnavailable`,
`FavoritesScreenTest.anUnavailableStoreRendersTheUnavailableRow`)