# turn-instruction-localization

## ADDED Requirements

### Requirement: Route analysis rows are localized from the step model

The route analysis rows SHALL resolve their text from the route's step model through `:core` resources, in the
device language, for every manoeuvre kind the step model carries — the start, the arrival, the seven turn
moves, roundabout enter and leave, motorway enter, change and leave, and a street-name change. The rows SHALL
NOT display a native description string, and SHALL NOT decide their icon by matching prose.

#### Scenario: A German row names the manoeuvre in German

- **WHEN** the device locale is German and a route's row describes a left turn into a street
- **THEN** the row SHALL read as the German "links abbiegen" wording plus the street
- **AND** SHALL NOT read as the native English text
- **Case**: `app: RoutePanelLocalizedRowsTest` "a German left turn row is localized"

#### Scenario: A motorway change is worded as one

- **WHEN** a row describes keeping left onto another motorway
- **THEN** the row SHALL read as the localized motorway-change wording
- **AND** SHALL NOT read as a plain left turn
- **Case**: `app: RoutePanelLocalizedRowsTest` "a motorway change row is not a plain turn"

#### Scenario: The row icon follows the manoeuvre, not the text

- **WHEN** a row's icon is chosen
- **THEN** it SHALL be chosen from the step's manoeuvre kind and move
- **AND** SHALL NOT be chosen by matching the row's text
- **Case**: `app: RoutePanelLocalizedRowsTest` "the row icon follows the step kind"
