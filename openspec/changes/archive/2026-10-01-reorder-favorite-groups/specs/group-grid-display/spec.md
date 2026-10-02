# Spec Delta

## ADDED Requirements

### Requirement: Group grid supports drag-and-drop reordering

The group grid SHALL let the user pick up a group card by touch-and-hold and drop it at another position in the grid. The grid SHALL show the picked-up card following the finger and the surrounding cards shifting to make room. Only the released position SHALL be committed to the store, and the order shown when the drag ends SHALL be the order that is stored.

#### Scenario: Drag a group to the first position

- **GIVEN** the grid shows several groups
- **WHEN** the user holds the last group card, drags it before the first card and releases
- **THEN** the grid SHALL show that group first
- **AND** the new order SHALL be persisted

#### Scenario: Dragged card follows the finger

- **WHEN** the user drags a group card
- **THEN** the card SHALL be visually lifted and follow the drag
- **AND** the other cards SHALL shift to show the resulting position

#### Scenario: Nothing is persisted during the drag

- **WHEN** the user is dragging a group card
- **THEN** the favorites store SHALL NOT be written until the drop is completed

#### Scenario: Grid shows the stored order on open

- **WHEN** the favorites sheet is opened
- **THEN** the cards SHALL appear in the stored group order

#### Scenario: Dragging a group that holds no favorites reorders the grid

- **GIVEN** the grid shows groups whose favorite lists are empty
- **WHEN** the user drags one of them to another position and releases
- **THEN** the grid SHALL show that group at the new position
- **AND** the new order SHALL be persisted

### Requirement: Group drag writes nothing when it commits nothing

A group drag that ends where it started SHALL write nothing. A drag aborted because the favorites sheet is dismissed mid-drag SHALL write nothing, and the next opening of the sheet SHALL show the stored order. A group that disappears mid-drag (deleted or renamed while it was being dragged) SHALL NOT be committed, and the app SHALL NOT crash.

#### Scenario: Same-position drag writes nothing

- **WHEN** a group drag ends with the card at the position it started from
- **THEN** the favorites store SHALL NOT be written

#### Scenario: Sheet dismissed during a drag

- **GIVEN** the user is dragging a group card
- **WHEN** the favorites sheet closes before the drag ends
- **THEN** the favorites store SHALL NOT be written
- **AND** the next opening of the sheet SHALL show the stored order

#### Scenario: Dragged group deleted mid-drag

- **GIVEN** the user is dragging a group card
- **WHEN** that group is deleted
- **THEN** the drag SHALL end without committing an order
- **AND** the app SHALL NOT crash

#### Scenario: Single group cannot be reordered

- **GIVEN** the store holds exactly one group
- **WHEN** the user drags that card
- **THEN** no order change SHALL be committed
- **AND** the app SHALL NOT crash

### Requirement: Group card tap and action menu survive the drag affordance

A tap on a group card SHALL still open that group's detail view, and the card's action menu (Set Color, Rename, Delete) SHALL keep working unchanged. Only a hold SHALL start a drag.

#### Scenario: Tap opens the group

- **WHEN** the user taps a card body without holding
- **THEN** the group detail view SHALL open
- **AND** no reorder SHALL be started

#### Scenario: Menu actions still work

- **WHEN** the user opens a card's action menu and selects Set Color, Rename or Delete
- **THEN** the corresponding action SHALL run as before

### Requirement: Group reorder commits are serialised

While a group reorder is being persisted, a further group reorder commit SHALL be ignored rather than interleaved.

#### Scenario: Second drop during an in-flight reorder

- **GIVEN** a group reorder is being persisted
- **WHEN** the user completes another group reorder before the first one finished
- **THEN** the second commit SHALL be dropped
- **AND** the grid SHALL afterwards show the order of the completed move
