## MODIFIED Requirements

### Requirement: Search results reusable for route location picking
The search panel SHALL be reusable for picking start and destination locations in the route panel. When opened from the route panel, the search panel SHALL behave identically to the map screen search, but selecting a result SHALL set the chosen route field instead of centering the map and opening the details sheet.

#### Scenario: Search opens from route start field
- **WHEN** user taps the search icon on the route panel start field
- **THEN** the search panel SHALL open with the existing search-as-you-type behavior
- **AND** selecting a result SHALL set it as the route start location
- **AND** the search panel SHALL close

#### Scenario: Search opens from route destination field
- **WHEN** user taps the search icon on the route panel destination field
- **THEN** the search panel SHALL open with the existing search-as-you-type behavior
- **AND** selecting a result SHALL set it as the route destination location
- **AND** the search panel SHALL close

#### Scenario: Search panel dismiss without selection
- **WHEN** the search panel is open from the route panel
- **AND** user dismisses the search panel without selecting a result
- **THEN** the route field SHALL remain unchanged
