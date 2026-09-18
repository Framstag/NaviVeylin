## ADDED Requirements

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

## MODIFIED Requirements

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
