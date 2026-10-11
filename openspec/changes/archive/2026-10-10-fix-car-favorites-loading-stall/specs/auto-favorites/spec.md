# Spec Delta — auto-favorites

## ADDED Requirements

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
