## ADDED Requirements

### Requirement: POIs mode offers a name filter beside category and radius

In POIs mode the dialog SHALL offer a name filter field beside the category dropdown and the radius slider. Typing in it SHALL NOT run a search and SHALL NOT enable the search button. The field SHALL be usable both before a search and after one. Clearing it SHALL restore the unfiltered list of the current results without running a search. The field's label SHALL come from a string resource shared with the car variant (spec: `cross-variant-ui-parity`).

#### Scenario: The field is offered in POIs mode

- **WHEN** the user opens the unified search dialog in POIs mode
- **THEN** a name filter field SHALL be visible beside the category dropdown and the radius slider
- **Case**: `app: PoiSearchPanelComposeTest.theFilterFieldIsOfferedInPoisMode`

#### Scenario: Typing a filter enables nothing

- **WHEN** no category is selected and the user types into the filter field
- **THEN** the search button SHALL stay disabled
- **Case**: `app: PoiSearchPanelComposeTest.typingAFilterEnablesNothing`

#### Scenario: A filter typed before the search applies to it

- **WHEN** the user types a filter and then taps the search button
- **THEN** the search SHALL run and its result list SHALL be narrowed by that filter
- **Case**: `app: PoiSearchPanelComposeTest.aFilterTypedBeforeTheSearchAppliesToIt`

#### Scenario: Clearing the filter restores the list

- **WHEN** results are displayed under a filter and the user clears the field
- **THEN** the unfiltered list SHALL return without a new search
- **Case**: `app: PoiSearchPanelComposeTest.clearingTheFilterRestoresTheList`
