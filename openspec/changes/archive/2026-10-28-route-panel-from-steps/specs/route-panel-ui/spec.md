# route-panel-ui

## MODIFIED Requirements

### Requirement: Turn-by-turn instruction list

After successful route calculation, the route panel SHALL show the route summary inline, below the calculate
button. The route panel SHALL remain open and SHALL NOT be dismissed. The route panel SHALL NOT show the
instruction list separately — the route summary component contains the instructions. The rows of that list
SHALL be derived from the route's structured steps (spec: `route-analysis` — Rows are derived from the route's
steps) and SHALL carry localized text built from the manoeuvre's own values, so a roundabout exit, a motorway
entry, a motorway change and a motorway leave are named as they are named on every other surface, in the
device language (spec: `turn-instruction-localization` — Route analysis rows are localized from the step
model). The list SHALL NOT be built from, or parse, a native description string.

#### Scenario: Route summary dialog triggered after calculation
- **WHEN** route calculation completes successfully
- **THEN** the route summary SHALL be shown inline in the route panel below the calculate button
- **AND** the route panel SHALL remain open
- **AND** the instruction list SHALL NOT be shown separately in the route panel

#### Scenario: Route panel re-opens on summary dismiss
- **WHEN** the route summary dialog is dismissed
- **THEN** the route panel SHALL re-open with all previous state intact (start, destination, vehicle, route)

#### Scenario: Instructions scrollable in summary dialog
- **WHEN** the route summary component is displayed in the route panel
- **THEN** the instruction list SHALL be scrollable within the summary component

#### Scenario: A German route lists German rows

- **WHEN** the device locale is German and a calculated route leaves a roundabout at exit 3
- **THEN** the row SHALL read as the German roundabout-exit wording with the ordinal
- **AND** SHALL NOT read as the native English text
- **Case**: `app: RoutePanelLocalizedRowsTest` "a German route lists a German roundabout row"

#### Scenario: A roundabout and a motorway entry are listed

- **WHEN** a calculated route leaves a roundabout and enters a motorway
- **THEN** the row list SHALL contain a row for each
- **AND** neither row SHALL be missing from a route that has them
- **Case**: `app: RoutePanelLocalizedRowsTest` "roundabout and motorway manoeuvres appear in the list"
