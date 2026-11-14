# settings-surface Specification

## Purpose
Owns the phone's settings surfaces: the bounded quick sheet the map's gear opens, the full-height settings surface behind it, how each kind of row behaves there, and the phone's access to the settings Android Auto owns.

## Requirements

### Requirement: The quick sheet holds only the current mode's live controls

The quick sheet SHALL contain auto-zoom and orientation in FREE_DRIVE and NAVIGATION, orientation only in BROWSE, and, in every map state, a last row that opens the settings surface. It SHALL contain no other setting control.

#### Scenario: Driving modes show the live controls

- **WHEN** the map state is FREE_DRIVE or NAVIGATION and the quick sheet is open
- **THEN** the sheet shows the auto-zoom switch and the orientation choice
- **AND** its last row opens the settings surface
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

#### Scenario: Browse shows orientation only

- **WHEN** the map state is BROWSE and the quick sheet is open
- **THEN** the sheet shows the orientation choice and the entry row to the settings surface
- **AND** it shows no auto-zoom switch
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

#### Scenario: No other setting leaks into the sheet

- **WHEN** the quick sheet is enumerated for controls
- **THEN** every control it holds is either auto-zoom, orientation or the entry row
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

### Requirement: The settings surface is a full-height overlay inside the map screen

The settings surface SHALL render full-height above the map chrome inside the map screen's modal overlay, and SHALL NOT be a navigation destination. Opening it SHALL leave the map frame, follow state and route session unchanged.

#### Scenario: The map keeps running behind the surface

- **WHEN** the map is following the vehicle and the user opens the settings surface
- **THEN** the renderer keeps producing frames and follow mode stays engaged
- **AND** a calculated route is still in progress after the surface is dismissed
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

#### Scenario: The surface covers the full height

- **WHEN** the settings surface is opened
- **THEN** it fills the screen height above the system bars
- **AND** no map control from the chrome band is tappable while it is open
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

#### Scenario: Dismissing returns the untouched map

- **WHEN** the user opens the surface and dismisses it without changing anything
- **THEN** the map shows the same camera, mode and follow state as before
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

### Requirement: Three entry points, one surface at a time

The settings surface SHALL be reachable from the map menu's Settings entry, from the start screen's menu entry, and from the quick sheet's entry row. Opening it SHALL close the quick sheet, and no second settings surface SHALL be open at the same time.

#### Scenario: The map menu opens the surface

- **WHEN** the user selects Settings in the map menu
- **THEN** the settings surface opens at its root
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

#### Scenario: The start screen menu opens the surface

- **WHEN** no map is shown and the user selects Settings in the start screen's menu
- **THEN** the settings surface opens at its root
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

#### Scenario: The sheet's entry row opens the surface

- **WHEN** the quick sheet is open and the user selects its last row
- **THEN** the quick sheet closes and the settings surface opens
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

### Requirement: The surface opens at the root and dismisses to the map

Opening the surface SHALL show the topic root. System back SHALL close a topic page to the root and the root to the map, and SHALL NOT change the map mode, camera, follow state or route.

#### Scenario: Back leaves a topic page for the root

- **WHEN** a topic page is open and the user presses back
- **THEN** the root is shown
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

#### Scenario: Back leaves the root for the map

- **WHEN** the root is open and the user presses back
- **THEN** the surface closes and the map is shown in the state it had before
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

### Requirement: Every persisted phone setting is editable from the surface

The settings surface SHALL expose every persisted setting the phone can change, each exactly once, in the topic that owns its subject, and SHALL render those rows in every map state.

#### Scenario: Karte carries the map's presentation and handling

- **WHEN** the user opens the Karte topic
- **THEN** the page offers map style, POI symbols, dark mode with its ambient-light sensitivity, auto-zoom, orientation, render mode, keep screen on and the long-press delay
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

#### Scenario: Navigation & Route carries routing values

- **WHEN** the user opens the Navigation & Route topic
- **THEN** the page offers the three vehicle maximum speeds, the three avoidance switches and the vehicle anchors for routing and free driving
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

#### Scenario: Hinweise & Sprache carries the announcement values

- **WHEN** the user opens the Hinweise & Sprache topic
- **THEN** the page offers both voice guidance switches, GPS announcements, lane hints and the overspeed warning delta
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

