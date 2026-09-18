# about-dialog Specification

## Purpose

Provides users with app identity information including version, description, and open-source licenses in a standard Android dialog.

## Requirements

### Requirement: About dialog displays app name and version
The about dialog SHALL display the application name "NaviVeylin" and the current version name (e.g., "2026-08-19-1") sourced from the built app's version metadata.

#### Scenario: Dialog shows correct app name and version
- **WHEN** the about dialog is open
- **THEN** the dialog SHALL show "NaviVeylin" as the app name
- **AND** the dialog SHALL show the version string from the built app's version metadata

### Requirement: About dialog displays app description
The about dialog SHALL display a brief description of the application explaining it is an Android navigation app built on libosmscout.

#### Scenario: Dialog shows description text
- **WHEN** the about dialog is open
- **THEN** the dialog SHALL show descriptive text about the application

### Requirement: About dialog provides link to project source and licenses

The about dialog SHALL provide a way for users to access the project's source code
via a link to the project repository, and SHALL provide a way to reach the license
information of the components bundled in the running build.

#### Scenario: Licenses link is visible

- **WHEN** the about dialog is open
- **THEN** the dialog SHALL show a link to the project repository

#### Scenario: Licenses link opens repository in browser

- **WHEN** the user taps the repository link
- **THEN** the system SHALL open the project repository URL in the device browser

#### Scenario: License information is reachable in-app

- **WHEN** the about dialog is open on phone or tablet
- **THEN** the dialog SHALL provide a way to reach the bundled dependency license
  list in addition to the project repository link

### Requirement: About dialog is reachable from map screen menu

The about dialog SHALL be accessible from the overflow menu (⋮) on the map canvas screen AND from the main screen menu.

#### Scenario: Menu item opens about dialog
- **GIVEN** the map canvas screen is displayed
- **WHEN** the user taps the overflow menu (⋮)
- **THEN** the menu SHALL show an "About" item
- **WHEN** the user selects "About"
- **THEN** the about dialog SHALL open

#### Scenario: Menu item opens about dialog from main screen
- **GIVEN** the main screen is displayed
- **WHEN** the user taps the menu
- **THEN** the menu SHALL show an "About" item
- **WHEN** the user selects "About"
- **THEN** the about dialog SHALL open

### Requirement: About dialog can be dismissed
The about dialog SHALL have a dismiss action so the user can return to the map.

#### Scenario: Dialog dismisses on close button
- **WHEN** the about dialog is open
- **WHEN** the user taps the close/dismiss button
- **THEN** the dialog SHALL close

#### Scenario: Dialog dismisses on back press
- **WHEN** the about dialog is open
- **WHEN** the user presses the system back button
- **THEN** the dialog SHALL close

### Requirement: About dialog displays author name

The about dialog SHALL display the author name "Tim Teulings" to credit the original libosmscout author.

#### Scenario: Dialog shows author name

- **WHEN** the about dialog is open
- **THEN** the dialog SHALL show "Tim Teulings" as the author

### Requirement: About dialog displays copyright year

The about dialog SHALL display "Copyright 2026" to indicate the copyright year.

#### Scenario: Dialog shows copyright

- **WHEN** the about dialog is open
- **THEN** the dialog SHALL show "Copyright 2026"

### Requirement: About dialog provides link to OSM data licence
The about dialog SHALL provide a link to the OpenStreetMap data licence at https://www.openstreetmap.org/copyright and state that map data is available under the Open Database License (ODbL).

#### Scenario: OSM licence link visible
- **WHEN** the about dialog is open
- **THEN** the dialog SHALL show a link to the OSM data licence

#### Scenario: OSM licence link opens copyright page
- **WHEN** the user taps the OSM licence link
- **THEN** the system SHALL open https://www.openstreetmap.org/copyright in the device browser

### Requirement: About dialog provides the bundled dependency license list

On phone and tablet, the about dialog SHALL provide access to a list of the
third-party components bundled in the running build, showing for each component
its name, version, and license identifier, with the full license text reachable
from that list. The list SHALL be available without network access.

#### Scenario: License list is reachable from the about dialog

- **WHEN** the about dialog is open on phone or tablet
- **THEN** the dialog SHALL offer a way to open the bundled dependency license list
- **WHEN** the user opens it
- **THEN** the list SHALL show the bundled components with name, version, and
  license identifier

#### Scenario: Full license text is reachable per component

- **GIVEN** the bundled dependency license list is open
- **WHEN** the user selects a component whose license text may be distributed
  with the application
- **THEN** the full license text for that component's license SHALL be shown

#### Scenario: License without distributable text links to its source

- **GIVEN** a bundled component whose license is identified by a
  policy-declared `LicenseRef-` and whose text is not distributed with the
  application
- **WHEN** the user selects that component
- **THEN** the license list SHALL identify the license by name and SHALL offer a
  way to open its canonical source address
- **AND** the application SHALL NOT present an empty or missing license text

#### Scenario: License list works offline

- **GIVEN** the device has no network connection
- **WHEN** the user opens the bundled dependency license list and selects a
  component whose license text is distributed with the application
- **THEN** the component list and that license text SHALL be displayed
- **AND** components whose license is only reachable online SHALL remain listed
  with their identifier and name

#### Scenario: List reflects the running build

- **GIVEN** two builds whose bundled dependency sets differ
- **WHEN** the license list is opened in each build
- **THEN** each build SHALL show the components bundled in that build

### Requirement: License list is not surfaced in the car app

Because the car app host constrains non-actionable template rows
(`guidelines/UI.md` §3), the in-car about screen SHALL NOT present the bundled
dependency license list; it SHALL continue to show the app identity and the map
data attribution. This is a deliberate parity deviation between phone and car.

#### Scenario: Car about screen keeps identity and attribution only

- **WHEN** the about screen is opened in the car app
- **THEN** it SHALL show the application name, version, and map data attribution
- **AND** it SHALL NOT present a bundled dependency license list

### Requirement: About dialog shows the application's own license with full text

The bundled license list SHALL include an entry for the application itself,
showing the application's SPDX license identifier (`GPL-3.0-or-later`) and the
full license text, reachable without network access. This satisfies the GNU GPL
§5 obligation (a copy of the license accompanies the program) on every
distribution flavor.

#### Scenario: Application license entry is present

- **WHEN** the user opens the About dialog and then the license list
- **THEN** the list SHALL contain an entry for the application itself
- **AND** the entry SHALL display the license name "GNU General Public License,
  version 3 or later" (or equivalent localised text)
- **AND** the entry SHALL display the SPDX identifier `GPL-3.0-or-later`

#### Scenario: Application license text is available offline

- **WHEN** the user opens the application's license entry from the license list
- **THEN** the full GNU GPL version 3 license text SHALL be displayed
- **AND** the text SHALL be available with no network connection

#### Scenario: License text is present in both flavors

- **WHEN** the license list is opened in the phone/Android Auto flavor and in the
  Android Automotive OS flavor
- **THEN** both SHALL provide the application license entry with its full text
