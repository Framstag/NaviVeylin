## MODIFIED Requirements

### Requirement: Result item display
Each search result item SHALL display the location label and its region/admin hierarchy. When multiple results share the same label, each item SHALL additionally show distinguishing fields (`objectTypeName`, `postalArea`, region tail) to help users differentiate.

#### Scenario: Result shows label and region
- **WHEN** search results are displayed
- **THEN** each result SHALL show the location name (`label`) and the region hierarchy (`adminRegionHierarchy` or `region` array joined)

#### Scenario: Duplicate results show disambiguation fields
- **WHEN** search results are displayed
- **AND** two or more results share the same `label`
- **THEN** each result in the duplicate group SHALL additionally show `objectTypeName`, `postalArea`, and the most specific `region` component
- **AND** these fields SHALL be formatted as a single detail line (e.g., "restaurant · 44139 · Dortmund")

#### Scenario: Result item is tappable
- **WHEN** user taps a result item
- **THEN** the search panel SHALL dismiss
- **AND** the map SHALL center on the selected location's coordinates
- **AND** a marker SHALL be rendered at the selected location
