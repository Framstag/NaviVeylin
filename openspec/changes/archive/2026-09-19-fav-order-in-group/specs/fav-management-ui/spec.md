## Purpose

Extends the favorites management sheet's group detail view with drag-and-drop reordering of the favorites inside a group (phone).

## ADDED Requirements

### Requirement: Group detail list supports drag-and-drop reordering

The group detail view SHALL let the user pick up a favorite row by touch-and-hold and drop it at another position within the same group. The list SHALL show the dragged row following the finger and the surrounding rows shifting to make room. Only the released position SHALL be committed to the store.

#### Scenario: Drag a favorite to the top

- **GIVEN** a group lists several favorites
- **WHEN** the user holds the last favorite row and drags it above the first row, then releases
- **THEN** the list SHALL show that favorite first
- **AND** the new order SHALL be persisted

#### Scenario: Dragged row follows the finger

- **WHEN** the user drags a favorite row
- **THEN** the row SHALL be visually lifted and follow the drag
- **AND** the other rows SHALL shift to show the resulting position

#### Scenario: Nothing is persisted during the drag

- **WHEN** the user is dragging a favorite row
- **THEN** the favorites store SHALL NOT be written until the drop is completed

#### Scenario: Reordering a single-favorite group

- **GIVEN** a group holds exactly one favorite
- **WHEN** the user drags that row
- **THEN** no order change SHALL be committed
- **AND** the app SHALL NOT crash

### Requirement: Reorder gesture only applies to favorite rows

The group detail list's leading "Add favorite" row SHALL NOT be draggable and SHALL NOT be counted as a favorite position.

#### Scenario: Header row is not draggable

- **WHEN** the user holds the "Add favorite" row
- **THEN** no drag SHALL start
- **AND** tapping it SHALL still open the add-favorite dialog

#### Scenario: First favorite stays the first list entry after a drag

- **WHEN** the user drops the first favorite row below a later row
- **THEN** the "Add favorite" row SHALL remain above all favorites

### Requirement: Drag end commits the visible order; no-op and aborted drags write nothing

The order shown when a drag ends SHALL be the order that is stored: the end of a completed drag commits the position the user sees, whether the gesture ended by releasing or by a system-cancelled gesture (the drag library reports both as the end of the drag). A drag that ends where it started SHALL write nothing. A drag aborted because the favorites sheet is dismissed mid-drag SHALL write nothing and the next opening of the sheet SHALL show the stored order. A dragged favorite that disappears mid-drag SHALL NOT be committed.

#### Scenario: Released drag commits the visible order

- **WHEN** the user releases a dragged favorite at a new position
- **THEN** the order visible at that moment SHALL be persisted

#### Scenario: Same-position drag writes nothing

- **WHEN** a drag ends with the favorite at the position it started from
- **THEN** the favorites store SHALL NOT be written

#### Scenario: System-cancelled gesture commits the visible order

- **GIVEN** the user is dragging a favorite and has moved it to another position
- **WHEN** the gesture is cancelled by the system instead of being released
- **THEN** the order visible at that moment SHALL be persisted

#### Scenario: Sheet dismissed during a drag

- **GIVEN** the user is dragging a favorite
- **WHEN** the favorites sheet closes (back gesture or sheet dismissal) before the drag ends
- **THEN** the favorites store SHALL NOT be written
- **AND** the next opening of the sheet SHALL show the stored order

#### Scenario: Dragged favorite deleted mid-drag

- **GIVEN** the user is dragging a favorite row
- **WHEN** that favorite is removed from the group
- **THEN** the drag SHALL end without committing an order
- **AND** the app SHALL NOT crash

### Requirement: Row actions remain available

Star, rename, and delete actions on a favorite row SHALL keep working unchanged, and a tap on a row SHALL still center the map on the favorite.

#### Scenario: Buttons still work

- **WHEN** the user taps the star, rename, or delete control on a favorite row
- **THEN** the corresponding action SHALL run as before

#### Scenario: Tap opens the favorite

- **WHEN** the user taps the row body without holding
- **THEN** the map SHALL center on that favorite and the sheet SHALL close
- **AND** no reorder SHALL be started

### Requirement: Reorder commits are serialised

While a reorder is being persisted, a further reorder commit SHALL be ignored rather than interleaved.

#### Scenario: Second drop during an in-flight reorder

- **GIVEN** a reorder is being persisted
- **WHEN** the user completes another reorder before the first one finished
- **THEN** the second commit SHALL be dropped
- **AND** the list SHALL afterwards show the order of the completed move

### Requirement: Search results are not reorderable

The favorites sheet's search-results view SHALL list matches in stored order and SHALL NOT offer the drag interaction.

#### Scenario: No drag in search results

- **WHEN** the user searches for favorites
- **THEN** the matching rows SHALL be shown in stored order
- **AND** holding a result row SHALL NOT start a drag
