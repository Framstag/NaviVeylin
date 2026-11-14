# settings-taxonomy Specification

## Purpose
Owns the durable shape of the app's settings: which topic a setting belongs to, which surface it applies to, how a surface-specific row is marked, where a new setting goes, and how a row carries help text — so the same setting is found at the same place on the phone and on Android Auto.

## Requirements

### Requirement: The settings root holds six topics and no deeper level

The settings root SHALL present exactly these six entries, in this order: Karte, Navigation & Route, Hinweise & Sprache, Karten & Daten, Erweitert, Über NaviVeylin. Selecting a topic SHALL open its rows, and no page below the root SHALL open another category page.

#### Scenario: Root lists the six topics

- **WHEN** the user opens the settings surface
- **THEN** the root shows exactly the six entries Karte, Navigation & Route, Hinweise & Sprache, Karten & Daten, Erweitert and Über NaviVeylin
- **AND** the root shows no individual setting row
- **Case**: none — none of these rows exists on HEAD: the case is written with the registry and renderer rows (tasks.md groups 1-2)

#### Scenario: A topic page holds rows, not categories

- **WHEN** the user selects "Navigation & Route"
- **THEN** the page lists that topic's setting rows under section titles
- **AND** no row on that page opens a further category page
- **Case**: none — none of these rows exists on HEAD: the case is written with the registry and renderer rows (tasks.md groups 1-2)

#### Scenario: A link row leaves the surface

- **WHEN** the user selects "Karten & Daten"
- **THEN** the app opens the Map Manager screen
- **AND** no map-source or download control is edited inside the settings surface
- **Case**: none — none of these rows exists on HEAD: the case is written with the registry and renderer rows (tasks.md groups 1-2)

### Requirement: A new setting joins an existing topic

A new user-changeable setting SHALL be added to exactly one existing topic, and SHALL appear on exactly one page. A topic whose rows exceed twelve SHALL be split into section groups on the same page rather than into a new page, so the root stays at six entries.

#### Scenario: A crowded topic grows groups, not pages

- **WHEN** a topic holds more than twelve setting rows
- **THEN** its page groups the rows under section titles
- **AND** the root still shows the same six entries
- **Case**: none — none of these rows exists on HEAD: the case is written with the registry and renderer rows (tasks.md groups 1-2)

#### Scenario: A setting appears once

- **WHEN** the settings surface is searched page by page for a given setting key
- **THEN** the key appears in exactly one topic page
- **Case**: none — none of these rows exists on HEAD: the case is written with the registry and renderer rows (tasks.md groups 1-2)

### Requirement: Every row declares the surface it belongs to

Every setting row SHALL declare one scope: shared (one stored value that both surfaces read and either may edit), phone-only, or car-only. A shared row SHALL belong to the topic its subject names; a surface-specific row SHALL sit in the same topic as the shared row of its subject.

#### Scenario: A shared row carries one value

- **WHEN** the user changes the GPS announcements row on the phone
- **THEN** the car reads the changed value on its next settings load
- **AND** no second GPS announcements row exists for the car
- **Case**: none — none of these rows exists on HEAD: the case is written with the registry and renderer rows (tasks.md groups 1-2)

#### Scenario: A phone-only row is absent from the car

- **WHEN** the car's settings screens are enumerated
- **THEN** no row for keep-screen-on, long-press delay or the phone's own voice switch is present
- **Case**: none — none of these rows exists on HEAD: the case is written with the registry and renderer rows (tasks.md groups 1-2)

### Requirement: A surface-specific row is marked, a shared row is not

A row whose scope is phone-only or car-only SHALL carry a leading surface icon with a content description naming that surface. A shared row SHALL carry no such icon.

#### Scenario: A car-only row is marked for the car

- **WHEN** the user opens "Hinweise & Sprache" on the phone
- **THEN** the car's voice guidance row carries a leading car icon whose content description names Android Auto
- **Case**: none — none of these rows exists on HEAD: the case is written with the registry and renderer rows (tasks.md groups 1-2)

#### Scenario: A shared row carries no mark

- **WHEN** the GPS announcements row is displayed
- **THEN** it carries neither a phone nor a car icon
- **Case**: none — none of these rows exists on HEAD: the case is written with the registry and renderer rows (tasks.md groups 1-2)

#### Scenario: A screen reader names the surface

- **WHEN** a screen reader focuses a phone-only row
- **THEN** it announces the row's label and the surface the row applies to
- **Case**: none — none of these rows exists on HEAD: the case is written with the registry and renderer rows (tasks.md groups 1-2)

### Requirement: A row carries a short help line and may carry a long one

Each setting row SHALL be able to show a short help line beneath its label, and, where a long explanation exists, SHALL offer that text on a surface of its own. A row without help text SHALL render as a single-line row; the short help line SHALL NOT truncate or replace the label.

#### Scenario: A short help line sits under the label

- **WHEN** a row that declares a short help line is displayed
- **THEN** the label remains fully visible and the help line renders beneath it in the supporting text style
- **Case**: none — none of these rows exists on HEAD: the case is written with the registry and renderer rows (tasks.md groups 1-2)

#### Scenario: A long explanation opens on its own surface

- **WHEN** the user opens the explanation of a row that declares a long help text
- **THEN** the long text is shown on a surface of its own
- **AND** the setting value is unchanged when that surface is dismissed
- **Case**: none — none of these rows exists on HEAD: the case is written with the registry and renderer rows (tasks.md groups 1-2)

#### Scenario: A row without help text stays single-line

- **WHEN** a row that declares no help text is displayed
- **THEN** it shows only its label and its control
- **Case**: none — none of these rows exists on HEAD: the case is written with the registry and renderer rows (tasks.md groups 1-2)

### Requirement: Both surfaces use one topic vocabulary and one label set

The phone surface and the car surface SHALL use the same topic names and the same row labels for the settings they share, and a surface-specific row SHALL appear only on the surface its scope names.

#### Scenario: The car's voice row carries the phone's label

- **WHEN** the voice guidance row for the car is displayed on the phone and on the car
- **THEN** both surfaces show the same label text
- **Case**: none — none of these rows exists on HEAD: the case is written with the registry and renderer rows (tasks.md groups 1-2)

#### Scenario: A surface shows only its own rows

- **WHEN** the car presents the settings it can change
- **THEN** every row it shows is either shared or car-only
- **Case**: none — none of these rows exists on HEAD: the case is written with the registry and renderer rows (tasks.md groups 1-2)