#### Scenario: The surface is not mode-dependent

- **WHEN** the map state is BROWSE and the settings surface is opened
- **THEN** the same rows are offered as in NAVIGATION
- **AND** no row is hidden because of the map state
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

### Requirement: The phone edits the settings Android Auto owns

The settings surface SHALL present the car's voice guidance and both car vehicle anchors as rows marked for Android Auto and SHALL let the phone change them. A car anchor that still inherits the phone's value SHALL show that state and SHALL offer returning to it.

#### Scenario: The phone changes a car value

- **WHEN** the user changes the car's voice guidance on the phone
- **THEN** the next Android Auto session reads the changed value
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

#### Scenario: An inheriting anchor is shown as inheriting

- **WHEN** a car anchor has never been set on the phone
- **THEN** its row states that it uses the phone's value
- **AND** the row offers returning to that state after it was set
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

#### Scenario: Returning to the phone value

- **WHEN** the user selects the return-to-phone-value option on a car anchor row
- **THEN** the car anchor no longer holds its own value
- **AND** the car follows the phone's anchor from then on
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

### Requirement: A change applies immediately and persists

Every change on the surface SHALL apply to the running map without a save action and SHALL be persisted to the shared settings storage as it is made; reopening the surface or restarting the app SHALL show the new value.

#### Scenario: A change persists without a save action

- **WHEN** the user switches POI symbols off
- **THEN** the map reflects it immediately
- **AND** after an app restart the surface shows the switch off
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

#### Scenario: A shared change reaches the car

- **WHEN** the user moves the overspeed warning delta on the phone
- **THEN** the persisted value is the one Android Auto reads
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

### Requirement: A topic page groups its rows under section titles

A topic page SHALL render its rows as one scrolling column grouped under section titles, and SHALL omit a section whose rows do not apply to the current state.

#### Scenario: A page shows its groups

- **WHEN** the user opens the Karte topic
- **THEN** its rows appear under the section titles that own them
- **AND** the section titles are not tappable
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

#### Scenario: An inapplicable section is omitted

- **WHEN** dark mode is not Automatic and the Karte topic is open
- **THEN** the ambient-light sensitivity row is not shown
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

### Requirement: A finite value set is presented as options, never only by cycling

A row whose setting has a finite set of values SHALL present those values with the current one marked; it SHALL NOT be reachable only by repeated taps. A set of at most four values SHALL be presented inline as a selection list, a larger set on a selection surface of its own.

#### Scenario: Dark mode lists its three values

- **WHEN** the dark mode row is displayed
- **THEN** Off, On and Automatic are offered as a selection list with the current value marked
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

#### Scenario: Map style opens a selection surface

- **WHEN** the user selects the map style row
- **THEN** a surface opens listing every available style with the current one marked
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

### Requirement: A numeric range is a slider with labelled endpoints

A setting with a numeric range SHALL render as a slider that shows its current value and the meaning of both endpoints, and every permitted value SHALL be reachable.

#### Scenario: The overspeed delta is a bounded slider

- **WHEN** the overspeed warning row is displayed
- **THEN** it shows its current value in km/h and a slider covering 0 to 30 km/h
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

#### Scenario: A vehicle speed shows its full range

- **WHEN** the car's maximum speed row is displayed
- **THEN** every whole value from 30 to 160 km/h is reachable
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

### Requirement: A link row leaves the surface

A row that opens another screen SHALL render as a link row with a trailing chevron and SHALL carry no inline control.

#### Scenario: Karten & Daten opens the Map Manager

- **WHEN** the user selects the Karten & Daten entry
- **THEN** the Map Manager screen opens
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

#### Scenario: About opens the existing dialog

- **WHEN** the user selects the Über NaviVeylin entry
- **THEN** the app's About dialog opens
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)

### Requirement: The surface is one column at every window size

The settings surface SHALL lay out as a single scrolling column on phone, foldable and tablet widths. A two-pane wide layout is out of scope for this change; the narrow- and wide-width shell is owned by the backlog item that introduces width classes.

#### Scenario: A tablet shows one column

- **WHEN** the surface is opened on a 900 dp wide window
- **THEN** the rows render in one column at full width
- **Case**: none — none of these rows exists on HEAD: the case is written with the surface rows (tasks.md groups 2-5)
